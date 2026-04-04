package com.techcobber.smarttrader.v1.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.model.common.Granularity;
import com.coinbase.advanced.model.products.ListProductsResponse;
import com.coinbase.advanced.model.products.Product;
import com.techcobber.smarttrader.v1.models.CoinScanResult;
import com.techcobber.smarttrader.v1.models.ListCandles;
import com.techcobber.smarttrader.v1.models.MyCandle;

/**
 * Unit tests for {@link MarketScannerService}.
 *
 * <p><b>Testing strategy:</b> The {@link CoinbasePublicServiceImpl} dependency is
 * created as a spy so that {@code getFilteredProducts()} and {@code fetchCandles()}
 * can be stubbed without making real API calls.</p>
 */
@ExtendWith(MockitoExtension.class)
class MarketScannerServiceTest {

	@Mock
	private CoinbaseAdvancedClient mockClient;

	private CoinbasePublicServiceImpl publicServiceSpy;
	private MarketScannerService scanner;

	@BeforeEach
	void setUp() {
		publicServiceSpy = Mockito.spy(new CoinbasePublicServiceImpl(mockClient));
		scanner = new MarketScannerService(publicServiceSpy);
	}

	// ------------------------------------------------------------------
	// Helper methods
	// ------------------------------------------------------------------

	private static Product createActiveProduct(String productId, String volume24h,
			String volumeChange, String priceChange, String price) {
		Product product = new Product();
		product.setProductId(productId);
		product.setVolume24h(volume24h);
		product.setVolumePercentageChange24h(volumeChange);
		product.setPricePercentageChange24h(priceChange);
		product.setPrice(price);
		product.setTradingDisabled(false);
		product.setDisabled(false);
		product.setCancelOnly(false);
		product.setViewOnly(false);
		return product;
	}

	private static ListProductsResponse buildResponse(List<Product> products) {
		ListProductsResponse response = new ListProductsResponse();
		response.setProducts(products);
		return response;
	}

	private static MyCandle candle(double open, double close, double high, double low,
			long start, double volume) {
		return new MyCandle.Builder()
				.open(open).close(close).high(high).low(low)
				.start(start).volume(volume)
				.build();
	}

	/**
	 * Creates 25+ candles simulating an uptrend with increasing volume.
	 */
	private static List<MyCandle> uptrendCandles() {
		List<MyCandle> candles = new ArrayList<>();
		double base = 100;
		for (int i = 0; i < 25; i++) {
			double drift = i * 1.5;
			double open = base + drift;
			double close = open + 1.5;
			double high = close + 1;
			double low = open - 1;
			candles.add(candle(open, close, high, low, i, 1000 + i * 100));
		}
		return candles;
	}

	private static ListCandles wrapCandles(List<MyCandle> candles) {
		ListCandles lc = new ListCandles();
		lc.setCandles(candles);
		return lc;
	}

	// =======================================================================
	// scanPairs Tests
	// =======================================================================

	@Nested
	@DisplayName("scanPairs")
	class ScanPairsTests {

		@Test
		@DisplayName("Returns empty list when no products found")
		void returnsEmptyWhenNoProducts() throws CoinbaseAdvancedException {
			doReturn(buildResponse(List.of())).when(publicServiceSpy).listPublicProducts();

			List<CoinScanResult> results = scanner.scanPairs("USDC", Granularity.ONE_HOUR, 10);

			assertThat(results).isEmpty();
		}

		@Test
		@DisplayName("Filters out disabled products")
		void filtersDisabledProducts() throws CoinbaseAdvancedException {
			Product disabled = createActiveProduct("DISABLED-USDC", "1000", "5", "2", "10");
			disabled.setDisabled(true);

			Product active = createActiveProduct("BTC-USDC", "50000", "10", "3", "65000");

			doReturn(buildResponse(List.of(disabled, active))).when(publicServiceSpy).listPublicProducts();
			doReturn(wrapCandles(uptrendCandles()))
					.when(publicServiceSpy).fetchCandles(eq("BTC-USDC"), anyLong(), anyLong(),
							eq(Granularity.ONE_HOUR));

			List<CoinScanResult> results = scanner.scanPairs("USDC", Granularity.ONE_HOUR, 10);

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getProductId()).isEqualTo("BTC-USDC");
		}

