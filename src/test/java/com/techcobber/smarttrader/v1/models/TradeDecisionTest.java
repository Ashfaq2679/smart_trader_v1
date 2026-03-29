package com.techcobber.smarttrader.v1.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link TradeDecision} model.
 * Verifies Lombok-generated builder, getters, equals, hashCode, and toString.
 */
class TradeDecisionTest {

    private static TradeDecision sampleDecision() {
        return TradeDecision.builder()
                .signal(TradeDecision.Signal.BUY)
                .confidence(0.85)
                .reasoning("Strong uptrend with bullish engulfing")
                .detectedPatterns(List.of("BULLISH_ENGULFING", "HAMMER"))
                .trendDirection("UP")
                .nearestSupport(95.0)
                .nearestResistance(115.0)
                .productId("BTC-USD")
                .timestamp(LocalDateTime.of(2025, 6, 15, 12, 0, 0))
                .build();
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Builder creates TradeDecision with all fields set")
        void builderSetsAllFields() {
            TradeDecision decision = sampleDecision();

            assertThat(decision.getSignal()).isEqualTo(TradeDecision.Signal.BUY);
            assertThat(decision.getConfidence()).isEqualTo(0.85);
            assertThat(decision.getReasoning()).isEqualTo("Strong uptrend with bullish engulfing");
            assertThat(decision.getDetectedPatterns()).containsExactly("BULLISH_ENGULFING", "HAMMER");
            assertThat(decision.getTrendDirection()).isEqualTo("UP");
            assertThat(decision.getNearestSupport()).isEqualTo(95.0);
            assertThat(decision.getNearestResistance()).isEqualTo(115.0);
            assertThat(decision.getProductId()).isEqualTo("BTC-USD");
            assertThat(decision.getTimestamp()).isEqualTo(LocalDateTime.of(2025, 6, 15, 12, 0, 0));
        }

        @Test
        @DisplayName("Builder allows null optional fields")
        void builderAllowsNullOptionalFields() {
            TradeDecision decision = TradeDecision.builder()
                    .signal(TradeDecision.Signal.HOLD)
                    .confidence(0.0)
                    .reasoning("No data")
                    .build();

            assertThat(decision.getSignal()).isEqualTo(TradeDecision.Signal.HOLD);
            assertThat(decision.getConfidence()).isEqualTo(0.0);
            assertThat(decision.getDetectedPatterns()).isNull();
            assertThat(decision.getNearestSupport()).isNull();
            assertThat(decision.getNearestResistance()).isNull();
            assertThat(decision.getProductId()).isNull();
            assertThat(decision.getTimestamp()).isNull();
        }

        @Test
        @DisplayName("Builder supports SELL signal")
        void builderSupportsSellSignal() {
            TradeDecision decision = TradeDecision.builder()
                    .signal(TradeDecision.Signal.SELL)
                    .confidence(0.72)
                    .reasoning("Bearish pattern in downtrend")
                    .trendDirection("DOWN")
                    .build();

            assertThat(decision.getSignal()).isEqualTo(TradeDecision.Signal.SELL);
            assertThat(decision.getTrendDirection()).isEqualTo("DOWN");
        }
    }

    @Nested
    @DisplayName("Getter/Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Setters update fields correctly")
        void settersUpdateFields() {
            TradeDecision decision = sampleDecision();

            decision.setSignal(TradeDecision.Signal.SELL);
            decision.setConfidence(0.55);
            decision.setProductId("ETH-USD");

            assertThat(decision.getSignal()).isEqualTo(TradeDecision.Signal.SELL);
            assertThat(decision.getConfidence()).isEqualTo(0.55);
            assertThat(decision.getProductId()).isEqualTo("ETH-USD");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Equal objects have same hashCode")
        void equalObjectsSameHashCode() {
            LocalDateTime ts = LocalDateTime.of(2025, 6, 15, 12, 0, 0);
            TradeDecision d1 = TradeDecision.builder()
                    .signal(TradeDecision.Signal.BUY).confidence(0.8)
                    .reasoning("test").productId("BTC-USD").timestamp(ts).build();
            TradeDecision d2 = TradeDecision.builder()
                    .signal(TradeDecision.Signal.BUY).confidence(0.8)
                    .reasoning("test").productId("BTC-USD").timestamp(ts).build();

            assertThat(d1).isEqualTo(d2);
            assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
        }

        @Test
        @DisplayName("Different objects are not equal")
        void differentObjectsNotEqual() {
            TradeDecision d1 = TradeDecision.builder()
                    .signal(TradeDecision.Signal.BUY).confidence(0.8).build();
            TradeDecision d2 = TradeDecision.builder()
                    .signal(TradeDecision.Signal.SELL).confidence(0.8).build();

            assertThat(d1).isNotEqualTo(d2);
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString contains field values")
        void toStringContainsFieldValues() {
            TradeDecision decision = sampleDecision();
            String result = decision.toString();

            assertThat(result).contains("BUY");
            assertThat(result).contains("0.85");
            assertThat(result).contains("BTC-USD");
        }
    }

    @Nested
    @DisplayName("Signal Enum Tests")
    class SignalEnumTests {

        @Test
        @DisplayName("Signal enum contains BUY, SELL, HOLD")
        void signalEnumValues() {
            TradeDecision.Signal[] values = TradeDecision.Signal.values();
            assertThat(values).containsExactly(
                    TradeDecision.Signal.BUY,
                    TradeDecision.Signal.SELL,
                    TradeDecision.Signal.HOLD);
        }

        @Test
        @DisplayName("Signal valueOf works correctly")
        void signalValueOf() {
            assertThat(TradeDecision.Signal.valueOf("BUY")).isEqualTo(TradeDecision.Signal.BUY);
            assertThat(TradeDecision.Signal.valueOf("SELL")).isEqualTo(TradeDecision.Signal.SELL);
            assertThat(TradeDecision.Signal.valueOf("HOLD")).isEqualTo(TradeDecision.Signal.HOLD);
        }
    }
}
