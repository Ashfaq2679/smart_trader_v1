package com.techcobber.smarttrader.v1.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.techcobber.smarttrader.v1.models.CoinScanResult;
import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.scheduler.MarketScanScheduler;
import com.techcobber.smarttrader.v1.services.TradingOrchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for triggering market scans and individual product analysis.
 *
 * <p>Exposes endpoints to manually trigger scans (on-demand), retrieve cached
 * results from the latest scheduled scan, and analyse a specific product.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
public class MarketScanController {

	private final MarketScanScheduler marketScanScheduler;
	private final TradingOrchestrator tradingOrchestrator;

	/**
	 * Triggers a market scan on demand and returns the top results.
	 *
	 * @param userId user whose Coinbase credentials to use
	 * @param limit  maximum number of results (default 10)
	 * @return list of scan results sorted by profit-potential score
	 */
	@PostMapping("/scan")
	public ResponseEntity<?> triggerScan(
			@RequestParam String userId,
			@RequestParam(defaultValue = "10") int limit) {
		try {
			List<CoinScanResult> results = marketScanScheduler.runScanNow(userId, limit);
			log.info("On-demand scan for user [{}] completed — {} results", userId, results.size());
			return ResponseEntity.ok(results);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("On-demand scan failed for user [{}]: {}", userId, e.getMessage());
			return ResponseEntity.internalServerError()
					.body(Map.of("error", "Scan failed: " + e.getMessage()));
		}
	}

	/**
	 * Returns the cached results from the most recent scan.
	 *
	 * @return list of scan results or empty list if no scan has run yet
	 */
	@GetMapping("/results")
	public ResponseEntity<List<CoinScanResult>> getLatestResults() {
		List<CoinScanResult> results = marketScanScheduler.getLatestResults();
		return ResponseEntity.ok(results);
	}

	/**
	 * Analyses a specific product using the TradingOrchestrator.
	 *
	 * <p>Fetches candle data for the given product and runs
	 * the full trading analysis pipeline.</p>
	 *
	 * @param productId the product to analyse (e.g. "BTC-USDC")
	 * @param userId    user whose Coinbase credentials to use
	 * @return the trade decision
	 */
	@GetMapping("/analyze/{productId}")
	public ResponseEntity<?> analyzeProduct(
			@PathVariable String productId,
			@RequestParam String userId) {
		try {
			List<MyCandle> candles = marketScanScheduler.fetchCandlesForProduct(userId, productId);
			if (candles == null || candles.isEmpty()) {
				return ResponseEntity.badRequest()
						.body(Map.of("error", "No candle data available for " + productId));
			}
			TradeDecision decision = tradingOrchestrator.executeAnalysis(candles, productId);
			return ResponseEntity.ok(decision);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("Analysis failed for {}: {}", productId, e.getMessage());
			return ResponseEntity.internalServerError()
					.body(Map.of("error", "Analysis failed: " + e.getMessage()));
		}
	}
}
