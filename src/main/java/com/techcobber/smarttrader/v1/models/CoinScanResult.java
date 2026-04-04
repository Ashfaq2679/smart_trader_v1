package com.techcobber.smarttrader.v1.models;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Represents the result of scanning a single coin for profit potential.
 *
 * <p>Each result contains the trade decision from the PriceAction strategy,
 * augmented with volume metrics and an overall profit-potential score that
 * combines price action analysis, volume strength, and trend alignment.</p>
 *
 * <p>Results are designed to be ranked by {@code profitPotentialScore}
 * in descending order to identify the best trading candidates.</p>
 */
@Data
@Builder
public class CoinScanResult {

	private String productId;
	private TradeDecision tradeDecision;

	// Volume metrics from the exchange
	private double volume24h;
	private double volumeChangePercent24h;
	private double priceChangePercent24h;
	private double currentPrice;

	// Computed score combining all factors (0.0 – 100.0)
	private double profitPotentialScore;

	// Summary for quick reference
	private String summary;
	private LocalDateTime scannedAt;

	/**
	 * Calculates the profit-potential score based on multiple factors.
	 *
	 * <p><b>Scoring breakdown (max 100):</b></p>
	 * <ul>
	 *   <li><b>Signal weight (0–30):</b> BUY=30, SELL=20, HOLD=0</li>
	 *   <li><b>Confidence (0–25):</b> decision confidence × 25</li>
	 *   <li><b>Trend alignment (0–15):</b> UP trend with BUY or DOWN trend with SELL = strength × 15</li>
	 *   <li><b>Volume score (0–15):</b> normalised 24h volume relative to median</li>
	 *   <li><b>Volume momentum (0–10):</b> positive volume change % capped at 10</li>
	 *   <li><b>Price momentum (0–5):</b> absolute price change % capped at 5</li>
	 * </ul>
	 *
	 * @param medianVolume the median 24h volume across all scanned coins,
	 *                     used to normalise the volume component
	 * @param trendStrength the trend strength from the TrendAnalyzer (0.0–1.0)
	 * @return the calculated score, clamped between 0.0 and 100.0
	 */
	public static double calculateScore(TradeDecision decision,
			double volume24h, double volumeChangePercent24h,
			double priceChangePercent24h, double medianVolume,
			double trendStrength) {

		double score = 0.0;

		// 1. Signal weight (0-30)
		if (decision.getSignal() == TradeDecision.Signal.BUY) {
			score += 30.0;
		} else if (decision.getSignal() == TradeDecision.Signal.SELL) {
			score += 20.0;
		}

		// 2. Confidence component (0-25)
		score += decision.getConfidence() * 25.0;

		// 3. Trend alignment (0-15)
		boolean trendAligned =
				(decision.getSignal() == TradeDecision.Signal.BUY
						&& "UP".equals(decision.getTrendDirection()))
				|| (decision.getSignal() == TradeDecision.Signal.SELL
						&& "DOWN".equals(decision.getTrendDirection()));
		if (trendAligned) {
			score += trendStrength * 15.0;
		}

		// 4. Volume score (0-15) — normalised against median
		if (medianVolume > 0 && volume24h > 0) {
			double volumeRatio = volume24h / medianVolume;
			score += Math.min(15.0, volumeRatio * 5.0);
		}

		// 5. Volume momentum (0-10) — positive change is bullish
		if (volumeChangePercent24h > 0) {
			score += Math.min(10.0, volumeChangePercent24h / 10.0);
		}

		// 6. Price momentum (0-5) — absolute movement shows activity
		score += Math.min(5.0, Math.abs(priceChangePercent24h) / 10.0);

		return Math.min(100.0, Math.max(0.0, Math.round(score * 100.0) / 100.0));
	}

	/**
	 * Builds a human-readable summary of this scan result.
	 */
	public static String buildSummary(String productId, TradeDecision decision,
			double priceChangePercent24h, double volumeChangePercent24h, double score) {

		return String.format(
				"%s | Signal: %s (%.0f%% confidence) | Trend: %s | "
						+ "Price Δ24h: %.2f%% | Volume Δ24h: %.2f%% | Score: %.1f",
				productId,
				decision.getSignal(),
				decision.getConfidence() * 100,
				decision.getTrendDirection(),
				priceChangePercent24h,
				volumeChangePercent24h,
				score);
	}
}
