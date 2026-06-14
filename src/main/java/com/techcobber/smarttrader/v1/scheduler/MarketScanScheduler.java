package com.techcobber.smarttrader.v1.scheduler;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.model.common.Granularity;
import com.techcobber.smarttrader.v1.models.CoinScanResult;
import com.techcobber.smarttrader.v1.models.ListCandles;
import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.OrderRequest;
import com.techcobber.smarttrader.v1.models.OrderResponse;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.models.UserPreferences;
import com.techcobber.smarttrader.v1.repositories.CoinsRepository;
import com.techcobber.smarttrader.v1.repositories.UserPreferencesRepository;
import com.techcobber.smarttrader.v1.services.CoinbaseClientFactory;
import com.techcobber.smarttrader.v1.services.CoinbasePublicServiceImpl;
import com.techcobber.smarttrader.v1.services.MarketScannerService;
import com.techcobber.smarttrader.v1.services.MarketScannerService.GranularityConfig;
import com.techcobber.smarttrader.v1.services.OrderService;
import com.techcobber.smarttrader.v1.services.TradeDecisionService;
import com.techcobber.smarttrader.v1.services.TradingOrchestrator;
import com.techcobber.smarttrader.v1.services.UserService;
import com.techcobber.smarttrader.v1.strategy.PriceActionStrategy;
import com.techcobber.smarttrader.v1.strategy.RiskManager.RiskAssessment;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler that periodically runs market scans using
 * {@link MarketScannerService} and caches the latest results in memory.
 *
 * <p>
 * The scan runs every hour by default (configurable via
 * {@code scanner.interval.ms} property). Results are available via
 * {@link #getLatestResults()} and can also be triggered on demand via
 * {@link #runScanNow(String, int)}.
 * </p>
 *
 * <p>
 * Each scan requires a userId to resolve the user's Coinbase client. The
 * scheduled task uses the configured default user
 * ({@code scanner.default.userId} property).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketScanScheduler {

	@Value("${APP_DEFAULT_USER}")
	private String defaultUserId;
	@Value("${candles.ignore.names:BTC-USDC,ETH-USDC}")
	private List<String> ignoreProductIds;
	@Value("${trading.granularity.ltf:FIFTEEN_MINUTE}")
	private String ltfGranularityName;
	@Value("${trading.granularity.confirm:ONE_HOUR}")
	private String confirmGranularityName;
	@Value("${trading.granularity.htf:TWO_HOUR}")
	private String htfGranularityName;
	static final int DEFAULT_LIMIT = 10;
	private static final int CANDLE_COUNT = 100;

	private final CoinbaseClientFactory coinbaseClientFactory;
	private final CircuitBreakerRegistry circuitBreakerRegistry;

	private volatile List<CoinScanResult> latestResults = Collections.emptyList();
	private final TradingOrchestrator tradingOrchestrator;
	private final CoinsRepository coinsRepository;
	private final TradeDecisionService tradeDecisionService; // optional, may be null
	private final OrderService orderService;
	private final UserPreferencesRepository userPreferencesRepository;
	private final UserService userService;

	/**
	 * Scheduled task — runs every hour (3 600 000 ms). The initial delay allows the
	 * application to fully start before the first scan.
	 *
	 * <p>
	 * Uses a well-known "system" user. If no credentials are stored for this user
	 * the scan is silently skipped rather than crashing the app.
	 * </p>
	 */
	@Scheduled(fixedDelayString = "${scanner.interval.ms:3600000}", initialDelayString = "${scanner.initial.delay.ms:60000}")
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
		MarketScannerService scanner = new MarketScannerService(
				publicService, new PriceActionStrategy(), new TrendAnalyzer(), null, buildGranularityConfig());
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
	public List<MyCandle> fetchCandlesForProduct(String userId, String productId) throws CoinbaseAdvancedException {
		CoinbasePublicServiceImpl publicService = createPublicService(userId);
		Granularity ltf = buildGranularityConfig().ltf();

		long endTime = Instant.now().getEpochSecond();
		long startTime = endTime - MarketScannerService.granularityToSeconds(ltf) * CANDLE_COUNT;

		ListCandles listCandles = publicService.fetchCandles(productId, startTime, endTime, ltf);
		if (listCandles == null || listCandles.getCandles() == null) {
			return Collections.emptyList();
		}
		return listCandles.getCandles();
	}
	
	@Scheduled(fixedDelayString = "${scanner.candles.interval.ms:3606000}", initialDelayString = "${scanner.candles.initial.delay.ms:180000}")
	public void updateOrderStatus() {
		log.info("Scheduled order status update starting…");
		this.orderService.updateOrderStatusFromExchange();
		log.info("Scheduled order status update completed.");
	}

	@Scheduled(fixedDelayString = "${scanner.candles.interval.ms:3600000}", initialDelayString = "${scanner.candles.initial.delay.ms:120000}")
	public void scheduledCandleFetch() {
		log.info("Scheduled candle fetch starting…");
		List<String> productIds = coinsRepository.findAll().stream()
				.filter(coin -> coin.productId() != null
						&& (ignoreProductIds == null || !ignoreProductIds.contains(coin.productId())))
				.map(coin -> coin.productId()).toList();
		try {
			productIds.forEach(pId -> {
				List<MyCandle> candles = fetchCandlesForProduct(pId);

				// Resolve user preferences and account balance for risk-aware analysis.
				UserPreferences prefs = userPreferencesRepository
						.findByUserId(defaultUserId).orElse(null);
				double balance = 0.0;
				try {
					balance = userService.findByUserName(defaultUserId).getCurrentFunds();
				} catch (Exception ex) {
					log.warn("Could not fetch account balance for {}: {}", defaultUserId, ex.getMessage());
				}

				// Full risk-gated analysis: replaces bare executeAnalysis() so that
				// location filter, ATR spike guard, consolidation check, and R:R gate
				// all fire before any order is placed.
				java.util.Map<String, Object> result =
						tradingOrchestrator.executeWithRisk(candles, pId, prefs, balance);

				TradeDecision decision = (TradeDecision) result.get("decision");
				RiskAssessment risk    = (RiskAssessment) result.get("riskAssessment");

				log.info("Scheduled candle fetch completed for {}. Signal: {} | RiskApproved: {}",
						pId, decision != null ? decision.getSignal() : "N/A",
						risk != null ? risk.isApproved() : "not assessed");

				// Only place an order when the risk manager has explicitly approved it.
				// This ensures confidence, location, R:R, and HTF alignment were all met.
				if (decision != null && risk != null && risk.isApproved()
						&& decision.getSignal() == Signal.BUY) {
					try {
						tradeDecisionService.save(decision);
						OrderRequest orderRequest = OrderRequest.builder()
								.productId(pId)
								.side(decision.getSignal().name())
								.orderType("LIMIT")
								.baseSize(risk.getPositionSize())
								.limitPrice(decision.getSuggestedPrice())
								.stopLoss(risk.getStopLoss())
								.takeProfit(risk.getTakeProfit())
								.entryPriceNum(decision.getSuggestedPrice())
								.comments(String.format(
										"Auto-generated BUY — confidence=%.2f R:R=%.2f SL=%.4f TP=%.4f",
										decision.getConfidence(), risk.getRiskRewardRatio(),
										risk.getStopLoss(), risk.getTakeProfit()))
								.build();
						OrderResponse response = orderService.placeOrder(defaultUserId, orderRequest);
						log.info("Order placed for {} — success={} coinbaseId={}",
								pId, response.isSuccess(), response.getCoinbaseOrderId());
					} catch (Exception e) {
						log.warn("Failed to place order for {}: {}", pId, e.getMessage());
					}
				} else if (decision != null && risk != null && !risk.isApproved()) {
					log.info("Trade decision for {} NOT approved by risk manager — skipping order placement. " +
							"Confidence: {}, R:R: {}, SL: {}, TP: {}",
							pId, decision.getConfidence(), risk.getRiskRewardRatio(),
							risk.getStopLoss(), risk.getTakeProfit());
				}
			});

		} catch (Exception e) {
			log.error("Scheduled candle fetch failed: {}", e.getMessage(), e);
		}
	}

	private List<MyCandle> fetchCandlesForProduct(String productId) throws CoinbaseAdvancedException {
		if (defaultUserId == null || defaultUserId.isBlank()) {
			log.warn("No default user configured for fetching candles. Returning empty list.");
			return Collections.emptyList();
		}
		CoinbasePublicServiceImpl publicService = createPublicService(defaultUserId);
		Granularity ltf = buildGranularityConfig().ltf();

		long endTime = Instant.now().getEpochSecond();
		long startTime = endTime - MarketScannerService.granularityToSeconds(ltf) * CANDLE_COUNT;
		ListCandles listCandles = null;

		log.info("Fetching candles for product {}", productId);
		try {
			listCandles = publicService.fetchCandles(productId, startTime, endTime, ltf);
		} catch (Exception e) {
			log.error("Failed to fetch candles for product {}: {}", productId, e.getMessage());
		}
		if (listCandles == null || listCandles.getCandles() == null) {
			return Collections.emptyList();
		}
		log.info("Fetched {} candles for product BTC-USDC", listCandles.getCandles().size());
		// Sort candles by start time ascending (newest last) before returning
		return listCandles.getCandles().stream().sorted((c1, c2) -> Long.compare(c1.getStart(), c2.getStart()))
				.toList();
	}

	/**
	 * Builds a {@link GranularityConfig} from injected property values.
	 * Falls back to defaults for any unrecognised property value.
	 */
	private GranularityConfig buildGranularityConfig() {
		Granularity ltf     = parseGranularity(ltfGranularityName,     Granularity.FIFTEEN_MINUTE);
		Granularity confirm = parseGranularity(confirmGranularityName, Granularity.ONE_HOUR);
		Granularity htf     = parseGranularity(htfGranularityName,     Granularity.TWO_HOUR);
		return new GranularityConfig(ltf, confirm, htf);
	}

	private static Granularity parseGranularity(String name, Granularity fallback) {
		if (name == null || name.isBlank()) return fallback;
		try {
			return Granularity.valueOf(name.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			log.warn("Unknown granularity '{}' — using default {}", name, fallback);
			return fallback;
		}
	}

	/**
	 * Creates a {@link CoinbasePublicServiceImpl} bound to the given user's client.
	 */
	CoinbasePublicServiceImpl createPublicService(String userId) {
		CoinbaseAdvancedClient client = coinbaseClientFactory.getClientForUser(userId);
		return new CoinbasePublicServiceImpl(client, circuitBreakerRegistry.circuitBreaker("coinbasePublicService"));
	}
}
