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
import com.techcobber.smarttrader.v1.strategy.ConsolidationDetector.ConsolidationResult;
import com.techcobber.smarttrader.v1.strategy.MultiTimeframeAnalyzer.MultiTimeframeResult;
import com.techcobber.smarttrader.v1.strategy.SupportResistanceDetector.Level;
import com.techcobber.smarttrader.v1.strategy.SupportResistanceDetector.LevelType;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendDirection;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PriceActionStrategy implements TradingStrategy {

	private static final int MIN_CANDLES = 20;
	private static final int SR_LOOKBACK = 50;
	private static final int TREND_LOOKBACK = 20;

	// Default entry filter thresholds (overridden by UserPreferences when available)
	private static final double DEFAULT_EMA50_THRESHOLD_PCT   = 3.0;
	private static final double DEFAULT_SUPPORT_PROXIMITY_PCT = 2.0;
	private static final double DEFAULT_ATR_SPIKE_MULTIPLIER  = 2.0;

	private final SupportResistanceDetector srDetector       = new SupportResistanceDetector();
	private final TrendAnalyzer             trendAnalyzer     = new TrendAnalyzer();
	private final CandlePatternDetector     patternDetector   = new CandlePatternDetector();
	private final EmaIndicator              emaIndicator      = new EmaIndicator();
	private final AtrIndicator              atrIndicator      = new AtrIndicator();
	private final ConsolidationDetector     consolidationDetector = new ConsolidationDetector();

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
	 * @param candles     1H candles (entry timeframe)
	 * @param productId   trading pair identifier
	 * @param mtfResult   optional MTF alignment result; when provided, BUY is blocked if HTF is DOWN
	 * @param prefs       optional user preferences for threshold overrides; {@code null} uses defaults
	 */
	public TradeDecision analyze(List<MyCandle> candles, String productId,
			MultiTimeframeResult mtfResult,
			com.techcobber.smarttrader.v1.models.UserPreferences prefs,
			Boolean consolidationOverride) {

		log.info("=== PriceAction Strategy Analysis ===");
		log.info("Analyzing {} candles", candles == null ? 0 : candles.size());

		if (candles == null || candles.size() < MIN_CANDLES) {
			log.warn("Insufficient candles for analysis (need at least {}, got {})",
					MIN_CANDLES, candles == null ? 0 : candles.size());
			return TradeDecision.builder()
					.signal(Signal.HOLD)
					.confidence(0.0)
					.reasoning("Insufficient data: need at least " + MIN_CANDLES + " candles")
					.detectedPatterns(List.of())
					.trendDirection("UNKNOWN")
					.consolidationDetected(false)
					.timestamp(LocalDateTime.now(ZoneOffset.UTC))
					.build();
		}

		MyCandle latest = candles.get(candles.size() - 1);
		double currentPrice = latest.getClose();

		// --- Pre-flight: Compute EMA50 and ATR ---
		double ema50 = emaIndicator.calculate(candles, 50);
		double atr   = atrIndicator.calculate(candles);
		double distanceFromEma50Pct = ema50 > 0 ? ((currentPrice - ema50) / ema50) * 100.0 : 0.0;

		log.info("EMA50={} | ATR={} | distanceFromEMA50={}%",
				String.format("%.4f", ema50),
				String.format("%.4f", atr),
				String.format("%.2f", distanceFromEma50Pct));

		// --- Pre-flight: Consolidation check ---
		boolean consolidationDetected = consolidationOverride != null
				? consolidationOverride
				: consolidationDetector.detect(candles, TREND_LOOKBACK).isConsolidating();

		if (consolidationDetected) {
			log.info("HOLD: consolidating market detected — skipping entry");
			return TradeDecision.builder()
					.signal(Signal.HOLD)
					.confidence(0.0)
					.reasoning("Consolidating market — no directional entry")
					.detectedPatterns(List.of())
					.trendDirection("SIDEWAYS")
					.ema50(ema50)
					.atr(atr)
					.distanceFromEma50Pct(distanceFromEma50Pct)
					.consolidationDetected(true)
					.htfTrendDirection(mtfResult != null ? mtfResult.getHtfTrend().name() : null)
					.confirmTrendDirection(mtfResult != null ? mtfResult.getConfirmTrend().name() : null)
					.timestamp(LocalDateTime.now(ZoneOffset.UTC))
					.build();
		}

		// --- Pre-flight: ATR spike check ---
		double spikeMultiplier = parseDoubleOrDefault(prefs != null ? prefs.getAtrSpikeMultiplier() : null, DEFAULT_ATR_SPIKE_MULTIPLIER);
		if (atrIndicator.isSpike(candles, spikeMultiplier)) {
			log.info("HOLD: ATR spike detected — high volatility, skipping entry");
			return TradeDecision.builder()
					.signal(Signal.HOLD)
					.confidence(0.0)
					.reasoning("ATR spike — high volatility, no entry")
					.detectedPatterns(List.of())
					.trendDirection("UNKNOWN")
					.ema50(ema50)
					.atr(atr)
					.distanceFromEma50Pct(distanceFromEma50Pct)
					.consolidationDetected(false)
					.htfTrendDirection(mtfResult != null ? mtfResult.getHtfTrend().name() : null)
					.confirmTrendDirection(mtfResult != null ? mtfResult.getConfirmTrend().name() : null)
					.timestamp(LocalDateTime.now(ZoneOffset.UTC))
					.build();
		}

		// Step 1: Support/Resistance
		log.info("--- Step 1: Support/Resistance Detection ---");
		List<Level> levels = srDetector.detectLevels(candles, SR_LOOKBACK);

		List<Level> supports = levels.stream()
				.filter(l -> l.getType() == LevelType.SUPPORT)
				.toList();
		List<Level> resistances = levels.stream()
				.filter(l -> l.getType() == LevelType.RESISTANCE)
				.toList();

		log.debug("Support levels: {}", supports.stream()
				.map(l -> String.format("%.2f (strength:%d)", l.getPrice(), l.getStrength()))
				.toList());
		log.debug("Resistance levels: {}", resistances.stream()
				.map(l -> String.format("%.2f (strength:%d)", l.getPrice(), l.getStrength()))
				.toList());

		Double nearestSupport = supports.stream()
				.map(Level::getPrice)
				.filter(p -> p < currentPrice)
				.max(Double::compareTo)
				.orElse(null);
		Double nearestResistance = resistances.stream()
				.map(Level::getPrice)
				.filter(p -> p > currentPrice)
				.min(Double::compareTo)
				.orElse(null);

		log.info("Current price: {} | Nearest support: {} | Nearest resistance: {}",
				String.format("%.2f", currentPrice),
				nearestSupport != null ? String.format("%.2f", nearestSupport) : "none",
				nearestResistance != null ? String.format("%.2f", nearestResistance) : "none");

		// Step 2: Trend Analysis
		log.info("--- Step 2: Trend Analysis ---");
		TrendResult trend = trendAnalyzer.analyzeTrend(candles, TREND_LOOKBACK);
		log.info("Trend analysis: {} (strength: {})", trend.getDirection(), String.format("%.2f", trend.getStrength()));

		// Step 3: Candle Pattern Detection
		log.info("--- Step 3: Candle Pattern Detection ---");
		List<DetectedPattern> detectedPatterns = patternDetector.detectPatterns(candles);

		List<String> patternNames = detectedPatterns.stream()
				.map(DetectedPattern::getName)
				.toList();
		log.debug("Detected candle patterns: {}", patternNames);

		long bullishPatterns = detectedPatterns.stream()
				.filter(p -> p.getBias() == PatternBias.BULLISH)
				.count();
		long bearishPatterns = detectedPatterns.stream()
				.filter(p -> p.getBias() == PatternBias.BEARISH)
				.count();
		long neutralPatterns = detectedPatterns.stream()
				.filter(p -> p.getBias() == PatternBias.NEUTRAL)
				.count();

		log.info("Pattern bias breakdown — Bullish: {}, Bearish: {}, Neutral: {}",
				bullishPatterns, bearishPatterns, neutralPatterns);

		// Step 4: Location filter (BUY-side only)
		double ema50ThresholdPct   = parseDoubleOrDefault(prefs != null ? prefs.getEma50ThresholdPct() : null, DEFAULT_EMA50_THRESHOLD_PCT);
		double supportProximityPct = parseDoubleOrDefault(prefs != null ? prefs.getSupportProximityPct() : null, DEFAULT_SUPPORT_PROXIMITY_PCT);

		boolean onEMAPullback  = distanceFromEma50Pct <= ema50ThresholdPct;
		boolean nearSupport    = nearestSupport != null
				&& ((currentPrice - nearestSupport) / currentPrice * 100.0) <= supportProximityPct;
		boolean locationOK = onEMAPullback || nearSupport;

		log.info("Location filter — EMA50 distance={}% (threshold={}%) | nearSupport={} | locationOK={}",
				String.format("%.2f", distanceFromEma50Pct), ema50ThresholdPct, nearSupport, locationOK);

		// --- Pre-flight: HTF BUY block ---
		boolean htfBlocksBuy = mtfResult != null && mtfResult.getHtfTrend() == TrendDirection.DOWN;
		if (htfBlocksBuy) {
			log.info("HTF(1D) is DOWN — BUY signals suppressed for this product");
		}

		// Step 5: Signal determination
		log.info("--- Step 5: Signal Determination ---");
		Signal signal = determineSignal(trend, detectedPatterns, currentPrice,
				nearestSupport, nearestResistance, levels,
				locationOK, htfBlocksBuy, candles, ema50);

		// Step 6: Confidence calculation
		double confidence = calculateConfidence(signal, trend, detectedPatterns,
				currentPrice, nearestSupport, nearestResistance);

		// Step 7: Build reasoning
		String reasoning = buildReasoning(signal, trend, detectedPatterns,
				currentPrice, nearestSupport, nearestResistance);

		log.info("=== Product: {} | Signal: {} | Confidence: {} | Patterns: {} | Reasoning: {} ===",
				productId, signal, String.format("%.2f", confidence), patternNames, reasoning);

		return TradeDecision.builder()
				.signal(signal)
				.confidence(confidence)
				.reasoning(reasoning)
				.detectedPatterns(patternNames)
				.trendDirection(trend.getDirection().name())
				.nearestSupport(nearestSupport)
				.nearestResistance(nearestResistance)
				.ema50(ema50)
				.atr(atr)
				.distanceFromEma50Pct(Math.round(distanceFromEma50Pct * 100.0) / 100.0)
				.consolidationDetected(false)
				.htfTrendDirection(mtfResult != null ? mtfResult.getHtfTrend().name() : null)
				.confirmTrendDirection(mtfResult != null ? mtfResult.getConfirmTrend().name() : null)
				.entryScore(confidence)
				.timestamp(LocalDateTime.now(ZoneOffset.UTC))
				.build();
	}

	private Signal determineSignal(TrendResult trend, List<DetectedPattern> patterns,
			double currentPrice, Double nearestSupport, Double nearestResistance,
			List<Level> allLevels, boolean locationOK, boolean htfBlocksBuy,
			List<MyCandle> candles, double ema50) {

		long bullishCount = patterns.stream().filter(p -> p.getBias() == PatternBias.BULLISH).count();
		long bearishCount = patterns.stream().filter(p -> p.getBias() == PatternBias.BEARISH).count();

		// ----------------------------------------------------------------
		// SELL / Exit signals — evaluated first; aggressive for SPOT
		// ----------------------------------------------------------------

		// EMA crossover momentum exit: EMA9 crosses below EMA21 and price is below EMA50
		double ema9  = emaIndicator.calculate(candles, 9);
		double ema21 = emaIndicator.calculate(candles, 21);
		boolean emaCrossDown     = ema9 < ema21;
		boolean priceBelowEma50  = currentPrice < ema50;
		boolean momentumSell     = emaCrossDown && priceBelowEma50;

		// Bearish structure break: price below recent swing low (last 5 candles)
		double recentSwingLow    = atrIndicator.getRecentSwingLow(candles, 5);
		boolean structureBreak   = currentPrice < recentSwingLow;

		if ((momentumSell || structureBreak) && trend.getDirection() != TrendDirection.UP) {
			String reason = momentumSell ? "EMA momentum cross (EMA9<EMA21 + below EMA50)" : "Bearish structure break";
			log.info("SELL signal (aggressive exit): {}", reason);
			return Signal.SELL;
		}

		// Classic SELL patterns
		boolean breakdownBelow = false;
		if (nearestSupport != null) {
			double supportTolerance = nearestSupport * 0.002;
			breakdownBelow = currentPrice < nearestSupport - supportTolerance;
			if (breakdownBelow) {
				log.info("Breakdown detected: price {} below support {}", String.format("%.2f", currentPrice), String.format("%.2f", nearestSupport));
			}
		}

		boolean rejectedAtResistance = false;
		if (nearestResistance != null) {
			double proximity = Math.abs(currentPrice - nearestResistance) / nearestResistance;
			rejectedAtResistance = proximity < 0.005 && bearishCount > 0;
			if (rejectedAtResistance) {
				log.info("Rejection at resistance {} with bearish patterns", String.format("%.2f", nearestResistance));
			}
		}

		if (breakdownBelow && trend.getDirection() == TrendDirection.DOWN && bearishCount > 0) {
			log.info("SELL signal: breakdown below support in downtrend with bearish patterns");
			return Signal.SELL;
		}

		if (rejectedAtResistance && trend.getDirection() != TrendDirection.UP) {
			log.info("SELL signal: rejection at resistance with bearish patterns and non-bullish trend");
			return Signal.SELL;
		}

		if (bearishCount >= 2 && trend.getDirection() == TrendDirection.DOWN) {
			log.info("SELL signal: multiple bearish patterns ({}) aligned with downtrend", bearishCount);
			return Signal.SELL;
		}

		// ----------------------------------------------------------------
		// BUY signals — gated by location filter and HTF alignment
		// ----------------------------------------------------------------

		if (htfBlocksBuy) {
			log.info("BUY blocked: HTF(1D) trend is DOWN");
			return Signal.HOLD;
		}

		if (!locationOK) {
			log.info("BUY blocked: location filter — price not near EMA50 pullback or support");
			return Signal.HOLD;
		}

		boolean breakoutAbove = false;
		if (nearestResistance != null) {
			double resistanceTolerance = nearestResistance * 0.002;
			breakoutAbove = currentPrice > nearestResistance + resistanceTolerance;
			if (breakoutAbove) {
				log.info("Breakout detected: price {} above resistance {}", String.format("%.2f", currentPrice), String.format("%.2f", nearestResistance));
			}
		}

		boolean bouncedAtSupport = false;
		if (nearestSupport != null) {
			double proximity = Math.abs(currentPrice - nearestSupport) / nearestSupport;
			bouncedAtSupport = proximity < 0.005 && bullishCount > 0;
			if (bouncedAtSupport) {
				log.info("Bounce at support {} with bullish patterns", String.format("%.2f", nearestSupport));
			}
		}

		if (breakoutAbove && trend.getDirection() == TrendDirection.UP && bullishCount > 0) {
			log.info("BUY signal: breakout above resistance in uptrend with bullish patterns");
			return Signal.BUY;
		}

		if (bouncedAtSupport && trend.getDirection() != TrendDirection.DOWN) {
			log.info("BUY signal: bounce at support with bullish patterns and non-bearish trend");
			return Signal.BUY;
		}

		if (bullishCount >= 2 && trend.getDirection() == TrendDirection.UP) {
			log.info("BUY signal: multiple bullish patterns ({}) aligned with uptrend", bullishCount);
			return Signal.BUY;
		}

		log.info("HOLD signal: no strong confluence detected (bullish: {}, bearish: {}, trend: {})",
				bullishCount, bearishCount, trend.getDirection());
		return Signal.HOLD;
	}

	private double calculateConfidence(Signal signal, TrendResult trend,
			List<DetectedPattern> patterns, double currentPrice,
			Double nearestSupport, Double nearestResistance) {

		if (signal == Signal.HOLD) {
			return 0.0;
		}

		double confidence = 0.3;

		// Trend alignment bonus
		boolean trendAligned = (signal == Signal.BUY && trend.getDirection() == TrendDirection.UP)
				|| (signal == Signal.SELL && trend.getDirection() == TrendDirection.DOWN);
		if (trendAligned) {
			confidence += 0.2 * trend.getStrength();
			log.debug("Trend alignment bonus: +{}", String.format("%.2f", 0.2 * trend.getStrength()));
		}

		// Pattern strength bonus
		long relevantPatterns = patterns.stream()
				.filter(p -> (signal == Signal.BUY && p.getBias() == PatternBias.BULLISH)
						|| (signal == Signal.SELL && p.getBias() == PatternBias.BEARISH))
				.count();
		boolean hasStrongPattern = PatternUtils.hasStrongPattern(patterns);
		confidence += Math.min(0.25, relevantPatterns * 0.08);
		if (hasStrongPattern) {
			confidence += 0.1;
			log.debug("Strong pattern bonus: +0.10");
		}

		// S/R proximity bonus
		if (signal == Signal.BUY && nearestSupport != null) {
			double proximity = (currentPrice - nearestSupport) / currentPrice;
			if (proximity < 0.02) {
				confidence += 0.15;
				log.debug("Close to support bonus: +0.15");
			} else if (proximity < 0.05) {
				confidence += 0.05;
				log.debug("Near support bonus: +0.05");
			}
		}
		if (signal == Signal.SELL && nearestResistance != null) {
			double proximity = (nearestResistance - currentPrice) / currentPrice;
			if (proximity < 0.02) {
				confidence += 0.15;
				log.debug("Close to resistance bonus: +0.15");
			} else if (proximity < 0.05) {
				confidence += 0.05;
				log.debug("Near resistance bonus: +0.05");
			}
		}

		// New: incorporate Risk:Reward into confidence
		double rr = calculateRiskRewardRatio(signal, currentPrice, nearestSupport, nearestResistance);
		log.debug("Calculated Risk:Reward (reward/risk) = {}", String.format("%.2f", rr));

		if (rr < 2.0) {
			// If R:R is less than 1:2, do not allow confidence to exceed 0.7
			if (confidence > 0.7) {
				log.debug("Capping confidence to 0.70 because R:R < 1:2");
				confidence = 0.7;
			}
		} else {
			// Reward good R:R with a small bonus (max +0.10)
			double bonus = Math.min(0.10, (rr - 2.0) * 0.02);
			if (bonus > 0) {
				confidence = Math.min(1.0, confidence + bonus);
				log.debug("R:R bonus applied: +{}", String.format("%.2f", bonus));
			}
		}

		confidence = Math.min(1.0, Math.max(0.0, confidence));
		log.info("Confidence calculation: {} (trend-aligned: {}, relevant-patterns: {}, strong-pattern: {}, R:R: {})",
				String.format("%.2f", confidence), trendAligned, relevantPatterns, hasStrongPattern, String.format("%.2f", rr));
		return Math.round(confidence * 100.0) / 100.0;
	}

	// New helper to compute reward/risk ratio. For BUY: reward = resistance - price, risk = price - support.
	// For SELL: reward = price - support, risk = resistance - price.
	private double calculateRiskRewardRatio(Signal signal, double currentPrice,
			Double nearestSupport, Double nearestResistance) {
		if (signal == Signal.HOLD) {
			return 1.0;
		}

		try {
			if (signal == Signal.BUY) {
				if (nearestSupport == null || nearestResistance == null) {
					return 1.0;
				}
				double risk = currentPrice - nearestSupport;
				double reward = nearestResistance - currentPrice;
				if (risk <= 0 || reward <= 0) {
					return 1.0;
				}
				return reward / risk;
			}

			if (signal == Signal.SELL) {
				if (nearestSupport == null || nearestResistance == null) {
					return 1.0;
				}
				double risk = nearestResistance - currentPrice;
				double reward = currentPrice - nearestSupport;
				if (risk <= 0 || reward <= 0) {
					return 1.0;
				}
				return reward / risk;
			}
		} catch (Exception e) {
			log.debug("Error calculating R:R: {}", e.getMessage());
		}
		return 1.0;
	}

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

		if (nearestSupport != null) {
			sb.append(String.format("Support: %.2f. ", nearestSupport));
		}
		if (nearestResistance != null) {
			sb.append(String.format("Resistance: %.2f. ", nearestResistance));
		}

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
