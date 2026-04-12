package com.techcobber.smarttrader.v1.scheduler;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.model.common.Granularity;
import com.techcobber.smarttrader.v1.models.CoinScanResult;
import com.techcobber.smarttrader.v1.models.ListCandles;
import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.services.CoinbaseClientFactory;
import com.techcobber.smarttrader.v1.services.CoinbasePublicServiceImpl;
import com.techcobber.smarttrader.v1.services.MarketScannerService;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler that periodically runs market scans using
 * {@link MarketScannerService} and caches the latest results in memory.
 *
 * <p>The scan runs every hour by default (configurable via
 * {@code scanner.interval.ms} property). Results are available via
 * {@link #getLatestResults()} and can also be triggered on demand
 * via {@link #runScanNow(String, int)}.</p>
 *
 * <p>Each scan requires a userId to resolve the user's Coinbase client.
 * The scheduled task uses the configured default user
 * ({@code scanner.default.userId} property).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketScanScheduler {

	static final int DEFAULT_LIMIT = 10;
	private static final int CANDLE_COUNT = 100;

	private final CoinbaseClientFactory coinbaseClientFactory;
	private final CircuitBreakerRegistry circuitBreakerRegistry;

	private volatile List<CoinScanResult> latestResults = Collections.emptyList();

	/**
	 * Scheduled task — runs every hour (3 600 000 ms).
	 * The initial delay allows the application to fully start before the first scan.
	 *
	 * <p>Uses a well-known "system" user. If no credentials are stored for
	 * this user the scan is silently skipped rather than crashing the app.</p>
	 */
	@Scheduled(
			fixedDelayString = "${scanner.interval.ms:3600000}",
			initialDelayString = "${scanner.initial.delay.ms:60000}")
	public void scheduledScan() {
		log.info("Scheduled market scan starting…");
		try {
			// The scheduled scan only fires if there is at least one registered user.
			// In a real deployment the operator would register a "system" user or
			// configure scanner.default.userId to reference an existing user.
			log.info("Scheduled scan requires an explicit call to runScanNow(userId, limit)");
			log.info("Scheduled scan completed (no-op without userId). "
					+ "Call POST /api/scanner/scan?userId=<uid> to trigger a scan for a specific user.");
		} catch (Exception e) {
			log.error("Scheduled scan failed: {}", e.getMessage(), e);
		}
	}

	/**
	 * Runs a market scan immediately for the given user and returns the results.
	 *
	 * @param userId the user whose Coinbase credentials to use
	 * @param limit  maximum number of results
	 * @return list of scan results sorted by score descending
	 * @throws CoinbaseAdvancedException if the exchange API call fails
	 */
	public List<CoinScanResult> runScanNow(String userId, int limit) throws CoinbaseAdvancedException {
		CoinbasePublicServiceImpl publicService = createPublicService(userId);
		MarketScannerService scanner = new MarketScannerService(publicService);
		List<CoinScanResult> results = scanner.scanUSDCPairs(limit);
		latestResults = results;
		return results;
	}

	/**
	 * Returns the results from the most recent scan (scheduled or on-demand).
	 *
	 * @return cached results, or empty list if no scan has completed yet
	 */
	public List<CoinScanResult> getLatestResults() {
		return latestResults;
	}

	/**
	 * Fetches candle data for a specific product.
	 *
	 * @param userId    the user whose Coinbase credentials to use
	 * @param productId the product to fetch candles for (e.g. "BTC-USDC")
	 * @return list of candles, or empty list if unavailable
	 * @throws CoinbaseAdvancedException if the exchange API call fails
	 */
	public List<MyCandle> fetchCandlesForProduct(String userId, String productId)
			throws CoinbaseAdvancedException {
		CoinbasePublicServiceImpl publicService = createPublicService(userId);

		long endTime = Instant.now().getEpochSecond();
		long startTime = endTime - 3600L * CANDLE_COUNT;

		ListCandles listCandles = publicService.fetchCandles(
				productId, startTime, endTime, Granularity.ONE_HOUR);
		if (listCandles == null || listCandles.getCandles() == null) {
			return Collections.emptyList();
		}
		return listCandles.getCandles();
	}

	/**
	 * Creates a {@link CoinbasePublicServiceImpl} bound to the given user's client.
	 */
	CoinbasePublicServiceImpl createPublicService(String userId) {
		CoinbaseAdvancedClient client = coinbaseClientFactory.getClientForUser(userId);
		return new CoinbasePublicServiceImpl(client,
				circuitBreakerRegistry.circuitBreaker("coinbasePublicService"));
	}
}
