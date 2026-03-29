package com.techcobber.smarttrader.v1.strategy;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.strategy.CandlePatternDetector.DetectedPattern;
import com.techcobber.smarttrader.v1.strategy.CandlePatternDetector.PatternBias;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CandlePatternDetector}.
 */
class CandlePatternDetectorTest {

    private CandlePatternDetector detector;

    @BeforeEach
    void setUp() {
        detector = new CandlePatternDetector();
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

    private static MyCandle bullishCandle(double open, double close, double high, double low) {
        return candle(open, close, high, low, 1L);
    }

    private static MyCandle bearishCandle(double open, double close, double high, double low) {
        return candle(open, close, high, low, 1L);
    }

    /**
     * Creates a list of simple bullish candles to provide context for pattern detection.
     * The detector looks at the last 5 candles for single patterns.
     */
    private static List<MyCandle> paddedCandles(MyCandle... tailCandles) {
        List<MyCandle> candles = new ArrayList<>();
        // Add padding candles so the tail candles land in the detection window
        for (int i = 0; i < 5; i++) {
            candles.add(candle(100 + i, 102 + i, 104 + i, 98 + i, i));
        }
        for (MyCandle c : tailCandles) {
            candles.add(c);
        }
        return candles;
    }

    // =======================================================================
    // Edge Case Tests
    // =======================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty candle list returns empty patterns")
        void emptyListReturnsEmpty() {
            List<DetectedPattern> patterns = detector.detectPatterns(Collections.emptyList());
            assertThat(patterns).isEmpty();
        }

        @Test
        @DisplayName("Null candles returns empty patterns")
        void nullReturnsEmpty() {
            List<DetectedPattern> patterns = detector.detectPatterns(null);
            assertThat(patterns).isEmpty();
        }
    }

    // =======================================================================
    // Single-Candle Pattern Tests
    // =======================================================================

    @Nested
    @DisplayName("Single-Candle Patterns")
    class SingleCandlePatterns {

        @Test
        @DisplayName("Detects hammer pattern from last 5 candles")
        void detectsHammer() {
            // Hammer: small body at top, long lower wick, minimal upper wick
            // bodyRatio < 0.35, lowerWick >= bodySize*2, upperWickRatio < 0.15
            // open=108, close=110, high=111, low=100 -> body=2, range=11, lowerWick=8, upperWick=1
            MyCandle hammer = candle(108, 110, 111, 100, 10L);

            List<MyCandle> candles = paddedCandles(hammer);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("HAMMER");
        }

        @Test
        @DisplayName("Detects shooting star pattern")
        void detectsShootingStar() {
            // Shooting star: small bearish body at bottom, long upper wick
            // open=109, close=107, high=120, low=106 -> body=2, range=14, upperWick=11, lowerWick=1
            MyCandle shootingStar = candle(109, 107, 120, 106, 10L);

            List<MyCandle> candles = paddedCandles(shootingStar);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("SHOOTING_STAR");
        }

        @Test
        @DisplayName("Detects doji pattern")
        void detectsDoji() {
            // Doji: open == close (very small body)
            MyCandle doji = candle(100.0, 100.0, 105.0, 95.0, 10L);

            List<MyCandle> candles = paddedCandles(doji);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("DOJI");
        }

        @Test
        @DisplayName("Detects marubozu bullish pattern")
        void detectsMarubozuBullish() {
            // Marubozu bullish: bodyRatio > 0.9, almost no wicks
            // open=100, close=110, high=110.2, low=99.9 -> body=10, range=10.3
            MyCandle marubozu = candle(100, 110, 110.2, 99.9, 10L);

            List<MyCandle> candles = paddedCandles(marubozu);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("MARUBOZU_BULLISH");
        }
    }

    // =======================================================================
    // Two-Candle Pattern Tests
    // =======================================================================

    @Nested
    @DisplayName("Two-Candle Patterns")
    class TwoCandlePatterns {

        @Test
        @DisplayName("Detects bullish engulfing pattern")
        void detectsBullishEngulfing() {
            // Bearish candle followed by larger bullish candle that engulfs it
            // prev: open=110, close=100, bearish; curr: open<=100, close>=110, bullish
            MyCandle prev = candle(110, 100, 112, 98, 1L);
            MyCandle curr = candle(99, 112, 114, 97, 2L);

            List<MyCandle> candles = paddedCandles(prev, curr);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("BULLISH_ENGULFING");
        }

