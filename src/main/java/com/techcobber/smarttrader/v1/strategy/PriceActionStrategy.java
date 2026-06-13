package com.techcobber.smarttrader.v1.strategy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.strategy.CandlePatternDetector.DetectedPattern;
import com.techcobber.smarttrader.v1.strategy.CandlePatternDetector.PatternBias;
import com.techcobber.smarttrader.v1.strategy.MultiTimeframeAnalyzer.MultiTimeframeResult;
import com.techcobber.smarttrader.v1.strategy.SupportResistanceDetector.Level;
import com.techcobber.smarttrader.v1.strategy.SupportResistanceDetector.LevelType;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendDirection;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PriceActionStrategy implements TradingStrategy {

	private static final int    MIN_CANDLES                   = 20;
	private static final int    SR_LOOKBACK                   = 50;
	private static final int    TREND_LOOKBACK                = 20;

	// Entry-filter thresholds (overridden per-user via UserPreferences)
	private static final double DEFAULT_EMA50_THRESHOLD_PCT      = 3.0;
	private static final double DEFAULT_SUPPORT_PROXIMITY_PCT    = 2.0;
	/** Price must be at least this far below resistance to enter a BUY. */
	private static final double DEFAULT_RESISTANCE_PROXIMITY_PCT = 2.0;
	private static final double DEFAULT_ATR_SPIKE_MULTIPLIER     = 2.0;

	// Candidate validation constants
	/** Minimum reward-to-risk ratio; candidates below this are invalidated. */
	private static final double MIN_RR                    = 2.0;
	/** Minimum edge score (out of {@value #MAX_SCORE_NORM}) to fire a signal. */
	private static final int    MIN_SCORE                 = 4;
	/** Trend strength above this threshold is considered "strong". */
	private static final double STRONG_TREND_THRESHOLD    = 0.5;
	/** Score denominator for confidence normalisation. */
	private static final double MAX_SCORE_NORM            = 12.0;
	/** S/R range below this percentage is treated as a chop zone — no trade. */
	private static final double MIN_RANGE_PCT             = 3.0;
	/** ATR fraction used as buffer on stop and target levels for realistic R:R. */
	private static final double ATR_BUFFER_MULTIPLIER     = 0.5;
	/** EMA distance below this percentage is treated as neutral — no EMA alignment bonus. */
	private static final double EMA_NEUTRAL_THRESHOLD     = 0.5;

	private final SupportResistanceDetector srDetector            = new SupportResistanceDetector();
	private final TrendAnalyzer             trendAnalyzer          = new TrendAnalyzer();
	private final CandlePatternDetector     patternDetector        = new CandlePatternDetector();
	private final EmaIndicator              emaIndicator           = new EmaIndicator();
	private final AtrIndicator              atrIndicator           = new AtrIndicator();
	private final ConsolidationDetector     consolidationDetector  = new ConsolidationDetector();

	// -----------------------------------------------------------------------
	// Private value types — carry all intermediate analysis results
	// -----------------------------------------------------------------------

	/**
	 * Carries every indicator, filter flag, and computed boolean needed by
	 * {@link #evaluateCandidates} so that {@link #analyze} stays thin.
	 */
	private record AnalysisContext(
			double  currentPrice,
			double  ema50,
			double  ema9,
			double  ema21,
			double  atr,
			double  distanceFromEma50Pct,
			double  recentSwingLow,
			boolean consolidationDetected,
			boolean atrSpike,
			Double  nearestSupport,
			Double  nearestResistance,
			List<Level>           allLevels,
			TrendResult           trend,
			List<DetectedPattern> patterns,
			boolean isAboveEMA,
			boolean bullishLocation,
			boolean bearishLocation,
			boolean nearSupport,
			boolean nearResistance,
			boolean htfBullish,
			boolean htfBearish,
			double  ema50ThresholdPct,
			double  resistanceProximityPct,
			MultiTimeframeResult  mtfResult
	) {}

	/** Outcome of the full candidate pipeline (signal + edge score + R:R). */
	private record SignalResult(Signal signal, int score, double rr) {}

	// -----------------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------------

	@Override
	public String getName() {
		return "PriceAction";
	}

	@Override
	public TradeDecision analyze(List<MyCandle> candles, String productId) {
		return analyze(candles, productId, null, null, null);
	}

	/**
	 * Full analysis with optional Higher Time Frame context.
	 *
	 * @param candles             1H candles (entry timeframe)
	 * @param productId           trading pair identifier
	 * @param mtfResult           optional MTF alignment; BUY blocked if HTF DOWN, SELL blocked if HTF UP
	 * @param prefs               optional user-preference overrides; {@code null} → defaults
	 * @param consolidationOverride when non-null, bypasses the built-in consolidation detector
	 */
	public TradeDecision analyze(List<MyCandle> candles, String productId,
			MultiTimeframeResult mtfResult,
			com.techcobber.smarttrader.v1.models.UserPreferences prefs,
			Boolean consolidationOverride) {

		log.info("=== PriceAction Strategy Analysis ===");
		log.info("Analyzing {} candles", candles == null ? 0 : candles.size());

		// Guard: insufficient data
		if (candles == null || candles.size() < MIN_CANDLES) {
			log.warn("Insufficient candles (need at least {}, got {})",
					MIN_CANDLES, candles == null ? 0 : candles.size());
			return TradeDecision.builder()
					.signal(Signal.HOLD).confidence(0.0)
					.reasoning("Insufficient data: need at least " + MIN_CANDLES + " candles")
					.detectedPatterns(List.of()).trendDirection("UNKNOWN")
					.consolidationDetected(false)
					.timestamp(LocalDateTime.now(ZoneOffset.UTC))
					.build();
		}

		// Section 0: compute all indicators, S/R, trend, patterns, and location flags
		AnalysisContext ctx = buildContext(candles, prefs, mtfResult, consolidationOverride);

		// Section 1: consolidation → immediate HOLD (chop zone)
		if (ctx.consolidationDetected()) {
			log.info("HOLD: consolidating market — skipping entry");
			return TradeDecision.builder()
					.signal(Signal.HOLD).confidence(0.0)
					.reasoning("Consolidating market — no directional entry")
					.detectedPatterns(List.of()).trendDirection("SIDEWAYS")
					.ema50(ctx.ema50()).atr(ctx.atr())
					.distanceFromEma50Pct(ctx.distanceFromEma50Pct())
					.consolidationDetected(true)
					.htfTrendDirection(mtfResult != null ? mtfResult.getHtfTrend().name() : null)
					.confirmTrendDirection(mtfResult != null ? mtfResult.getConfirmTrend().name() : null)
					.timestamp(LocalDateTime.now(ZoneOffset.UTC))
					.build();
		}

		// Sections 2–8: full candidate pipeline → signal + score + R:R
		SignalResult result = evaluateCandidates(ctx);

		List<String> patternNames = ctx.patterns().stream()
				.map(DetectedPattern::getName).toList();
		double confidence = normalizeScore(result.signal(), result.score());
		String reasoning  = buildReasoning(result.signal(), ctx.trend(), ctx.patterns(),
				ctx.currentPrice(), ctx.nearestSupport(), ctx.nearestResistance());

		log.info("=== Product: {} | Signal: {} | Confidence: {} | Score: {} | R:R: {} | Patterns: {} ===",
				productId, result.signal(), String.format("%.2f", confidence),
				result.score(), String.format("%.2f", result.rr()), patternNames);

		return TradeDecision.builder()
				.signal(result.signal())
				.confidence(confidence)
				.reasoning(reasoning)
				.detectedPatterns(patternNames)
				.trendDirection(ctx.trend().getDirection().name())
				.nearestSupport(ctx.nearestSupport())
				.nearestResistance(ctx.nearestResistance())
				.ema50(ctx.ema50())
				.atr(ctx.atr())
				.distanceFromEma50Pct(Math.round(ctx.distanceFromEma50Pct() * 100.0) / 100.0)
				.consolidationDetected(false)
				.nearResistanceDetected(ctx.nearResistance())
				.htfTrendDirection(mtfResult != null ? mtfResult.getHtfTrend().name() : null)
				.confirmTrendDirection(mtfResult != null ? mtfResult.getConfirmTrend().name() : null)
				.entryScore(confidence)
				.timestamp(LocalDateTime.now(ZoneOffset.UTC))
				.build();
	}

	// -----------------------------------------------------------------------
	// Section 0: build analysis context
	// -----------------------------------------------------------------------

	private AnalysisContext buildContext(List<MyCandle> candles,
			com.techcobber.smarttrader.v1.models.UserPreferences prefs,
			MultiTimeframeResult mtfResult,
			Boolean consolidationOverride) {

		double currentPrice = candles.get(candles.size() - 1).getClose();

		// Indicators
		double ema50 = emaIndicator.calculate(candles, 50);
		double ema9  = emaIndicator.calculate(candles, 9);
		double ema21 = emaIndicator.calculate(candles, 21);
		double atr   = atrIndicator.calculate(candles);
		double distanceFromEma50Pct = ema50 > 0 ? ((currentPrice - ema50) / ema50) * 100.0 : 0.0;
		double recentSwingLow = atrIndicator.getRecentSwingLow(candles, 5);

		log.info("EMA50={} | EMA9={} | EMA21={} | ATR={} | distanceFromEMA50={}%",
				String.format("%.4f", ema50), String.format("%.4f", ema9),
				String.format("%.4f", ema21), String.format("%.4f", atr),
				String.format("%.2f", distanceFromEma50Pct));

		// Section 1 flags (consolidation acted on in analyze(); atrSpike acted on in §6)
		boolean consolidationDetected = consolidationOverride != null
				? consolidationOverride
				: consolidationDetector.detect(candles, TREND_LOOKBACK).isConsolidating();
		double spikeMultiplier = parseDoubleOrDefault(
				prefs != null ? prefs.getAtrSpikeMultiplier() : null, DEFAULT_ATR_SPIKE_MULTIPLIER);
		boolean atrSpike = atrIndicator.isSpike(candles, spikeMultiplier);
		if (atrSpike) log.info("ATR spike detected — volatility context will gate entries (§6)");

		// Step 1: S/R detection
		log.info("--- Step 1: Support / Resistance Detection ---");
		List<Level> levels     = srDetector.detectLevels(candles, SR_LOOKBACK);
		List<Level> supports   = levels.stream().filter(l -> l.getType() == LevelType.SUPPORT).toList();
		List<Level> resistances = levels.stream().filter(l -> l.getType() == LevelType.RESISTANCE).toList();

		log.debug("Supports: {}", supports.stream()
				.map(l -> String.format("%.2f(%d)", l.getPrice(), l.getStrength())).toList());
		log.debug("Resistances: {}", resistances.stream()
				.map(l -> String.format("%.2f(%d)", l.getPrice(), l.getStrength())).toList());

		Double nearestSupport = supports.stream()
				.map(Level::getPrice).filter(p -> p < currentPrice).max(Double::compareTo).orElse(null);
		Double nearestResistance = resistances.stream()
				.map(Level::getPrice).filter(p -> p > currentPrice).min(Double::compareTo).orElse(null);

		log.info("Price: {} | Support: {} | Resistance: {}",
				String.format("%.2f", currentPrice),
				nearestSupport    != null ? String.format("%.2f", nearestSupport)    : "none",
				nearestResistance != null ? String.format("%.2f", nearestResistance) : "none");

		// Step 2: Trend analysis
		log.info("--- Step 2: Trend Analysis ---");
		TrendResult trend = trendAnalyzer.analyzeTrend(candles, TREND_LOOKBACK);
		log.info("Trend: {} (strength: {})", trend.getDirection(), String.format("%.2f", trend.getStrength()));

		// Step 3: Pattern detection
		log.info("--- Step 3: Pattern Detection ---");
		List<DetectedPattern> patterns = patternDetector.detectPatterns(candles);
		long bullishPats = patterns.stream().filter(p -> p.getBias() == PatternBias.BULLISH).count();
		long bearishPats = patterns.stream().filter(p -> p.getBias() == PatternBias.BEARISH).count();
		long neutralPats = patterns.stream().filter(p -> p.getBias() == PatternBias.NEUTRAL).count();
		log.info("Patterns — Bullish: {}, Bearish: {}, Neutral: {}", bullishPats, bearishPats, neutralPats);

		// Section 2: location analysis (direction-aware, pseudocode-aligned)
		double ema50ThresholdPct      = parseDoubleOrDefault(
				prefs != null ? prefs.getEma50ThresholdPct() : null, DEFAULT_EMA50_THRESHOLD_PCT);
		double supportProximityPct    = parseDoubleOrDefault(
				prefs != null ? prefs.getSupportProximityPct() : null, DEFAULT_SUPPORT_PROXIMITY_PCT);
		double resistanceProximityPct = parseDoubleOrDefault(
				prefs != null ? prefs.getResistanceProximityPct() : null, DEFAULT_RESISTANCE_PROXIMITY_PCT);

		boolean isAboveEMA      = currentPrice >= ema50;
		// BUY-side: price is above EMA and not overextended
		boolean pullbackToEMA   = isAboveEMA  && distanceFromEma50Pct <= ema50ThresholdPct;
		// SELL-side: price is below EMA but not already overextended to the downside
		boolean rejectionFromEMA = !isAboveEMA && Math.abs(distanceFromEma50Pct) <= ema50ThresholdPct;

		boolean nearSupport    = nearestSupport != null
				&& ((currentPrice - nearestSupport) / currentPrice * 100.0) <= supportProximityPct;
		boolean nearResistance = nearestResistance != null
				&& ((nearestResistance - currentPrice) / currentPrice * 100.0) <= resistanceProximityPct;

		// Both location conditions require price to be on the correct side of EMA
		boolean bullishLocation = isAboveEMA  && (nearSupport    || pullbackToEMA);
		boolean bearishLocation = !isAboveEMA && (nearResistance || rejectionFromEMA);

		log.info("Location — isAboveEMA={} | pullback={} | rejection={} | nearSupport={} | nearResistance={} (gap={}%, thr={}%) | bullish={} | bearish={}",
				isAboveEMA, pullbackToEMA, rejectionFromEMA, nearSupport, nearResistance,
				nearestResistance != null
						? String.format("%.2f", (nearestResistance - currentPrice) / currentPrice * 100.0) : "N/A",
				resistanceProximityPct, bullishLocation, bearishLocation);

		// Section 3: HTF alignment (null-safe: absent MTF data never blocks a candidate)
		boolean htfBullish = mtfResult == null || mtfResult.getHtfTrend() == TrendDirection.UP;
		boolean htfBearish = mtfResult == null || mtfResult.getHtfTrend() == TrendDirection.DOWN;
		if (mtfResult != null) {
			log.info("HTF(1D): {} | htfBullish={} | htfBearish={}",
					mtfResult.getHtfTrend(), htfBullish, htfBearish);
		}

		return new AnalysisContext(
				currentPrice, ema50, ema9, ema21, atr, distanceFromEma50Pct, recentSwingLow,
				consolidationDetected, atrSpike,
				nearestSupport, nearestResistance, levels,
				trend, patterns,
				isAboveEMA, bullishLocation, bearishLocation, nearSupport, nearResistance,
				htfBullish, htfBearish,
				ema50ThresholdPct, resistanceProximityPct,
				mtfResult);
	}

	// -----------------------------------------------------------------------
	// Sections 2–8: candidate pipeline (pseudocode-aligned)
	// -----------------------------------------------------------------------

	private SignalResult evaluateCandidates(AnalysisContext ctx) {

	    double price = ctx.currentPrice();

	    long bullishCount = ctx.patterns().stream()
	            .filter(p -> p.getBias() == PatternBias.BULLISH).count();
	    long bearishCount = ctx.patterns().stream()
	            .filter(p -> p.getBias() == PatternBias.BEARISH).count();

	    // ----------------------------------------------------------------
	    // 0. NO-TRADE ZONE (tight range filter)
	    // ----------------------------------------------------------------
	    if (ctx.nearestSupport() != null && ctx.nearestResistance() != null) {
	        double rangePct = (ctx.nearestResistance() - ctx.nearestSupport()) / price * 100.0;

	        if (rangePct < MIN_RANGE_PCT) {
	            log.info("HOLD: tight range detected ({}%) — no-trade zone",
	                    String.format("%.2f", rangePct));
	            return new SignalResult(Signal.HOLD, 0, 1.0);
	        }
	    }

	    // ----------------------------------------------------------------
	    // 1. PROTECTIVE SELL (but NOT into support)
	    // ----------------------------------------------------------------
	    boolean emaCrossDown    = ctx.ema9() < ctx.ema21();
	    boolean priceBelowEma50 = price < ctx.ema50();
	    boolean momentumSell    = emaCrossDown && priceBelowEma50;
	    boolean structureBreak  = price < ctx.recentSwingLow();

	    if ((momentumSell || structureBreak)
	            && ctx.trend().getDirection() != TrendDirection.UP) {

	        if (ctx.nearSupport()) {
	            log.info("Protective exit suppressed: price near support — potential bounce; continuing to candidate evaluation");
	        } else {
	            String reason = momentumSell
	                    ? "EMA momentum cross (EMA9<EMA21 + below EMA50)"
	                    : "Bearish structure break below recent swing low";

	            log.info("SELL signal (protective exit): {}", reason);
	            return new SignalResult(Signal.SELL, MIN_SCORE, 1.0);
	        }
	    }

	    // ----------------------------------------------------------------
	    // 2. CANDIDATE BUILDING
	    // ----------------------------------------------------------------
	    boolean longCandidate = ctx.trend().getDirection() == TrendDirection.UP
	            && ctx.bullishLocation()
	            && !ctx.nearResistance()
	            && ctx.htfBullish();

	    boolean shortCandidate = ctx.trend().getDirection() == TrendDirection.DOWN
	            && ctx.bearishLocation()
	            && !ctx.nearSupport()
	            && ctx.htfBearish();

	    log.info("--- Step 2: Candidates — long={} | short={} ---", longCandidate, shortCandidate);

	    // ----------------------------------------------------------------
	    // 3. RISK:REWARD WITH BUFFERS (realistic)
	    // ----------------------------------------------------------------
	    double longRR = 1.0;
	    double shortRR = 1.0;

	    double atr = ctx.atr();
	    double buffer = atr * ATR_BUFFER_MULTIPLIER;

	    if (longCandidate && ctx.nearestSupport() != null && ctx.nearestResistance() != null) {

	        double risk = price - (ctx.nearestSupport() - buffer);
	        double reward = (ctx.nearestResistance() - buffer) - price;

	        if (risk > 0 && reward > 0) {
	            longRR = reward / risk;
	        } else {
	            log.info("Long R:R degenerate (reward={}) — buffer={} consumed target margin; candidate rejected",
	                    String.format("%.4f", reward), String.format("%.4f", buffer));
	            longCandidate = false;
	        }

	        if (longCandidate && longRR < MIN_RR) {
	            log.info("Long rejected: R:R {} < {}", String.format("%.2f", longRR), MIN_RR);
	            longCandidate = false;
	        }
	    }

	    if (shortCandidate && ctx.nearestSupport() != null && ctx.nearestResistance() != null) {

	        double risk = (ctx.nearestResistance() + buffer) - price;
	        double reward = price - (ctx.nearestSupport() + buffer);

	        if (risk > 0 && reward > 0) {
	            shortRR = reward / risk;
	        } else {
	            log.info("Short R:R degenerate (reward={}) — buffer={} consumed target margin; candidate rejected",
	                    String.format("%.4f", reward), String.format("%.4f", buffer));
	            shortCandidate = false;
	        }

	        if (shortCandidate && shortRR < MIN_RR) {
	            log.info("Short rejected: R:R {} < {}", String.format("%.2f", shortRR), MIN_RR);
	            shortCandidate = false;
	        }
	    }

	    log.info("--- Step 3: R:R — long={} | short={} ---",
	            String.format("%.2f", longRR), String.format("%.2f", shortRR));

	    // ----------------------------------------------------------------
	    // 4. ATR VOLATILITY FILTER
	    // ----------------------------------------------------------------
	    if (ctx.atrSpike()) {
	        boolean breakout =
	                (ctx.nearestResistance() != null && price > ctx.nearestResistance()) ||
	                (ctx.nearestSupport() != null && price < ctx.nearestSupport());

	        if (!breakout) {
	            log.info("ATR spike without breakout — blocking trades");
	            longCandidate = false;
	            shortCandidate = false;
	        } else {
	            log.info("ATR spike + breakout — allowed");
	        }
	    }

	    // ----------------------------------------------------------------
	    // 5. SCORING SYSTEM
	    // ----------------------------------------------------------------
	    int longScore = 0;
	    int shortScore = 0;

	    // Trend strength
	    if (ctx.trend().getStrength() > STRONG_TREND_THRESHOLD) {
	        longScore += 2;
	        shortScore += 2;
	    }

	    // Pattern scoring (context-aware: half credit when trend does not align)
	    if (ctx.trend().getDirection() == TrendDirection.UP) {
	        longScore += (int) Math.min(bullishCount, 3);
	    } else {
	        longScore += (int) Math.min(bullishCount, 3) / 2;
	    }

	    if (ctx.trend().getDirection() == TrendDirection.DOWN) {
	        shortScore += (int) Math.min(bearishCount, 3);
	    } else {
	        shortScore += (int) Math.min(bearishCount, 3) / 2;
	    }

	    // Location
	    if (ctx.nearSupport()) longScore += 2;
	    if (ctx.nearResistance()) shortScore += 2;

	    // EMA alignment (with neutrality)
	    double emaDistance = Math.abs(ctx.distanceFromEma50Pct());
	    if (emaDistance > EMA_NEUTRAL_THRESHOLD) {
	        if (ctx.isAboveEMA()) longScore += 1;
	        else shortScore += 1;
	    }

	    // HTF bonus
	    if (ctx.htfBullish() && ctx.mtfResult() != null) longScore += 2;
	    if (ctx.htfBearish() && ctx.mtfResult() != null) shortScore += 2;

	    // R:R bonus
	    if (longRR > 3.0) longScore += 2;
	    else if (longRR > 2.0) longScore += 1;

	    if (shortRR > 3.0) shortScore += 2;
	    else if (shortRR > 2.0) shortScore += 1;

	    // Zero scores if candidate invalid
	    if (!longCandidate) longScore = 0;
	    if (!shortCandidate) shortScore = 0;

	    log.info("--- Step 5: Scores — long={} | short={} ---", longScore, shortScore);

	    // ----------------------------------------------------------------
	    // 6. FINAL DECISION
	    // ----------------------------------------------------------------
	    // Note: longCandidate (requires trend==UP) and shortCandidate (requires trend==DOWN)
	    // are mutually exclusive by construction — no conflict resolution needed.
	    if (longCandidate && longScore >= MIN_SCORE) {
	        log.info("BUY: score={} R:R={}", longScore, String.format("%.2f", longRR));
	        return new SignalResult(Signal.BUY, longScore, longRR);
	    }

	    if (shortCandidate && shortScore >= MIN_SCORE) {
	        log.info("SELL: score={} R:R={}", shortScore, String.format("%.2f", shortRR));
	        return new SignalResult(Signal.SELL, shortScore, shortRR);
	    }

	    log.info("HOLD: no valid trade (long={}, short={})", longCandidate, shortCandidate);
	    return new SignalResult(Signal.HOLD, 0, 1.0);
	}
	// -----------------------------------------------------------------------
	// Confidence normalisation: 0.30 at MIN_SCORE, scales linearly to 1.0
	// -----------------------------------------------------------------------

	private double normalizeScore(Signal signal, int score) {
		if (signal == Signal.HOLD) return 0.0;
		double normalized = 0.3 + Math.max(0, score - MIN_SCORE) * (0.7 / (MAX_SCORE_NORM - MIN_SCORE));
		return Math.round(Math.min(1.0, normalized) * 100.0) / 100.0;
	}

	// -----------------------------------------------------------------------
	// Reasoning builder (format preserved for test expectations)
	// -----------------------------------------------------------------------

	private String buildReasoning(Signal signal, TrendResult trend,
			List<DetectedPattern> patterns, double currentPrice,
			Double nearestSupport, Double nearestResistance) {

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Signal: %s. ", signal));
		sb.append(String.format("Trend: %s (strength: %.2f). ", trend.getDirection(), trend.getStrength()));

		if (!patterns.isEmpty()) {
			String patternSummary = patterns.stream()
					.map(p -> p.getName() + "(" + p.getBias() + ")")
					.collect(Collectors.joining(", "));
			sb.append(String.format("Patterns: [%s]. ", patternSummary));
		} else {
			sb.append("No significant patterns detected. ");
		}

		if (nearestSupport    != null) sb.append(String.format("Support: %.2f. ",    nearestSupport));
		if (nearestResistance != null) sb.append(String.format("Resistance: %.2f. ", nearestResistance));
		sb.append(String.format("Price: %.2f.", currentPrice));
		return sb.toString();
	}

	private double parseDoubleOrDefault(String value, double defaultValue) {
		if (value == null || value.isBlank()) return defaultValue;
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
}
