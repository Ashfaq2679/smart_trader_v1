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

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * REST controller for triggering market scans and individual product analysis.
 */
@Slf4j
@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
@Tag(name = "Scanner", description = "Market scanning and product analysis")
public class MarketScanController {

	private final MarketScanScheduler marketScanScheduler;
	private final TradingOrchestrator tradingOrchestrator;

	@Operation(summary = "Trigger market scan", description = "Run an on-demand market scan using user's credentials")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Scan results"),
			@ApiResponse(responseCode = "400", description = "Invalid input"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	@PostMapping("/scan")
	@RateLimiter(name = "apiRateLimiter")
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

	@Operation(summary = "Get latest scan results", description = "Retrieve cached results from most recent scan")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	@GetMapping("/results")
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<List<CoinScanResult>> getLatestResults() {
		List<CoinScanResult> results = marketScanScheduler.getLatestResults();
		return ResponseEntity.ok(results);
	}

	@Operation(summary = "Analyze product", description = "Analyze a specific product and return trade decision")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Analysis result"),
			@ApiResponse(responseCode = "400", description = "Invalid input"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	@GetMapping("/analyze/{productId}")
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<?> analyzeProduct(
			@PathVariable String productId,
			@RequestParam String userId) {
		try {
			TradeDecision decision = tradingOrchestrator.analyzeProduct(productId, userId);
			return ResponseEntity.ok(decision);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("Analysis failed for [{}]: {}", productId, e.getMessage());
			return ResponseEntity.internalServerError()
					.body(Map.of("error", "Analysis failed: " + e.getMessage()));
		}
	}
}
