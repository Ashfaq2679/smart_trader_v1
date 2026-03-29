package com.techcobber.smarttrader.v1.models;

import com.techcobber.smarttrader.v1.models.MyCandle.CandleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for the {@link MyCandle} model class.
 *
 * <p>MyCandle uses the <b>Builder design pattern</b> to construct candle instances.
 * The Builder collects OHLCV (Open, High, Low, Close, Volume) values via a fluent
 * API and automatically calls {@code computeFields()} on {@code build()}, ensuring
 * every candle produced by the builder has its derived fields (color, bodySize,
 * wick percentages, candle type classifications) fully initialised.</p>
 */
class MyCandleTest {

    // -----------------------------------------------------------------------
    // Helper factory methods for creating test candles
    // -----------------------------------------------------------------------

    /** Bullish candle: open < close */
    private static MyCandle bullishCandle(double open, double close, double high, double low) {
        return new MyCandle.Builder()
                .open(open).close(close).high(high).low(low)
                .start(1L).volume(100)
                .build();
    }

    /** Bearish candle: open > close */
    private static MyCandle bearishCandle(double open, double close, double high, double low) {
        return new MyCandle.Builder()
                .open(open).close(close).high(high).low(low)
                .start(1L).volume(100)
                .build();
    }

    /** Neutral candle: open == close */
    private static MyCandle neutralCandle(double price, double high, double low) {
        return new MyCandle.Builder()
                .open(price).close(price).high(high).low(low)
                .start(1L).volume(100)
                .build();
    }

    /** Flat candle where open == close == high == low (range == 0) */
    private static MyCandle flatCandle(double price) {
        return new MyCandle.Builder()
                .open(price).close(price).high(price).low(price)
                .start(1L).volume(100)
                .build();
    }

    // =======================================================================
    // Builder Tests
    // =======================================================================

    /**
     * Tests for the Builder design pattern implementation.
     * The Builder provides a fluent API for constructing {@link MyCandle} instances
     * and automatically calls {@code computeFields()} when {@code build()} is invoked.
     */
    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Builder creates candle with correct OHLCV values")
        void builderSetsOhlcvValues() {
            MyCandle candle = new MyCandle.Builder()
                    .open(100.0).close(110.0).high(115.0).low(95.0)
                    .start(12345L).volume(500.0)
                    .build();

            assertThat(candle.getOpen()).isEqualTo(100.0);
            assertThat(candle.getClose()).isEqualTo(110.0);
            assertThat(candle.getHigh()).isEqualTo(115.0);
            assertThat(candle.getLow()).isEqualTo(95.0);
            assertThat(candle.getStart()).isEqualTo(12345L);
            assertThat(candle.getVolume()).isEqualTo(500.0);
        }