        @Test
        @DisplayName("Detects bearish engulfing pattern")
        void detectsBearishEngulfing() {
            // Bullish candle followed by larger bearish candle
            MyCandle prev = candle(100, 110, 112, 98, 1L);
            MyCandle curr = candle(111, 99, 113, 97, 2L);

            List<MyCandle> candles = paddedCandles(prev, curr);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("BEARISH_ENGULFING");
        }

        @Test
        @DisplayName("Detects tweezer bottom pattern")
        void detectsTweezerBottom() {
            // Two candles with matching lows: first bearish, second bullish
            MyCandle prev = candle(110, 100, 112, 95, 1L);
            MyCandle curr = candle(100, 108, 110, 95, 2L);

            List<MyCandle> candles = paddedCandles(prev, curr);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("TWEEZER_BOTTOM");
        }
    }

    // =======================================================================
    // Three-Candle Pattern Tests
    // =======================================================================

    @Nested
    @DisplayName("Three-Candle Patterns")
    class ThreeCandlePatterns {

        @Test
        @DisplayName("Detects morning star pattern")
        void detectsMorningStar() {
            // Morning star: bearish, small body (doji-like), then bullish closing above midpoint of first
            // first: bearish open=110 close=100
            // second: small body below first's close, e.g. open=99 close=99.5 (bodyRatio<0.3)
            // third: bullish close > (110+100)/2 = 105
            MyCandle first = candle(110, 100, 112, 98, 1L);
            MyCandle second = candle(99, 99.5, 100, 97, 2L);  // small body, range=3, body=0.5
            MyCandle third = candle(100, 108, 110, 99, 3L);

            List<MyCandle> candles = paddedCandles(first, second, third);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("MORNING_STAR");
        }

        @Test
        @DisplayName("Detects evening star pattern")
        void detectsEveningStar() {
            // Evening star: bullish, small body above first's close, then bearish closing below midpoint
            // first: bullish open=100 close=110
            // second: small body above first's close, open=111 close=111.5 (bodyRatio<0.3)
            // third: bearish close < (100+110)/2 = 105
            MyCandle first = candle(100, 110, 112, 98, 1L);
            MyCandle second = candle(111, 111.5, 113, 110, 2L);  // small body
            MyCandle third = candle(110, 103, 111, 101, 3L);

            List<MyCandle> candles = paddedCandles(first, second, third);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("EVENING_STAR");
        }

        @Test
        @DisplayName("Detects three white soldiers pattern")
        void detectsThreeWhiteSoldiers() {
            // Three consecutive bullish candles with higher opens and higher closes
            MyCandle first = candle(100, 108, 110, 99, 1L);
            MyCandle second = candle(106, 116, 118, 105, 2L);
            MyCandle third = candle(114, 124, 126, 113, 3L);

            List<MyCandle> candles = paddedCandles(first, second, third);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("THREE_WHITE_SOLDIERS");
        }

        @Test
        @DisplayName("Detects three black crows pattern")
        void detectsThreeBlackCrows() {
            // Three consecutive bearish candles with lower opens and lower closes
            MyCandle first = candle(124, 116, 126, 115, 1L);
            MyCandle second = candle(118, 108, 120, 107, 2L);
            MyCandle third = candle(110, 100, 112, 99, 3L);

            List<MyCandle> candles = paddedCandles(first, second, third);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("THREE_BLACK_CROWS");
        }
    }

    // =======================================================================
    // Bias Classification Tests
    // =======================================================================

    @Nested
    @DisplayName("Bias Classification")
    class BiasClassification {

        @Test
        @DisplayName("Hammer is classified as BULLISH")
        void hammerIsBullish() {
            MyCandle hammer = candle(108, 110, 111, 100, 10L);
            List<MyCandle> candles = paddedCandles(hammer);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            DetectedPattern hammerPattern = patterns.stream()
                    .filter(p -> p.getName().equals("HAMMER"))
                    .findFirst().orElse(null);

            assertThat(hammerPattern).isNotNull();
            assertThat(hammerPattern.getBias()).isEqualTo(PatternBias.BULLISH);
        }

