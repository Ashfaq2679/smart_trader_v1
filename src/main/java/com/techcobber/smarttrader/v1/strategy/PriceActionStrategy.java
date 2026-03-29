package com.techcobber.smarttrader.v1.strategy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.strategy.CandlePatternDetector.DetectedPattern;
import com.techcobber.smarttrader.v1.strategy.CandlePatternDetector.PatternBias;
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

	private final SupportResistanceDetector srDetector = new SupportResistanceDetector();
	private final TrendAnalyzer trendAnalyzer = new TrendAnalyzer();
	private final CandlePatternDetector patternDetector = new CandlePatternDetector();

	@Override
	public String getName() {
		return "PriceAction";
	}

	@Override
	public TradeDecision analyze(List<MyCandle> candles) {
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
					.timestamp(LocalDateTime.now(ZoneOffset.UTC))
					.build();
		}

		MyCandle latest = candles.get(candles.size() - 1);
		double currentPrice = latest.getClose();

		// Step 1: Support/Resistance
		log.info("--- Step 1: Support/Resistance Detection ---");
		List<Level> levels = srDetector.detectLevels(candles, SR_LOOKBACK);

		List<Level> supports = levels.stream()
				.filter(l -> l.getType() == LevelType.SUPPORT)
				.toList();
		List<Level> resistances = levels.stream()
				.filter(l -> l.getType() == LevelType.RESISTANCE)
				.toList();

		log.info("Support levels: {}", supports.stream()
				.map(l -> String.format("%.2f (strength:%d)", l.getPrice(), l.getStrength()))
				.toList());
		log.info("Resistance levels: {}", resistances.stream()
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

		log.info("Current price: {:.2f} | Nearest support: {} | Nearest resistance: {}",
				currentPrice,
				nearestSupport != null ? String.format("%.2f", nearestSupport) : "none",
				nearestResistance != null ? String.format("%.2f", nearestResistance) : "none");

		// Step 2: Trend Analysis
		log.info("--- Step 2: Trend Analysis ---");
		TrendResult trend = trendAnalyzer.analyzeTrend(candles, TREND_LOOKBACK);
		log.info("Trend analysis: {} (strength: {:.2f})", trend.getDirection(), trend.getStrength());

		// Step 3: Candle Pattern Detection
		log.info("--- Step 3: Candle Pattern Detection ---");
		List<DetectedPattern> detectedPatterns = patternDetector.detectPatterns(candles);

		List<String> patternNames = detectedPatterns.stream()
				.map(DetectedPattern::getName)
				.toList();
		log.info("Detected candle patterns: {}", patternNames);

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

		// Step 4: Signal determination
		log.info("--- Step 4: Signal Determination ---");
		Signal signal = determineSignal(trend, detectedPatterns, currentPrice,
				nearestSupport, nearestResistance, levels);

		// Step 5: Confidence calculation
		double confidence = calculateConfidence(signal, trend, detectedPatterns,
				currentPrice, nearestSupport, nearestResistance);

		// Step 6: Build reasoning
		String reasoning = buildReasoning(signal, trend, detectedPatterns,
				currentPrice, nearestSupport, nearestResistance);

		log.info("=== Signal: {} | Confidence: {:.2f} | Patterns: {} | Reasoning: {} ===",
				signal, confidence, patternNames, reasoning);

		return TradeDecision.builder()
				.signal(signal)
				.confidence(confidence)
				.reasoning(reasoning)
				.detectedPatterns(patternNames)
				.trendDirection(trend.getDirection().name())
				.nearestSupport(nearestSupport)
				.nearestResistance(nearestResistance)
				.timestamp(LocalDateTime.now(ZoneOffset.UTC))
				.build();
	}

	private Signal determineSignal(TrendResult trend, List<DetectedPattern> patterns,
			double currentPrice, Double nearestSupport, Double nearestResistance,
			List<Level> allLevels) {

		long bullishCount = patterns.stream().filter(p -> p.getBias() == PatternBias.BULLISH).count();
		long bearishCount = patterns.stream().filter(p -> p.getBias() == PatternBias.BEARISH).count();

		// Check for breakout above resistance
		boolean breakoutAbove = false;
		if (nearestResistance != null) {
			double resistanceTolerance = nearestResistance * 0.002;
			breakoutAbove = currentPrice > nearestResistance + resistanceTolerance;
			if (breakoutAbove) {
				log.info("Breakout detected: price {:.2f} above resistance {:.2f}", currentPrice, nearestResistance);
			}
		}

		// Check for breakdown below support
		boolean breakdownBelow = false;
		if (nearestSupport != null) {
			double supportTolerance = nearestSupport * 0.002;
			breakdownBelow = currentPrice < nearestSupport - supportTolerance;
			if (breakdownBelow) {
				log.info("Breakdown detected: price {:.2f} below support {:.2f}", currentPrice, nearestSupport);
			}
		}

		// Check for rejection at resistance
		boolean rejectedAtResistance = false;
		if (nearestResistance != null) {
			double proximity = Math.abs(currentPrice - nearestResistance) / nearestResistance;
			rejectedAtResistance = proximity < 0.005 && bearishCount > 0;
			if (rejectedAtResistance) {
				log.info("Rejection at resistance {:.2f} with bearish patterns", nearestResistance);
			}
		}

		// Check for bounce at support
		boolean bouncedAtSupport = false;
		if (nearestSupport != null) {
			double proximity = Math.abs(currentPrice - nearestSupport) / nearestSupport;
			bouncedAtSupport = proximity < 0.005 && bullishCount > 0;
			if (bouncedAtSupport) {
				log.info("Bounce at support {:.2f} with bullish patterns", nearestSupport);
			}
		}

		// Decision logic
		if (breakoutAbove && trend.getDirection() == TrendDirection.UP && bullishCount > 0) {
			log.info("BUY signal: breakout above resistance in uptrend with bullish patterns");
			return Signal.BUY;
		}

		if (breakdownBelow && trend.getDirection() == TrendDirection.DOWN && bearishCount > 0) {
			log.info("SELL signal: breakdown below support in downtrend with bearish patterns");
			return Signal.SELL;
		}

		if (bouncedAtSupport && trend.getDirection() != TrendDirection.DOWN) {
			log.info("BUY signal: bounce at support with bullish patterns and non-bearish trend");
			return Signal.BUY;
		}

		if (rejectedAtResistance && trend.getDirection() != TrendDirection.UP) {
			log.info("SELL signal: rejection at resistance with bearish patterns and non-bullish trend");
			return Signal.SELL;
		}

		if (bullishCount >= 2 && trend.getDirection() == TrendDirection.UP) {
			log.info("BUY signal: multiple bullish patterns ({}) aligned with uptrend", bullishCount);
			return Signal.BUY;
		}

		if (bearishCount >= 2 && trend.getDirection() == TrendDirection.DOWN) {
			log.info("SELL signal: multiple bearish patterns ({}) aligned with downtrend", bearishCount);
			return Signal.SELL;
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
			log.debug("Trend alignment bonus: +{:.2f}", 0.2 * trend.getStrength());
		}

		// Pattern strength bonus
		long relevantPatterns = patterns.stream()
				.filter(p -> (signal == Signal.BUY && p.getBias() == PatternBias.BULLISH)
						|| (signal == Signal.SELL && p.getBias() == PatternBias.BEARISH))
				.count();
		boolean hasStrongPattern = patterns.stream().anyMatch(p ->
				p.getName().contains("ENGULFING") || p.getName().contains("MORNING_STAR")
						|| p.getName().contains("EVENING_STAR") || p.getName().contains("THREE_WHITE")
						|| p.getName().contains("THREE_BLACK") || p.getName().contains("MARUBOZU"));
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

		confidence = Math.min(1.0, Math.max(0.0, confidence));
		log.info("Confidence calculation: {:.2f} (trend-aligned: {}, relevant-patterns: {}, strong-pattern: {})",
				confidence, trendAligned, relevantPatterns, hasStrongPattern);
		return Math.round(confidence * 100.0) / 100.0;
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
}
