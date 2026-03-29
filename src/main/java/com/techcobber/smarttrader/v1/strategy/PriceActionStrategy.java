package com.techcobber.smarttrader.v1.strategy;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;

import org.springframework.stereotype.Component;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.MyCandle.CandleType;

import lombok.extern.slf4j.Slf4j;

/**
 * Price-action trading strategy that leverages {@link MyCandle} pattern detection
 * to produce buy/sell/hold signals.
 *
 * <p><b>Design Pattern: Strategy</b> — Implements {@link TradingStrategy} so it
 * can be swapped with other strategy implementations at runtime.</p>
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li><b>Trend detection</b> — determines the prevailing trend over the candle
 *       window using an EMA-crossover approach (fast vs. slow exponential moving
 *       averages of closing prices).</li>
 *   <li><b>Pattern scanning</b> — uses {@link MyCandle}'s single-, two-, and
 *       three-candle pattern detectors to find candlestick formations.</li>
 *   <li><b>Noise filtering</b> — rejects patterns that occur on candles whose
 *       body or volume is too small relative to the recent average (inducements /
 *       false flags).</li>
 *   <li><b>Scoring &amp; confluence</b> — each qualifying pattern is weighted;
 *       scores accumulate and are normalised into a 0–1 confidence value.</li>
 *   <li><b>Signal generation</b> — if the net score exceeds a threshold, a
 *       {@link Signal#BUY} or {@link Signal#SELL} is emitted; otherwise
 *       {@link Signal#HOLD}.</li>
 * </ol>
 *
 * <p>This class is <em>thread-safe</em> and stateless per invocation — it may be
 * declared as a Spring singleton bean.</p>
 */
@Component
@Slf4j
public class PriceActionStrategy implements TradingStrategy {

	private static final String STRATEGY_NAME = "PRICE_ACTION";

	/** Minimum candles needed for a meaningful analysis. */
	static final int MIN_CANDLES = 5;

	/** Maximum confidence value used to cap the normalised score. */
	private static final double MAX_CONFIDENCE = 1.0;

	/** Threshold below which a pattern's candle is considered noise. */
	static final double BODY_SIGNIFICANCE_THRESHOLD = 0.3;

	/** Fraction of average volume below which a candle is considered low-volume. */
	static final double VOLUME_FILTER_RATIO = 0.5;

	/** Minimum net score required to emit a BUY or SELL signal (absolute value). */
	static final double SIGNAL_THRESHOLD = 0.20;

	/** EMA period for the fast moving average. */
	private static final int FAST_EMA_PERIOD = 3;

	/** EMA period for the slow moving average. */
	private static final int SLOW_EMA_PERIOD = 8;

	// -----------------------------------------------------------------------
	// Pattern weights — bullish patterns are positive, bearish are negative.
	// -----------------------------------------------------------------------

	/** Weight for strong reversal patterns (engulfing, morning/evening star). */
	private static final double WEIGHT_STRONG = 0.30;

	/** Weight for moderate patterns (harami, piercing line, dark cloud cover). */
	private static final double WEIGHT_MODERATE = 0.20;

	/** Weight for single-candle reversal hints (hammer, shooting star). */
	private static final double WEIGHT_WEAK = 0.10;

	/** Weight for continuation patterns (three soldiers/crows, marubozu). */
	private static final double WEIGHT_CONTINUATION = 0.25;

	/** Bonus multiplier when a pattern aligns with the prevailing trend. */
	private static final double TREND_ALIGNMENT_BONUS = 1.3;

	@Override
	public String getName() {
		return STRATEGY_NAME;
	}

