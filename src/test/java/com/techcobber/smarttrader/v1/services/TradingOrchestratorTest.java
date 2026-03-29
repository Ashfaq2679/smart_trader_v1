package com.techcobber.smarttrader.v1.services;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.models.UserPreferences;
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
        orchestrator = new TradingOrchestrator();
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
     * Creates 25+ candles simulating a strong uptrend ending with bullish patterns.
     */
    private static List<MyCandle> bullishUptrendCandles() {
        List<MyCandle> candles = new ArrayList<>();
        double base = 100;
        for (int i = 0; i < 22; i++) {
            double drift = i * 1.5;
            double wave = 3.0 * Math.sin(i * 0.8);
            double open = base + drift + wave;
            double close = open + 1.5 + (i % 3 == 0 ? -0.5 : 0.5);
            double high = Math.max(open, close) + 2;
            double low = Math.min(open, close) - 2;
            candles.add(candle(open, close, high, low, i));
        }
        // End with bullish engulfing pattern
        double lastClose = candles.get(candles.size() - 1).getClose();
        candles.add(candle(lastClose + 2, lastClose - 1, lastClose + 3, lastClose - 2, 22L));
        double prevOpen = lastClose + 2;
        double prevClose = lastClose - 1;
        candles.add(candle(prevClose - 1, prevOpen + 2, prevOpen + 4, prevClose - 2, 23L));
        double nextOpen = prevOpen + 2;
        candles.add(candle(nextOpen, nextOpen + 8, nextOpen + 8.5, nextOpen - 0.3, 24L));
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
}
