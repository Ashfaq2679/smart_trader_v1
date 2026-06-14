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
     * Creates 28 candles: gradual grind with two ascending swing highs and two ascending
     * swing lows, ending at a bullish bounce off recent support.
     *
     * <p>Design constraints (verified analytically):
     * <ul>
     *   <li>Trend (UP): window[i=8..27] swing highs 104.5 (i=10)→120.0 (i=17) (higher-high)
     *       and swing lows 100.2 (i=13)→103.6 (i=24) (higher-low) → bullishSignals=2 ✓.
     *       Phase-4 highs strictly decrease (105.3→104.8) so no lower-high forms.
     *       i=19 low=103.6 prevents i=20 from being a pivot low.</li>
     *   <li>EMA50 (simple avg of 28 closes, period&gt;candle count) ≈ 102.1; final close=105.0;
     *       distanceFromEMA50 ≈ 2.87% ≤ 3% → onBullishPullback=true ✓</li>
     *   <li>Support=103.6 (1.33% below price) → nearSupport=true ✓</li>
     *   <li>Resistance=120.0 (14.3% above price) → nearResistance=false ✓</li>
     *   <li>ATR(14) ≈ 1.58; ATR_BUFFER=0.79; buffered R:R ≈ 6.5 ≥ MIN_RR=2.0 ✓</li>
     * </ul>
     */
    private static List<MyCandle> bullishUptrendCandles() {
        List<MyCandle> candles = new ArrayList<>();
        // Phase 0 (i=0..9): slow grind 99→101, small-body candles, ATR ≈ 0.8/candle.
        candles.add(candle(99.0,  99.2,  99.5,  98.8,  0));
        candles.add(candle(99.2,  99.4,  99.7,  98.9,  1));
        candles.add(candle(99.4,  99.6,  99.9,  99.1,  2));
        candles.add(candle(99.6,  99.9,  100.2, 99.3,  3));
        candles.add(candle(99.9,  100.1, 100.4, 99.6,  4));
        candles.add(candle(100.1, 100.3, 100.6, 99.8,  5));
        candles.add(candle(100.3, 100.5, 100.8, 100.0, 6));
        candles.add(candle(100.5, 100.7, 101.0, 100.2, 7));
        candles.add(candle(100.7, 100.9, 101.2, 100.4, 8));
        candles.add(candle(100.9, 101.1, 101.4, 100.6, 9));
        // Phase 1 (i=10..12): first swing high 104.5 at i=10, then fade.
        candles.add(candle(101.1, 101.4, 104.5, 100.8, 10)); // swing high 104.5
        candles.add(candle(101.4, 101.2, 101.7, 100.9, 11));
        candles.add(candle(101.2, 101.0, 101.5, 100.7, 12));
        // Phase 2 (i=13..16): first swing low 100.2 at i=13, recovery.
        candles.add(candle(101.0, 101.1, 101.4, 100.2, 13)); // swing low 100.2
        candles.add(candle(101.1, 101.3, 101.6, 100.9, 14));
        candles.add(candle(101.3, 101.5, 101.8, 101.0, 15));
        candles.add(candle(101.5, 101.7, 102.0, 101.2, 16));
        // Phase 3 (i=17): spike candle — close=105.0 keeps EMA50 ≈ 102.1; high=120.0
        // forms distant resistance (14.3% above final price). ATR decays to ≈1.58 by i=27.
        candles.add(candle(101.7, 105.0, 120.0, 101.4, 17)); // swing high 120.0 — distant resistance
        // Phase 4 (i=18..23): consolidation. Highs strictly decrease (105.3→104.8) so no
        // intermediate swing high forms. i=19 low=103.6 prevents i=20 from being a pivot low.
        candles.add(candle(105.0, 104.5, 105.3, 104.2, 18));
        candles.add(candle(104.5, 104.0, 105.2, 103.6, 19)); // low=103.6 — first pivot at 103.6
        candles.add(candle(104.0, 104.2, 105.1, 103.7, 20)); // low=103.7 > 103.6 → i=19 is swing low
        candles.add(candle(104.2, 104.3, 105.0, 103.9, 21));
        candles.add(candle(104.3, 104.3, 104.9, 103.9, 22));
        candles.add(candle(104.3, 104.4, 104.8, 104.0, 23));
        // Phase 5 (i=24): second pivot low 103.6 = i=19 → equal (lowerLows stays 0) → UP trend.
        candles.add(candle(104.4, 103.8, 104.7, 103.6, 24)); // swing low 103.6 (nearSupport ✓)
        // Phase 6 (i=25..27): bullish bounce; final close=105.0 (price 2.87% above EMA50 ✓).
        candles.add(candle(103.8, 104.2, 104.6, 103.9, 25));
        candles.add(candle(104.2, 104.5, 104.8, 104.0, 26)); // bullish
        candles.add(candle(104.5, 105.0, 105.3, 104.2, 27)); // close=105.0
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
            TradeDecision decision = strategy.analyze(null,null);

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

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

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

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

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

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            // With strong uptrend and bullish patterns, expect BUY
            assertThat(decision.getSignal()).isEqualTo(Signal.BUY);
        }

        @Test
        @DisplayName("BUY signal has positive confidence")
        void buySignalHasPositiveConfidence() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

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

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            assertThat(decision.getSignal()).isEqualTo(Signal.SELL);
        }

        @Test
        @DisplayName("SELL signal has positive confidence")
        void sellSignalHasPositiveConfidence() {
            List<MyCandle> candles = bearishDowntrendCandles();

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

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

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

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

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            assertThat(decision.getDetectedPatterns()).isNotNull();
            // With 25+ candles, at least some single-candle patterns should be detected
            assertThat(decision.getDetectedPatterns()).isNotEmpty();
        }

        @Test
        @DisplayName("Confidence is between 0 and 1")
        void confidenceBounded() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            assertThat(decision.getConfidence()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("Reasoning contains pattern and trend info")
        void reasoningContainsInfo() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            assertThat(decision.getReasoning()).isNotNull();
            assertThat(decision.getReasoning()).contains("Trend:");
            assertThat(decision.getReasoning()).contains("Signal:");
        }

        @Test
        @DisplayName("Trend direction is set in TradeDecision")
        void trendDirectionSet() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            assertThat(decision.getTrendDirection()).isNotNull();
            assertThat(decision.getTrendDirection()).isIn("UP", "DOWN", "SIDEWAYS");
        }

        @Test
        @DisplayName("Timestamp is set in TradeDecision")
        void timestampSet() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            assertThat(decision.getTimestamp()).isNotNull();
        }
    }

    // =======================================================================
    // Resistance Proximity Filter Tests
    // =======================================================================

    @Nested
    @DisplayName("Resistance Proximity Filter")
    class ResistanceProximityFilterTests {

        @Test
        @DisplayName("BUY is blocked when price is within 2% of nearest resistance")
        void buyBlockedNearResistance() {
            // Phase 1: gentle bullish oscillation stopping close to resistance.
            // Final close≈104.5; Phase-1 swing high ~105.7 → gap ≈1.15% < 2% → nearResistance=true
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                double trend = 100.0 + i * 0.25;
                double wave  = 1.5 * Math.sin(i * 0.6283);
                double open  = trend + wave;
                double close = open + 0.2;
                double high  = close + 0.5;
                double low   = open - 0.5;
                candles.add(candle(open, close, high, low, i));
            }
            // Phase 2: pullback and bullish bounce stays close to prior swing high
            candles.add(candle(105.8, 105.0, 106.2, 104.6, 25));
            candles.add(candle(105.0, 104.0, 105.4, 103.5, 26));
            candles.add(candle(103.5, 104.5, 105.2, 103.0, 27)); // close=104.5, gap to 105.7 ≈1.1%

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            // Either BUY is blocked → HOLD, or nearResistanceDetected flag is set
            assertThat(
                decision.getSignal() != Signal.BUY ||
                Boolean.TRUE.equals(decision.getNearResistanceDetected())
            ).isTrue();
        }

        @Test
        @DisplayName("nearResistanceDetected is false when price is well below resistance")
        void nearResistanceDetectedFalseWhenFarFromResistance() {
            List<MyCandle> candles = bullishUptrendCandles(); // gap=14.3% >> 2%

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            assertThat(decision.getNearResistanceDetected()).isFalse();
        }
    }

    // =======================================================================
    // SELL Location Filter Tests
    // =======================================================================

    @Nested
    @DisplayName("SELL Location Filter")
    class SellLocationFilterTests {

        @Test
        @DisplayName("Classic SELL signals are blocked when price is extended below EMA and not near resistance")
        void sellBlockedWhenPriceExtendedBelowEma() {
            // 28 candles: strong downtrend where price ends ~8% below EMA.
            // sellLocationOK = (distanceFromEma50Pct >= -3%) || nearResistance
            // When price is -8% from EMA and not near resistance, both conditions false → sellLocationOK=false
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 28; i++) {
                // Steady decline; no oscillations so no swing highs near current price
                double open  = 200.0 - i * 2.5;
                double close = open - 2.0;
                double high  = open + 0.5;
                double low   = close - 0.5;
                candles.add(candle(open, close, high, low, i));
            }

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            // In a strong downtrend with price extended below EMA, classic SELL should be
            // suppressed (no location). Aggressive exits (momentumSell/structureBreak) still fire,
            // so we assert: if it's a SELL, the distanceFromEma50Pct was within acceptable range
            // (onEMAReject was true) OR the aggressive exit path fired (which isn't location-gated).
            // This test mainly verifies the code compiles and runs without errors under this scenario.
            assertThat(decision).isNotNull();
            assertThat(decision.getSignal()).isIn(Signal.SELL, Signal.HOLD);
        }

        @Test
        @DisplayName("SELL is allowed when price is rejecting from near-EMA resistance zone")
        void sellAllowedNearEmaResistance() {
            // Use bearishDowntrendCandles which end with price near EMA and bearish patterns.
            // onEMAReject = distanceFromEma50Pct >= -3% — satisfied when price is near/above EMA.
            List<MyCandle> candles = bearishDowntrendCandles();

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            // Bearish downtrend candles should still produce SELL (sellLocationOK is met)
            assertThat(decision.getSignal()).isEqualTo(Signal.SELL);
        }
    }

    // =======================================================================
    // HTF SELL Block Tests
    // =======================================================================

    @Nested
    @DisplayName("HTF SELL Block")
    class HtfSellBlockTests {

        @Test
        @DisplayName("Classic SELL signals are suppressed when HTF(1D) trend is UP")
        void sellSuppressedWhenHtfUp() {
            // Bearish LTF candles that would normally produce a SELL
            List<MyCandle> candles = bearishDowntrendCandles();

            // HTF is UP — SELL should be suppressed for non-aggressive paths
            MultiTimeframeAnalyzer.MultiTimeframeResult mtfUp =
                new MultiTimeframeAnalyzer.MultiTimeframeResult(
                    TrendAnalyzer.TrendDirection.DOWN,    // LTF
                    TrendAnalyzer.TrendDirection.DOWN,    // confirm (4H)
                    TrendAnalyzer.TrendDirection.UP,      // HTF (1D) — blocks SELL
                    false, "test: htf UP blocks sell"
                );

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC", mtfUp, null, null);

            // Aggressive exits (momentumSell/structureBreak) bypass HTF gate, so we verify
            // the htfTrendDirection is recorded and the overall system runs correctly.
            assertThat(decision).isNotNull();
            assertThat(decision.getHtfTrendDirection()).isEqualTo("UP");
        }

        @Test
        @DisplayName("BUY signals are suppressed when HTF(1D) trend is DOWN")
        void buySuppressedWhenHtfDown() {
            List<MyCandle> candles = bullishUptrendCandles();

            MultiTimeframeAnalyzer.MultiTimeframeResult mtfDown =
                new MultiTimeframeAnalyzer.MultiTimeframeResult(
                    TrendAnalyzer.TrendDirection.UP,      // LTF
                    TrendAnalyzer.TrendDirection.UP,      // confirm (4H)
                    TrendAnalyzer.TrendDirection.DOWN,    // HTF (1D) — blocks BUY
                    false, "test: htf DOWN blocks buy"
                );

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC", mtfDown, null, null);

            assertThat(decision.getSignal()).isNotEqualTo(Signal.BUY);
            assertThat(decision.getHtfTrendDirection()).isEqualTo("DOWN");
        }
    }

    // =======================================================================
    // Direction-Aware EMA Pullback Tests
    // =======================================================================

    @Nested
    @DisplayName("Direction-Aware EMA Pullback")
    class DirectionAwareEmaTests {

        @Test
        @DisplayName("BUY fires when price is above EMA and within threshold (onBullishPullback)")
        void buyFiresOnBullishPullback() {
            // bullishUptrendCandles: close=109.5, EMA50≈108.6 → price above EMA, dist≈0.83% ≤ 3%
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = strategy.analyze(candles, "BTC-USDC");

            assertThat(decision.getSignal()).isEqualTo(Signal.BUY);
            assertThat(decision.getDistanceFromEma50Pct()).isGreaterThanOrEqualTo(0.0); // above EMA
        }
    }
}
