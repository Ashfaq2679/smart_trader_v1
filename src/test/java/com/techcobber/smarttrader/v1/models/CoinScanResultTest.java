package com.techcobber.smarttrader.v1.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CoinScanResult}.
 */
class CoinScanResultTest {

	// ------------------------------------------------------------------
	// Helper methods
	// ------------------------------------------------------------------

	private static TradeDecision buyDecision(double confidence, String trend) {
		return TradeDecision.builder()
				.signal(TradeDecision.Signal.BUY)
				.confidence(confidence)
				.reasoning("test")
				.detectedPatterns(List.of("HAMMER"))
				.trendDirection(trend)
				.timestamp(LocalDateTime.now(ZoneOffset.UTC))
				.build();
	}

	private static TradeDecision sellDecision(double confidence, String trend) {
		return TradeDecision.builder()
				.signal(TradeDecision.Signal.SELL)
				.confidence(confidence)
				.reasoning("test")
				.detectedPatterns(List.of("SHOOTING_STAR"))
				.trendDirection(trend)
				.timestamp(LocalDateTime.now(ZoneOffset.UTC))
				.build();
	}

	private static TradeDecision holdDecision() {
		return TradeDecision.builder()
				.signal(TradeDecision.Signal.HOLD)
				.confidence(0.0)
				.reasoning("no signal")
				.detectedPatterns(List.of())
				.trendDirection("SIDEWAYS")
				.timestamp(LocalDateTime.now(ZoneOffset.UTC))
				.build();
	}

	// =======================================================================
	// calculateScore Tests
	// =======================================================================

	@Nested
	@DisplayName("calculateScore")
	class CalculateScoreTests {

		@Test
		@DisplayName("BUY signal gets higher base score than SELL")
		void buySignalGetsHigherBaseScore() {
			TradeDecision buy = buyDecision(0.0, "SIDEWAYS");
			TradeDecision sell = sellDecision(0.0, "SIDEWAYS");

			double buyScore = CoinScanResult.calculateScore(buy, 0, 0, 0, 1, 0);
			double sellScore = CoinScanResult.calculateScore(sell, 0, 0, 0, 1, 0);

			assertThat(buyScore).isGreaterThan(sellScore);
		}

		@Test
		@DisplayName("HOLD signal gets zero base score")
		void holdSignalGetsZeroBase() {
			TradeDecision hold = holdDecision();

			double score = CoinScanResult.calculateScore(hold, 0, 0, 0, 1, 0);

			assertThat(score).isEqualTo(0.0);
		}

		@Test
		@DisplayName("Higher confidence increases score")
		void higherConfidenceIncreasesScore() {
			TradeDecision lowConf = buyDecision(0.3, "SIDEWAYS");
			TradeDecision highConf = buyDecision(0.9, "SIDEWAYS");

			double lowScore = CoinScanResult.calculateScore(lowConf, 0, 0, 0, 1, 0);
			double highScore = CoinScanResult.calculateScore(highConf, 0, 0, 0, 1, 0);

			assertThat(highScore).isGreaterThan(lowScore);
		}

		@Test
		@DisplayName("Trend alignment adds bonus for BUY+UP")
		void trendAlignmentBonusBuyUp() {
			TradeDecision aligned = buyDecision(0.5, "UP");
			TradeDecision notAligned = buyDecision(0.5, "DOWN");

			double alignedScore = CoinScanResult.calculateScore(aligned, 0, 0, 0, 1, 0.8);
			double notAlignedScore = CoinScanResult.calculateScore(notAligned, 0, 0, 0, 1, 0.8);

			assertThat(alignedScore).isGreaterThan(notAlignedScore);
		}

		@Test
		@DisplayName("Trend alignment adds bonus for SELL+DOWN")
		void trendAlignmentBonusSellDown() {
			TradeDecision aligned = sellDecision(0.5, "DOWN");
			TradeDecision notAligned = sellDecision(0.5, "UP");

			double alignedScore = CoinScanResult.calculateScore(aligned, 0, 0, 0, 1, 0.8);
			double notAlignedScore = CoinScanResult.calculateScore(notAligned, 0, 0, 0, 1, 0.8);

			assertThat(alignedScore).isGreaterThan(notAlignedScore);
		}

		@Test
		@DisplayName("Higher volume increases score")
		void higherVolumeIncreasesScore() {
			TradeDecision decision = buyDecision(0.5, "UP");

			double lowVolScore = CoinScanResult.calculateScore(decision, 100, 0, 0, 1000, 0.5);
			double highVolScore = CoinScanResult.calculateScore(decision, 5000, 0, 0, 1000, 0.5);

			assertThat(highVolScore).isGreaterThan(lowVolScore);
		}

		@Test
		@DisplayName("Positive volume change adds momentum bonus")
		void positiveVolumeChangeAddsBonus() {
			TradeDecision decision = buyDecision(0.5, "UP");

			double noChange = CoinScanResult.calculateScore(decision, 1000, 0, 0, 1000, 0.5);
			double positiveChange = CoinScanResult.calculateScore(decision, 1000, 50, 0, 1000, 0.5);

			assertThat(positiveChange).isGreaterThan(noChange);
		}

