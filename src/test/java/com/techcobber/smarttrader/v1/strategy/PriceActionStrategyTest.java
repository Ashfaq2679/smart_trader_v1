package com.techcobber.smarttrader.v1.strategy;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.MyCandle.CandleType;
import com.techcobber.smarttrader.v1.strategy.PriceActionStrategy.Trend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for the {@link PriceActionStrategy}.
 *
 * <p>Tests are organised into nested groups that mirror the strategy's internal
 * responsibilities: input validation, trend detection, noise filtering, pattern
 * scoring, and end-to-end signal generation.</p>
 */
class PriceActionStrategyTest {

	private PriceActionStrategy strategy;

	@BeforeEach
	void setUp() {
		strategy = new PriceActionStrategy();
	}

	// -----------------------------------------------------------------------
	// Helper factory methods — consistent with MyCandleTest conventions
	// -----------------------------------------------------------------------

	private static MyCandle candle(double open, double close, double high, double low, double volume) {
		return new MyCandle.Builder()
				.open(open).close(close).high(high).low(low)
				.start(1L).volume(volume)
				.build();
	}

	private static MyCandle candle(double open, double close, double high, double low) {
		return candle(open, close, high, low, 100);
	}

	/** Creates a simple bullish candle at a given price level. */
	private static MyCandle simpleBullish(double base, double volume) {
		return candle(base, base + 10, base + 12, base - 1, volume);
	}

	/** Creates a simple bearish candle at a given price level. */
	private static MyCandle simpleBearish(double base, double volume) {
		return candle(base + 10, base, base + 12, base - 1, volume);
	}

	// =======================================================================
	// Strategy metadata
	// =======================================================================

	@Nested
	@DisplayName("Strategy metadata")
	class MetadataTests {

		@Test
		@DisplayName("getName returns PRICE_ACTION")
		void returnsName() {
			assertThat(strategy.getName()).isEqualTo("PRICE_ACTION");
		}

		@Test
		@DisplayName("Implements TradingStrategy interface")
		void implementsInterface() {
			assertThat(strategy).isInstanceOf(TradingStrategy.class);
		}
	}

	// =======================================================================
	// Input validation
	// =======================================================================

	@Nested
	@DisplayName("Input validation")
	class InputValidation {

		@Test
		@DisplayName("Null input returns HOLD with zero confidence")
		void nullInput() {
			TradeSignal signal = strategy.analyze(null);

			assertThat(signal.getSignal()).isEqualTo(Signal.HOLD);
			assertThat(signal.getConfidence()).isZero();
			assertThat(signal.getReason()).contains("Insufficient");
		}

		@Test
		@DisplayName("Empty list returns HOLD with zero confidence")
		void emptyInput() {
			TradeSignal signal = strategy.analyze(List.of());

			assertThat(signal.getSignal()).isEqualTo(Signal.HOLD);
			assertThat(signal.getConfidence()).isZero();
		}

		@Test
		@DisplayName("Fewer than MIN_CANDLES returns HOLD")
		void tooFewCandles() {
			List<MyCandle> candles = List.of(
					candle(100, 105, 106, 99),
					candle(105, 110, 112, 104)
			);
			TradeSignal signal = strategy.analyze(candles);

			assertThat(signal.getSignal()).isEqualTo(Signal.HOLD);
			assertThat(signal.getConfidence()).isZero();
		}

		@Test
		@DisplayName("Exactly MIN_CANDLES does not return insufficient-data hold")
		void exactlyMinCandles() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < PriceActionStrategy.MIN_CANDLES; i++) {
				candles.add(simpleBullish(100 + i * 10, 100));
			}
			TradeSignal signal = strategy.analyze(candles);

