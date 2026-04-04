package com.techcobber.smarttrader.v1.controllers;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.techcobber.smarttrader.v1.models.CoinScanResult;
import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.scheduler.MarketScanScheduler;
import com.techcobber.smarttrader.v1.services.TradingOrchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MarketScanController}.
 * Uses Mockito — no database, exchange API, or credentials required.
 */
@ExtendWith(MockitoExtension.class)
class MarketScanControllerTest {

	@Mock
	private MarketScanScheduler marketScanScheduler;

	@Mock
	private TradingOrchestrator tradingOrchestrator;

	@InjectMocks
	private MarketScanController controller;

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private static CoinScanResult sampleResult(String productId, double score) {
		TradeDecision decision = TradeDecision.builder()
				.signal(Signal.BUY)
				.confidence(0.8)
				.reasoning("Test")
				.trendDirection("UP")
				.detectedPatterns(List.of("Bullish Engulfing"))
				.productId(productId)
				.build();

		return CoinScanResult.builder()
				.productId(productId)
				.tradeDecision(decision)
				.profitPotentialScore(score)
				.summary("test summary")
				.scannedAt(LocalDateTime.now())
				.build();
	}

	private static List<MyCandle> sampleCandles() {
		List<MyCandle> candles = new java.util.ArrayList<>();
		for (int i = 0; i < 25; i++) {
			candles.add(new MyCandle.Builder()
					.open(100 + i).close(101 + i).high(103 + i).low(99 + i)
					.start(i).volume(1000)
					.build());
		}
		return candles;
	}

	// =======================================================================
	// triggerScan
	// =======================================================================

	@Nested
	@DisplayName("POST /scan — triggerScan")
	class TriggerScanTests {

		@Test
		@DisplayName("Returns scan results on success")
		@SuppressWarnings("unchecked")
		void returnsResultsOnSuccess() throws Exception {
			List<CoinScanResult> results = List.of(
					sampleResult("BTC-USDC", 75.0),
					sampleResult("ETH-USDC", 68.0));
			when(marketScanScheduler.runScanNow("user-1", 10)).thenReturn(results);

			ResponseEntity<?> response = controller.triggerScan("user-1", 10);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			List<CoinScanResult> body = (List<CoinScanResult>) response.getBody();
			assertThat(body).hasSize(2);
			assertThat(body.get(0).getProductId()).isEqualTo("BTC-USDC");
		}

		@Test
		@DisplayName("Returns 400 on IllegalArgumentException (e.g. missing credentials)")
		void returnsBadRequestOnMissingCredentials() throws Exception {
			when(marketScanScheduler.runScanNow("bad-user", 5))
					.thenThrow(new IllegalArgumentException("No credentials found for user: bad-user"));

			ResponseEntity<?> response = controller.triggerScan("bad-user", 5);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}

		@Test
		@DisplayName("Returns 500 on exchange API failure")
		@SuppressWarnings("unchecked")
		void returnsServerErrorOnApiFailure() throws Exception {
			when(marketScanScheduler.runScanNow("user-1", 10))
					.thenThrow(new CoinbaseAdvancedException(500, "API error"));

			ResponseEntity<?> response = controller.triggerScan("user-1", 10);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			Map<String, String> body = (Map<String, String>) response.getBody();
			assertThat(body.get("error")).contains("API error");
		}
	}

	// =======================================================================
	// getLatestResults
	// =======================================================================

	@Nested
	@DisplayName("GET /results — getLatestResults")
	class GetLatestResultsTests {

		@Test
		@DisplayName("Returns cached results")
		void returnsCachedResults() {
			List<CoinScanResult> cached = List.of(sampleResult("SOL-USDC", 60.0));
			when(marketScanScheduler.getLatestResults()).thenReturn(cached);

			ResponseEntity<List<CoinScanResult>> response = controller.getLatestResults();

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).hasSize(1);
		}

		@Test
		@DisplayName("Returns empty list when no scan has run")
		void returnsEmptyWhenNoScan() {
			when(marketScanScheduler.getLatestResults()).thenReturn(Collections.emptyList());

			ResponseEntity<List<CoinScanResult>> response = controller.getLatestResults();

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isEmpty();
		}
	}

	// =======================================================================
	// analyzeProduct
	// =======================================================================

	@Nested
	@DisplayName("GET /analyze/{productId} — analyzeProduct")
	class AnalyzeProductTests {

		@Test
		@DisplayName("Returns trade decision on success")
		void returnsDecisionOnSuccess() throws Exception {
			List<MyCandle> candles = sampleCandles();
			when(marketScanScheduler.fetchCandlesForProduct("user-1", "BTC-USDC"))
					.thenReturn(candles);
			TradeDecision decision = TradeDecision.builder()
					.signal(Signal.BUY).confidence(0.75).reasoning("Test")
					.trendDirection("UP").productId("BTC-USDC")
					.detectedPatterns(List.of()).build();
			when(tradingOrchestrator.executeAnalysis(candles, "BTC-USDC"))
					.thenReturn(decision);

			ResponseEntity<?> response = controller.analyzeProduct("BTC-USDC", "user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isInstanceOf(TradeDecision.class);
			assertThat(((TradeDecision) response.getBody()).getProductId()).isEqualTo("BTC-USDC");
		}

		@Test
		@DisplayName("Returns 400 when no candle data available")
		void returnsBadRequestWhenNoCandles() throws Exception {
			when(marketScanScheduler.fetchCandlesForProduct("user-1", "UNKNOWN-USDC"))
					.thenReturn(Collections.emptyList());

			ResponseEntity<?> response = controller.analyzeProduct("UNKNOWN-USDC", "user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			verifyNoInteractions(tradingOrchestrator);
		}

		@Test
		@DisplayName("Returns 400 when candles are null")
		void returnsBadRequestWhenCandlesNull() throws Exception {
			when(marketScanScheduler.fetchCandlesForProduct("user-1", "X-USDC"))
					.thenReturn(null);

			ResponseEntity<?> response = controller.analyzeProduct("X-USDC", "user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}

		@Test
		@DisplayName("Returns 400 on missing credentials")
		void returnsBadRequestOnMissingCredentials() throws Exception {
			when(marketScanScheduler.fetchCandlesForProduct("bad-user", "BTC-USDC"))
					.thenThrow(new IllegalArgumentException("No credentials found for user: bad-user"));

			ResponseEntity<?> response = controller.analyzeProduct("BTC-USDC", "bad-user");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}

		@Test
		@DisplayName("Returns 500 on exchange API failure")
		void returnsServerErrorOnApiFailure() throws Exception {
			when(marketScanScheduler.fetchCandlesForProduct("user-1", "BTC-USDC"))
					.thenThrow(new CoinbaseAdvancedException(500, "timeout"));

			ResponseEntity<?> response = controller.analyzeProduct("BTC-USDC", "user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