        @Test
        @DisplayName("Shooting star is classified as BEARISH")
        void shootingStarIsBearish() {
            MyCandle shootingStar = candle(109, 107, 120, 106, 10L);
            List<MyCandle> candles = paddedCandles(shootingStar);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            DetectedPattern ssPattern = patterns.stream()
                    .filter(p -> p.getName().equals("SHOOTING_STAR"))
                    .findFirst().orElse(null);

            assertThat(ssPattern).isNotNull();
            assertThat(ssPattern.getBias()).isEqualTo(PatternBias.BEARISH);
        }

        @Test
        @DisplayName("Doji is classified as NEUTRAL")
        void dojiIsNeutral() {
            MyCandle doji = candle(100.0, 100.0, 105.0, 95.0, 10L);
            List<MyCandle> candles = paddedCandles(doji);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            DetectedPattern dojiPattern = patterns.stream()
                    .filter(p -> p.getName().equals("DOJI"))
                    .findFirst().orElse(null);

            assertThat(dojiPattern).isNotNull();
            assertThat(dojiPattern.getBias()).isEqualTo(PatternBias.NEUTRAL);
        }

        @Test
        @DisplayName("Bullish engulfing is classified as BULLISH")
        void bullishEngulfingIsBullish() {
            MyCandle prev = candle(110, 100, 112, 98, 1L);
            MyCandle curr = candle(99, 112, 114, 97, 2L);
            List<MyCandle> candles = paddedCandles(prev, curr);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            DetectedPattern engulfing = patterns.stream()
                    .filter(p -> p.getName().equals("BULLISH_ENGULFING"))
                    .findFirst().orElse(null);

            assertThat(engulfing).isNotNull();
            assertThat(engulfing.getBias()).isEqualTo(PatternBias.BULLISH);
        }

        @Test
        @DisplayName("Morning star is classified as BULLISH")
        void morningStarIsBullish() {
            MyCandle first = candle(110, 100, 112, 98, 1L);
            MyCandle second = candle(99, 99.5, 100, 97, 2L);
            MyCandle third = candle(100, 108, 110, 99, 3L);
            List<MyCandle> candles = paddedCandles(first, second, third);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            DetectedPattern ms = patterns.stream()
                    .filter(p -> p.getName().equals("MORNING_STAR"))
                    .findFirst().orElse(null);

            assertThat(ms).isNotNull();
            assertThat(ms.getBias()).isEqualTo(PatternBias.BULLISH);
        }
    }

    // =======================================================================
    // Mixed Pattern Detection Tests
    // =======================================================================

    @Nested
    @DisplayName("Mixed Pattern Detection")
    class MixedPatternDetection {

        @Test
        @DisplayName("Detects multiple pattern types from a single candle set")
        void detectsMultiplePatterns() {
            // Build candles that produce both single-candle and two-candle patterns
            // Bearish candle followed by bullish engulfing which is also a hammer
            MyCandle bearish = candle(110, 100, 112, 98, 1L);
            // This candle engulfs the previous AND has hammer characteristics
            // open=99, close=112 -> bullish engulfing; also lowerWick long
            MyCandle engulfingHammer = candle(99, 112, 114, 97, 2L);

            List<MyCandle> candles = paddedCandles(bearish, engulfingHammer);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            // Should have at least 2 patterns detected
            assertThat(patterns.size()).isGreaterThanOrEqualTo(2);

            List<String> names = patterns.stream().map(DetectedPattern::getName).toList();
            assertThat(names).contains("BULLISH_ENGULFING");
        }

        @Test
        @DisplayName("Each detected pattern has a valid candleIndex")
        void patternsHaveValidCandleIndex() {
            MyCandle prev = candle(110, 100, 112, 98, 1L);
            MyCandle curr = candle(99, 112, 114, 97, 2L);
            List<MyCandle> candles = paddedCandles(prev, curr);
            List<DetectedPattern> patterns = detector.detectPatterns(candles);

            for (DetectedPattern p : patterns) {
                assertThat(p.getCandleIndex()).isGreaterThanOrEqualTo(0);
                assertThat(p.getCandleIndex()).isLessThan(candles.size());
            }
        }
    }
}
