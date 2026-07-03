package com.techcobber.smarttrader.v1.strategy;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.techcobber.smarttrader.v1.strategy.MultiTimeframeAnalyzer.MultiTimeframeResult;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendDirection;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultiTimeframeAnalyzer")
class MultiTimeframeAnalyzerTest {

    @Mock
    private TrendAnalyzer mockTrendAnalyzer;

    private MultiTimeframeAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new MultiTimeframeAnalyzer(mockTrendAnalyzer);
    }

    private List<MyCandle> candles(int n) {
        List<MyCandle> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            MyCandle c = new MyCandle();
            c.setClose(100.0 + i);
            list.add(c);
        }
        return list;
    }

    private TrendResult trend(TrendDirection dir, double strength) {
        return new TrendResult(dir, strength, dir.name());
    }

    @Nested
    @DisplayName("isAligned — BUY gate")
    class AlignmentTests {

        @Test
        @DisplayName("Aligned when 4H=UP, 1H=UP, 15m=UP")
        void allUp() {
            when(mockTrendAnalyzer.analyzeTrend(candles(5), 20)).thenReturn(trend(TrendDirection.UP, 0.8));
            List<MyCandle> ltf  = candles(5);
            List<MyCandle> conf = candles(5);
            List<MyCandle> htf  = candles(5);
            when(mockTrendAnalyzer.analyzeTrend(ltf,  20)).thenReturn(trend(TrendDirection.UP, 0.8));
            when(mockTrendAnalyzer.analyzeTrend(conf, 20)).thenReturn(trend(TrendDirection.UP, 0.7));
            when(mockTrendAnalyzer.analyzeTrend(htf,  20)).thenReturn(trend(TrendDirection.UP, 0.9));

            MultiTimeframeResult result = analyzer.analyze(ltf, conf, htf);
            assertThat(result.isAligned()).isTrue();
        }

        @Test
        @DisplayName("NOT aligned when 4H=DOWN")
        void htfDown() {
            List<MyCandle> ltf  = candles(5);
            List<MyCandle> conf = candles(5);
            List<MyCandle> htf  = candles(5);
            when(mockTrendAnalyzer.analyzeTrend(ltf,  20)).thenReturn(trend(TrendDirection.UP, 0.8));
            when(mockTrendAnalyzer.analyzeTrend(conf, 20)).thenReturn(trend(TrendDirection.UP, 0.7));
            when(mockTrendAnalyzer.analyzeTrend(htf,  20)).thenReturn(trend(TrendDirection.DOWN, 0.9));

            MultiTimeframeResult result = analyzer.analyze(ltf, conf, htf);
            assertThat(result.isAligned()).isFalse();
            assertThat(result.getHtfTrend()).isEqualTo(TrendDirection.DOWN);
        }

        @Test
        @DisplayName("NOT aligned when 1H=DOWN contradicts 15m=UP")
        void confirmDown_ltfUp() {
                // Use different-length lists so Mockito's equals-based matching doesn't confuse stubs
                List<MyCandle> ltf  = candles(5);
                List<MyCandle> conf = candles(6);
                List<MyCandle> htf  = candles(7);
                when(mockTrendAnalyzer.analyzeTrend(ltf,  20)).thenReturn(trend(TrendDirection.UP, 0.8));
                when(mockTrendAnalyzer.analyzeTrend(conf, 20)).thenReturn(trend(TrendDirection.DOWN, 0.6));
                when(mockTrendAnalyzer.analyzeTrend(htf,  20)).thenReturn(trend(TrendDirection.SIDEWAYS, 0.3));

                MultiTimeframeResult result = analyzer.analyze(ltf, conf, htf);
                assertThat(result.isAligned()).isFalse();
            }

        @Test
        @DisplayName("Aligned when 4H=SIDEWAYS, 1H=UP, 15m=UP")
        void htfSidewaysAllowsBuy() {
            List<MyCandle> ltf  = candles(5);
            List<MyCandle> conf = candles(5);
            List<MyCandle> htf  = candles(5);
            when(mockTrendAnalyzer.analyzeTrend(ltf,  20)).thenReturn(trend(TrendDirection.UP, 0.7));
            when(mockTrendAnalyzer.analyzeTrend(conf, 20)).thenReturn(trend(TrendDirection.UP, 0.6));
            when(mockTrendAnalyzer.analyzeTrend(htf,  20)).thenReturn(trend(TrendDirection.SIDEWAYS, 0.4));

            MultiTimeframeResult result = analyzer.analyze(ltf, conf, htf);
            assertThat(result.isAligned()).isTrue();
        }

        @Test
        @DisplayName("Returns SIDEWAYS for empty candle lists")
        void emptyLists() {
            MultiTimeframeResult result = analyzer.analyze(List.of(), List.of(), List.of());
            assertThat(result.getLtfTrend()).isEqualTo(TrendDirection.SIDEWAYS);
            assertThat(result.getHtfTrend()).isEqualTo(TrendDirection.SIDEWAYS);
        }
    }

    @Nested
    @DisplayName("analyzeWithKnownLtf()")
    class KnownLtfTests {

        @Test
        @DisplayName("Uses provided LTF direction without re-analyzing LTF candles")
        void usesKnownLtf() {
            List<MyCandle> conf = candles(5);
            List<MyCandle> htf  = candles(5);
            when(mockTrendAnalyzer.analyzeTrend(conf, 20)).thenReturn(trend(TrendDirection.UP, 0.7));
            when(mockTrendAnalyzer.analyzeTrend(htf,  20)).thenReturn(trend(TrendDirection.UP, 0.9));

            MultiTimeframeResult result = analyzer.analyzeWithKnownLtf(TrendDirection.UP, conf, htf);
            assertThat(result.getLtfTrend()).isEqualTo(TrendDirection.UP);
            assertThat(result.isAligned()).isTrue();
        }
    }
}