		@Test
		@DisplayName("Filters out trading-disabled products")
		void filtersTradingDisabledProducts() throws CoinbaseAdvancedException {
			Product tradingOff = createActiveProduct("XRP-USDC", "2000", "3", "1", "0.5");
			tradingOff.setTradingDisabled(true);

			Product active = createActiveProduct("ETH-USDC", "30000", "8", "4", "3500");

			doReturn(buildResponse(List.of(tradingOff, active))).when(publicServiceSpy).listPublicProducts();
			doReturn(wrapCandles(uptrendCandles()))
					.when(publicServiceSpy).fetchCandles(eq("ETH-USDC"), anyLong(), anyLong(),
							eq(Granularity.ONE_HOUR));

			List<CoinScanResult> results = scanner.scanPairs("USDC", Granularity.ONE_HOUR, 10);

			assertThat(results).noneMatch(r -> r.getProductId().equals("XRP-USDC"));
		}

		@Test
		@DisplayName("Filters out products without USDC suffix")
		void filtersNonUSDCSuffix() throws CoinbaseAdvancedException {
			Product usdcMiddle = createActiveProduct("USDC-BTC", "5000", "5", "2", "0.00002");
			Product correctPair = createActiveProduct("SOL-USDC", "20000", "15", "8", "150");

			doReturn(buildResponse(List.of(usdcMiddle, correctPair))).when(publicServiceSpy).listPublicProducts();
			doReturn(wrapCandles(uptrendCandles()))
					.when(publicServiceSpy).fetchCandles(eq("SOL-USDC"), anyLong(), anyLong(),
							eq(Granularity.ONE_HOUR));

			List<CoinScanResult> results = scanner.scanPairs("USDC", Granularity.ONE_HOUR, 10);

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getProductId()).isEqualTo("SOL-USDC");
		}

		@Test
		@DisplayName("Filters out zero-volume products")
		void filtersZeroVolume() throws CoinbaseAdvancedException {
			Product zeroVol = createActiveProduct("DOGE-USDC", "0", "0", "0", "0.1");
			Product active = createActiveProduct("BTC-USDC", "50000", "10", "3", "65000");

			doReturn(buildResponse(List.of(zeroVol, active))).when(publicServiceSpy).listPublicProducts();
			doReturn(wrapCandles(uptrendCandles()))
					.when(publicServiceSpy).fetchCandles(eq("BTC-USDC"), anyLong(), anyLong(),
							eq(Granularity.ONE_HOUR));

			List<CoinScanResult> results = scanner.scanPairs("USDC", Granularity.ONE_HOUR, 10);

			assertThat(results).noneMatch(r -> r.getProductId().equals("DOGE-USDC"));
		}

		@Test
		@DisplayName("Results are sorted by score descending")
		void resultsSortedByScoreDescending() throws CoinbaseAdvancedException {
			Product lowVol = createActiveProduct("DOGE-USDC", "100", "1", "0.5", "0.1");
			Product highVol = createActiveProduct("BTC-USDC", "99999", "50", "10", "65000");
			Product midVol = createActiveProduct("ETH-USDC", "5000", "20", "5", "3500");

			doReturn(buildResponse(List.of(lowVol, highVol, midVol))).when(publicServiceSpy).listPublicProducts();

			doReturn(wrapCandles(uptrendCandles()))
					.when(publicServiceSpy).fetchCandles(anyString(), anyLong(), anyLong(),
							eq(Granularity.ONE_HOUR));

			List<CoinScanResult> results = scanner.scanPairs("USDC", Granularity.ONE_HOUR, 10);

			assertThat(results).hasSizeGreaterThanOrEqualTo(2);
			for (int i = 0; i < results.size() - 1; i++) {
				assertThat(results.get(i).getProfitPotentialScore())
						.isGreaterThanOrEqualTo(results.get(i + 1).getProfitPotentialScore());
			}
		}

		@Test
		@DisplayName("Respects limit parameter")
		void respectsLimitParameter() throws CoinbaseAdvancedException {
			List<Product> products = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				products.add(createActiveProduct("COIN" + i + "-USDC",
						String.valueOf(1000 + i * 100), "5", "2", "10"));
			}

			doReturn(buildResponse(products)).when(publicServiceSpy).listPublicProducts();
			doReturn(wrapCandles(uptrendCandles()))
					.when(publicServiceSpy).fetchCandles(anyString(), anyLong(), anyLong(),
							eq(Granularity.ONE_HOUR));

			List<CoinScanResult> results = scanner.scanPairs("USDC", Granularity.ONE_HOUR, 3);

			assertThat(results).hasSize(3);
		}

		@Test
		@DisplayName("Skips products with no candle data")
		void skipsProductsWithNoCandles() throws CoinbaseAdvancedException {
			Product noCandleProduct = createActiveProduct("RARE-USDC", "500", "2", "1", "5");
			Product goodProduct = createActiveProduct("BTC-USDC", "50000", "10", "3", "65000");

			doReturn(buildResponse(List.of(noCandleProduct, goodProduct))).when(publicServiceSpy).listPublicProducts();

			ListCandles emptyCandles = new ListCandles();
			emptyCandles.setCandles(List.of());
			doReturn(emptyCandles).when(publicServiceSpy).fetchCandles(
					eq("RARE-USDC"), anyLong(), anyLong(), eq(Granularity.ONE_HOUR));
			doReturn(wrapCandles(uptrendCandles())).when(publicServiceSpy).fetchCandles(
					eq("BTC-USDC"), anyLong(), anyLong(), eq(Granularity.ONE_HOUR));

			List<CoinScanResult> results = scanner.scanPairs("USDC", Granularity.ONE_HOUR, 10);

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getProductId()).isEqualTo("BTC-USDC");
		}

		@Test
		@DisplayName("Continues scanning when individual product fetch fails")
		void continuesOnFetchFailure() throws CoinbaseAdvancedException {
			Product failProduct = createActiveProduct("FAIL-USDC", "500", "2", "1", "5");
			Product goodProduct = createActiveProduct("BTC-USDC", "50000", "10", "3", "65000");

			doReturn(buildResponse(List.of(failProduct, goodProduct))).when(publicServiceSpy).listPublicProducts();

			doThrow(new CoinbaseAdvancedException("API error"))
					.when(publicServiceSpy).fetchCandles(
							eq("FAIL-USDC"), anyLong(), anyLong(), eq(Granularity.ONE_HOUR));
			doReturn(wrapCandles(uptrendCandles()))
					.when(publicServiceSpy).fetchCandles(
							eq("BTC-USDC"), anyLong(), anyLong(), eq(Granularity.ONE_HOUR));

			List<CoinScanResult> results = scanner.scanPairs("USDC", Granularity.ONE_HOUR, 10);

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getProductId()).isEqualTo("BTC-USDC");
		}

		@Test
		@DisplayName("Each result contains required fields")
		void resultsContainRequiredFields() throws CoinbaseAdvancedException {
			Product product = createActiveProduct("BTC-USDC", "50000", "10", "3", "65000");

			doReturn(buildResponse(List.of(product))).when(publicServiceSpy).listPublicProducts();
			doReturn(wrapCandles(uptrendCandles()))
					.when(publicServiceSpy).fetchCandles(eq("BTC-USDC"), anyLong(), anyLong(),
							eq(Granularity.ONE_HOUR));

			List<CoinScanResult> results = scanner.scanPairs("USDC", Granularity.ONE_HOUR, 10);

			assertThat(results).hasSize(1);
			CoinScanResult result = results.get(0);
			assertThat(result.getProductId()).isEqualTo("BTC-USDC");
			assertThat(result.getTradeDecision()).isNotNull();
			assertThat(result.getVolume24h()).isEqualTo(50000.0);
			assertThat(result.getVolumeChangePercent24h()).isEqualTo(10.0);
			assertThat(result.getPriceChangePercent24h()).isEqualTo(3.0);
			assertThat(result.getCurrentPrice()).isEqualTo(65000.0);
			assertThat(result.getProfitPotentialScore()).isGreaterThanOrEqualTo(0.0);
			assertThat(result.getSummary()).isNotEmpty();
			assertThat(result.getScannedAt()).isNotNull();
		}
	}

	// =======================================================================
	// scanUSDCPairs Tests
	// =======================================================================

	@Nested
	@DisplayName("scanUSDCPairs")
	class ScanUSDCPairsTests {

		@Test
		@DisplayName("Delegates to scanPairs with USDC and ONE_HOUR")
		void delegatesToScanPairs() throws CoinbaseAdvancedException {
			Product product = createActiveProduct("BTC-USDC", "50000", "10", "3", "65000");

			doReturn(buildResponse(List.of(product))).when(publicServiceSpy).listPublicProducts();
			doReturn(wrapCandles(uptrendCandles()))
					.when(publicServiceSpy).fetchCandles(eq("BTC-USDC"), anyLong(), anyLong(),
							eq(Granularity.ONE_HOUR));

			List<CoinScanResult> results = scanner.scanUSDCPairs(5);

			assertThat(results).isNotEmpty();
			assertThat(results.get(0).getProductId()).endsWith("-USDC");
		}
	}

	// =======================================================================
	// computeMedianVolume Tests
	// =======================================================================

	@Nested
	@DisplayName("computeMedianVolume")
	class ComputeMedianVolumeTests {

		@Test
		@DisplayName("Returns median for odd number of products")
		void medianOddCount() {
			List<Product> products = List.of(
					createActiveProduct("A-USDC", "300", "0", "0", "1"),
					createActiveProduct("B-USDC", "100", "0", "0", "1"),
					createActiveProduct("C-USDC", "200", "0", "0", "1"));

			double median = scanner.computeMedianVolume(products);

			assertThat(median).isEqualTo(200.0);
		}

		@Test
		@DisplayName("Returns average of two middle for even count")
		void medianEvenCount() {
			List<Product> products = List.of(
					createActiveProduct("A-USDC", "100", "0", "0", "1"),
					createActiveProduct("B-USDC", "200", "0", "0", "1"),
					createActiveProduct("C-USDC", "300", "0", "0", "1"),
					createActiveProduct("D-USDC", "400", "0", "0", "1"));

			double median = scanner.computeMedianVolume(products);

			assertThat(median).isEqualTo(250.0);
		}

		@Test
		@DisplayName("Returns zero for empty product list")
		void zeroForEmpty() {
			double median = scanner.computeMedianVolume(List.of());

			assertThat(median).isEqualTo(0.0);
		}

		@Test
		@DisplayName("Ignores products with zero or null volume")
		void ignoresZeroVolume() {
			List<Product> products = List.of(
					createActiveProduct("A-USDC", "0", "0", "0", "1"),
					createActiveProduct("B-USDC", null, "0", "0", "1"),
					createActiveProduct("C-USDC", "500", "0", "0", "1"));

			double median = scanner.computeMedianVolume(products);

			assertThat(median).isEqualTo(500.0);
		}
	}

	// =======================================================================
	// parseDoubleOrZero Tests
	// =======================================================================

	@Nested
	@DisplayName("parseDoubleOrZero")
	class ParseDoubleOrZeroTests {

		@Test
		@DisplayName("Parses valid double string")
		void parsesValidDouble() {
			assertThat(MarketScannerService.parseDoubleOrZero("123.45")).isEqualTo(123.45);
		}

		@Test
		@DisplayName("Returns zero for null")
		void zeroForNull() {
			assertThat(MarketScannerService.parseDoubleOrZero(null)).isEqualTo(0.0);
		}

		@Test
		@DisplayName("Returns zero for empty string")
		void zeroForEmpty() {
			assertThat(MarketScannerService.parseDoubleOrZero("")).isEqualTo(0.0);
		}

		@Test
		@DisplayName("Returns zero for invalid string")
		void zeroForInvalid() {
			assertThat(MarketScannerService.parseDoubleOrZero("not-a-number")).isEqualTo(0.0);
		}
	}

	// =======================================================================
	// granularityToSeconds Tests
	// =======================================================================

	@Nested
	@DisplayName("granularityToSeconds")
	class GranularityToSecondsTests {

		@Test
		@DisplayName("ONE_MINUTE = 60 seconds")
		void oneMinute() {
			assertThat(MarketScannerService.granularityToSeconds(Granularity.ONE_MINUTE)).isEqualTo(60L);
		}

		@Test
		@DisplayName("FIVE_MINUTE = 300 seconds")
		void fiveMinute() {
			assertThat(MarketScannerService.granularityToSeconds(Granularity.FIVE_MINUTE)).isEqualTo(300L);
		}

		@Test
		@DisplayName("ONE_HOUR = 3600 seconds")
		void oneHour() {
			assertThat(MarketScannerService.granularityToSeconds(Granularity.ONE_HOUR)).isEqualTo(3600L);
		}

		@Test
		@DisplayName("ONE_DAY = 86400 seconds")
		void oneDay() {
			assertThat(MarketScannerService.granularityToSeconds(Granularity.ONE_DAY)).isEqualTo(86400L);
		}
	}
}
