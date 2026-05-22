package com.techcobber.smarttrader.v1.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.model.common.Granularity;
import com.coinbase.advanced.model.products.Product;
import com.techcobber.smarttrader.v1.models.ListCandles;
import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.strategy.PriceActionStrategy;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendDirection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketScannerServicePersistenceTest {

    @Mock
    private CoinbasePublicServiceImpl publicService;

    @Mock
    private TradeDecisionService tradeDecisionService;

    private PriceActionStrategy mockedStrategy;
    private TrendAnalyzer mockedTrendAnalyzer;
    private MarketScannerService scanner;

    @BeforeEach
    void setUp() {
        // We'll inject mocked strategy and trend analyzer in the constructor later per-test
    }

    private static Product product(String id, String vol, String volChange, String priceChange, String price) {
        Product p = new Product();
        p.setProductId(id);
        p.setVolume24h(vol);
        p.setVolumePercentageChange24h(volChange);
        p.setPricePercentageChange24h(priceChange);
        p.setPrice(price);
        p.setTradingDisabled(false);
        p.setDisabled(false);
        p.setCancelOnly(false);
        p.setViewOnly(false);
        return p;
    }

    private static MyCandle candle(double open, double close, double high, double low, long start) {
        return new MyCandle.Builder().open(open).close(close).high(high).low(low).start(start).volume(100).build();
    }

    private static ListCandles manyCandles(int n) {
        List<MyCandle> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(candle(100 + i, 101 + i, 103 + i, 99 + i, i));
        }
        ListCandles lc = new ListCandles();
        lc.setCandles(list);
        return lc;
    }

    @Nested
    @DisplayName("Persistence behaviour")
    class PersistenceBehaviour {

        @Test
        @DisplayName("Persists decision when confidence > 0.70 and strong pattern present")
        void persistsWhenHighConfidenceAndStrongPattern() throws CoinbaseAdvancedException {
            // Arrange
            mockedStrategy = org.mockito.Mockito.mock(PriceActionStrategy.class);
            mockedTrendAnalyzer = org.mockito.Mockito.mock(TrendAnalyzer.class);
            scanner = new MarketScannerService(publicService, mockedStrategy, mockedTrendAnalyzer, tradeDecisionService);

            Product p = product("BTC-USDC", "1000", "5", "2", "1000");
            when(publicService.fetchCandles(anyString(), anyLong(), anyLong(), any(Granularity.class))).thenReturn(manyCandles(25));

            TradeDecision decision = TradeDecision.builder()
                    .signal(TradeDecision.Signal.BUY)
                    .confidence(0.85)
                    .detectedPatterns(List.of("BULLISH_ENGULFING"))
                    .reasoning("r")
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            when(mockedStrategy.analyze(any(), anyString())).thenReturn(decision);
            when(mockedTrendAnalyzer.analyzeTrend(any(), anyInt())).thenReturn(new TrendAnalyzer.TrendResult(TrendDirection.UP, 0.9, "desc"));

            // Act
            var result = scanner.analyseProduct(p, 0L, 1L, Granularity.ONE_HOUR, 1000.0);

            // Assert
            assertThat(result).isNotNull();
            ArgumentCaptor<TradeDecision> cap = ArgumentCaptor.forClass(TradeDecision.class);
            verify(tradeDecisionService, times(1)).save(cap.capture());
            TradeDecision saved = cap.getValue();
            assertThat(saved.getProductId()).isEqualTo("BTC-USDC");
            assertThat(saved.getConfidence()).isGreaterThan(0.70);
        }

        @Test
        @DisplayName("Does not persist when confidence <= 0.70")
        void doesNotPersistWhenConfidenceLow() throws CoinbaseAdvancedException {
            mockedStrategy = org.mockito.Mockito.mock(PriceActionStrategy.class);
            mockedTrendAnalyzer = org.mockito.Mockito.mock(TrendAnalyzer.class);
            scanner = new MarketScannerService(publicService, mockedStrategy, mockedTrendAnalyzer, tradeDecisionService);

            Product p = product("ETH-USDC", "2000", "2", "1", "2000");
            when(publicService.fetchCandles(anyString(), anyLong(), anyLong(), any(Granularity.class))).thenReturn(manyCandles(25));

            TradeDecision decision = TradeDecision.builder()
                    .signal(TradeDecision.Signal.BUY)
                    .confidence(0.70)
                    .detectedPatterns(List.of("BULLISH_ENGULFING"))
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            when(mockedStrategy.analyze(any(), anyString())).thenReturn(decision);
            when(mockedTrendAnalyzer.analyzeTrend(any(), anyInt())).thenReturn(new TrendAnalyzer.TrendResult(TrendDirection.UP, 0.5, "desc"));

            var result = scanner.analyseProduct(p, 0L, 1L, Granularity.ONE_HOUR, 1000.0);

            assertThat(result).isNotNull();
            verify(tradeDecisionService, times(0)).save(any());
        }

        @Test
        @DisplayName("Does not persist when no strong pattern present")
        void doesNotPersistWhenNoStrongPattern() throws CoinbaseAdvancedException {
            mockedStrategy = org.mockito.Mockito.mock(PriceActionStrategy.class);
            mockedTrendAnalyzer = org.mockito.Mockito.mock(TrendAnalyzer.class);
            scanner = new MarketScannerService(publicService, mockedStrategy, mockedTrendAnalyzer, tradeDecisionService);

            Product p = product("SOL-USDC", "3000", "3", "2", "30");
            when(publicService.fetchCandles(anyString(), anyLong(), anyLong(), any(Granularity.class))).thenReturn(manyCandles(25));

            TradeDecision decision = TradeDecision.builder()
                    .signal(TradeDecision.Signal.BUY)
                    .confidence(0.85)
                    .detectedPatterns(List.of("DOJI", "HAMMER"))
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            when(mockedStrategy.analyze(any(), anyString())).thenReturn(decision);
            when(mockedTrendAnalyzer.analyzeTrend(any(), anyInt())).thenReturn(new TrendAnalyzer.TrendResult(TrendDirection.UP, 0.7, "desc"));

            var result = scanner.analyseProduct(p, 0L, 1L, Granularity.ONE_HOUR, 1000.0);

            assertThat(result).isNotNull();
            verify(tradeDecisionService, times(0)).save(any());
        }

        @Test
        @DisplayName("Handles exceptions from save gracefully")
        void handlesExceptionFromSave() throws CoinbaseAdvancedException {
            mockedStrategy = org.mockito.Mockito.mock(PriceActionStrategy.class);
            mockedTrendAnalyzer = org.mockito.Mockito.mock(TrendAnalyzer.class);
            scanner = new MarketScannerService(publicService, mockedStrategy, mockedTrendAnalyzer, tradeDecisionService);

            Product p = product("LTC-USDC", "3000", "3", "2", "60");
            when(publicService.fetchCandles(anyString(), anyLong(), anyLong(), any(Granularity.class))).thenReturn(manyCandles(25));

            TradeDecision decision = TradeDecision.builder()
                    .signal(TradeDecision.Signal.BUY)
                    .confidence(0.85)
                    .detectedPatterns(List.of("ENGULFING"))
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            when(mockedStrategy.analyze(any(), anyString())).thenReturn(decision);
            when(mockedTrendAnalyzer.analyzeTrend(any(), anyInt())).thenReturn(new TrendAnalyzer.TrendResult(TrendDirection.UP, 0.8, "desc"));

            doThrow(new RuntimeException("db down")).when(tradeDecisionService).save(any());

            assertDoesNotThrow(() -> {
                var result = scanner.analyseProduct(p, 0L, 1L, Granularity.ONE_HOUR, 1000.0);
                assertThat(result).isNotNull();
            });

            verify(tradeDecisionService, times(1)).save(any());
        }
    }
}