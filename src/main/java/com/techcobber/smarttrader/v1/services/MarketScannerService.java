package com.techcobber.smarttrader.v1.services;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.model.common.Granularity;
import com.coinbase.advanced.model.products.Product;
import com.techcobber.smarttrader.v1.models.CoinScanResult;
import com.techcobber.smarttrader.v1.models.ListCandles;
import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.strategy.MultiTimeframeAnalyzer;
import com.techcobber.smarttrader.v1.strategy.MultiTimeframeAnalyzer.MultiTimeframeResult;
import com.techcobber.smarttrader.v1.strategy.PriceActionStrategy;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer;
import com.techcobber.smarttrader.v1.strategy.PatternUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Scans all coins on the exchange paired with a specified quote currency
 * (e.g. USDC) and ranks them by profit potential.
 *
 * <p>The scanner combines three dimensions of analysis:
 * <ul>
 *   <li><b>Price Action</b> — using {@link PriceActionStrategy} (candle patterns,
 *       support/resistance, trend detection)</li>
 *   <li><b>Volume</b> — 24h trading volume and volume momentum (% change)</li>
 *   <li><b>Trend</b> — direction and strength from {@link TrendAnalyzer}</li>
 * </ul>
 *
 * <p>Each coin receives a composite <em>profit-potential score</em> (0–100)
 * and results are returned sorted from highest to lowest score.</p>
 */
@Slf4j
public class MarketScannerService {

	private static final int CANDLE_COUNT         = 100;
	private static final int HTF_CONFIRM_CANDLES  = 50;   // 1H candles (≈ 50h of history)
	private static final int HTF_DIRECTION_CANDLES = 30;  // 4H candles (≈ 120h of history)
	private static final int TREND_LOOKBACK = 20;
	private static final String DEFAULT_QUOTE_CURRENCY = "USDC";

	/**
	 * Holds the three timeframe granularities used in analysis.
	 *
	 * <ul>
	 *   <li>{@code ltf} — entry timeframe (default: FIFTEEN_MINUTE)</li>
	 *   <li>{@code confirm} — confirmation timeframe (default: ONE_HOUR)</li>
	 *   <li>{@code htf} — directional timeframe (default: TWO_HOUR, proxying 4H)</li>
	 * </ul>
	 *
	 * <p>All three can be overridden via application properties:
	 * {@code trading.granularity.ltf}, {@code trading.granularity.confirm},
	 * {@code trading.granularity.htf}.
	 * Values must match a {@link Granularity} enum constant name.</p>
	 */
	public record GranularityConfig(Granularity ltf, Granularity confirm, Granularity htf) {
		public static GranularityConfig defaults() {
			return new GranularityConfig(Granularity.FIFTEEN_MINUTE, Granularity.ONE_HOUR, Granularity.TWO_HOUR);
		}
	}

	private final CoinbasePublicServiceImpl publicService;
	private final PriceActionStrategy strategy;
	private final TrendAnalyzer trendAnalyzer;
	private final MultiTimeframeAnalyzer mtfAnalyzer;
	private final GranularityConfig granularityConfig;

	public MarketScannerService(CoinbasePublicServiceImpl publicService) {
		this(publicService, new PriceActionStrategy(), new TrendAnalyzer(), null, GranularityConfig.defaults());
	}

	public MarketScannerService(CoinbasePublicServiceImpl publicService,
						 PriceActionStrategy strategy,
						 TrendAnalyzer trendAnalyzer,
						 TradeDecisionService tradeDecisionService) {
		this(publicService, strategy, trendAnalyzer, tradeDecisionService, GranularityConfig.defaults());
	}

	public MarketScannerService(CoinbasePublicServiceImpl publicService,
						 PriceActionStrategy strategy,
						 TrendAnalyzer trendAnalyzer,
						 TradeDecisionService tradeDecisionService,
						 GranularityConfig granularityConfig) {
		this.publicService = publicService;
		this.strategy = strategy;
		this.trendAnalyzer = trendAnalyzer;
		this.mtfAnalyzer = new MultiTimeframeAnalyzer();
		this.granularityConfig = granularityConfig;
	}

	/**
	 * Scans all USDC-paired coins using the configured LTF granularity and returns
	 * the top candidates ranked by profit potential.
	 *
	 * @param limit maximum number of results to return
	 * @return list of {@link CoinScanResult} sorted by score descending
	 * @throws CoinbaseAdvancedException if the exchange API call fails
	 */
	public List<CoinScanResult> scanUSDCPairs(int limit) throws CoinbaseAdvancedException {
		return scanPairs(DEFAULT_QUOTE_CURRENCY, granularityConfig.ltf(), limit);
	}

