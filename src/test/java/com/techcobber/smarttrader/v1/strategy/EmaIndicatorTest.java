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

@DisplayName("EmaIndicator")
class EmaIndicatorTest {

    private EmaIndicator ema;

    @BeforeEach
    void setUp() {
        ema = new EmaIndicator();
    }

    private MyCandle candle(double close) {
        MyCandle c = new MyCandle();
        c.setOpen(close);
        c.setHigh(close + 1);
        c.setLow(close - 1);
        c.setClose(close);
        return c;
    }

    @Nested
    @DisplayName("calculate()")
    class CalculateTests {

        @Test
        @DisplayName("Returns 0 for null candles")
        void nullCandles() {
            assertThat(ema.calculate(null, 9)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Returns simple average when fewer candles than period")
        void fewerCandlesThanPeriod() {
            List<MyCandle> candles = List.of(candle(10), candle(20), candle(30));
            double result = ema.calculate(candles, 9);
            assertThat(result).isCloseTo(20.0, within(0.001));
        }

        @Test
        @DisplayName("Returns SMA seed value when exactly period candles")
        void exactlyPeriodCandles() {
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 1; i <= 5; i++) candles.add(candle(i * 10.0)); // 10,20,30,40,50
            double result = ema.calculate(candles, 5);
            // SMA of 10,20,30,40,50 = 30, with no additional candles to smooth
            assertThat(result).isCloseTo(30.0, within(0.001));
        }

        @Test
        @DisplayName("EMA responds to price changes after seed")
        void emaRespondsToPrice() {
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 10; i++) candles.add(candle(100.0));
            candles.add(candle(200.0)); // sudden jump
            double ema5 = ema.calculate(candles, 5);
            // After seed of 100, one update at 200: ema = (200-100)*(2/6)+100 = 133.33
            assertThat(ema5).isGreaterThan(100.0);
            assertThat(ema5).isLessThan(200.0);
        }

        @Test
        @DisplayName("EMA9 < EMA21 when price is in sustained decline")
        void ema9BelowEma21OnDecline() {
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 100; i > 0; i--) candles.add(candle(i)); // declining
            double ema9val  = ema.calculate(candles, 9);
            double ema21val = ema.calculate(candles, 21);
            assertThat(ema9val).isLessThan(ema21val);
        }

        @Test
        @DisplayName("EMA9 > EMA21 when price is in sustained rise")
        void ema9AboveEma21OnRise() {
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 1; i <= 100; i++) candles.add(candle(i)); // rising
            double ema9val  = ema.calculate(candles, 9);
            double ema21val = ema.calculate(candles, 21);
            assertThat(ema9val).isGreaterThan(ema21val);
        }
    }

    @Nested
    @DisplayName("calculateSeries()")
    class SeriesTests {

        @Test
        @DisplayName("Returns empty list when insufficient candles")
        void emptyOnInsufficientData() {
            List<MyCandle> candles = List.of(candle(10), candle(20));
            assertThat(ema.calculateSeries(candles, 5)).isEmpty();
        }

        @Test
        @DisplayName("Series length = candles - period + 1")
        void correctSeriesLength() {
            List<MyCandle> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) candles.add(candle(50.0));
            List<Double> series = ema.calculateSeries(candles, 5);
            assertThat(series).hasSize(16); // 20 - 5 + 1
        }
    }
}
