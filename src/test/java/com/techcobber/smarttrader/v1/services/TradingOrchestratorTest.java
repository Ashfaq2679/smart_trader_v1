package com.techcobber.smarttrader.v1.services;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.Order;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.models.UserPreferences;
import com.techcobber.smarttrader.v1.strategy.RiskManager;
import com.techcobber.smarttrader.v1.strategy.RiskManager.RiskAssessment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TradingOrchestrator}.
 */
class TradingOrchestratorTest {

    private TradingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new TradingOrchestrator(new RiskManager());
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

    private static UserPreferences defaultPrefs() {
        UserPreferences prefs = new UserPreferences();
        prefs.setPositionSize("5");
        prefs.setMaxDailyLoss("5000");
        return prefs;
    }

    /**
     * Creates 28 candles: gradual grind with two ascending swing highs and two ascending
     * swing lows, ending at a bullish bounce off recent support.
     *
     * <p>Design constraints (verified analytically):
     * <ul>
     *   <li>Trend (UP): window[i=8..27] swing highs 104.5→120.0 (higher-high) and
     *       swing lows 100.2→103.6 (higher-low) → bullishSignals=2 ✓</li>
     *   <li>EMA50 (simple avg of 28 closes) ≈ 102.1; final close=105.0;
     *       distanceFromEMA50 ≈ 2.87% ≤ 3% → onBullishPullback=true ✓</li>
     *   <li>Support=103.6 (1.33% below price); Resistance=120.0 (14.3% above price) ✓</li>
     *   <li>ATR(14) ≈ 1.55; buffered R:R ≈ 6.5 ≥ MIN_RR=2.0 ✓</li>
     * </ul>
     */
    private static List<MyCandle> bullishUptrendCandles() {
        List<MyCandle> candles = new ArrayList<>();
        // Phase 0 (i=0..9): slow grind 99→101, small bodies, ATR ≈ 0.8/candle.
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
        // Phase 1 (i=10..12): first swing high 104.5, then fade.
        candles.add(candle(101.1, 101.4, 104.5, 100.8, 10)); // swing high 104.5
        candles.add(candle(101.4, 101.2, 101.7, 100.9, 11));
        candles.add(candle(101.2, 101.0, 101.5, 100.7, 12));
        // Phase 2 (i=13..16): swing low 100.2, then recovery.
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
        // Phase 5 (i=24): second pivot low 103.6 = i=19 (equal → lowerLows stays 0) → UP trend.
        candles.add(candle(104.4, 103.8, 104.7, 103.6, 24)); // swing low 103.6
        // Phase 6 (i=25..27): bullish bounce; final close=105.0.
        candles.add(candle(103.8, 104.2, 104.6, 103.9, 25));
        candles.add(candle(104.2, 104.5, 104.8, 104.0, 26));
        candles.add(candle(104.5, 105.0, 105.3, 104.2, 27)); // close=105.0
        return candles;
    }

    /**
     * Creates 25+ sideways candles that should result in HOLD.
     */
    private static List<MyCandle> sidewaysCandles() {
        List<MyCandle> candles = new ArrayList<>();
        double base = 100;
        for (int i = 0; i < 25; i++) {
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
    // executeAnalysis Tests
    // =======================================================================

    @Nested
    @DisplayName("executeAnalysis")
    class ExecuteAnalysisTests {

        @Test
        @DisplayName("Returns TradeDecision with productId set")
        void returnsDecisionWithProductId() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = orchestrator.executeAnalysis(candles, "BTC-USD");

            assertThat(decision).isNotNull();
            assertThat(decision.getProductId()).isEqualTo("BTC-USD");
        }

        @Test
        @DisplayName("Sets productId even with different product names")
        void setsProductIdCorrectly() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = orchestrator.executeAnalysis(candles, "ETH-USD");

            assertThat(decision.getProductId()).isEqualTo("ETH-USD");
        }

        @Test
        @DisplayName("Insufficient candles returns HOLD")
        void insufficientCandlesReturnsHold() {
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                candles.add(candle(100 + i, 101 + i, 103 + i, 99 + i, i));
            }

            TradeDecision decision = orchestrator.executeAnalysis(candles, "BTC-USD");

            assertThat(decision.getSignal()).isEqualTo(Signal.HOLD);
            assertThat(decision.getProductId()).isEqualTo("BTC-USD");
        }