	/**
	 * Analyzes the given candle history and produces a {@link TradeSignal}.
	 *
	 * @param candles chronologically ordered candles (oldest → newest); must not be null
	 * @return a signal with confidence and a human-readable reason
	 */
	@Override
	public TradeSignal analyze(List<MyCandle> candles) {
		if (candles == null || candles.size() < MIN_CANDLES) {
			return holdSignal(candles, "Insufficient candle data for analysis");
		}

		int size = candles.size();
		MyCandle latest = candles.get(size - 1);
		MyCandle previous = candles.get(size - 2);
		MyCandle thirdLast = candles.get(size - 3);

		// 1. Trend detection
		Trend trend = detectTrend(candles);

		// 2. Compute average volume and body for noise filtering
		double avgVolume = averageVolume(candles);
		double avgBody = averageBody(candles);

		// 3. Collect all detected patterns from the most-recent candles
		List<CandleType> allPatterns = new ArrayList<>();
		allPatterns.addAll(latest.getCandleTypes() != null ? latest.getCandleTypes() : List.of());
		allPatterns.addAll(MyCandle.detectTwoCandlePatterns(previous, latest));
		allPatterns.addAll(MyCandle.detectThreeCandlePatterns(thirdLast, previous, latest));

		// 4. Filter noise: reject patterns on insignificant candles
		boolean isSignificant = isSignificantCandle(latest, avgBody, avgVolume);
		if (!isSignificant) {
			log.debug("Latest candle failed significance filter — treating as noise");
			return holdSignal(candles, "Latest candle is insignificant (noise/inducement filtered)");
		}

		// 5. Score each pattern
		double rawScore = 0.0;
		List<CandleType> confirmedPatterns = new ArrayList<>();

		for (CandleType pattern : allPatterns) {
			double weight = weightForPattern(pattern);
			if (weight == 0.0) {
				continue;
			}

			// Apply trend-alignment bonus
			if (isTrendAligned(pattern, trend)) {
				weight *= TREND_ALIGNMENT_BONUS;
			}

			rawScore += weight;
			confirmedPatterns.add(pattern);
		}

		// 6. Determine signal
		double confidence = Math.min(Math.abs(rawScore), MAX_CONFIDENCE);
		Signal signal;
		String reason;

		if (rawScore >= SIGNAL_THRESHOLD) {
			signal = Signal.BUY;
			reason = buildReason(signal, confirmedPatterns, trend, confidence);
		} else if (rawScore <= -SIGNAL_THRESHOLD) {
			signal = Signal.SELL;
			reason = buildReason(signal, confirmedPatterns, trend, confidence);
		} else {
			signal = Signal.HOLD;
			reason = confirmedPatterns.isEmpty()
					? "No actionable patterns detected"
					: "Conflicting or weak signals — patterns: " + confirmedPatterns;
		}

		log.debug("PriceAction analysis: score={}, signal={}, confidence={}, patterns={}",
				rawScore, signal, confidence, confirmedPatterns);

		return new TradeSignal.Builder()
				.signal(signal)
				.confidence(confidence)
				.reason(reason)
				.detectedPatterns(confirmedPatterns)
				.timestamp(latest.getStart())
				.strategyName(STRATEGY_NAME)
				.build();
	}

	// -----------------------------------------------------------------------
	// Trend detection via EMA crossover
	// -----------------------------------------------------------------------

	enum Trend { UP, DOWN, SIDEWAYS }

	/**
	 * Determines the prevailing trend using a fast/slow EMA crossover on closing
	 * prices. Sideways is returned when the EMAs are within 0.1 % of each other.
	 */
	Trend detectTrend(List<MyCandle> candles) {
		double fastEma = ema(candles, FAST_EMA_PERIOD);
		double slowEma = ema(candles, SLOW_EMA_PERIOD);

		if (slowEma == 0) {
			return Trend.SIDEWAYS;
		}

		double diff = (fastEma - slowEma) / slowEma;

		if (diff > 0.001) {
			return Trend.UP;
		} else if (diff < -0.001) {
			return Trend.DOWN;
		}
		return Trend.SIDEWAYS;
	}

	/**
	 * Computes a simple exponential moving average of closing prices for the
	 * given period.
	 */
	private double ema(List<MyCandle> candles, int period) {
		int effectivePeriod = Math.min(period, candles.size());
		double multiplier = 2.0 / (effectivePeriod + 1);
		double ema = candles.get(0).getClose();

		for (int i = 1; i < candles.size(); i++) {
			ema = (candles.get(i).getClose() - ema) * multiplier + ema;
		}
		return ema;
	}

	// -----------------------------------------------------------------------
	// Noise / inducement filtering
	// -----------------------------------------------------------------------

