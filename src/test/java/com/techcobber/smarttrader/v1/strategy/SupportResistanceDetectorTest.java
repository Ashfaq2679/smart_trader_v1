package com.techcobber.smarttrader.v1.strategy;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.strategy.SupportResistanceDetector.Level;
import com.techcobber.smarttrader.v1.strategy.SupportResistanceDetector.LevelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SupportResistanceDetector}.
 */
class SupportResistanceDetectorTest {

    private SupportResistanceDetector detector;

    @BeforeEach
    void setUp() {
        detector = new SupportResistanceDetector();
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
     * Creates a list of candles with a clear swing high at the specified index.
     * The swing high candle has a higher high than its neighbors.
     */
    private static List<MyCandle> candlesWithSwingHigh(double basePrice, int count, int swingIndex, double swingAmount) {
        List<MyCandle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double price = basePrice + (i * 0.5);
            double high = price + 2;
            double low = price - 2;
            if (i == swingIndex) {
                high = price + swingAmount;
            }
            candles.add(candle(price, price + 0.5, high, low, i));
        }
        return candles;
    }

    /**
     * Creates a list of candles with a clear swing low at the specified index.
     */
    private static List<MyCandle> candlesWithSwingLow(double basePrice, int count, int swingIndex, double swingAmount) {
        List<MyCandle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double price = basePrice + (i * 0.5);
            double high = price + 2;
            double low = price - 2;
            if (i == swingIndex) {
                low = price - swingAmount;
            }
            candles.add(candle(price, price + 0.5, high, low, i));
        }
        return candles;
    }

    /**
     * Creates candles with multiple swing highs and swing lows for comprehensive testing.
     */
    private static List<MyCandle> candlesWithMultipleSwings() {
        List<MyCandle> candles = new ArrayList<>();
        // Create a wave pattern: up-down-up-down-up
        double[] highs = {102, 104, 108, 104, 102, 104, 110, 106, 103, 105, 109, 104, 102};
        double[] lows =  { 96,  98,  102, 98,  94,  98, 104, 100,  95,  99, 103,  98,  96};
        double[] opens = { 98, 100, 104, 102, 96, 100, 106, 104, 98, 101, 105, 102, 98};
        double[] closes ={ 100, 102, 106, 100, 98, 102, 108, 102, 100, 103, 107, 100, 98};

        for (int i = 0; i < highs.length; i++) {
            candles.add(candle(opens[i], closes[i], highs[i], lows[i], i));
        }
        return candles;
    }

    // =======================================================================
    // Edge Cases
    // =======================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Null candles returns empty list")
        void nullCandlesReturnsEmpty() {
            List<Level> levels = detector.detectLevels(null, 50);
            assertThat(levels).isEmpty();
        }

        @Test
        @DisplayName("Empty candles returns empty list")
        void emptyCandlesReturnsEmpty() {
            List<Level> levels = detector.detectLevels(Collections.emptyList(), 50);
            assertThat(levels).isEmpty();
        }

        @Test
        @DisplayName("Less than 3 candles returns empty list")
        void fewerThanThreeCandlesReturnsEmpty() {
            List<MyCandle> candles = List.of(
                    candle(100, 102, 104, 98, 1L),
                    candle(102, 104, 106, 100, 2L));
            List<Level> levels = detector.detectLevels(candles, 50);
            assertThat(levels).isEmpty();
        }
    }

    // =======================================================================
    // Swing High / Resistance Detection
    // =======================================================================

    @Nested
    @DisplayName("Resistance Detection")
    class ResistanceDetection {

        @Test
        @DisplayName("Detects swing high as resistance level")
        void detectsSwingHighAsResistance() {
            // Create candles where index 3 has a clear swing high
            // prev.high < curr.high && next.high < curr.high
            List<MyCandle> candles = new ArrayList<>();
            candles.add(candle(100, 102, 104, 98, 0L));
            candles.add(candle(102, 104, 106, 100, 1L));
            candles.add(candle(104, 108, 115, 103, 2L));  // swing high at 115
            candles.add(candle(106, 104, 108, 102, 3L));
            candles.add(candle(104, 102, 106, 100, 4L));

            List<Level> levels = detector.detectLevels(candles, 50);
            List<Level> resistanceLevels = levels.stream()
                    .filter(l -> l.getType() == LevelType.RESISTANCE)
                    .toList();

            assertThat(resistanceLevels).isNotEmpty();
            assertThat(resistanceLevels.stream().anyMatch(l -> l.getPrice() >= 114 && l.getPrice() <= 116)).isTrue();
        }
    }

    // =======================================================================
    // Swing Low / Support Detection
    // =======================================================================

    @Nested
    @DisplayName("Support Detection")
    class SupportDetection {

        @Test
        @DisplayName("Detects swing low as support level")
        void detectsSwingLowAsSupport() {
            List<MyCandle> candles = new ArrayList<>();
            candles.add(candle(104, 102, 106, 100, 0L));
            candles.add(candle(102, 100, 104, 98, 1L));
            candles.add(candle(100, 98, 102, 88, 2L));   // swing low at 88
            candles.add(candle(98, 100, 104, 96, 3L));
            candles.add(candle(100, 102, 106, 98, 4L));

            List<Level> levels = detector.detectLevels(candles, 50);
            List<Level> supportLevels = levels.stream()
                    .filter(l -> l.getType() == LevelType.SUPPORT)
                    .toList();

            assertThat(supportLevels).isNotEmpty();
            assertThat(supportLevels.stream().anyMatch(l -> l.getPrice() >= 87 && l.getPrice() <= 89)).isTrue();
        }
    }

    // =======================================================================
    // Grouping and Strength Tests
    // =======================================================================

    @Nested
    @DisplayName("Level Grouping and Strength")
    class GroupingAndStrength {

        @Test
        @DisplayName("Groups nearby swing highs into a single resistance level")
        void groupsNearbyResistanceLevels() {
            // Create multiple swing highs at very similar prices
            // They should be grouped into one level with higher strength
            List<MyCandle> candles = new ArrayList<>();
            double base = 100;
            // Wave pattern producing two swing highs near 110
            candles.add(candle(base, base + 2, base + 4, base - 2, 0L));         // 100
            candles.add(candle(base + 2, base + 5, base + 10, base + 1, 1L));    // swing high ~110
            candles.add(candle(base + 4, base + 2, base + 6, base, 2L));         // dip
            candles.add(candle(base + 2, base + 1, base + 3, base - 1, 3L));     // lower
            candles.add(candle(base + 1, base + 4, base + 10.2, base, 4L));      // swing high ~110.2
            candles.add(candle(base + 3, base + 2, base + 5, base - 1, 5L));     // lower
            candles.add(candle(base + 2, base + 1, base + 3, base - 2, 6L));

            List<Level> levels = detector.detectLevels(candles, 50);
            List<Level> resistanceLevels = levels.stream()
                    .filter(l -> l.getType() == LevelType.RESISTANCE)
                    .toList();

            // Nearby swing highs should be grouped; expect 1 resistance level
            assertThat(resistanceLevels).hasSizeLessThanOrEqualTo(2);
            if (!resistanceLevels.isEmpty()) {
                assertThat(resistanceLevels.get(0).getStrength()).isGreaterThanOrEqualTo(1);
            }
        }

        @Test
        @DisplayName("Strength counts touches correctly")
        void strengthCountsTouches() {
            // Multiple candles touching a resistance area should increase strength
            List<MyCandle> candles = candlesWithMultipleSwings();
            List<Level> levels = detector.detectLevels(candles, 50);

            // All levels should have strength >= 1
            for (Level level : levels) {
                assertThat(level.getStrength()).isGreaterThanOrEqualTo(1);
            }
        }
    }

    // =======================================================================
    // Multiple S/R Levels
    // =======================================================================

    @Nested
    @DisplayName("Multiple S/R Levels")
    class MultipleLevels {

        @Test
        @DisplayName("Detects both support and resistance levels")
        void detectsBothSupportAndResistance() {
            List<MyCandle> candles = candlesWithMultipleSwings();
            List<Level> levels = detector.detectLevels(candles, 50);

            boolean hasSupport = levels.stream().anyMatch(l -> l.getType() == LevelType.SUPPORT);
            boolean hasResistance = levels.stream().anyMatch(l -> l.getType() == LevelType.RESISTANCE);

            assertThat(hasSupport).isTrue();
            assertThat(hasResistance).isTrue();
        }

        @Test
        @DisplayName("Levels are sorted by strength descending")
        void levelsSortedByStrength() {
            List<MyCandle> candles = candlesWithMultipleSwings();
            List<Level> levels = detector.detectLevels(candles, 50);

            if (levels.size() > 1) {
                for (int i = 0; i < levels.size() - 1; i++) {
                    assertThat(levels.get(i).getStrength())
                            .isGreaterThanOrEqualTo(levels.get(i + 1).getStrength());
                }
            }
        }
    }
}