		@Test
		@DisplayName("Negative volume change does not add bonus")
		void negativeVolumeChangeNoBonus() {
			TradeDecision decision = buyDecision(0.5, "UP");

			double noChange = CoinScanResult.calculateScore(decision, 1000, 0, 0, 1000, 0.5);
			double negativeChange = CoinScanResult.calculateScore(decision, 1000, -20, 0, 1000, 0.5);

			assertThat(negativeChange).isEqualTo(noChange);
		}

		@Test
		@DisplayName("Price momentum adds to score")
		void priceMomentumAddsToScore() {
			TradeDecision decision = buyDecision(0.5, "UP");

			double noMovement = CoinScanResult.calculateScore(decision, 1000, 0, 0, 1000, 0.5);
			double bigMovement = CoinScanResult.calculateScore(decision, 1000, 0, 25, 1000, 0.5);

			assertThat(bigMovement).isGreaterThan(noMovement);
		}

		@Test
		@DisplayName("Score is clamped to 0–100")
		void scoreIsClamped() {
			TradeDecision decision = buyDecision(1.0, "UP");

			double score = CoinScanResult.calculateScore(
					decision, 999999999, 999, 999, 1, 1.0);

			assertThat(score).isLessThanOrEqualTo(100.0);
			assertThat(score).isGreaterThanOrEqualTo(0.0);
		}

		@Test
		@DisplayName("Score with zero median volume does not break")
		void zeroMedianVolumeDoesNotBreak() {
			TradeDecision decision = buyDecision(0.5, "UP");

			double score = CoinScanResult.calculateScore(decision, 1000, 10, 5, 0, 0.5);

			assertThat(score).isGreaterThanOrEqualTo(0.0);
		}

		@Test
		@DisplayName("Maximum score scenario")
		void maximumScoreScenario() {
			// BUY (30) + confidence 1.0 (25) + trend aligned (15) + volume (15) + vol momentum (10) + price momentum (5) = 100
			TradeDecision decision = buyDecision(1.0, "UP");

			double score = CoinScanResult.calculateScore(
					decision, 10000, 200, 100, 1000, 1.0);

			assertThat(score).isEqualTo(100.0);
		}
	}

	// =======================================================================
	// buildSummary Tests
	// =======================================================================

	@Nested
	@DisplayName("buildSummary")
	class BuildSummaryTests {

		@Test
		@DisplayName("Summary contains product ID")
		void summaryContainsProductId() {
			TradeDecision decision = buyDecision(0.75, "UP");
			String summary = CoinScanResult.buildSummary("BTC-USDC", decision, 5.2, 15.5, 72.5);

			assertThat(summary).contains("BTC-USDC");
		}

		@Test
		@DisplayName("Summary contains signal")
		void summaryContainsSignal() {
			TradeDecision decision = sellDecision(0.6, "DOWN");
			String summary = CoinScanResult.buildSummary("ETH-USDC", decision, -3.1, -10, 45.0);

			assertThat(summary).contains("SELL");
		}

		@Test
		@DisplayName("Summary contains confidence percentage")
		void summaryContainsConfidence() {
			TradeDecision decision = buyDecision(0.85, "UP");
			String summary = CoinScanResult.buildSummary("SOL-USDC", decision, 8.0, 20.0, 80.0);

			assertThat(summary).contains("85%");
		}

		@Test
		@DisplayName("Summary contains score")
		void summaryContainsScore() {
			TradeDecision decision = buyDecision(0.9, "UP");
			String summary = CoinScanResult.buildSummary("SOL-USDC", decision, 10.0, 30.0, 92.5);

			assertThat(summary).contains("92.5");
		}
	}

	// =======================================================================
	// Builder / Model Tests
	// =======================================================================

	@Nested
	@DisplayName("builder")
	class BuilderTests {

		@Test
		@DisplayName("Builder creates complete result")
		void builderCreatesCompleteResult() {
			TradeDecision decision = buyDecision(0.8, "UP");
			LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

			CoinScanResult result = CoinScanResult.builder()
					.productId("BTC-USDC")
					.tradeDecision(decision)
					.volume24h(50000.0)
					.volumeChangePercent24h(12.5)
					.priceChangePercent24h(3.2)
					.currentPrice(65000.0)
					.profitPotentialScore(85.5)
					.summary("test summary")
					.scannedAt(now)
					.build();

			assertThat(result.getProductId()).isEqualTo("BTC-USDC");
			assertThat(result.getTradeDecision()).isEqualTo(decision);
			assertThat(result.getVolume24h()).isEqualTo(50000.0);
			assertThat(result.getVolumeChangePercent24h()).isEqualTo(12.5);
			assertThat(result.getPriceChangePercent24h()).isEqualTo(3.2);
			assertThat(result.getCurrentPrice()).isEqualTo(65000.0);
			assertThat(result.getProfitPotentialScore()).isEqualTo(85.5);
			assertThat(result.getSummary()).isEqualTo("test summary");
			assertThat(result.getScannedAt()).isEqualTo(now);
		}
	}
}
