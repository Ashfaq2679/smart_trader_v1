package com.techcobber.smarttrader.v1.strategy;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PriceActionStrategy}.
 */
class PriceActionStrategyTest {

    private PriceActionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PriceActionStrategy();
    }

    // -----------------------------------------------------------------------
    // Helper factory methods
    // -----------------------------------------------------------------------

    private static MyCandle candle(double open, double close, double high, double low, long start) {
        return new MyCandle.Builder()
                .open(open).close(close).high(high).low(low)
                .start(start).volume(100)
                .build();
    }

    /**
     * Creates 25+ candles simulating a strong uptrend with higher highs/lows,
     * ending with bullish patterns to trigger a BUY signal.
     */
    private static List<MyCandle> bullishUptrendCandles() {
        List<MyCandle> candles = new ArrayList<>();
        double base = 100;
        // Build a wave-like uptrend with clear swing structure
        for (int i = 0; i < 22; i++) {
            double drift = i * 1.5;
            // Oscillate to create swing highs and lows
            double wave = 3.0 * Math.sin(i * 0.8);
            double open = base + drift + wave;
            double close = open + 1.5 + (i % 3 == 0 ? -0.5 : 0.5);
            double high = Math.max(open, close) + 2;
            double low = Math.min(open, close) - 2;
            candles.add(candle(open, close, high, low, i));
        }
        // End with two strong bullish candles (bullish engulfing pattern)
        double lastClose = candles.get(candles.size() - 1).getClose();
        // Bearish candle
        candles.add(candle(lastClose + 2, lastClose - 1, lastClose + 3, lastClose - 2, 22L));
        // Bullish engulfing candle (open <= prev.close, close >= prev.open)
        double prevOpen = lastClose + 2;
        double prevClose = lastClose - 1;
        candles.add(candle(prevClose - 1, prevOpen + 2, prevOpen + 4, prevClose - 2, 23L));
        // One more strong bullish candle
        double nextOpen = prevOpen + 2;
        candles.add(candle(nextOpen, nextOpen + 8, nextOpen + 8.5, nextOpen - 0.3, 24L));

        return candles;
    }

    /**
     * Creates 25+ candles simulating a strong downtrend with lower highs/lows,
     * ending with bearish patterns to trigger a SELL signal.
     */
    private static List<MyCandle> bearishDowntrendCandles() {
        List<MyCandle> candles = new ArrayList<>();
        double base = 200;
        for (int i = 0; i < 22; i++) {
            double drift = -i * 1.5;
            double wave = 3.0 * Math.sin(i * 0.8);
            double open = base + drift + wave;
            double close = open - 1.5 - (i % 3 == 0 ? -0.5 : 0.5);
            double high = Math.max(open, close) + 2;
            double low = Math.min(open, close) - 2;
            candles.add(candle(open, close, high, low, i));
        }
        // End with bearish engulfing pattern
        double lastClose = candles.get(candles.size() - 1).getClose();
        // Bullish candle
        candles.add(candle(lastClose - 2, lastClose + 1, lastClose + 2, lastClose - 3, 22L));
        // Bearish engulfing (open >= prev.close, close <= prev.open)
        double prevOpen = lastClose - 2;
        double prevClose = lastClose + 1;
        candles.add(candle(prevClose + 1, prevOpen - 2, prevClose + 3, prevOpen - 3, 23L));
        // One more strong bearish candle
        double nextOpen = prevOpen - 2;
        candles.add(candle(nextOpen, nextOpen - 8, nextOpen + 0.3, nextOpen - 8.5, 24L));

        return candles;
    }

    /**
     * Creates 25+ sideways candles with no clear trend or patterns.
     */
    private static List<MyCandle> sidewaysCandles() {
        List<MyCandle> candles = new ArrayList<>();
        double base = 100;
        for (int i = 0; i < 25; i++) {
            // oscillate around base with no drift
            double wave = 3.0 * Math.sin(i * 1.2);
            double open = base + wave;
            double close = open + (i % 2 == 0 ? 0.5 : -0.5);
            double high = Math.max(open, close) + 1.5;
            double low = Math.min(open, close) - 1.5;
            candles.add(candle(open, close, high, low, i));
        }
        return candles;
    }

    // =======================================================================
    // getName Tests
    // =======================================================================

    @Nested
    @DisplayName("getName")
    class GetNameTests {

        @Test
        @DisplayName("Returns 'PriceAction'")
        void returnsPriceAction() {
            assertThat(strategy.getName()).isEqualTo("PriceAction");
        }
    }

    // =======================================================================
    // Null / Insufficient Data Tests
    // =======================================================================

    @Nested
    @DisplayName("Null and Insufficient Data")
    class NullAndInsufficientData {

        @Test
        @DisplayName("Null candles returns HOLD with 0 confidence")
        void nullCandlesReturnsHold() {
            TradeDecision decision = strategy.analyze(null);

            assertThat(decision.getSignal()).isEqualTo(Signal.HOLD);
            assertThat(decision.getConfidence()).isEqualTo(0.0);
            assertThat(decision.getReasoning()).contains("Insufficient");
        }

        @Test
        @DisplayName("Insufficient candles returns HOLD")
        void insufficientCandlesReturnsHold() {
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                candles.add(candle(100 + i, 101 + i, 103 + i, 99 + i, i));
            }

            TradeDecision decision = strategy.analyze(candles);

            assertThat(decision.getSignal()).isEqualTo(Signal.HOLD);
            assertThat(decision.getConfidence()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Insufficient candles sets trendDirection to UNKNOWN")
        void insufficientCandlesSetsTrendUnknown() {
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                candles.add(candle(100 + i, 101 + i, 103 + i, 99 + i, i));
            }

            TradeDecision decision = strategy.analyze(candles);

            assertThat(decision.getTrendDirection()).isEqualTo("UNKNOWN");
        }
    }

    // =======================================================================
    // BUY Signal Tests
    // =======================================================================

    @Nested
    @DisplayName("BUY Signal Generation")
    class BuySignalTests {

        @Test
        @DisplayName("Generates BUY signal with bullish patterns in uptrend")
        void generatesBuySignalInUptrend() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles);

            // With strong uptrend and bullish patterns, expect BUY
            assertThat(decision.getSignal()).isEqualTo(Signal.BUY);
        }

        @Test
        @DisplayName("BUY signal has positive confidence")
        void buySignalHasPositiveConfidence() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles);

            if (decision.getSignal() == Signal.BUY) {
                assertThat(decision.getConfidence()).isGreaterThan(0.0);
            }
        }
    }

    // =======================================================================
    // SELL Signal Tests
    // =======================================================================

    @Nested
    @DisplayName("SELL Signal Generation")
    class SellSignalTests {

        @Test
        @DisplayName("Generates SELL signal with bearish patterns in downtrend")
        void generatesSellSignalInDowntrend() {
            List<MyCandle> candles = bearishDowntrendCandles();

            TradeDecision decision = strategy.analyze(candles);

            assertThat(decision.getSignal()).isEqualTo(Signal.SELL);
        }

        @Test
        @DisplayName("SELL signal has positive confidence")
        void sellSignalHasPositiveConfidence() {
            List<MyCandle> candles = bearishDowntrendCandles();

            TradeDecision decision = strategy.analyze(candles);

            if (decision.getSignal() == Signal.SELL) {
                assertThat(decision.getConfidence()).isGreaterThan(0.0);
            }
        }
    }

    // =======================================================================
    // HOLD Signal Tests
    // =======================================================================

    @Nested
    @DisplayName("HOLD Signal")
    class HoldSignalTests {

        @Test
        @DisplayName("HOLD when no strong confluence")
        void holdWhenNoConfluence() {
            List<MyCandle> candles = sidewaysCandles();

            TradeDecision decision = strategy.analyze(candles);

            assertThat(decision.getSignal()).isEqualTo(Signal.HOLD);
            assertThat(decision.getConfidence()).isEqualTo(0.0);
        }
    }

    // =======================================================================
    // TradeDecision Field Tests
    // =======================================================================

    @Nested
    @DisplayName("TradeDecision Fields")
    class TradeDecisionFields {

        @Test
        @DisplayName("Detected patterns are populated in TradeDecision")
        void detectedPatternsPopulated() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles);

            assertThat(decision.getDetectedPatterns()).isNotNull();
            // With 25+ candles, at least some single-candle patterns should be detected
            assertThat(decision.getDetectedPatterns()).isNotEmpty();
        }

        @Test
        @DisplayName("Confidence is between 0 and 1")
        void confidenceBounded() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles);

            assertThat(decision.getConfidence()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("Reasoning contains pattern and trend info")
        void reasoningContainsInfo() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles);

            assertThat(decision.getReasoning()).isNotNull();
            assertThat(decision.getReasoning()).contains("Trend:");
            assertThat(decision.getReasoning()).contains("Signal:");
        }

        @Test
        @DisplayName("Trend direction is set in TradeDecision")
        void trendDirectionSet() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles);

            assertThat(decision.getTrendDirection()).isNotNull();
            assertThat(decision.getTrendDirection()).isIn("UP", "DOWN", "SIDEWAYS");
        }

        @Test
        @DisplayName("Timestamp is set in TradeDecision")
        void timestampSet() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles);

            assertThat(decision.getTimestamp()).isNotNull();
        }
    }
}