	/**
	 * Returns {@code true} when the candle's body and volume are large enough
	 * to be considered a genuine market move rather than noise or an inducement.
	 */
	boolean isSignificantCandle(MyCandle candle, double avgBody, double avgVolume) {
		double body = Math.abs(candle.getClose() - candle.getOpen());
		double range = candle.range();

		// A candle whose body is tiny relative to its range is indecisive
		if (range > 0 && (body / range) < BODY_SIGNIFICANCE_THRESHOLD) {
			// Allow known indecision patterns (doji family) — they carry their own signal
			if (candle.getCandleTypes() != null && candle.getCandleTypes().stream()
					.anyMatch(t -> t == CandleType.DOJI
							|| t == CandleType.DRAGONFLY_DOJI
							|| t == CandleType.GRAVESTONE_DOJI)) {
				return true;
			}
			return false;
		}

		// Low-volume candles are often inducements
		if (avgVolume > 0 && candle.getVolume() != null
				&& candle.getVolume() < avgVolume * VOLUME_FILTER_RATIO) {
			return false;
		}

		return true;
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private double averageVolume(List<MyCandle> candles) {
		DoubleSummaryStatistics stats = candles.stream()
				.filter(c -> c.getVolume() != null)
				.mapToDouble(MyCandle::getVolume)
				.summaryStatistics();
		return stats.getCount() > 0 ? stats.getAverage() : 0;
	}

	private double averageBody(List<MyCandle> candles) {
		DoubleSummaryStatistics stats = candles.stream()
				.filter(c -> c.getOpen() != null && c.getClose() != null)
				.mapToDouble(c -> Math.abs(c.getClose() - c.getOpen()))
				.summaryStatistics();
		return stats.getCount() > 0 ? stats.getAverage() : 0;
	}

	/**
	 * Returns a signed weight for the given pattern. Bullish patterns are
	 * positive, bearish patterns are negative.
	 */
	private double weightForPattern(CandleType pattern) {
		return switch (pattern) {
			// Strong reversal
			case BULLISH_ENGULFING  ->  WEIGHT_STRONG;
			case BEARISH_ENGULFING  -> -WEIGHT_STRONG;
			case MORNING_STAR       ->  WEIGHT_STRONG;
			case EVENING_STAR       -> -WEIGHT_STRONG;

			// Moderate reversal
			case BULLISH_HARAMI     ->  WEIGHT_MODERATE;
			case BEARISH_HARAMI     -> -WEIGHT_MODERATE;
			case PIERCING_LINE      ->  WEIGHT_MODERATE;
			case DARK_CLOUD_COVER   -> -WEIGHT_MODERATE;
			case TWEEZER_BOTTOM     ->  WEIGHT_MODERATE;
			case TWEEZER_TOP        -> -WEIGHT_MODERATE;

			// Continuation
			case THREE_WHITE_SOLDIERS ->  WEIGHT_CONTINUATION;
			case THREE_BLACK_CROWS    -> -WEIGHT_CONTINUATION;
			case MARUBOZU_BULLISH     ->  WEIGHT_CONTINUATION;
			case MARUBOZU_BEARISH     -> -WEIGHT_CONTINUATION;

			// Weak single-candle hints
			case HAMMER             ->  WEIGHT_WEAK;
			case INVERTED_HAMMER    ->  WEIGHT_WEAK;
			case HANGING_MAN        -> -WEIGHT_WEAK;
			case SHOOTING_STAR      -> -WEIGHT_WEAK;
			case DRAGONFLY_DOJI     ->  WEIGHT_WEAK;
			case GRAVESTONE_DOJI    -> -WEIGHT_WEAK;

			// Direction markers — lightweight
			case BULLISH            ->  0.05;
			case BEARISH            -> -0.05;

			// Neutral / indecision — no directional weight
			case DOJI, SPINNING_TOP, NEUTRAL -> 0.0;
		};
	}

	/**
	 * Returns {@code true} when the pattern's direction aligns with the prevailing
	 * trend, which makes the signal more reliable.
	 */
	private boolean isTrendAligned(CandleType pattern, Trend trend) {
		if (trend == Trend.SIDEWAYS) {
			return false;
		}
		boolean bullishPattern = weightForPattern(pattern) > 0;
		return (bullishPattern && trend == Trend.UP) || (!bullishPattern && trend == Trend.DOWN);
	}

	private TradeSignal holdSignal(List<MyCandle> candles, String reason) {
		Long ts = candles != null && !candles.isEmpty()
				? candles.get(candles.size() - 1).getStart()
				: null;
		return new TradeSignal.Builder()
				.signal(Signal.HOLD)
				.confidence(0.0)
				.reason(reason)
				.detectedPatterns(List.of())
				.timestamp(ts)
				.strategyName(STRATEGY_NAME)
				.build();
	}

	private String buildReason(Signal signal, List<CandleType> patterns, Trend trend, double confidence) {
		return String.format("%s signal (confidence %.0f%%) — trend: %s, patterns: %s",
				signal, confidence * 100, trend, patterns);
	}
}
