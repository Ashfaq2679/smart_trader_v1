package com.techcobber.smarttrader.v1.strategy;

import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.models.UserPreferences;
import com.techcobber.smarttrader.v1.strategy.RiskManager.RiskAssessment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RiskManager}.
 */
class RiskManagerTest {

    private RiskManager riskManager;

    @BeforeEach
    void setUp() {
        riskManager = new RiskManager();
    }

    // -----------------------------------------------------------------------
    // Helper factory methods
    // -----------------------------------------------------------------------

    private static UserPreferences defaultPrefs() {
        UserPreferences prefs = new UserPreferences();
        prefs.setPositionSize("5");
        prefs.setMaxDailyLoss("500");
        return prefs;
    }

    private static TradeDecision buyDecision(double confidence, Double support, Double resistance) {
        return TradeDecision.builder()
                .signal(Signal.BUY)
                .confidence(confidence)
                .reasoning("Test buy")
                .detectedPatterns(List.of("BULLISH_ENGULFING"))
                .trendDirection("UP")
                .nearestSupport(support)
                .nearestResistance(resistance)
                .build();
    }

    private static TradeDecision sellDecision(double confidence, Double support, Double resistance) {
        return TradeDecision.builder()
                .signal(Signal.SELL)
                .confidence(confidence)
                .reasoning("Test sell")
                .detectedPatterns(List.of("BEARISH_ENGULFING"))
                .trendDirection("DOWN")
                .nearestSupport(support)
                .nearestResistance(resistance)
                .build();
    }

    private static TradeDecision holdDecision() {
        return TradeDecision.builder()
                .signal(Signal.HOLD)
                .confidence(0.0)
                .reasoning("No trade")
                .detectedPatterns(List.of())
                .trendDirection("SIDEWAYS")
                .build();
    }

    // =======================================================================
    // HOLD Signal Tests
    // =======================================================================

    @Nested
    @DisplayName("HOLD Signal")
    class HoldSignalTests {

        @Test
        @DisplayName("HOLD signal returns unapproved assessment")
        void holdReturnsUnapproved() {
            RiskAssessment result = riskManager.assess(holdDecision(), defaultPrefs(), 100.0, 10000.0);

            assertThat(result.isApproved()).isFalse();
            assertThat(result.getPositionSize()).isEqualTo(0);
            assertThat(result.getStopLoss()).isEqualTo(0);
            assertThat(result.getTakeProfit()).isEqualTo(0);
            assertThat(result.getReason()).contains("HOLD");
        }
    }

    // =======================================================================
    // BUY Signal - Stop Loss Tests
    // =======================================================================

    @Nested
    @DisplayName("BUY Signal Stop Loss")
    class BuyStopLossTests {

        @Test
        @DisplayName("BUY signal calculates stop-loss below support")
        void buyStopLossBelowSupport() {
            TradeDecision decision = buyDecision(0.8, 95.0, 115.0);

            RiskAssessment result = riskManager.assess(decision, defaultPrefs(), 100.0, 10000.0);

            // Stop-loss = nearestSupport * 0.995 = 95 * 0.995 = 94.525 -> rounded to 94.53
            assertThat(result.getStopLoss()).isLessThan(95.0);
            assertThat(result.getStopLoss()).isGreaterThan(90.0);
            assertThat(result.isApproved()).isTrue();
        }

        @Test
        @DisplayName("BUY signal uses default stop-loss when no support")
        void buyDefaultStopLossWhenNoSupport() {
            TradeDecision decision = buyDecision(0.8, null, 115.0);

            RiskAssessment result = riskManager.assess(decision, defaultPrefs(), 100.0, 10000.0);

            // Default: price * (1 - 0.02) = 98.0
            assertThat(result.getStopLoss()).isEqualTo(98.0);
        }
    }

    // =======================================================================
    // SELL Signal - Stop Loss Tests
    // =======================================================================

    @Nested
    @DisplayName("SELL Signal Stop Loss")
    class SellStopLossTests {

        @Test
        @DisplayName("SELL signal calculates stop-loss above resistance")
        void sellStopLossAboveResistance() {
            TradeDecision decision = sellDecision(0.8, 85.0, 105.0);

            RiskAssessment result = riskManager.assess(decision, defaultPrefs(), 100.0, 10000.0);

            // Stop-loss = nearestResistance * 1.005 = 105 * 1.005 = 105.525 -> 105.53
            assertThat(result.getStopLoss()).isGreaterThan(105.0);
            assertThat(result.getStopLoss()).isLessThan(110.0);
            assertThat(result.isApproved()).isTrue();
        }

        @Test
        @DisplayName("SELL signal uses default stop-loss when no resistance")
        void sellDefaultStopLossWhenNoResistance() {
            TradeDecision decision = sellDecision(0.8, 85.0, null);

            RiskAssessment result = riskManager.assess(decision, defaultPrefs(), 100.0, 10000.0);

            // Default: price * (1 + 0.02) = 102.0
            assertThat(result.getStopLoss()).isEqualTo(102.0);
        }
    }

