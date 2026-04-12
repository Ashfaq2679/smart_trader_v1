package com.techcobber.smarttrader.v1.scheduler;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.model.common.Granularity;
import com.techcobber.smarttrader.v1.models.CoinScanResult;
import com.techcobber.smarttrader.v1.models.ListCandles;
import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.services.CoinbaseClientFactory;
import com.techcobber.smarttrader.v1.services.CoinbasePublicServiceImpl;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MarketScanScheduler}.
 * Uses Mockito — no database, exchange API, or credentials required.
 */
@ExtendWith(MockitoExtension.class)
class MarketScanSchedulerTest {

	@Mock
	private CoinbaseClientFactory coinbaseClientFactory;

	@Mock
	private CoinbaseAdvancedClient mockClient;

	private CircuitBreakerRegistry circuitBreakerRegistry;

	private MarketScanScheduler scheduler;

	@BeforeEach
	void setUp() {
		circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
		scheduler = new MarketScanScheduler(coinbaseClientFactory, circuitBreakerRegistry);
	}

	// =======================================================================
	// getLatestResults
	// =======================================================================

	@Nested
	@DisplayName("getLatestResults")
	class GetLatestResultsTests {

		@Test
		@DisplayName("Returns empty list before any scan has run")
		void returnsEmptyBeforeAnyScan() {
			assertThat(scheduler.getLatestResults()).isEmpty();
		}
	}

	// =======================================================================
	// scheduledScan
	// =======================================================================

	@Nested
	@DisplayName("scheduledScan")
	class ScheduledScanTests {

		@Test
		@DisplayName("Does not throw and does not require credentials")
		void doesNotThrow() {
			// The scheduled scan is a no-op without an explicit userId
			scheduler.scheduledScan();

			// Verify it didn't try to use the factory
			verifyNoInteractions(coinbaseClientFactory);
		}

		@Test
		@DisplayName("Latest results remain empty after scheduled scan")
		void latestResultsRemainEmpty() {
			scheduler.scheduledScan();
			assertThat(scheduler.getLatestResults()).isEmpty();
		}
	}

	// =======================================================================
	// runScanNow
	// =======================================================================

	@Nested
	@DisplayName("runScanNow")
	class RunScanNowTests {

		@Test
		@DisplayName("Throws IllegalArgumentException for unknown user")
		void throwsForUnknownUser() {
			when(coinbaseClientFactory.getClientForUser("unknown"))
					.thenThrow(new IllegalArgumentException("No credentials found for user: unknown"));

			assertThatThrownBy(() -> scheduler.runScanNow("unknown", 5))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("No credentials found");
		}

		@Test
		@DisplayName("Delegates to factory to get client for user")
		void delegatesToFactory() throws Exception {
			when(coinbaseClientFactory.getClientForUser("user-1")).thenReturn(mockClient);

			// The scan will fail because the mock client doesn't respond to API calls,
			// but we can verify the factory was invoked correctly.
			try {
				scheduler.runScanNow("user-1", 5);
			} catch (Exception e) {
				// Expected — mock client doesn't implement real API
			}

			verify(coinbaseClientFactory).getClientForUser("user-1");
		}
	}

	// =======================================================================
	// fetchCandlesForProduct
	// =======================================================================

	@Nested
	@DisplayName("fetchCandlesForProduct")
	class FetchCandlesTests {

		@Test
		@DisplayName("Throws IllegalArgumentException for unknown user")
		void throwsForUnknownUser() {
			when(coinbaseClientFactory.getClientForUser("unknown"))
					.thenThrow(new IllegalArgumentException("No credentials found for user: unknown"));

			assertThatThrownBy(() -> scheduler.fetchCandlesForProduct("unknown", "BTC-USDC"))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("Returns empty list when no candle data")
		void returnsEmptyWhenNoCandleData() throws Exception {
			MarketScanScheduler spyScheduler = spy(scheduler);
			CoinbasePublicServiceImpl mockPublicService = mock(CoinbasePublicServiceImpl.class);
			doReturn(mockPublicService).when(spyScheduler).createPublicService("user-1");
			when(mockPublicService.fetchCandles(eq("BTC-USDC"), anyLong(), anyLong(), any(Granularity.class)))
					.thenReturn(null);

			List<MyCandle> result = spyScheduler.fetchCandlesForProduct("user-1", "BTC-USDC");

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("Returns candles when data is available")
		void returnsCandlesWhenAvailable() throws Exception {
			MarketScanScheduler spyScheduler = spy(scheduler);
			CoinbasePublicServiceImpl mockPublicService = mock(CoinbasePublicServiceImpl.class);
			doReturn(mockPublicService).when(spyScheduler).createPublicService("user-1");

			ListCandles listCandles = new ListCandles();
			List<MyCandle> candles = List.of(
					new MyCandle.Builder().open(100).close(102).high(103).low(99).start(1).volume(1000).build(),
					new MyCandle.Builder().open(102).close(104).high(105).low(101).start(2).volume(1200).build());
			listCandles.setCandles(candles);

			when(mockPublicService.fetchCandles(eq("BTC-USDC"), anyLong(), anyLong(), any(Granularity.class)))
					.thenReturn(listCandles);

			List<MyCandle> result = spyScheduler.fetchCandlesForProduct("user-1", "BTC-USDC");

			assertThat(result).hasSize(2);
			assertThat(result.get(0).getOpen()).isEqualTo(100.0);
		}

		@Test
		@DisplayName("Returns empty list when ListCandles has null candles list")
		void returnsEmptyWhenCandlesListNull() throws Exception {
			MarketScanScheduler spyScheduler = spy(scheduler);
			CoinbasePublicServiceImpl mockPublicService = mock(CoinbasePublicServiceImpl.class);
			doReturn(mockPublicService).when(spyScheduler).createPublicService("user-1");

			ListCandles listCandles = new ListCandles();
			listCandles.setCandles(null);

			when(mockPublicService.fetchCandles(eq("BTC-USDC"), anyLong(), anyLong(), any(Granularity.class)))
					.thenReturn(listCandles);

			List<MyCandle> result = spyScheduler.fetchCandlesForProduct("user-1", "BTC-USDC");

			assertThat(result).isEmpty();
		}
	}

	// =======================================================================
	// createPublicService
	// =======================================================================

	@Nested
	@DisplayName("createPublicService")
	class CreatePublicServiceTests {

		@Test
		@DisplayName("Returns a CoinbasePublicServiceImpl bound to user's client")
		void returnsPublicServiceForUser() {
			when(coinbaseClientFactory.getClientForUser("user-1")).thenReturn(mockClient);

			CoinbasePublicServiceImpl result = scheduler.createPublicService("user-1");

			assertThat(result).isNotNull();
			verify(coinbaseClientFactory).getClientForUser("user-1");
		}
	}
}
