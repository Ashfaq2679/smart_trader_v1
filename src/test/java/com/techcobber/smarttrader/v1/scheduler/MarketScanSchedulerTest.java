package com.techcobber.smarttrader.v1.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.model.common.Granularity;
import com.techcobber.smarttrader.v1.models.ListCandles;
import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.OrderResponse;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.models.User;
import com.techcobber.smarttrader.v1.models.UserPreferences;
import com.techcobber.smarttrader.v1.repositories.CoinsRepository;
import com.techcobber.smarttrader.v1.repositories.UserPreferencesRepository;
import com.techcobber.smarttrader.v1.services.CoinbaseClientFactory;
import com.techcobber.smarttrader.v1.services.CoinbasePublicServiceImpl;
import com.techcobber.smarttrader.v1.services.OrderService;
import com.techcobber.smarttrader.v1.services.TradeDecisionService;
import com.techcobber.smarttrader.v1.services.TradingOrchestrator;
import com.techcobber.smarttrader.v1.services.UserService;
import com.techcobber.smarttrader.v1.strategy.RiskManager.RiskAssessment;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Unit tests for {@link MarketScanScheduler}. Uses Mockito — no database,
 * exchange API, or credentials required.
 */
@ExtendWith(MockitoExtension.class)
class MarketScanSchedulerTest {

	@Mock
	private CoinbaseClientFactory coinbaseClientFactory;

	@Mock
	private CoinbaseAdvancedClient mockClient;

	@Mock
	private TradingOrchestrator tradingOrchestrator;

	@Mock
	private CoinsRepository coinsRepository;

	@Mock
	private TradeDecisionService tradeDecisionService;

	@Mock
	private OrderService orderService;

	@Mock
	private UserPreferencesRepository userPreferencesRepository;

	@Mock
	private UserService userService;

	private CircuitBreakerRegistry circuitBreakerRegistry;
	private MarketScanScheduler scheduler;

	@BeforeEach
	void setUp() {
		circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
		scheduler = new MarketScanScheduler(coinbaseClientFactory, circuitBreakerRegistry,
				tradingOrchestrator, coinsRepository, tradeDecisionService, orderService,
				userPreferencesRepository, userService);
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

			assertThatThrownBy(() -> scheduler.runScanNow("unknown", 5)).isInstanceOf(IllegalArgumentException.class)
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

	// =======================================================================
	// scheduledCandleFetch — order-placement gating
	// =======================================================================

	@Nested
	@DisplayName("scheduledCandleFetch order-placement gating")
	class ScheduledCandleFetchTests {

		/** Builds a minimal approved RiskAssessment. */
		private RiskAssessment approvedRisk() {
			return new RiskAssessment(10.0, 95.0, 106.0, 2.2, true,
					"approved", 94.0, 97.0);
		}

		/** Builds a minimal rejected RiskAssessment (R:R too low). */
		private RiskAssessment rejectedRisk(String reason) {
			return new RiskAssessment(0, 95.0, 101.0, 0.9, false,
					reason, 94.0, 0.0);
		}

		private TradeDecision buyDecision() {
			return TradeDecision.builder()
					.productId("BTC-USDC").signal(Signal.BUY)
					.confidence(0.82).suggestedPrice(100.0).build();
		}

		private TradeDecision holdDecision() {
			return TradeDecision.builder()
					.productId("BTC-USDC").signal(Signal.HOLD)
					.confidence(0.5).build();
		}

		/** One product coin visible to the scheduler. */
		private com.techcobber.smarttrader.v1.models.CoinDocument btcCoin() {
			return new com.techcobber.smarttrader.v1.models.CoinDocument("BTC", 1, "BTC-USDC");
		}

		/** User with £1 000 balance. */
		private User aUser() {
			User u = new User();
			u.setCurrentFunds(1000.0);
			return u;
		}

		/** Wire up the mocks that every test in this class needs. */
		private void wireCommon() {
			when(coinsRepository.findAll()).thenReturn(List.of(btcCoin()));
			when(userPreferencesRepository.findByUserId(any())).thenReturn(Optional.empty());
			when(userService.findByUserName(any())).thenReturn(aUser());
		}

		@Test
		@DisplayName("Places order when risk is approved for a BUY signal")
		void placesOrderOnApprovedBuy() {
			wireCommon();
			when(tradingOrchestrator.executeWithRisk(any(), anyString(), any(), anyDouble()))
					.thenReturn(Map.of("decision", buyDecision(), "riskAssessment", approvedRisk()));
			when(orderService.placeOrder(any(), any()))
					.thenReturn(OrderResponse.builder().success(true).build());

			scheduler.scheduledCandleFetch();

			verify(orderService).placeOrder(any(), any());
		}

		@Test
		@DisplayName("Does NOT place order when risk is rejected")
		void doesNotPlaceOrderOnRejectedRisk() {
			wireCommon();
			when(tradingOrchestrator.executeWithRisk(any(), anyString(), any(), anyDouble()))
					.thenReturn(Map.of("decision", buyDecision(),
							"riskAssessment", rejectedRisk("BUY rejected: R:R 0.90 < required 2.0")));

			scheduler.scheduledCandleFetch();

			verify(orderService, never()).placeOrder(any(), any());
		}

		@Test
		@DisplayName("Does NOT place order when signal is HOLD even if risk would pass")
		void doesNotPlaceOrderOnHoldSignal() {
			wireCommon();
			// Risk manager returns nothing for HOLD (not in the result map)
			when(tradingOrchestrator.executeWithRisk(any(), anyString(), any(), anyDouble()))
					.thenReturn(Map.of("decision", holdDecision()));

			scheduler.scheduledCandleFetch();

			verify(orderService, never()).placeOrder(any(), any());
		}

		@Test
		@DisplayName("Does NOT place order when no riskAssessment is present in result")
		void doesNotPlaceOrderWhenRiskAssessmentAbsent() {
			wireCommon();
			// riskAssessment key absent — signal is BUY but risk was skipped
			when(tradingOrchestrator.executeWithRisk(any(), anyString(), any(), anyDouble()))
					.thenReturn(Map.of("decision", buyDecision()));

			scheduler.scheduledCandleFetch();

			verify(orderService, never()).placeOrder(any(), any());
		}
	}
}
