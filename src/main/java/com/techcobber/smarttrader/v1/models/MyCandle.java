package com.techcobber.smarttrader.v1.models;

import com.coinbase.advanced.model.products.Candle;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MyCandle {

	public enum CandleType {
		HAMMER,
		INVERTED_HAMMER,
		SHOOTING_STAR,
		HANGING_MAN,
		DOJI,
		DRAGONFLY_DOJI,
		GRAVESTONE_DOJI,
		SPINNING_TOP,
		MARUBOZU_BULLISH,
		MARUBOZU_BEARISH,
		BULLISH_ENGULFING,
		BEARISH_ENGULFING,
		BULLISH_HARAMI,
		BEARISH_HARAMI,
		MORNING_STAR,
		EVENING_STAR,
		THREE_WHITE_SOLDIERS,
		THREE_BLACK_CROWS,
		PIERCING_LINE,
		DARK_CLOUD_COVER,
		TWEEZER_TOP,
		TWEEZER_BOTTOM,
		BULLISH,
		BEARISH,
		NEUTRAL
	}

	@JsonProperty("start")
	private Long start;

	@JsonProperty("low")
	private Double low;

	@JsonProperty("high")
	private Double high;

	@JsonProperty("open")
	private Double open;

	@JsonProperty("close")
	private Double close;

	@JsonProperty("volume")
	private Double volume;

	private String color;
	private double bodySize;
	private double upperWickPercent;
	private double lowerWickPercent;
	private List<CandleType> candleTypes;

	public boolean isBullish() {
		return close > open;
	}

	public boolean isBearish() {
		if (!isNeutral()) {
			return !isBullish();
		}
		return false;
	}

	public boolean isNeutral() {
		return close.equals(open);
	}

	public double bodyPercent() {
		return ((close - open) / open) * 100;
	}

	public double range() {
		return high - low;
	}

	public void computeFields() {
		if (open == null || close == null || high == null || low == null) {
			return;
		}

		if (close > open) {
			this.color = "GREEN";
		} else if (close < open) {
			this.color = "RED";
		} else {
			this.color = "NEUTRAL";
		}

		double range = high - low;
		this.bodySize = Math.abs(close - open);

		if (range == 0) {
			this.upperWickPercent = 0;
			this.lowerWickPercent = 0;
		} else {
			double upperWick = high - Math.max(open, close);
			double lowerWick = Math.min(open, close) - low;
			this.upperWickPercent = (upperWick / range) * 100;
			this.lowerWickPercent = (lowerWick / range) * 100;
		}

		this.candleTypes = detectSingleCandlePatterns();
	}

	private List<CandleType> detectSingleCandlePatterns() {
		List<CandleType> types = new ArrayList<>();
		double range = range();

		if (range == 0) {
			types.add(CandleType.NEUTRAL);
			return types;
		}

		double bodyRatio = bodySize / range;
		double upperWick = high - Math.max(open, close);
		double lowerWick = Math.min(open, close) - low;
		double upperWickRatio = upperWick / range;
		double lowerWickRatio = lowerWick / range;

		if (bodyRatio < 0.05) {
			if (lowerWickRatio > 0.7 && upperWickRatio < 0.1) {
				types.add(CandleType.DRAGONFLY_DOJI);
			} else if (upperWickRatio > 0.7 && lowerWickRatio < 0.1) {
				types.add(CandleType.GRAVESTONE_DOJI);
			} else {
				types.add(CandleType.DOJI);
			}
			return types;
		}

		if (bodyRatio > 0.9 && upperWickRatio < 0.05 && lowerWickRatio < 0.05) {
			types.add(isBullish() ? CandleType.MARUBOZU_BULLISH : CandleType.MARUBOZU_BEARISH);
			return types;
		}

		if (bodyRatio < 0.3 && Math.abs(upperWickRatio - lowerWickRatio) < 0.15) {
			types.add(CandleType.SPINNING_TOP);
			return types;
		}

		if (lowerWick >= bodySize * 2 && upperWickRatio < 0.15 && bodyRatio < 0.35) {
			types.add(isBullish() ? CandleType.HAMMER : CandleType.HANGING_MAN);
		}

		if (upperWick >= bodySize * 2 && lowerWickRatio < 0.15 && bodyRatio < 0.35) {
			types.add(isBullish() ? CandleType.INVERTED_HAMMER : CandleType.SHOOTING_STAR);
		}

		if (types.isEmpty()) {
			types.add(isBullish() ? CandleType.BULLISH : (isBearish() ? CandleType.BEARISH : CandleType.NEUTRAL));
		}

		return types;
	}

	public static List<CandleType> detectTwoCandlePatterns(MyCandle previous, MyCandle current) {
		List<CandleType> patterns = new ArrayList<>();
		if (previous == null || current == null) {
			return patterns;
		}

		double prevBody = Math.abs(previous.close - previous.open);
		double currBody = Math.abs(current.close - current.open);

		if (previous.isBearish() && current.isBullish()
				&& current.open <= previous.close && current.close >= previous.open) {
			patterns.add(CandleType.BULLISH_ENGULFING);
		}

		if (previous.isBullish() && current.isBearish()
				&& current.open >= previous.close && current.close <= previous.open) {
			patterns.add(CandleType.BEARISH_ENGULFING);
		}

		if (previous.isBearish() && current.isBullish()
				&& currBody < prevBody
				&& current.open >= previous.close && current.close <= previous.open) {
			patterns.add(CandleType.BULLISH_HARAMI);
		}

		if (previous.isBullish() && current.isBearish()
				&& currBody < prevBody
				&& current.open <= previous.close && current.close >= previous.open) {
			patterns.add(CandleType.BEARISH_HARAMI);
		}

		double prevMid = (previous.open + previous.close) / 2;
		if (previous.isBearish() && current.isBullish()
				&& current.open < previous.low && current.close > prevMid
				&& current.close < previous.open) {
			patterns.add(CandleType.PIERCING_LINE);
		}

		if (previous.isBullish() && current.isBearish()
				&& current.open > previous.high && current.close < prevMid
				&& current.close > previous.open) {
			patterns.add(CandleType.DARK_CLOUD_COVER);
		}

		double tolerance = previous.range() * 0.02;
		if (Math.abs(previous.low - current.low) <= tolerance
				&& previous.isBearish() && current.isBullish()) {
			patterns.add(CandleType.TWEEZER_BOTTOM);
		}

		if (Math.abs(previous.high - current.high) <= tolerance
				&& previous.isBullish() && current.isBearish()) {
			patterns.add(CandleType.TWEEZER_TOP);
		}

		return patterns;
	}

	public static List<CandleType> detectThreeCandlePatterns(MyCandle first, MyCandle second, MyCandle third) {
		List<CandleType> patterns = new ArrayList<>();
		if (first == null || second == null || third == null) {
			return patterns;
		}

		double secondBody = Math.abs(second.close - second.open);
		double secondRange = second.range();
		double secondBodyRatio = secondRange > 0 ? secondBody / secondRange : 0;

		if (first.isBearish() && secondBodyRatio < 0.3 && third.isBullish()
				&& second.close < first.close && third.close > (first.open + first.close) / 2) {
			patterns.add(CandleType.MORNING_STAR);
		}

		if (first.isBullish() && secondBodyRatio < 0.3 && third.isBearish()
				&& second.close > first.close && third.close < (first.open + first.close) / 2) {
			patterns.add(CandleType.EVENING_STAR);
		}

		if (first.isBullish() && second.isBullish() && third.isBullish()
				&& second.close > first.close && third.close > second.close
				&& second.open > first.open && third.open > second.open) {
			patterns.add(CandleType.THREE_WHITE_SOLDIERS);
		}

		if (first.isBearish() && second.isBearish() && third.isBearish()
				&& second.close < first.close && third.close < second.close
				&& second.open < first.open && third.open < second.open) {
			patterns.add(CandleType.THREE_BLACK_CROWS);
		}

		return patterns;
	}

	public MyCandle() {
	}

	private MyCandle(Builder builder) {
		this.start = builder.start;
		this.low = builder.low;
		this.high = builder.high;
		this.open = builder.open;
		this.close = builder.close;
		this.volume = builder.volume;
		computeFields();
	}

	public Long getStart() {
		return start;
	}

	public void setStart(long start) {
		this.start = start;
	}

	public Double getLow() {
		return low;
	}

	public void setLow(double low) {
		this.low = low;
	}

	public Double getHigh() {
		return high;
	}

	public void setHigh(double high) {
		this.high = high;
	}

	public Double getOpen() {
		return open;
	}

	public void setOpen(double open) {
		this.open = open;
	}

	public Double getClose() {
		return close;
	}

	public void setClose(double close) {
		this.close = close;
	}

	public Double getVolume() {
		return volume;
	}

	public void setVolume(double volume) {
		this.volume = volume;
	}

	public static class Builder {
		private long start;
		private double low;
		private double high;
		private double open;
		private double close;
		private double volume;

		public Builder start(long start) {
			this.start = start;
			return this;
		}

		public Builder low(double low) {
			this.low = low;
			return this;
		}

		public Builder high(double high) {
			this.high = high;
			return this;
		}

		public Builder open(double open) {
			this.open = open;
			return this;
		}

		public Builder close(double close) {
			this.close = close;
			return this;
		}

		public Builder volume(double volume) {
			this.volume = volume;
			return this;
		}

		public MyCandle build() {
			return new MyCandle(this);
		}

		public Builder from(Candle candle) {
			this.start = Long.parseLong(candle.getStart());
			this.low = Double.parseDouble(candle.getLow());
			this.high = Double.parseDouble(candle.getHigh());
			this.open = Double.parseDouble(candle.getOpen());
			this.close = Double.parseDouble(candle.getClose());
			this.volume = Double.parseDouble(candle.getVolume());
			return this;
		}
	}
}