	/**
	 * Scans all coins paired with the given quote currency and returns
	 * the top candidates ranked by profit potential.
	 *
	 * @param quoteCurrency the quote currency to filter by (e.g. "USDC")
	 * @param granularity   the candle granularity for technical analysis
	 * @param limit         maximum number of results to return
	 * @return list of {@link CoinScanResult} sorted by score descending
	 * @throws CoinbaseAdvancedException if the exchange API call fails
	 */
	public List<CoinScanResult> scanPairs(String quoteCurrency, Granularity granularity, int limit)
			throws CoinbaseAdvancedException {

		log.info("========================================");
		log.info("Starting market scan for {}-paired coins (granularity: {})",
				quoteCurrency, granularity);
		log.info("========================================");

		// Step 1: Get all products matching the quote currency
		List<Product> products = getActiveProducts(quoteCurrency);
		if (products.isEmpty()) {
			log.warn("No active products found for quote currency: {}", quoteCurrency);
			return List.of();
		}

		log.info("Found {} active {}-paired products to scan", products.size(), quoteCurrency);

		// Step 2: Compute median volume for normalisation
		double medianVolume = computeMedianVolume(products);
		log.info("Median 24h volume: {}", medianVolume);

		// Step 3: Analyse each product
		List<CoinScanResult> results = new ArrayList<>();
		long endTime = Instant.now().getEpochSecond();
		long startTime = endTime - granularityToSeconds(granularity) * CANDLE_COUNT;

		for (Product product : products) {
			CoinScanResult result = analyseProduct(product, startTime, endTime,
					granularity, medianVolume);
			if (result != null) {
				results.add(result);
			}
		}

		log.info("Successfully analysed {} out of {} products", results.size(), products.size());

		// Step 4: Sort by score descending and return the top results
		results.sort(Comparator.comparingDouble(CoinScanResult::getProfitPotentialScore).reversed());

		List<CoinScanResult> topResults = results.stream().limit(limit).toList();

		log.info("========================================");
		log.info("Top {} candidates:", topResults.size());
		for (int i = 0; i < topResults.size(); i++) {
			log.info("  #{}: {}", i + 1, topResults.get(i).getSummary());
		}
		log.info("========================================");

		return topResults;
	}

	/**
	 * Retrieves active, tradable products for the given quote currency.
	 * Filters out disabled, trading-disabled, cancel-only, and view-only products.
	 */
	List<Product> getActiveProducts(String quoteCurrency) throws CoinbaseAdvancedException {
		List<Product> allProducts = publicService.getFilteredProducts(quoteCurrency);

		return allProducts.stream()
				.filter(p -> !p.isTradingDisabled())
				.filter(p -> !p.isDisabled())
				.filter(p -> !p.isCancelOnly())
				.filter(p -> !p.isViewOnly())
				.filter(p -> p.getProductId().toUpperCase().endsWith("-" + quoteCurrency.toUpperCase()))
				.filter(p -> hasValidVolume(p))
				.toList();
	}

	/**
	 * Analyses a single product: fetches 15m candles, runs strategy, then lazily
	 * fetches 1H and 4H candles for HTF alignment validation on BUY candidates.
	 */
	CoinScanResult analyseProduct(Product product, long startTime, long endTime,
			Granularity granularity, double medianVolume) {

		String productId = product.getProductId();
		try {
			// Fetch 15m candle data and sort ascending (API returns newest-first)
			ListCandles listCandles = publicService.fetchCandles(productId, startTime, endTime, granularity);
			if (listCandles == null || listCandles.getCandles() == null
					|| listCandles.getCandles().isEmpty()) {
				log.debug("No candle data for {} — skipping", productId);
				return null;
			}

			List<MyCandle> candles = sortAscending(listCandles.getCandles());
			log.debug("Fetched {} candles for {}", candles.size(), productId);

			// Run price action analysis (15m — without HTF context first)
			TradeDecision decision = strategy.analyze(candles, productId);
			decision.setProductId(productId);

			// --- Lazy HTF fetch: only for BUY candidates ---
			if (decision.getSignal() == Signal.BUY) {
				MultiTimeframeResult mtfResult = fetchAndAnalyzeHTF(productId, endTime);
				if (mtfResult != null && !mtfResult.isAligned()) {
					log.info("HTF misaligned for {} (4H={}, 1H={}) — downgrading BUY to HOLD",
							productId, mtfResult.getHtfTrend(), mtfResult.getConfirmTrend());
					// Re-analyse with HTF context to get the correct HOLD decision and fields
					decision = strategy.analyze(candles, productId, mtfResult, null, null);
					decision.setProductId(productId);
				} else if (mtfResult != null) {
					// Update HTF fields on the existing decision
					decision.setHtfTrendDirection(mtfResult.getHtfTrend().name());
					decision.setConfirmTrendDirection(mtfResult.getConfirmTrend().name());
				}
			}

			// Compute trend strength
			TrendAnalyzer.TrendResult trendResult = trendAnalyzer.analyzeTrend(candles, TREND_LOOKBACK);
			double trendStrength = trendResult.getStrength();

			// Extract product metrics
			double volume24h = parseDoubleOrZero(product.getVolume24h());
			double volumeChange = parseDoubleOrZero(product.getVolumePercentageChange24h());
			double priceChange = parseDoubleOrZero(product.getPricePercentageChange24h());
			double currentPrice = parseDoubleOrZero(product.getPrice());

			// Calculate score
			double score = CoinScanResult.calculateScore(
					decision, volume24h, volumeChange, priceChange,
					medianVolume, trendStrength);

			// Build summary
			String summary = CoinScanResult.buildSummary(
					productId, decision, priceChange, volumeChange, score);

			return CoinScanResult.builder()
					.productId(productId)
					.tradeDecision(decision)
					.volume24h(volume24h)
					.volumeChangePercent24h(volumeChange)
					.priceChangePercent24h(priceChange)
					.currentPrice(currentPrice)
					.profitPotentialScore(score)
					.summary(summary)
					.scannedAt(LocalDateTime.now(ZoneOffset.UTC))
					.build();

		} catch (CoinbaseAdvancedException e) {
			log.warn("Failed to analyse {} — {}", productId, e.getMessage());
			return null;
		}
	}

