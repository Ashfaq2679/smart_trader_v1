package com.techcobber.smarttrader.v1.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.strategy.ConsolidationDetector.ConsolidationResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsolidationDetector")
class ConsolidationDetectorTest {

    @Mock
    private AtrIndicator mockAtr;

    private ConsolidationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ConsolidationDetector(mockAtr);
    }

    private MyCandle candle(double high, double low, double close) {
        MyCandle c = new MyCandle();
        c.setOpen(close);
        c.setHigh(high);
        c.setLow(low);
        c.setClose(close);
        return c;
    }

    private List<MyCandle> tightRangeCandles() {
        List<MyCandle> candles = new ArrayList<>();
        // price around 100 with very tight range (< 2.5%)
        for (int i = 0; i < 25; i++) {
            candles.add(candle(101.0, 99.0, 100.0));
        }
        return candles;
    }

    private List<MyCandle> wideRangeCandles() {
        List<MyCandle> candles = new ArrayList<>();
        candles.add(candle(120, 80, 100));   // wide range (40% of mid)
        for (int i = 0; i < 24; i++) {
            candles.add(candle(110, 90, 100));
        }
        return candles;
    }

    @Nested
    @DisplayName("detect() — consolidation")
    class ConsolidatingTests {

        @Test
        @DisplayName("Detects consolidation when range is tight and ATR is low")
        void detectsConsolidation() {
            when(mockAtr.calculate(any())).thenReturn(0.3); // ATR % = 0.3/100 = 0.3% < 0.5%
            List<MyCandle> candles = tightRangeCandles();
            ConsolidationResult result = detector.detect(candles, 20, 2.5, 0.5, 1.5);
            assertThat(result.isConsolidating()).isTrue();
        }

        @Test
        @DisplayName("Not consolidating when range is wide")
        void notConsolidatingWideRange() {
            when(mockAtr.calculate(any())).thenReturn(0.3);
            List<MyCandle> candles = wideRangeCandles();
            ConsolidationResult result = detector.detect(candles, 20, 2.5, 0.5, 1.5);
            assertThat(result.isConsolidating()).isFalse();
        }

        @Test
        @DisplayName("Not consolidating when ATR is high even with tight range")
        void notConsolidatingHighAtr() {
            when(mockAtr.calculate(any())).thenReturn(5.0); // 5% ATR > 0.5% threshold
            List<MyCandle> candles = tightRangeCandles();
            ConsolidationResult result = detector.detect(candles, 20, 2.5, 0.5, 1.5);
            assertThat(result.isConsolidating()).isFalse();
        }
    }

    @Nested
    @DisplayName("detect() — edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Returns non-consolidating for null candles")
        void nullCandles() {
            ConsolidationResult result = detector.detect(null);
            assertThat(result.isConsolidating()).isFalse();
        }

        @Test
        @DisplayName("Returns non-consolidating for fewer than 5 candles")
        void tooFewCandles() {
            ConsolidationResult result = detector.detect(List.of(candle(110, 90, 100)));
            assertThat(result.isConsolidating()).isFalse();
        }

        @Test
        @DisplayName("Result contains range and ATR percent values")
        void resultContainsMetrics() {
            when(mockAtr.calculate(any())).thenReturn(0.2);
            List<MyCandle> candles = tightRangeCandles();
            ConsolidationResult result = detector.detect(candles, 20, 2.5, 0.5,1.5);
            assertThat(result.getRangePercent()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.getAtrPercent()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.getDescription()).isNotBlank();
        }
    }
}
