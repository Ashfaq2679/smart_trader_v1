package com.techcobber.smarttrader.v1.strategy;

import java.util.ArrayList;
import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrendAnalyzer {

	public enum TrendDirection {
		UP, DOWN, SIDEWAYS
	}

	@Data
	public static class TrendResult {
		private final TrendDirection direction;
		private final double strength;
		private final String description;

		public TrendResult(TrendDirection direction, double strength, String description) {
			this.direction = direction;
			this.strength = strength;
			this.description = description;
		}
	}

	public TrendResult analyzeTrend(List<MyCandle> candles, int lookback) {
		if (candles == null || candles.size() < 5) {
			log.warn("Insufficient candles for trend analysis: {}", candles == null ? 0 : candles.size());
			return new TrendResult(TrendDirection.SIDEWAYS, 0.0, "Insufficient data for trend analysis");
		}

		int start = Math.max(0, candles.size() - lookback);
		List<MyCandle> window = candles.subList(start, candles.size());

		List<Double> swingHighs = new ArrayList<>();
		List<Double> swingLows = new ArrayList<>();
		List<Integer> highIndices = new ArrayList<>();
		List<Integer> lowIndices = new ArrayList<>();

		for (int i = 1; i < window.size() - 1; i++) {
			MyCandle prev = window.get(i - 1);
			MyCandle curr = window.get(i);
			MyCandle next = window.get(i + 1);

			if (curr.getHigh() > prev.getHigh() && curr.getHigh() > next.getHigh()) {
				swingHighs.add(curr.getHigh());
				highIndices.add(i);
			}

			if (curr.getLow() < prev.getLow() && curr.getLow() < next.getLow()) {
				swingLows.add(curr.getLow());
				lowIndices.add(i);
			}
		}

		log.debug("Swing highs: {} at indices {}", swingHighs, highIndices);
		log.debug("Swing lows: {} at indices {}", swingLows, lowIndices);

		int higherHighs = countHigherSequences(swingHighs);
		int lowerHighs = countLowerSequences(swingHighs);
		int higherLows = countHigherSequences(swingLows);
		int lowerLows = countLowerSequences(swingLows);

		log.debug("Higher-highs: {}, lower-highs: {}, higher-lows: {}, lower-lows: {}",
				higherHighs, lowerHighs, higherLows, lowerLows);

		int bullishSignals = higherHighs + higherLows;
		int bearishSignals = lowerHighs + lowerLows;
		int totalSignals = bullishSignals + bearishSignals;

		TrendDirection direction;
		double strength;
		String description;

		if (totalSignals == 0) {
			direction = TrendDirection.SIDEWAYS;
			strength = 0.0;
			description = "No clear swing structure detected — sideways market";
		} else if (bullishSignals > bearishSignals) {
			direction = TrendDirection.UP;
			strength = Math.min(1.0, (double) bullishSignals / totalSignals);
			description = String.format("Uptrend detected: %d higher-highs, %d higher-lows (strength: %.2f)",
					higherHighs, higherLows, strength);
		} else if (bearishSignals > bullishSignals) {
			direction = TrendDirection.DOWN;
			strength = Math.min(1.0, (double) bearishSignals / totalSignals);
			description = String.format("Downtrend detected: %d lower-highs, %d lower-lows (strength: %.2f)",
					lowerHighs, lowerLows, strength);
		} else {
			direction = TrendDirection.SIDEWAYS;
			strength = 0.3;
			description = String.format("Mixed signals: %d bullish vs %d bearish — sideways",
					bullishSignals, bearishSignals);
		}

		log.info("Trend analysis: {} (strength: {:.2f}) — {}", direction, strength, description);
		return new TrendResult(direction, strength, description);
	}

	private int countHigherSequences(List<Double> values) {
		int count = 0;
		for (int i = 1; i < values.size(); i++) {
			if (values.get(i) > values.get(i - 1)) {
				count++;
			}
		}
		return count;
	}

	private int countLowerSequences(List<Double> values) {
		int count = 0;
		for (int i = 1; i < values.size(); i++) {
			if (values.get(i) < values.get(i - 1)) {
				count++;
			}
		}
		return count;
	}
}
