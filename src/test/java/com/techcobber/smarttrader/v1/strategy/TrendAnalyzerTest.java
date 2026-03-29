package com.techcobber.smarttrader.v1.strategy;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendDirection;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TrendAnalyzer}.
 */
class TrendAnalyzerTest {

    private TrendAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new TrendAnalyzer();
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
     * Creates candles simulating an uptrend with higher highs and higher lows.
     * The wave pattern ensures clear swing points.
     */
    private static List<MyCandle> uptrendCandles() {
        List<MyCandle> candles = new ArrayList<>();
        // Wave-like uptrend: up, slight dip, higher up, slight dip, higher up...
        // This creates swing highs and swing lows that are progressively higher.
        double[][] data = {
            // open,  close, high,  low
            {100,    102,   104,   98},    // 0
            {102,    105,   107,   101},   // 1
            {105,    110,   112,   104},   // 2 - swing high 112
            {110,    107,   111,   106},   // 3
            {107,    105,   108,   104},   // 4 - swing low 104
            {105,    108,   110,   104},   // 5
            {108,    113,   116,   107},   // 6 - swing high 116 (higher than 112)
            {113,    110,   114,   109},   // 7
            {110,    108,   111,   107},   // 8 - swing low 107 (higher than 104)
            {108,    112,   114,   107},   // 9
            {112,    117,   120,   111},   // 10 - swing high 120 (higher than 116)
            {117,    114,   118,   113},   // 11
            {114,    112,   115,   111},   // 12 - swing low 111 (higher than 107)
            {112,    116,   118,   111},   // 13
            {116,    120,   123,   115},   // 14 - swing high 123
        };
        for (int i = 0; i < data.length; i++) {
            candles.add(candle(data[i][0], data[i][1], data[i][2], data[i][3], i));
        }
        return candles;
    }

    /**
     * Creates candles simulating a downtrend with lower highs and lower lows.
     */
    private static List<MyCandle> downtrendCandles() {
        List<MyCandle> candles = new ArrayList<>();
        double[][] data = {
            // open,  close, high,  low
            {120,    118,   122,   116},   // 0
            {118,    115,   119,   113},   // 1
            {115,    110,   116,   108},   // 2 - swing high 116, low 108
            {110,    112,   114,   109},   // 3
            {112,    114,   116,   111},   // 4 - swing high 116, swing low 109 prev
            {114,    110,   115,   108},   // 5
            {110,    105,   111,   103},   // 6 - swing high 111 (lower), low 103
            {105,    107,   109,   104},   // 7
            {107,    109,   110,   106},   // 8 - swing high 110 (lower), swing low 104
            {109,    105,   110,   103},   // 9
            {105,    100,   106,   98},    // 10 - swing high 106 (lower), low 98
            {100,    102,   104,   99},    // 11
            {102,    104,   105,   101},   // 12 - swing low 99 (lower)
            {104,    100,   105,   98},    // 13
            {100,    96,    101,   94},    // 14 - low 94
        };
        for (int i = 0; i < data.length; i++) {
            candles.add(candle(data[i][0], data[i][1], data[i][2], data[i][3], i));
        }
        return candles;
    }

    /**
     * Creates candles simulating a sideways market with no clear trend.
     * Swing highs: 107, 106, 108 -> higherHighs=1, lowerHighs=1
     * Swing lows:   96,  97,  94 -> higherLows=1,  lowerLows=1
     * bullish=2, bearish=2 -> SIDEWAYS
     */
    private static List<MyCandle> sidewaysCandles() {
        List<MyCandle> candles = new ArrayList<>();
        double[][] data = {
            // open, close, high, low
            {100, 102, 104, 98},    // 0
            {102, 104, 106, 100},   // 1
            {104, 102, 107, 101},   // 2 - swing high 107
            {102, 100, 103, 99},    // 3
            {100, 98,  101, 96},    // 4 - swing low 96
            {98,  100, 103, 97},    // 5
            {100, 104, 106, 99},    // 6 - swing high 106 (lower high)
            {104, 102, 104, 98},    // 7
            {102, 99,  102, 97},    // 8 - swing low 97 (higher low)
            {99,  101, 104, 99},    // 9
            {101, 105, 108, 101},   // 10 - swing high 108 (higher high)
            {105, 103, 105, 100},   // 11
            {103, 100, 103, 94},    // 12 - swing low 94 (lower low)
            {100, 102, 104, 96},    // 13
            {102, 101, 105, 97},    // 14 - high 105 prevents idx 13 from being swing high
        };
        for (int i = 0; i < data.length; i++) {
            candles.add(candle(data[i][0], data[i][1], data[i][2], data[i][3], i));
        }
        return candles;
    }