	/**
	 * Lazily fetches 1H (confirmation) and 4H (direction) candles for a BUY candidate
	 * and runs HTF alignment analysis.
	 *
	 * @return {@link MultiTimeframeResult} or {@code null} if candles cannot be fetched
	 */
	private MultiTimeframeResult fetchAndAnalyzeHTF(String productId, long endTime) {
		try {
			long confirm1hStart = endTime - granularityToSeconds(granularityConfig.confirm()) * HTF_CONFIRM_CANDLES;
			ListCandles confirm1h = publicService.fetchCandles(productId, confirm1hStart, endTime, granularityConfig.confirm());

			long direction4hStart = endTime - granularityToSeconds(granularityConfig.htf()) * HTF_DIRECTION_CANDLES;
			ListCandles direction4h = publicService.fetchCandles(productId, direction4hStart, endTime, granularityConfig.htf());

			// Sort ascending — API returns newest-first
			List<MyCandle> confirmCandles = (confirm1h != null && confirm1h.getCandles() != null)
					? sortAscending(confirm1h.getCandles()) : List.of();
			List<MyCandle> htfCandles = (direction4h != null && direction4h.getCandles() != null)
					? sortAscending(direction4h.getCandles()) : List.of();

			if (confirmCandles.isEmpty() && htfCandles.isEmpty()) {
				log.debug("No HTF candles available for {} — skipping MTF check", productId);
				return null;
			}

			return mtfAnalyzer.analyze(List.of(), confirmCandles, htfCandles);

		} catch (CoinbaseAdvancedException e) {
			log.warn("Failed to fetch HTF candles for {} — MTF check skipped: {}", productId, e.getMessage());
			return null;
		}
	}

	/**
	 * Computes the median 24h volume across all products for score normalisation.
	 */
	double computeMedianVolume(List<Product> products) {
		List<Double> volumes = new ArrayList<>();
		for (Product p : products) {
			double vol = parseDoubleOrZero(p.getVolume24h());
			if (vol > 0) {
				volumes.add(vol);
			}
		}

		if (volumes.isEmpty()) {
			return 0.0;
		}

		Collections.sort(volumes);
		int mid = volumes.size() / 2;
		if (volumes.size() % 2 == 0) {
			return (volumes.get(mid - 1) + volumes.get(mid)) / 2.0;
		}
		return volumes.get(mid);
	}

	private boolean hasValidVolume(Product product) {
		String vol = product.getVolume24h();
		if (vol == null || vol.isEmpty()) {
			return false;
		}
		try {
			return Double.parseDouble(vol) > 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Sorts a candle list in ascending order of {@code start} timestamp
	 * (oldest first, newest last), as required by all strategy and indicator classes.
	 *
	 * <p>The Coinbase API returns candles in <em>descending</em> order (newest first),
	 * so this sort must be applied before passing candles to any analysis component.</p>
	 */
	static List<MyCandle> sortAscending(List<MyCandle> candles) {
		return candles.stream()
				.sorted((a, b) -> Long.compare(a.getStart(), b.getStart()))
				.toList();
	}

	static double parseDoubleOrZero(String value) {
		if (value == null || value.isEmpty()) {
			return 0.0;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	/**
	 * Converts a granularity enum to its equivalent in seconds.
	 */
	public static long granularityToSeconds(Granularity granularity) {
		return switch (granularity) {
			case ONE_MINUTE -> 60L;
			case FIVE_MINUTE -> 300L;
			case FIFTEEN_MINUTE -> 900L;
			case THIRTY_MINUTE -> 1800L;
			case ONE_HOUR -> 3600L;
			case TWO_HOUR -> 7200L;
			case SIX_HOUR -> 21600L;
			case ONE_DAY -> 86400L;
			default -> 3600L;
		};
	}
}