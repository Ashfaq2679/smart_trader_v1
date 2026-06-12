package com.techcobber.smarttrader.v1.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.techcobber.smarttrader.v1.models.MyCandle;

@DisplayName("AtrIndicator")
class AtrIndicatorTest {

    private AtrIndicator atr;

    @BeforeEach
    void setUp() {
        atr = new AtrIndicator();
    }

    private MyCandle candle(double open, double high, double low, double close) {
        MyCandle c = new MyCandle();
        c.setOpen(open);
        c.setHigh(high);
        c.setLow(low);
        c.setClose(close);
        return c;
    }

    private List<MyCandle> buildStableCandles(int count, double price, double range) {
        List<MyCandle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candles.add(candle(price, price + range / 2, price - range / 2, price));
        }
        return candles;
    }

    @Nested
    @DisplayName("calculate()")
    class CalculateTests {

        @Test
        @DisplayName("Returns 0 when insufficient candles")
        void insufficientCandles() {
            List<MyCandle> candles = buildStableCandles(5, 100, 2);
            assertThat(atr.calculate(candles)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("ATR equals range for constant-range candles")
        void constantRangeCandles() {
            List<MyCandle> candles = buildStableCandles(20, 100, 4.0);
            double result = atr.calculate(candles);
            // For constant-range candles with close = midpoint, TR = high - low = 4.0
            assertThat(result).isCloseTo(4.0, within(0.5));
        }

        @Test
        @DisplayName("ATR is higher for volatile candles than stable ones")
        void volatileHigherThanStable() {
            List<MyCandle> stable   = buildStableCandles(20, 100, 2.0);
            List<MyCandle> volatile_ = buildStableCandles(20, 100, 10.0);
            assertThat(atr.calculate(volatile_)).isGreaterThan(atr.calculate(stable));
        }
    }

    @Nested
    @DisplayName("isSpike()")
    class SpikeTests {

        @Test
        @DisplayName("Not a spike when ATR is stable")
        void noSpikeForStableATR() {
            List<MyCandle> candles = buildStableCandles(60, 100, 2.0);
            assertThat(atr.isSpike(candles, 2.0)).isFalse();
        }

        @Test
        @DisplayName("Detects spike when last candle has very large range")
        void detectsSpike() {
            List<MyCandle> candles = buildStableCandles(59, 100, 1.0);
            // Add one candle with 10× the range
            candles.add(candle(100, 200, 50, 125));
            assertThat(atr.isSpike(candles, 2.0)).isTrue();
        }
    }

    @Nested
    @DisplayName("getRecentSwingHigh() / getRecentSwingLow()")
    class SwingLevelTests {

        @Test
        @DisplayName("Returns highest high within lookback")
        void recentSwingHigh() {
            List<MyCandle> candles = new ArrayList<>();
            candles.add(candle(100, 110, 90, 100));
            candles.add(candle(100, 150, 90, 100)); // highest
            candles.add(candle(100, 120, 90, 100));
            assertThat(atr.getRecentSwingHigh(candles, 3)).isCloseTo(150.0, within(0.001));
        }

        @Test
        @DisplayName("Returns lowest low within lookback")
        void recentSwingLow() {
            List<MyCandle> candles = new ArrayList<>();
            candles.add(candle(100, 110, 80, 100));
            candles.add(candle(100, 110, 50, 100)); // lowest
            candles.add(candle(100, 110, 70, 100));
            assertThat(atr.getRecentSwingLow(candles, 3)).isCloseTo(50.0, within(0.001));
        }

        @Test
        @DisplayName("lookback caps at candle list size")
        void lookbackBeyondSize() {
            List<MyCandle> candles = List.of(candle(100, 120, 80, 100));
            assertThat(atr.getRecentSwingHigh(candles, 10)).isCloseTo(120.0, within(0.001));
            assertThat(atr.getRecentSwingLow(candles, 10)).isCloseTo(80.0, within(0.001));
        }
    }
}