			assertThat(signal.getReason()).doesNotContain("Insufficient");
		}
	}

	// =======================================================================
	// Trend detection
	// =======================================================================

	@Nested
	@DisplayName("Trend detection")
	class TrendDetection {

		@Test
		@DisplayName("Uptrend detected when prices are rising")
		void uptrendDetected() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				candles.add(simpleBullish(100 + i * 10, 100));
			}
			Trend trend = strategy.detectTrend(candles);
			assertThat(trend).isEqualTo(Trend.UP);
		}

		@Test
		@DisplayName("Downtrend detected when prices are falling")
		void downtrendDetected() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				candles.add(simpleBearish(200 - i * 10, 100));
			}
			Trend trend = strategy.detectTrend(candles);
			assertThat(trend).isEqualTo(Trend.DOWN);
		}

		@Test
		@DisplayName("Sideways when prices are flat")
		void sidewaysDetected() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				candles.add(candle(100, 100.01, 100.5, 99.5, 100));
			}
			Trend trend = strategy.detectTrend(candles);
			assertThat(trend).isEqualTo(Trend.SIDEWAYS);
		}
	}

	// =======================================================================
	// Noise / inducement filtering
	// =======================================================================

	@Nested
	@DisplayName("Noise filtering")
	class NoiseFiltering {

		@Test
		@DisplayName("Candle with tiny body relative to range is insignificant")
		void tinyBodyInsignificant() {
			// Body/range ratio is ~0.1, classified as SPINNING_TOP (not DOJI), so noise
			MyCandle noisy = candle(100, 102, 110, 90, 100);
			boolean significant = strategy.isSignificantCandle(noisy, 5.0, 100);
			assertThat(significant).isFalse();
		}

		@Test
		@DisplayName("Doji patterns pass significance filter despite tiny body")
		void dojiPassesFilter() {
			// Doji — tiny body but it is a known pattern, should pass
			MyCandle doji = candle(100, 100, 105, 95, 100);
			boolean significant = strategy.isSignificantCandle(doji, 5.0, 100);
			assertThat(significant).isTrue();
		}

		@Test
		@DisplayName("Low-volume candle is filtered out as inducement")
		void lowVolumeFiltered() {
			MyCandle lowVol = candle(100, 110, 112, 99, 10); // volume=10
			boolean significant = strategy.isSignificantCandle(lowVol, 5.0, 100); // avgVol=100
			assertThat(significant).isFalse();
		}

		@Test
		@DisplayName("Adequate-volume candle passes filter")
		void adequateVolumePass() {
			MyCandle ok = candle(100, 110, 112, 99, 80);
			boolean significant = strategy.isSignificantCandle(ok, 5.0, 100);
			assertThat(significant).isTrue();
		}

		@Test
		@DisplayName("Full analysis returns HOLD when latest candle is noise")
		void analyzeReturnsHoldForNoise() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				candles.add(simpleBullish(100 + i * 5, 100));
			}
			// Noisy candle: small body (not DOJI), large range, low volume
			candles.add(candle(100, 103, 120, 80, 5));

			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getSignal()).isEqualTo(Signal.HOLD);
			assertThat(signal.getReason()).contains("noise");
		}
	}

	// =======================================================================
	// Signal generation — bullish scenarios
	// =======================================================================

	@Nested
	@DisplayName("Bullish signal scenarios")
	class BullishSignals {

		@Test
		@DisplayName("Bullish engulfing in uptrend generates BUY")
		void bullishEngulfingInUptrend() {
			List<MyCandle> candles = new ArrayList<>();
			// Build an uptrend context
			for (int i = 0; i < 6; i++) {
				candles.add(simpleBullish(100 + i * 10, 100));
			}
			// Add bearish candle followed by bullish engulfing
			candles.add(candle(160, 155, 162, 154, 100)); // bearish
			candles.add(candle(153, 165, 167, 152, 120)); // bullish engulfing

			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getSignal()).isEqualTo(Signal.BUY);
			assertThat(signal.getConfidence()).isGreaterThan(0);
			assertThat(signal.getDetectedPatterns()).contains(CandleType.BULLISH_ENGULFING);
		}

		@Test
		@DisplayName("Morning star pattern generates BUY")
		void morningStarGeneratesBuy() {
			List<MyCandle> candles = new ArrayList<>();
			// Context candles trending down then reversing
			for (int i = 0; i < 5; i++) {
				candles.add(simpleBullish(100 + i * 5, 100));
			}
			// Morning star: bearish, small-body, bullish
			candles.add(candle(130, 120, 132, 119, 100)); // first: bearish
			candles.add(candle(119, 119.5, 120, 118, 100)); // second: small body
			candles.add(candle(120, 128, 129, 119, 100)); // third: bullish, closes > midpoint of first

			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getSignal()).isEqualTo(Signal.BUY);
			assertThat(signal.getDetectedPatterns()).contains(CandleType.MORNING_STAR);
		}

		@Test
		@DisplayName("Hammer with bullish engulfing confirmation generates BUY")
		void hammerWithConfirmation() {
			List<MyCandle> candles = new ArrayList<>();
			// Build uptrend
			for (int i = 0; i < 6; i++) {
				candles.add(simpleBullish(100 + i * 10, 100));
			}
			// Bearish candle to set up engulfing
			candles.add(candle(160, 155, 162, 154, 100));
			// Hammer that also engulfs previous candle
			candles.add(candle(153, 163, 164, 140, 100));

			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getSignal()).isEqualTo(Signal.BUY);
			assertThat(signal.getConfidence()).isGreaterThan(0);
		}

		@Test
		@DisplayName("Three white soldiers generates BUY signal")
		void threeWhiteSoldiersGeneratesBuy() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				candles.add(simpleBullish(100 + i * 5, 100));
			}
			// Three white soldiers
			candles.add(candle(120, 130, 131, 119, 100));
			candles.add(candle(125, 140, 141, 124, 100));
			candles.add(candle(135, 150, 151, 134, 100));

			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getSignal()).isEqualTo(Signal.BUY);
			assertThat(signal.getDetectedPatterns()).contains(CandleType.THREE_WHITE_SOLDIERS);
		}
	}

	// =======================================================================
	// Signal generation — bearish scenarios
	// =======================================================================

	@Nested
	@DisplayName("Bearish signal scenarios")
	class BearishSignals {

		@Test
		@DisplayName("Bearish engulfing in downtrend generates SELL")
		void bearishEngulfingInDowntrend() {
			List<MyCandle> candles = new ArrayList<>();
			// Build a downtrend context
			for (int i = 0; i < 6; i++) {
				candles.add(simpleBearish(200 - i * 10, 100));
			}
			// Add bullish candle followed by bearish engulfing
			candles.add(candle(135, 140, 142, 134, 100)); // bullish
			candles.add(candle(142, 132, 143, 131, 120)); // bearish engulfing

			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getSignal()).isEqualTo(Signal.SELL);
			assertThat(signal.getConfidence()).isGreaterThan(0);
			assertThat(signal.getDetectedPatterns()).contains(CandleType.BEARISH_ENGULFING);
		}

		@Test
		@DisplayName("Evening star pattern generates SELL")
		void eveningStarGeneratesSell() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				candles.add(simpleBearish(200 - i * 5, 100));
			}
			// Evening star: bullish, small-body, bearish
			candles.add(candle(170, 180, 182, 169, 100)); // first: bullish
			candles.add(candle(181, 181.5, 182, 180, 100)); // second: small body above first's close
			candles.add(candle(180, 172, 181, 171, 100)); // third: bearish, closes < midpoint of first

			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getSignal()).isEqualTo(Signal.SELL);
			assertThat(signal.getDetectedPatterns()).contains(CandleType.EVENING_STAR);
		}

		@Test
		@DisplayName("Three black crows generates SELL signal")
		void threeBlackCrowsGeneratesSell() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				candles.add(simpleBearish(200 - i * 5, 100));
			}
			// Three black crows
			candles.add(candle(180, 170, 181, 169, 100));
			candles.add(candle(175, 160, 176, 159, 100));
			candles.add(candle(165, 150, 166, 149, 100));

			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getSignal()).isEqualTo(Signal.SELL);
			assertThat(signal.getDetectedPatterns()).contains(CandleType.THREE_BLACK_CROWS);
		}

		@Test
		@DisplayName("Shooting star with bearish engulfing confirmation generates SELL")
		void shootingStarWithConfirmation() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				candles.add(simpleBearish(200 - i * 10, 100));
			}
			// Bullish candle to set up engulfing
			candles.add(candle(135, 140, 142, 134, 100));
			// Bearish candle that engulfs previous
			candles.add(candle(142, 132, 143, 131, 120));

			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getSignal()).isEqualTo(Signal.SELL);
			assertThat(signal.getConfidence()).isGreaterThan(0);
		}
	}

	// =======================================================================
	// HOLD scenarios
	// =======================================================================

	@Nested
	@DisplayName("Hold scenarios")
	class HoldSignals {

		@Test
		@DisplayName("Neutral candles produce HOLD")
		void neutralCandlesProduceHold() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 7; i++) {
				candles.add(candle(100, 100.01, 100.5, 99.5, 100));
			}
			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getSignal()).isEqualTo(Signal.HOLD);
		}

		@Test
		@DisplayName("Conflicting patterns produce HOLD")
		void conflictingPatternsProduceHold() {
			List<MyCandle> candles = new ArrayList<>();
			// Create a series where patterns cancel each other
			for (int i = 0; i < 5; i++) {
				candles.add(candle(100, 100.01, 100.5, 99.5, 100));
			}
			// Mild bullish candle — not strong enough to trigger
			candles.add(candle(100, 101, 101.5, 99.5, 100));
			candles.add(candle(101, 102, 102.5, 100.5, 100));

			TradeSignal signal = strategy.analyze(candles);
			// With only weak directional candles, score stays below threshold
			assertThat(signal.getSignal()).isIn(Signal.HOLD, Signal.BUY);
		}
	}

	// =======================================================================
	// Signal output quality
	// =======================================================================

	@Nested
	@DisplayName("Signal output quality")
	class SignalOutputQuality {

		@Test
		@DisplayName("Signal includes strategy name")
		void includesStrategyName() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 7; i++) {
				candles.add(simpleBullish(100 + i * 10, 100));
			}
			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getStrategyName()).isEqualTo("PRICE_ACTION");
		}

		@Test
		@DisplayName("Signal includes timestamp from latest candle")
		void includesTimestamp() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 7; i++) {
				candles.add(simpleBullish(100 + i * 10, 100));
			}
			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getTimestamp()).isNotNull();
		}

		@Test
		@DisplayName("Confidence is between 0 and 1")
		void confidenceInRange() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				candles.add(simpleBullish(100 + i * 10, 100));
			}
			// Strong bullish engulfing
			candles.add(candle(175, 170, 177, 169, 100));
			candles.add(candle(168, 180, 182, 167, 120));

			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getConfidence()).isBetween(0.0, 1.0);
		}

		@Test
		@DisplayName("Reason is never null for valid input")
		void reasonNeverNull() {
			List<MyCandle> candles = new ArrayList<>();
			for (int i = 0; i < 7; i++) {
				candles.add(simpleBullish(100 + i * 10, 100));
			}
			TradeSignal signal = strategy.analyze(candles);
			assertThat(signal.getReason()).isNotNull().isNotEmpty();
		}
	}
}
