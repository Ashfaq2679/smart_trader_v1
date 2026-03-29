package com.techcobber.smarttrader.v1.strategy;

import java.util.ArrayList;
import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SupportResistanceDetector {

	public enum LevelType {
		SUPPORT, RESISTANCE
	}

	@Data
	public static class Level {
		private final double price;
		private final LevelType type;
		private int strength;

		public Level(double price, LevelType type, int strength) {
			this.price = price;
			this.type = type;
			this.strength = strength;
		}
	}

	public List<Level> detectLevels(List<MyCandle> candles, int lookback) {
		if (candles == null || candles.size() < 3) {
			log.warn("Insufficient candles for S/R detection: {}", candles == null ? 0 : candles.size());
			return new ArrayList<>();
		}

		int start = Math.max(0, candles.size() - lookback);
		List<MyCandle> window = candles.subList(start, candles.size());

		List<Double> swingHighs = new ArrayList<>();
		List<Double> swingLows = new ArrayList<>();

		for (int i = 1; i < window.size() - 1; i++) {
			MyCandle prev = window.get(i - 1);
			MyCandle curr = window.get(i);
			MyCandle next = window.get(i + 1);

			if (curr.getHigh() > prev.getHigh() && curr.getHigh() > next.getHigh()) {
				swingHighs.add(curr.getHigh());
			}

			if (curr.getLow() < prev.getLow() && curr.getLow() < next.getLow()) {
				swingLows.add(curr.getLow());
			}
		}

		log.debug("Found {} swing highs and {} swing lows in {} candle window",
				swingHighs.size(), swingLows.size(), window.size());

		List<Level> levels = new ArrayList<>();

		levels.addAll(groupLevels(swingHighs, LevelType.RESISTANCE, candles));
		levels.addAll(groupLevels(swingLows, LevelType.SUPPORT, candles));

		levels.sort((a, b) -> Double.compare(b.getStrength(), a.getStrength()));

		for (Level level : levels) {
			log.info("Detected {} level at {:.2f} with strength {}",
					level.getType(), level.getPrice(), level.getStrength());
		}

		return levels;
	}

	private List<Level> groupLevels(List<Double> prices, LevelType type, List<MyCandle> candles) {
		List<Level> grouped = new ArrayList<>();
		if (prices.isEmpty()) {
			return grouped;
		}

		double avgPrice = candles.stream()
				.mapToDouble(c -> (c.getHigh() + c.getLow()) / 2)
				.average()
				.orElse(1.0);
		double tolerance = avgPrice * 0.005;

		List<Double> sorted = new ArrayList<>(prices);
		sorted.sort(Double::compareTo);

		List<List<Double>> clusters = new ArrayList<>();
		List<Double> currentCluster = new ArrayList<>();
		currentCluster.add(sorted.get(0));

		for (int i = 1; i < sorted.size(); i++) {
			if (sorted.get(i) - sorted.get(i - 1) <= tolerance) {
				currentCluster.add(sorted.get(i));
			} else {
				clusters.add(currentCluster);
				currentCluster = new ArrayList<>();
				currentCluster.add(sorted.get(i));
			}
		}
		clusters.add(currentCluster);

		for (List<Double> cluster : clusters) {
			double avgLevel = cluster.stream().mapToDouble(Double::doubleValue).average().orElse(0);
			int touches = countTouches(avgLevel, tolerance, candles, type);
			grouped.add(new Level(Math.round(avgLevel * 100.0) / 100.0, type, Math.max(touches, cluster.size())));
		}

		return grouped;
	}

	private int countTouches(double level, double tolerance, List<MyCandle> candles, LevelType type) {
		int touches = 0;
		for (MyCandle candle : candles) {
			if (type == LevelType.RESISTANCE) {
				if (Math.abs(candle.getHigh() - level) <= tolerance) {
					touches++;
				}
			} else {
				if (Math.abs(candle.getLow() - level) <= tolerance) {
					touches++;
				}
			}
		}
		return touches;
	}
}
