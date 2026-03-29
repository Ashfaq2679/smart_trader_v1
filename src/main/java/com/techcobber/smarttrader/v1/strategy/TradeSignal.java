package com.techcobber.smarttrader.v1.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle.CandleType;

/**
 * Immutable value object representing the output of a {@link TradingStrategy}.
 *
 * <p><b>Design Pattern: Builder</b> — Uses a nested {@link Builder} to construct
 * instances with a fluent API, consistent with the project's existing
 * {@link com.techcobber.smarttrader.v1.models.MyCandle.Builder MyCandle.Builder}
 * convention.</p>
 *
 * <p>Every signal carries:</p>
 * <ul>
 *   <li>{@code signal} — the recommended action ({@link Signal#BUY}, {@link Signal#SELL}, or {@link Signal#HOLD})</li>
 *   <li>{@code confidence} — a score between 0.0 (no confidence) and 1.0 (maximum confidence)</li>
 *   <li>{@code reason} — a human-readable explanation of why the signal was generated</li>
 *   <li>{@code detectedPatterns} — the candlestick patterns that contributed to the decision</li>
 *   <li>{@code timestamp} — the candle start timestamp the signal is associated with</li>
 *   <li>{@code strategyName} — the name of the strategy that produced the signal</li>
 * </ul>
 */
public class TradeSignal {

	private final Signal signal;
	private final double confidence;
	private final String reason;
	private final List<CandleType> detectedPatterns;
	private final Long timestamp;
	private final String strategyName;

	private TradeSignal(Builder builder) {
		this.signal = builder.signal;
		this.confidence = Math.max(0.0, Math.min(1.0, builder.confidence));
		this.reason = builder.reason;
		this.detectedPatterns = Collections.unmodifiableList(
				builder.detectedPatterns != null ? builder.detectedPatterns : List.of());
		this.timestamp = builder.timestamp;
		this.strategyName = builder.strategyName;
	}

	public Signal getSignal() {
		return signal;
	}

	public double getConfidence() {
		return confidence;
	}

	public String getReason() {
		return reason;
	}

	public List<CandleType> getDetectedPatterns() {
		return detectedPatterns;
	}

	public Long getTimestamp() {
		return timestamp;
	}

	public String getStrategyName() {
		return strategyName;
	}

	@Override
	public String toString() {
		return "TradeSignal{" +
				"signal=" + signal +
				", confidence=" + String.format("%.2f", confidence) +
				", reason='" + reason + '\'' +
				", detectedPatterns=" + detectedPatterns +
				", timestamp=" + timestamp +
				", strategyName='" + strategyName + '\'' +
				'}';
	}

	// -----------------------------------------------------------------------
	// Builder
	// -----------------------------------------------------------------------

	public static class Builder {
		private Signal signal = Signal.HOLD;
		private double confidence;
		private String reason;
		private List<CandleType> detectedPatterns;
		private Long timestamp;
		private String strategyName;

		public Builder signal(Signal signal) {
			this.signal = signal;
			return this;
		}

		public Builder confidence(double confidence) {
			this.confidence = confidence;
			return this;
		}

		public Builder reason(String reason) {
			this.reason = reason;
			return this;
		}

		public Builder detectedPatterns(List<CandleType> detectedPatterns) {
			this.detectedPatterns = detectedPatterns != null
					? new ArrayList<>(detectedPatterns)
					: null;
			return this;
		}

		public Builder timestamp(Long timestamp) {
			this.timestamp = timestamp;
			return this;
		}

		public Builder strategyName(String strategyName) {
			this.strategyName = strategyName;
			return this;
		}

		public TradeSignal build() {
			return new TradeSignal(this);
		}
	}
}