        @Test
        @DisplayName("TradeDecision has signal, confidence, and reasoning")
        void decisionHasRequiredFields() {
            List<MyCandle> candles = bullishUptrendCandles();

            TradeDecision decision = orchestrator.executeAnalysis(candles, "BTC-USD");

            assertThat(decision.getSignal()).isNotNull();
            assertThat(decision.getReasoning()).isNotNull();
            assertThat(decision.getTrendDirection()).isNotNull();
        }
    }

    // =======================================================================
    // executeWithRisk Tests
    // =======================================================================

    @Nested
    @DisplayName("executeWithRisk")
    class ExecuteWithRiskTests {

        @Test
        @DisplayName("Includes risk assessment for high-confidence non-HOLD signals")
        void includesRiskForHighConfidenceSignal() {
            List<MyCandle> candles = bullishUptrendCandles();

            Map<String, Object> result = orchestrator.executeWithRisk(
                    candles, "BTC-USD", defaultPrefs(), 10000.0);

            TradeDecision decision = (TradeDecision) result.get("decision");
            assertThat(decision).isNotNull();

            if (decision.getSignal() != Signal.HOLD && decision.getConfidence() >= 0.6) {
                assertThat(result).containsKey("riskAssessment");
                RiskAssessment risk = (RiskAssessment) result.get("riskAssessment");
                assertThat(risk).isNotNull();
            }
        }

        @Test
        @DisplayName("Skips risk assessment for HOLD signal")
        void skipsRiskForHold() {
            List<MyCandle> candles = sidewaysCandles();

            Map<String, Object> result = orchestrator.executeWithRisk(
                    candles, "BTC-USD", defaultPrefs(), 10000.0);

            TradeDecision decision = (TradeDecision) result.get("decision");
            assertThat(decision).isNotNull();

            if (decision.getSignal() == Signal.HOLD) {
                assertThat(result).doesNotContainKey("riskAssessment");
            }
        }

        @Test
        @DisplayName("Skips risk assessment for low-confidence signals")
        void skipsRiskForLowConfidence() {
            // Use insufficient candles to get HOLD with 0 confidence
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                candles.add(candle(100 + i, 101 + i, 103 + i, 99 + i, i));
            }

            Map<String, Object> result = orchestrator.executeWithRisk(
                    candles, "BTC-USD", defaultPrefs(), 10000.0);

            TradeDecision decision = (TradeDecision) result.get("decision");
            assertThat(decision.getSignal()).isEqualTo(Signal.HOLD);
            assertThat(result).doesNotContainKey("riskAssessment");
        }

        @Test
        @DisplayName("Result always contains decision key")
        void resultAlwaysContainsDecision() {
            List<MyCandle> candles = bullishUptrendCandles();

            Map<String, Object> result = orchestrator.executeWithRisk(
                    candles, "BTC-USD", defaultPrefs(), 10000.0);

            assertThat(result).containsKey("decision");
            assertThat(result.get("decision")).isInstanceOf(TradeDecision.class);
        }

        @Test
        @DisplayName("Decision in result has productId set")
        void decisionInResultHasProductId() {
            List<MyCandle> candles = bullishUptrendCandles();

            Map<String, Object> result = orchestrator.executeWithRisk(
                    candles, "SOL-USD", defaultPrefs(), 5000.0);

            TradeDecision decision = (TradeDecision) result.get("decision");
            assertThat(decision.getProductId()).isEqualTo("SOL-USD");
        }
    }

    // =======================================================================
    // evaluateExit Tests
    // =======================================================================

    @Nested
    @DisplayName("evaluateExit")
    class EvaluateExitTests {

        private static final String PRODUCT = "ETH-USDC";

        private Order openBuy(double entryPrice, double stopLoss) {
            Order order = new Order();
            order.setProductId(PRODUCT);
            order.setEntryPriceNum(entryPrice);
            order.setStopLoss(stopLoss);
            order.setSide("BUY");
            return order;
        }

        /** Builds candles with a fixed price (for predictable EMA/ATR). */
        private List<MyCandle> flatCandles(int count, double price) {
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                candles.add(candle(price, price, price + 1, price - 1, i));
            }
            return candles;
        }

        @Test
        @DisplayName("Returns null for null order")
        void nullOrder() {
            assertThat(orchestrator.evaluateExit(null, flatCandles(30, 100), null)).isNull();
        }

        @Test
        @DisplayName("Returns null for empty candles")
        void emptyCandles() {
            Order order = openBuy(100.0, 95.0);
            assertThat(orchestrator.evaluateExit(order, List.of(), null)).isNull();
        }

        @Test
        @DisplayName("Trigger 1: Returns SELL when price <= effectiveStop (stored SL)")
        void effectiveStopTriggered() {
            // entryPrice=100, stopLoss=95, price drops to 94 (below SL)
            Order order = openBuy(100.0, 95.0);
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 29; i++) candles.add(candle(100, 100, 101, 99, i));
            candles.add(candle(94, 94, 95, 93, 29)); // final candle at 94 < 95 SL

            TradeDecision result = orchestrator.evaluateExit(order, candles, null);

            assertThat(result).isNotNull();
            assertThat(result.getSignal()).isEqualTo(Signal.SELL);
            assertThat(result.getReasoning()).contains("stop");
        }

        @Test
        @DisplayName("Trigger 1: effectiveStop uses maxLoss% when it is higher than stored SL")
        void effectiveStopUsesMaxLossPct() {
            // entryPrice=100, stopLoss=50 (far away), maxDailyLoss=3%
            // effectiveStop = max(50, 100*0.97) = 97
            Order order = openBuy(100.0, 50.0);
            UserPreferences prefs = new UserPreferences();
            prefs.setMaxDailyLoss("3.0");
            prefs.setTrailingAtrMultiplier("100.0"); // disable trailing stop

            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 29; i++) candles.add(candle(100, 100, 101, 99, i));
            candles.add(candle(96, 96, 97, 95, 29)); // price at 96 < effectiveStop(97)

            TradeDecision result = orchestrator.evaluateExit(order, candles, prefs);

            assertThat(result).isNotNull();
            assertThat(result.getSignal()).isEqualTo(Signal.SELL);
        }

        @Test
        @DisplayName("Returns null when price is well above all stops")
        void noExitWhenPriceAboveAllStops() {
            // entryPrice=100, stopLoss=90, price=120 — safely above everything
            Order order = openBuy(100.0, 90.0);
            // Rising candles so EMA9 > EMA21 (no momentum exit)
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 30; i++) candles.add(candle(100 + i, 100 + i, 102 + i, 99 + i, i));

            TradeDecision result = orchestrator.evaluateExit(order, candles, null);

            assertThat(result).isNull();
        }
    }
}