        @Test
        @DisplayName("Builder automatically computes derived fields (color, bodySize, wicks, patterns)")
        void builderComputesFields() {
            // Builder.build() calls computeFields() internally
            MyCandle candle = new MyCandle.Builder()
                    .open(100.0).close(110.0).high(115.0).low(95.0)
                    .start(1L).volume(100)
                    .build();

            assertThat(candle.getColor()).isNotNull();
            assertThat(candle.getBodySize()).isGreaterThan(0);
            assertThat(candle.getCandleTypes()).isNotNull().isNotEmpty();
        }
    }

    // =======================================================================
    // isBullish / isBearish / isNeutral Tests
    // =======================================================================

    @Nested
    @DisplayName("isBullish / isBearish / isNeutral Tests")
    class DirectionTests {

        @Test
        @DisplayName("Bullish candle (close > open) → isBullish true, isBearish false")
        void bullishCandle() {
            MyCandle candle = MyCandleTest.bullishCandle(100, 110, 115, 95);

            assertThat(candle.isBullish()).isTrue();
            assertThat(candle.isBearish()).isFalse();
            assertThat(candle.isNeutral()).isFalse();
        }

        @Test
        @DisplayName("Bearish candle (close < open) → isBearish true, isBullish false")
        void bearishCandle() {
            MyCandle candle = MyCandleTest.bearishCandle(110, 100, 115, 95);

            assertThat(candle.isBearish()).isTrue();
            assertThat(candle.isBullish()).isFalse();
            assertThat(candle.isNeutral()).isFalse();
        }

        @Test
        @DisplayName("Neutral candle (close == open) → isNeutral true")
        void neutralCandle() {
            MyCandle candle = MyCandleTest.neutralCandle(100, 110, 90);

            assertThat(candle.isNeutral()).isTrue();
            assertThat(candle.isBullish()).isFalse();
            assertThat(candle.isBearish()).isFalse();
        }
    }

    // =======================================================================
    // computeFields Tests
    // =======================================================================

    @Nested
    @DisplayName("computeFields Tests")
    class ComputeFieldsTests {

        @Test
        @DisplayName("Bullish candle gets color GREEN")
        void bullishColor() {
            MyCandle candle = bullishCandle(100, 110, 115, 95);
            assertThat(candle.getColor()).isEqualTo("GREEN");
        }

        @Test
        @DisplayName("Bearish candle gets color RED")
        void bearishColor() {
            MyCandle candle = bearishCandle(110, 100, 115, 95);
            assertThat(candle.getColor()).isEqualTo("RED");
        }

        @Test
        @DisplayName("Neutral candle gets color NEUTRAL")
        void neutralColor() {
            MyCandle candle = neutralCandle(100, 110, 90);
            assertThat(candle.getColor()).isEqualTo("NEUTRAL");
        }

        @Test
        @DisplayName("Body size is correctly computed as abs(close - open)")
        void bodySize() {
            MyCandle bullish = bullishCandle(100, 110, 115, 95);
            assertThat(bullish.getBodySize()).isEqualTo(10.0);

            MyCandle bearish = bearishCandle(110, 100, 115, 95);
            assertThat(bearish.getBodySize()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("Wick percentages are correctly computed")
        void wickPercentages() {
            // range = 20, upperWick = 115 - 110 = 5, lowerWick = 100 - 95 = 5
            MyCandle candle = bullishCandle(100, 110, 115, 95);
            assertThat(candle.getUpperWickPercent()).isEqualTo(25.0);
            assertThat(candle.getLowerWickPercent()).isEqualTo(25.0);
        }

        @Test
        @DisplayName("Null OHLCV fields cause early return without NPE")
        void nullFieldsEarlyReturn() {
            MyCandle candle = new MyCandle();
            // All fields null — computeFields should bail out gracefully
            candle.computeFields();

            assertThat(candle.getColor()).isNull();
            assertThat(candle.getCandleTypes()).isNull();
        }

        @Test
        @DisplayName("Zero range results in 0 wick percentages")
        void zeroRangeWicks() {
            MyCandle candle = flatCandle(100);

            assertThat(candle.getUpperWickPercent()).isEqualTo(0.0);
            assertThat(candle.getLowerWickPercent()).isEqualTo(0.0);
        }
    }

    // =======================================================================
    // bodyPercent and range Tests
    // =======================================================================

    @Nested
    @DisplayName("bodyPercent and range Tests")
    class BodyPercentAndRangeTests {

        @Test
        @DisplayName("bodyPercent returns correct percentage ((close - open) / open * 100)")
        void bodyPercent() {
            MyCandle candle = bullishCandle(100, 110, 115, 95);
            assertThat(candle.bodyPercent()).isEqualTo(10.0);

            MyCandle bearish = bearishCandle(110, 100, 115, 95);
            // (100 - 110) / 110 * 100 ≈ -9.0909
            assertThat(bearish.bodyPercent()).isCloseTo(-9.0909, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("range returns high - low")
        void range() {
            MyCandle candle = bullishCandle(100, 110, 115, 95);
            assertThat(candle.range()).isEqualTo(20.0);
        }
    }

    // =======================================================================
    // Single Candle Pattern Tests
    // =======================================================================

    @Nested
    @DisplayName("Single Candle Pattern Detection Tests")
    class SingleCandlePatternTests {

        @Test
        @DisplayName("Doji pattern — very small body relative to range")
        void dojiPattern() {
            // bodyRatio < 0.05, equal wicks → DOJI
            // range = 100, body = 1 (bodyRatio = 0.01), wicks roughly equal
            MyCandle candle = new MyCandle.Builder()
                    .open(150).close(151).high(200).low(100)
                    .start(1L).volume(100)
                    .build();
            assertThat(candle.getCandleTypes()).contains(CandleType.DOJI);
        }

        @Test
        @DisplayName("Dragonfly Doji — small body, long lower wick, tiny upper wick")
        void dragonflyDojiPattern() {
            // range = 100, body ≈ 1, lowerWick ~95, upperWick ~4
            // lowerWickRatio > 0.7, upperWickRatio < 0.1
            MyCandle candle = new MyCandle.Builder()
                    .open(196).close(197).high(200).low(100)
                    .start(1L).volume(100)
                    .build();
            assertThat(candle.getCandleTypes()).contains(CandleType.DRAGONFLY_DOJI);
        }

        @Test
        @DisplayName("Gravestone Doji — small body, long upper wick, tiny lower wick")
        void gravestoneDojiPattern() {
            // range = 100, body ≈ 1, upperWick ~96, lowerWick ~3
            // upperWickRatio > 0.7, lowerWickRatio < 0.1
            MyCandle candle = new MyCandle.Builder()
                    .open(104).close(103).high(200).low(100)
                    .start(1L).volume(100)
                    .build();
            assertThat(candle.getCandleTypes()).contains(CandleType.GRAVESTONE_DOJI);
        }

        @Test
        @DisplayName("Marubozu Bullish — large bullish body, tiny wicks")
        void marubozuBullishPattern() {
            // range = 100, body = 95 (bodyRatio = 0.95), upperWick = 3, lowerWick = 2
            MyCandle candle = new MyCandle.Builder()
                    .open(102).close(197).high(200).low(100)
                    .start(1L).volume(100)
                    .build();
            assertThat(candle.getCandleTypes()).contains(CandleType.MARUBOZU_BULLISH);
        }

        @Test
        @DisplayName("Marubozu Bearish — large bearish body, tiny wicks")
        void marubozuBearishPattern() {
            // range = 100, body = 95 (bodyRatio = 0.95), upperWick = 3, lowerWick = 2
            MyCandle candle = new MyCandle.Builder()
                    .open(197).close(102).high(200).low(100)
                    .start(1L).volume(100)
                    .build();
            assertThat(candle.getCandleTypes()).contains(CandleType.MARUBOZU_BEARISH);
        }

        @Test
        @DisplayName("Spinning Top — small body, roughly equal upper and lower wicks")
        void spinningTopPattern() {
            // range = 100, body = 10 (bodyRatio = 0.1), upperWick = 45, lowerWick = 45
            // abs(0.45 - 0.45) = 0 < 0.15 ✓
            MyCandle candle = new MyCandle.Builder()
                    .open(150).close(160).high(205).low(105)
                    .start(1L).volume(100)
                    .build();
            assertThat(candle.getCandleTypes()).contains(CandleType.SPINNING_TOP);
        }

        @Test
        @DisplayName("Hammer — bullish, small body at top, long lower wick")
        void hammerPattern() {
            // range = 100, body = 10, lowerWick = 80 (>= body*2=20), upperWick = 10
            // bodyRatio = 0.1 < 0.35, upperWickRatio = 0.1 < 0.15, lowerWick = 80 >= 20
            MyCandle candle = new MyCandle.Builder()
                    .open(190).close(200).high(210).low(110)
                    .start(1L).volume(100)
                    .build();
            assertThat(candle.getCandleTypes()).contains(CandleType.HAMMER);
        }

        @Test
        @DisplayName("Hanging Man — bearish, small body at top, long lower wick")
        void hangingManPattern() {
            // Same shape as hammer but bearish (open > close)
            // range = 100, body = 10, lowerWick = 80, upperWick = 10
            MyCandle candle = new MyCandle.Builder()
                    .open(200).close(190).high(210).low(110)
                    .start(1L).volume(100)
                    .build();
            assertThat(candle.getCandleTypes()).contains(CandleType.HANGING_MAN);
        }

        @Test
        @DisplayName("Inverted Hammer — bullish, small body at bottom, long upper wick")
        void invertedHammerPattern() {
            // range = 100, body = 10, upperWick = 80, lowerWick = 10
            // bodyRatio = 0.1 < 0.35, lowerWickRatio = 0.1 < 0.15, upperWick = 80 >= 20
            MyCandle candle = new MyCandle.Builder()
                    .open(110).close(120).high(200).low(100)
                    .start(1L).volume(100)
                    .build();
            assertThat(candle.getCandleTypes()).contains(CandleType.INVERTED_HAMMER);
        }

        @Test
        @DisplayName("Shooting Star — bearish, small body at bottom, long upper wick")
        void shootingStarPattern() {
            // Same shape as inverted hammer but bearish
            MyCandle candle = new MyCandle.Builder()
                    .open(120).close(110).high(200).low(100)
                    .start(1L).volume(100)
                    .build();
            assertThat(candle.getCandleTypes()).contains(CandleType.SHOOTING_STAR);
        }

        @Test
        @DisplayName("Default bullish pattern when no specific single-candle pattern matches")
        void defaultBullishPattern() {
            // Moderate bullish candle that doesn't match any special pattern
            // range = 20, body = 10 (bodyRatio = 0.5), upperWick = 5, lowerWick = 5
            MyCandle candle = bullishCandle(100, 110, 115, 95);
            assertThat(candle.getCandleTypes()).contains(CandleType.BULLISH);
        }

        @Test
        @DisplayName("Default bearish pattern when no specific single-candle pattern matches")
        void defaultBearishPattern() {
            MyCandle candle = bearishCandle(110, 100, 115, 95);
            assertThat(candle.getCandleTypes()).contains(CandleType.BEARISH);
        }

        @Test
        @DisplayName("Neutral pattern when range is zero")
        void neutralPatternZeroRange() {
            MyCandle candle = flatCandle(100);
            assertThat(candle.getCandleTypes()).contains(CandleType.NEUTRAL);
        }
    }

    // =======================================================================
    // Two Candle Pattern Tests
    // =======================================================================

    @Nested
    @DisplayName("Two Candle Pattern Detection Tests")
    class TwoCandlePatternTests {

        @Test
        @DisplayName("Bullish Engulfing — bearish prev, bullish current that engulfs")
        void bullishEngulfing() {
            // prev: bearish (open > close), current: bullish engulfs prev body
            MyCandle prev = bearishCandle(110, 100, 115, 95);
            // current.open <= prev.close(100), current.close >= prev.open(110)
            MyCandle current = bullishCandle(99, 111, 115, 95);

            List<CandleType> patterns = MyCandle.detectTwoCandlePatterns(prev, current);
            assertThat(patterns).contains(CandleType.BULLISH_ENGULFING);
        }

        @Test
        @DisplayName("Bearish Engulfing — bullish prev, bearish current that engulfs")
        void bearishEngulfing() {
            MyCandle prev = bullishCandle(100, 110, 115, 95);
            // current.open >= prev.close(110), current.close <= prev.open(100)
            MyCandle current = bearishCandle(111, 99, 115, 95);

            List<CandleType> patterns = MyCandle.detectTwoCandlePatterns(prev, current);
            assertThat(patterns).contains(CandleType.BEARISH_ENGULFING);
        }

        @Test
        @DisplayName("Bullish Harami — bearish prev, smaller bullish current inside prev body")
        void bullishHarami() {
            // prev: bearish open=120 close=100 (body=20)
            // current: bullish open=102 close=108 (body=6 < 20),
            // current.open >= prev.close(100), current.close <= prev.open(120)
            MyCandle prev = bearishCandle(120, 100, 125, 95);
            MyCandle current = bullishCandle(102, 108, 112, 98);

            List<CandleType> patterns = MyCandle.detectTwoCandlePatterns(prev, current);
            assertThat(patterns).contains(CandleType.BULLISH_HARAMI);
        }

        @Test
        @DisplayName("Bearish Harami — bullish prev, smaller bearish current inside prev body")
        void bearishHarami() {
            // prev: bullish open=100 close=120 (body=20)
            // current: bearish open=118 close=102 (body=16 < 20)
            // current.open <= prev.close(120), current.close >= prev.open(100)
            MyCandle prev = bullishCandle(100, 120, 125, 95);
            MyCandle current = bearishCandle(118, 102, 122, 98);

            List<CandleType> patterns = MyCandle.detectTwoCandlePatterns(prev, current);
            assertThat(patterns).contains(CandleType.BEARISH_HARAMI);
        }

        @Test
        @DisplayName("Piercing Line — bearish prev, bullish current opens below prev low and closes above prev midpoint")
        void piercingLine() {
            // prev: bearish open=120 close=100 low=95 → mid = (120+100)/2 = 110
            // current: bullish, open < prev.low(95), close > prevMid(110) && close < prev.open(120)
            MyCandle prev = bearishCandle(120, 100, 125, 95);
            MyCandle current = bullishCandle(94, 115, 118, 92);

            List<CandleType> patterns = MyCandle.detectTwoCandlePatterns(prev, current);
            assertThat(patterns).contains(CandleType.PIERCING_LINE);
        }

        @Test
        @DisplayName("Dark Cloud Cover — bullish prev, bearish current opens above prev high and closes below prev midpoint")
        void darkCloudCover() {
            // prev: bullish open=100 close=120 high=125 → mid = (100+120)/2 = 110
            // current: bearish, open > prev.high(125), close < prevMid(110) && close > prev.open(100)
            MyCandle prev = bullishCandle(100, 120, 125, 95);
            MyCandle current = bearishCandle(126, 105, 130, 102);

            List<CandleType> patterns = MyCandle.detectTwoCandlePatterns(prev, current);
            assertThat(patterns).contains(CandleType.DARK_CLOUD_COVER);
        }

        @Test
        @DisplayName("Tweezer Bottom — bearish prev and bullish current with matching lows")
        void tweezerBottom() {
            // Same low, prev bearish, current bullish
            // tolerance = prev.range() * 0.02 = (115-95)*0.02 = 0.4
            MyCandle prev = bearishCandle(110, 100, 115, 95);
            MyCandle current = bullishCandle(100, 110, 115, 95);

            List<CandleType> patterns = MyCandle.detectTwoCandlePatterns(prev, current);
            assertThat(patterns).contains(CandleType.TWEEZER_BOTTOM);
        }

        @Test
        @DisplayName("Tweezer Top — bullish prev and bearish current with matching highs")
        void tweezerTop() {
            // Same high, prev bullish, current bearish
            MyCandle prev = bullishCandle(100, 110, 115, 95);
            MyCandle current = bearishCandle(110, 100, 115, 95);

            List<CandleType> patterns = MyCandle.detectTwoCandlePatterns(prev, current);
            assertThat(patterns).contains(CandleType.TWEEZER_TOP);
        }

        @Test
        @DisplayName("Null candle inputs return empty list")
        void nullInputs() {
            MyCandle candle = bullishCandle(100, 110, 115, 95);
            assertThat(MyCandle.detectTwoCandlePatterns(null, candle)).isEmpty();
            assertThat(MyCandle.detectTwoCandlePatterns(candle, null)).isEmpty();
            assertThat(MyCandle.detectTwoCandlePatterns(null, null)).isEmpty();
        }
    }

    // =======================================================================
    // Three Candle Pattern Tests
    // =======================================================================

    @Nested
    @DisplayName("Three Candle Pattern Detection Tests")
    class ThreeCandlePatternTests {

        @Test
        @DisplayName("Morning Star — bearish first, small-body second, bullish third closing above first midpoint")
        void morningStar() {
            // first: bearish open=120 close=100 → mid = 110
            // second: small body, close < first.close(100), secondBodyRatio < 0.3
            // third: bullish, close > mid(110)
            MyCandle first = bearishCandle(120, 100, 125, 95);
            // second: small body, range = 10, body = 2 (bodyRatio = 0.2)
            MyCandle second = new MyCandle.Builder()
                    .open(97).close(99).high(104).low(94)
                    .start(1L).volume(100)
                    .build();
            MyCandle third = bullishCandle(102, 118, 122, 98);

            List<CandleType> patterns = MyCandle.detectThreeCandlePatterns(first, second, third);
            assertThat(patterns).contains(CandleType.MORNING_STAR);
        }

        @Test
        @DisplayName("Evening Star — bullish first, small-body second, bearish third closing below first midpoint")
        void eveningStar() {
            // first: bullish open=100 close=120 → mid = 110
            // second: small body, close > first.close(120), secondBodyRatio < 0.3
            // third: bearish, close < mid(110)
            MyCandle first = bullishCandle(100, 120, 125, 95);
            MyCandle second = new MyCandle.Builder()
                    .open(121).close(123).high(128).low(118)
                    .start(1L).volume(100)
                    .build();
            MyCandle third = bearishCandle(118, 105, 122, 100);

            List<CandleType> patterns = MyCandle.detectThreeCandlePatterns(first, second, third);
            assertThat(patterns).contains(CandleType.EVENING_STAR);
        }

        @Test
        @DisplayName("Three White Soldiers — three consecutive bullish candles with progressively higher opens and closes")
        void threeWhiteSoldiers() {
            // All bullish, each close > prev close, each open > prev open
            MyCandle first = bullishCandle(100, 110, 115, 95);
            MyCandle second = bullishCandle(105, 120, 125, 100);
            MyCandle third = bullishCandle(115, 130, 135, 110);

            List<CandleType> patterns = MyCandle.detectThreeCandlePatterns(first, second, third);
            assertThat(patterns).contains(CandleType.THREE_WHITE_SOLDIERS);
        }

        @Test
        @DisplayName("Three Black Crows — three consecutive bearish candles with progressively lower opens and closes")
        void threeBlackCrows() {
            // All bearish, each close < prev close, each open < prev open
            MyCandle first = bearishCandle(130, 120, 135, 115);
            MyCandle second = bearishCandle(118, 108, 122, 105);
            MyCandle third = bearishCandle(106, 96, 110, 92);

            List<CandleType> patterns = MyCandle.detectThreeCandlePatterns(first, second, third);
            assertThat(patterns).contains(CandleType.THREE_BLACK_CROWS);
        }

        @Test
        @DisplayName("Null candle inputs return empty list")
        void nullInputs() {
            MyCandle candle = bullishCandle(100, 110, 115, 95);
            assertThat(MyCandle.detectThreeCandlePatterns(null, candle, candle)).isEmpty();
            assertThat(MyCandle.detectThreeCandlePatterns(candle, null, candle)).isEmpty();
            assertThat(MyCandle.detectThreeCandlePatterns(candle, candle, null)).isEmpty();
            assertThat(MyCandle.detectThreeCandlePatterns(null, null, null)).isEmpty();
        }
    }
}
