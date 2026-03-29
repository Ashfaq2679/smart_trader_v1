package com.techcobber.smarttrader.v1.strategy;

import com.techcobber.smarttrader.v1.models.MyCandle.CandleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link TradeSignal} value object and its {@link TradeSignal.Builder}.
 */
class TradeSignalTest {

	// -----------------------------------------------------------------------
	// Builder defaults
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("Builder defaults")
	class BuilderDefaults {

		@Test
		@DisplayName("Default signal is HOLD with zero confidence")
		void defaultsToHold() {
			TradeSignal signal = new TradeSignal.Builder().build();

			assertThat(signal.getSignal()).isEqualTo(Signal.HOLD);
			assertThat(signal.getConfidence()).isZero();
			assertThat(signal.getReason()).isNull();
			assertThat(signal.getDetectedPatterns()).isEmpty();
			assertThat(signal.getTimestamp()).isNull();
			assertThat(signal.getStrategyName()).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// Builder populates all fields
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("Builder populates fields")
	class BuilderPopulation {

		@Test
		@DisplayName("All fields are set correctly via Builder")
		void allFieldsSet() {
			List<CandleType> patterns = List.of(CandleType.BULLISH_ENGULFING, CandleType.HAMMER);

			TradeSignal signal = new TradeSignal.Builder()
					.signal(Signal.BUY)
					.confidence(0.85)
					.reason("Strong bullish reversal")
					.detectedPatterns(patterns)
					.timestamp(1710000000L)
					.strategyName("PRICE_ACTION")
					.build();

			assertThat(signal.getSignal()).isEqualTo(Signal.BUY);
			assertThat(signal.getConfidence()).isEqualTo(0.85);
			assertThat(signal.getReason()).isEqualTo("Strong bullish reversal");
			assertThat(signal.getDetectedPatterns()).containsExactly(
					CandleType.BULLISH_ENGULFING, CandleType.HAMMER);
			assertThat(signal.getTimestamp()).isEqualTo(1710000000L);
			assertThat(signal.getStrategyName()).isEqualTo("PRICE_ACTION");
		}

		@Test
		@DisplayName("SELL signal with patterns")
		void sellSignal() {
			TradeSignal signal = new TradeSignal.Builder()
					.signal(Signal.SELL)
					.confidence(0.6)
					.reason("Bearish reversal detected")
					.detectedPatterns(List.of(CandleType.BEARISH_ENGULFING))
					.build();

			assertThat(signal.getSignal()).isEqualTo(Signal.SELL);
			assertThat(signal.getConfidence()).isEqualTo(0.6);
		}
	}

	// -----------------------------------------------------------------------
	// Confidence clamping
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("Confidence clamping")
	class ConfidenceClamping {

		@Test
		@DisplayName("Confidence above 1.0 is clamped to 1.0")
		void clampAboveOne() {
			TradeSignal signal = new TradeSignal.Builder()
					.confidence(1.5)
					.build();
			assertThat(signal.getConfidence()).isEqualTo(1.0);
		}

		@Test
		@DisplayName("Confidence below 0.0 is clamped to 0.0")
		void clampBelowZero() {
			TradeSignal signal = new TradeSignal.Builder()
					.confidence(-0.3)
					.build();
			assertThat(signal.getConfidence()).isZero();
		}

		@Test
		@DisplayName("Confidence within range is unchanged")
		void withinRange() {
			TradeSignal signal = new TradeSignal.Builder()
					.confidence(0.75)
					.build();
			assertThat(signal.getConfidence()).isEqualTo(0.75);
		}
	}

	// -----------------------------------------------------------------------
	// Immutability
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("Immutability")
	class Immutability {

		@Test
		@DisplayName("Detected patterns list is immutable")
		void patternsImmutable() {
			List<CandleType> mutable = new java.util.ArrayList<>(List.of(CandleType.HAMMER));

			TradeSignal signal = new TradeSignal.Builder()
					.detectedPatterns(mutable)
					.build();

			// Mutating the original list should not affect the signal
			mutable.add(CandleType.DOJI);
			assertThat(signal.getDetectedPatterns()).containsExactly(CandleType.HAMMER);
		}

		@Test
		@DisplayName("Null patterns list becomes empty immutable list")
		void nullPatternsBecomesEmpty() {
			TradeSignal signal = new TradeSignal.Builder()
					.detectedPatterns(null)
					.build();

			assertThat(signal.getDetectedPatterns()).isEmpty();
		}
	}

	// -----------------------------------------------------------------------
	// toString
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("toString")
	class ToStringTests {

		@Test
		@DisplayName("toString contains key fields")
		void containsKeyFields() {
			TradeSignal signal = new TradeSignal.Builder()
					.signal(Signal.BUY)
					.confidence(0.9)
					.reason("Test reason")
					.strategyName("PRICE_ACTION")
					.build();

			String str = signal.toString();
			assertThat(str).contains("BUY");
			assertThat(str).contains("0.90");
			assertThat(str).contains("Test reason");
			assertThat(str).contains("PRICE_ACTION");
		}
	}
}