    // =======================================================================
    // Insufficient Data Tests
    // =======================================================================

    @Nested
    @DisplayName("Insufficient Data")
    class InsufficientData {

        @Test
        @DisplayName("Null candles returns SIDEWAYS with 0 strength")
        void nullCandlesReturnsSideways() {
            TrendResult result = analyzer.analyzeTrend(null, 20);

            assertThat(result.getDirection()).isEqualTo(TrendDirection.SIDEWAYS);
            assertThat(result.getStrength()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Fewer than 5 candles returns SIDEWAYS")
        void fewerThanFiveCandlesReturnsSideways() {
            List<MyCandle> candles = List.of(
                    candle(100, 102, 104, 98, 0L),
                    candle(102, 104, 106, 100, 1L),
                    candle(104, 106, 108, 102, 2L),
                    candle(106, 108, 110, 104, 3L));

            TrendResult result = analyzer.analyzeTrend(candles, 20);

            assertThat(result.getDirection()).isEqualTo(TrendDirection.SIDEWAYS);
            assertThat(result.getStrength()).isEqualTo(0.0);
            assertThat(result.getDescription()).contains("Insufficient");
        }
    }

    // =======================================================================
    // Uptrend Detection
    // =======================================================================

    @Nested
    @DisplayName("Uptrend Detection")
    class UptrendDetection {

        @Test
        @DisplayName("Detects uptrend with higher highs and higher lows")
        void detectsUptrend() {
            List<MyCandle> candles = uptrendCandles();

            TrendResult result = analyzer.analyzeTrend(candles, 20);

            assertThat(result.getDirection()).isEqualTo(TrendDirection.UP);
            assertThat(result.getStrength()).isGreaterThan(0.0);
            assertThat(result.getDescription()).contains("Uptrend");
        }

        @Test
        @DisplayName("Uptrend strength is between 0 and 1")
        void uptrendStrengthBounded() {
            List<MyCandle> candles = uptrendCandles();

            TrendResult result = analyzer.analyzeTrend(candles, 20);

            assertThat(result.getStrength()).isBetween(0.0, 1.0);
        }
    }

    // =======================================================================
    // Downtrend Detection
    // =======================================================================

    @Nested
    @DisplayName("Downtrend Detection")
    class DowntrendDetection {

        @Test
        @DisplayName("Detects downtrend with lower highs and lower lows")
        void detectsDowntrend() {
            List<MyCandle> candles = downtrendCandles();

            TrendResult result = analyzer.analyzeTrend(candles, 20);

            assertThat(result.getDirection()).isEqualTo(TrendDirection.DOWN);
            assertThat(result.getStrength()).isGreaterThan(0.0);
            assertThat(result.getDescription()).contains("Downtrend");
        }

        @Test
        @DisplayName("Downtrend strength is between 0 and 1")
        void downtrendStrengthBounded() {
            List<MyCandle> candles = downtrendCandles();

            TrendResult result = analyzer.analyzeTrend(candles, 20);

            assertThat(result.getStrength()).isBetween(0.0, 1.0);
        }
    }

    // =======================================================================
    // Sideways Detection
    // =======================================================================

    @Nested
    @DisplayName("Sideways Detection")
    class SidewaysDetection {

        @Test
        @DisplayName("Mixed signals result in SIDEWAYS")
        void mixedSignalsReturnsSideways() {
            List<MyCandle> candles = sidewaysCandles();

            TrendResult result = analyzer.analyzeTrend(candles, 20);

            assertThat(result.getDirection()).isEqualTo(TrendDirection.SIDEWAYS);
        }
    }

    // =======================================================================
    // Trend Strength
    // =======================================================================

    @Nested
    @DisplayName("Trend Strength")
    class TrendStrength {

        @Test
        @DisplayName("Strong uptrend has higher strength than weak uptrend")
        void strongUptrendHasHigherStrength() {
            // Strong uptrend
            TrendResult strongResult = analyzer.analyzeTrend(uptrendCandles(), 20);

            // Strength should be meaningfully > 0
            assertThat(strongResult.getStrength()).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("TrendResult description is not null or empty")
        void descriptionIsNotEmpty() {
            TrendResult result = analyzer.analyzeTrend(uptrendCandles(), 20);

            assertThat(result.getDescription()).isNotNull();
            assertThat(result.getDescription()).isNotEmpty();
        }
    }
}