    // =======================================================================
    // Position Sizing Tests
    // =======================================================================

    @Nested
    @DisplayName("Position Sizing")
    class PositionSizingTests {

        @Test
        @DisplayName("Position size calculated from UserPreferences")
        void positionSizeFromPrefs() {
            UserPreferences prefs = new UserPreferences();
            prefs.setPositionSize("10");  // 10%
            prefs.setMaxDailyLoss("5000");

            TradeDecision decision = buyDecision(0.8, 95.0, 115.0);

            RiskAssessment result = riskManager.assess(decision, prefs, 100.0, 10000.0);

            // 10% of 10000 = 1000 value; 1000 / 100 price = 10 units
            assertThat(result.getPositionSize()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("Default position size when preference is blank")
        void defaultPositionSizeWhenBlank() {
            UserPreferences prefs = new UserPreferences();
            prefs.setMaxDailyLoss("5000");
            // positionSize is null -> defaults to 5%

            TradeDecision decision = buyDecision(0.8, 95.0, 115.0);

            RiskAssessment result = riskManager.assess(decision, prefs, 100.0, 10000.0);

            // 5% of 10000 = 500 value; 500 / 100 = 5 units
            assertThat(result.getPositionSize()).isEqualTo(5.0);
        }
    }

    // =======================================================================
    // Max Daily Loss Tests
    // =======================================================================

    @Nested
    @DisplayName("Max Daily Loss")
    class MaxDailyLossTests {

        @Test
        @DisplayName("Rejects trade when potential loss exceeds max daily loss")
        void rejectsWhenExceedsMaxDailyLoss() {
            UserPreferences prefs = new UserPreferences();
            prefs.setPositionSize("50");  // 50% = large position
            prefs.setMaxDailyLoss("10");  // Very low max daily loss

            TradeDecision decision = buyDecision(0.8, null, 115.0);

            RiskAssessment result = riskManager.assess(decision, prefs, 100.0, 10000.0);

            // Position: 50% of 10000 / 100 = 50 units
            // Risk per unit = |100 - 98| = 2
            // Potential loss = 50 * 2 = 100, which exceeds 10
            assertThat(result.isApproved()).isFalse();
            assertThat(result.getReason()).contains("exceeds");
        }

        @Test
        @DisplayName("Approves trade when potential loss is within max daily loss")
        void approvesWhenWithinMaxDailyLoss() {
            UserPreferences prefs = new UserPreferences();
            prefs.setPositionSize("5");
            prefs.setMaxDailyLoss("5000");

            TradeDecision decision = buyDecision(0.8, 95.0, 115.0);

            RiskAssessment result = riskManager.assess(decision, prefs, 100.0, 10000.0);

            assertThat(result.isApproved()).isTrue();
            assertThat(result.getReason()).contains("approved");
        }
    }

    // =======================================================================
    // Take Profit Tests
    // =======================================================================

    @Nested
    @DisplayName("Take Profit Calculation")
    class TakeProfitTests {

        @Test
        @DisplayName("BUY take-profit is above current price with default R:R ratio")
        void buyTakeProfitAbovePrice() {
            TradeDecision decision = buyDecision(0.8, 95.0, 115.0);

            RiskAssessment result = riskManager.assess(decision, defaultPrefs(), 100.0, 10000.0);

            // TP should be above current price for BUY
            assertThat(result.getTakeProfit()).isGreaterThan(100.0);
            // R:R ratio should be 2.0 (default)
            assertThat(result.getRiskRewardRatio()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("SELL take-profit is below current price with default R:R ratio")
        void sellTakeProfitBelowPrice() {
            TradeDecision decision = sellDecision(0.8, 85.0, 105.0);

            RiskAssessment result = riskManager.assess(decision, defaultPrefs(), 100.0, 10000.0);

            // TP should be below current price for SELL
            assertThat(result.getTakeProfit()).isLessThan(100.0);
            assertThat(result.getRiskRewardRatio()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("Take-profit distance is 2x stop-loss distance (R:R = 2)")
        void takeProfitDistanceIsDoubleStopLossDistance() {
            TradeDecision decision = buyDecision(0.8, null, 115.0);

            RiskAssessment result = riskManager.assess(decision, defaultPrefs(), 100.0, 10000.0);

            // SL = 98.0, risk = 2.0, TP = 100 + 4.0 = 104.0
            double riskDistance = Math.abs(100.0 - result.getStopLoss());
            double rewardDistance = Math.abs(result.getTakeProfit() - 100.0);
            assertThat(rewardDistance).isCloseTo(riskDistance * 2.0, org.assertj.core.data.Offset.offset(0.1));
        }
    }
}
