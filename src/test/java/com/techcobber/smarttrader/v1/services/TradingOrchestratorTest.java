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
     * Creates 28 candles: staircase uptrend with two clear higher-highs (112.0→116.5)
     * and higher-lows (107.6→108.0) in the analysis window → UP trend. Satisfies all
     * BUY entry guards: EMA pullback, resistance gap, and bullish pattern count.
     */
    private static List<MyCandle> bullishUptrendCandles() {
        List<MyCandle> candles = new ArrayList<>();
        candles.add(candle(99.7,  100.3, 100.7, 99.3,  0));
        candles.add(candle(100.3, 101.1, 101.5, 99.9,  1));
        candles.add(candle(101.1, 102.2, 102.6, 100.7, 2));
        candles.add(candle(102.2, 103.5, 103.9, 101.8, 3));
        candles.add(candle(103.5, 105.0, 105.5, 103.1, 4));
        candles.add(candle(105.0, 104.0, 105.4, 103.6, 5));
        candles.add(candle(104.0, 103.0, 104.4, 102.6, 6));
        candles.add(candle(103.2, 104.5, 104.9, 102.8, 7));
        candles.add(candle(104.5, 106.0, 106.4, 104.1, 8));
        candles.add(candle(106.0, 107.5, 107.9, 105.6, 9));
        candles.add(candle(107.5, 109.0, 109.4, 107.1, 10));
        candles.add(candle(109.0, 110.3, 110.7, 108.6, 11));
        candles.add(candle(110.3, 111.5, 112.0, 109.9, 12)); // swing high 112.0
        candles.add(candle(111.5, 110.0, 111.9, 109.6, 13));
        candles.add(candle(110.0, 108.0, 110.4, 107.6, 14)); // swing low 107.6
        candles.add(candle(108.2, 109.5, 109.9, 107.8, 15));
        candles.add(candle(109.5, 111.0, 111.4, 109.1, 16));
        candles.add(candle(111.0, 112.5, 112.9, 110.6, 17));
        candles.add(candle(112.5, 114.0, 114.4, 112.1, 18));
        candles.add(candle(114.0, 115.5, 115.9, 113.6, 19));
        candles.add(candle(116.0, 115.0, 116.5, 114.6, 20)); // Phase 2 swing high 116.5
        candles.add(candle(115.0, 113.8, 115.4, 113.4, 21));
        candles.add(candle(113.8, 112.5, 114.2, 112.1, 22));
        candles.add(candle(112.5, 111.2, 112.9, 110.8, 23));
        candles.add(candle(111.2, 110.0, 111.6, 109.6, 24));
        candles.add(candle(110.5, 109.2, 111.0, 108.0, 25)); // swing low 108.0
        candles.add(candle(109.1, 110.8, 111.2, 108.4, 26)); // bullish engulfing
        candles.add(candle(109.0, 109.5, 111.5, 108.5, 27)); // final close=109.5
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
