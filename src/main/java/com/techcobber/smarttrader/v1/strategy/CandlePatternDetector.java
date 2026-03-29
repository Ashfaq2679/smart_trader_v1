package com.techcobber.smarttrader.v1.strategy;

import java.util.ArrayList;
import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.MyCandle.CandleType;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CandlePatternDetector {

	public enum PatternBias {
		BULLISH, BEARISH, NEUTRAL
	}

	@Data
	public static class DetectedPattern {
		private final String name;
		private final PatternBias bias;
		private final int candleIndex;

		public DetectedPattern(String name, PatternBias bias, int candleIndex) {
			this.name = name;
			this.bias = bias;
			this.candleIndex = candleIndex;
		}
	}

	public List<DetectedPattern> detectPatterns(List<MyCandle> candles) {
		List<DetectedPattern> patterns = new ArrayList<>();

		if (candles == null || candles.isEmpty()) {
			log.warn("No candles provided for pattern detection");
			return patterns;
		}

		log.info("Starting candle pattern detection on {} candles", candles.size());

		// Single-candle patterns from all candles (focus on recent ones)
		int singleStart = Math.max(0, candles.size() - 5);
		for (int i = singleStart; i < candles.size(); i++) {
			MyCandle candle = candles.get(i);
			List<CandleType> types = candle.getCandleTypes();
			if (types != null) {
				for (CandleType type : types) {
					PatternBias bias = classifyBias(type);
					patterns.add(new DetectedPattern(type.name(), bias, i));
					log.info("Single-candle pattern at index {}: {} (bias: {})", i, type.name(), bias);
				}
			}
		}

		// Two-candle patterns on recent pairs
		int twoStart = Math.max(0, candles.size() - 4);
		for (int i = twoStart + 1; i < candles.size(); i++) {
			MyCandle previous = candles.get(i - 1);
			MyCandle current = candles.get(i);
			List<CandleType> twoPatterns = MyCandle.detectTwoCandlePatterns(previous, current);
			for (CandleType type : twoPatterns) {
				PatternBias bias = classifyBias(type);
				patterns.add(new DetectedPattern(type.name(), bias, i));
				log.info("Two-candle pattern at index {}-{}: {} (bias: {})", i - 1, i, type.name(), bias);
			}
		}

		// Three-candle patterns on recent triplets
		int threeStart = Math.max(0, candles.size() - 5);
		for (int i = threeStart + 2; i < candles.size(); i++) {
			MyCandle first = candles.get(i - 2);
			MyCandle second = candles.get(i - 1);
			MyCandle third = candles.get(i);
			List<CandleType> threePatterns = MyCandle.detectThreeCandlePatterns(first, second, third);
			for (CandleType type : threePatterns) {
				PatternBias bias = classifyBias(type);
				patterns.add(new DetectedPattern(type.name(), bias, i));
				log.info("Three-candle pattern at index {}-{}-{}: {} (bias: {})",
						i - 2, i - 1, i, type.name(), bias);
			}
		}

		log.info("Pattern detection complete — {} total patterns detected: {}",
				patterns.size(), patterns.stream().map(DetectedPattern::getName).toList());

		return patterns;
	}

	private PatternBias classifyBias(CandleType type) {
		return switch (type) {
			case HAMMER, INVERTED_HAMMER, DRAGONFLY_DOJI, MARUBOZU_BULLISH,
					BULLISH_ENGULFING, BULLISH_HARAMI, MORNING_STAR,
					THREE_WHITE_SOLDIERS, PIERCING_LINE, TWEEZER_BOTTOM, BULLISH -> PatternBias.BULLISH;
			case SHOOTING_STAR, HANGING_MAN, GRAVESTONE_DOJI, MARUBOZU_BEARISH,
					BEARISH_ENGULFING, BEARISH_HARAMI, EVENING_STAR,
					THREE_BLACK_CROWS, DARK_CLOUD_COVER, TWEEZER_TOP, BEARISH -> PatternBias.BEARISH;
			case DOJI, SPINNING_TOP, NEUTRAL -> PatternBias.NEUTRAL;
		};
	}
}
