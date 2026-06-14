package com.techcobber.smarttrader.v1.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.Order;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.models.UserPreferences;
import com.techcobber.smarttrader.v1.strategy.AtrIndicator;
import com.techcobber.smarttrader.v1.strategy.EmaIndicator;
import com.techcobber.smarttrader.v1.strategy.PriceActionStrategy;
import com.techcobber.smarttrader.v1.strategy.RiskManager;
import com.techcobber.smarttrader.v1.strategy.RiskManager.RiskAssessment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingOrchestrator {

	private static final double MIN_CONFIDENCE = 0.65;

	/** Injected singleton — no need to create a new instance per call. */
	private final RiskManager riskManager;

	private final EmaIndicator emaIndicator = new EmaIndicator();
	private final AtrIndicator atrIndicator = new AtrIndicator();

	public TradeDecision executeAnalysis(List<MyCandle> candles, String productId) {
		if (candles == null || candles.isEmpty())
			return TradeDecision.builder()
					.productId(productId)
					.signal(Signal.HOLD)
					.confidence(0.0)
					.reasoning("No candle data available for analysis")
					.build();
		log.debug("========================================");
		log.info("Starting trading analysis for product: {}", productId);
		log.debug("Candle count: {}", candles.size());
		log.debug("========================================");

		PriceActionStrategy strategy = new PriceActionStrategy();
		log.debug("Using strategy: {}", strategy.getName());

		TradeDecision decision = strategy.analyze(candles, productId);
		decision.setProductId(productId);
		decision.setSuggestedPrice(candles.get(candles.size() - 1).getClose());

		log.debug("========================================");
		log.info("Analysis complete for {}", productId);
		log.debug("Product: {} | Signal: {} | Confidence: {} | Trend: {}",
				productId, decision.getSignal(), decision.getConfidence(), decision.getTrendDirection());
		log.debug("Detected patterns: {}", decision.getDetectedPatterns());
		log.debug("Support: {} | Resistance: {}",
				decision.getNearestSupport(), decision.getNearestResistance());
		log.info("Reasoning: Product: {} | {}", productId, decision.getReasoning());
		log.debug("========================================");

		return decision;
	}

	public Map<String, Object> executeWithRisk(List<MyCandle> candles, String productId,
			UserPreferences prefs, double accountBalance) {

		log.info("Starting full analysis with risk management for {} (balance: {})", productId, accountBalance);

		TradeDecision decision = executeAnalysis(candles, productId);

		Map<String, Object> result = new HashMap<>();
		result.put("decision", decision);

		double minScore = parseDoubleOrDefault(prefs != null ? prefs.getMinEntryScore() : null, MIN_CONFIDENCE);
		boolean consolidating = Boolean.TRUE.equals(decision.getConsolidationDetected());

		if (decision.getSignal() != Signal.HOLD && decision.getConfidence() >= minScore && !consolidating) {
			log.info("Confidence {} >= {} threshold — proceeding with risk assessment",
					decision.getConfidence(), minScore);

			double currentPrice = candles.get(candles.size() - 1).getClose();
				// locationOK: price must be near EMA50 pullback or support AND not already crowding resistance
			boolean locationOK = decision.getDistanceFromEma50Pct() != null
						&& decision.getDistanceFromEma50Pct() <= 3.0
						&& !Boolean.TRUE.equals(decision.getNearResistanceDetected());
			boolean htfAligned = decision.getHtfTrendDirection() == null
					|| !"DOWN".equals(decision.getHtfTrendDirection());

			RiskAssessment risk = riskManager.assess(decision, prefs, currentPrice, accountBalance,
					candles, locationOK, htfAligned);

			log.info("Risk assessment result: approved={}, positionSize={}, SL={}, TP={}, R:R=1:{}, effectiveStop={}, trailingStop={}",
					risk.isApproved(), risk.getPositionSize(), risk.getStopLoss(),
					risk.getTakeProfit(), risk.getRiskRewardRatio(), risk.getEffectiveStop(), risk.getTrailingStop());

			result.put("riskAssessment", risk);
		} else {
			log.info("Skipping risk assessment — signal: {}, confidence: {} (threshold: {}), consolidating: {}",
					decision.getSignal(), decision.getConfidence(), minScore, consolidating);
		}

		log.debug("Full analysis complete for {}", productId);
		return result;
	}

	/**
	 * Evaluates whether an open BUY position should be exited on the next scan.
	 *
	 * <p>Exit triggers (evaluated in priority order):
	 * <ol>
	 *   <li>currentPrice ≤ effectiveStop — floor stop triggered</li>
	 *   <li>currentPrice ≤ trailingStop  — ATR trailing stop locks profit</li>
	 *   <li>EMA9 &lt; EMA21 AND price &lt; EMA50 — momentum deterioration</li>
	 *   <li>price &lt; recentSwingLow(5)  — bearish structure break</li>
	 * </ol>
	 * Returns {@code null} when no exit condition is met (hold the position).
	 * </p>
	 *
	 * @param openBuyOrder   the open BUY order persisted in MongoDB
	 * @param candles        current 1H candles (used for ATR / EMA computation)
	 * @param prefs          user preferences for trailing ATR multiplier
	 * @return a SELL {@link TradeDecision} with reasoning, or {@code null} to hold
	 */
	public TradeDecision evaluateExit(Order openBuyOrder, List<MyCandle> candles, UserPreferences prefs) {
		if (openBuyOrder == null || candles == null || candles.isEmpty()) {
			log.warn("evaluateExit: missing order or candles — cannot evaluate exit");
			return null;
		}

		String productId   = openBuyOrder.getProductId();
		double currentPrice = candles.get(candles.size() - 1).getClose();
		double entryPrice   = openBuyOrder.getEntryPriceNum() != null
				? openBuyOrder.getEntryPriceNum()
				: currentPrice;

		double atr          = atrIndicator.calculate(candles);
		double ema50        = emaIndicator.calculate(candles, 50);
		double ema9         = emaIndicator.calculate(candles, 9);
		double ema21        = emaIndicator.calculate(candles, 21);
		double recentSwingHigh = atrIndicator.getRecentSwingHigh(candles, 10);
		double recentSwingLow  = atrIndicator.getRecentSwingLow(candles, 5);

		double trailingK   = parseDoubleOrDefault(prefs != null ? prefs.getTrailingAtrMultiplier() : null, 2.0);
		double maxLossPct  = parseDoubleOrDefault(prefs != null ? prefs.getMaxDailyLoss() : null, 5.0);

		double storedStop      = openBuyOrder.getStopLoss() != null ? openBuyOrder.getStopLoss() : entryPrice * 0.98;
		double trailingStop    = recentSwingHigh > 0 && atr > 0 ? recentSwingHigh - (trailingK * atr) : storedStop;
		double effectiveStop   = Math.max(storedStop, entryPrice * (1.0 - maxLossPct / 100.0));

		log.info("evaluateExit [{}] — price={} | effectiveStop={} | trailingStop={} | EMA9={} | EMA21={} | EMA50={} | swingLow={}",
				productId, currentPrice, effectiveStop, trailingStop, ema9, ema21, ema50, recentSwingLow);

		// 1. Effective stop floor
		if (currentPrice <= effectiveStop) {
			String reasoning = String.format(
					"Effective stop triggered: price %.4f <= effectiveStop %.4f (storedSL=%.4f, maxLoss=%.1f%%)",
					currentPrice, effectiveStop, storedStop, maxLossPct);
			log.info("EXIT [{}]: {}", productId, reasoning);
			return buildSellDecision(productId, currentPrice, reasoning, ema50, atr);
		}

		// 2. ATR trailing stop
		if (trailingStop > 0 && currentPrice <= trailingStop) {
			String reasoning = String.format(
					"Trailing stop triggered: price %.4f <= trailingStop %.4f (swingHigh=%.4f - %.1f×ATR)",
					currentPrice, trailingStop, recentSwingHigh, trailingK);
			log.info("EXIT [{}]: {}", productId, reasoning);
			return buildSellDecision(productId, currentPrice, reasoning, ema50, atr);
		}

		// 3. Momentum deterioration (EMA cross + price below EMA50)
		boolean emaCrossDown    = ema9 < ema21;
		boolean priceBelowEma50 = currentPrice < ema50;
		if (emaCrossDown && priceBelowEma50) {
			String reasoning = String.format(
					"Momentum exit: EMA9(%.4f) < EMA21(%.4f) AND price(%.4f) < EMA50(%.4f)",
					ema9, ema21, currentPrice, ema50);
			log.info("EXIT [{}]: {}", productId, reasoning);
			return buildSellDecision(productId, currentPrice, reasoning, ema50, atr);
		}

		// 4. Bearish structure break
		if (recentSwingLow > 0 && currentPrice < recentSwingLow) {
			String reasoning = String.format(
					"Structure break: price %.4f < recentSwingLow %.4f",
					currentPrice, recentSwingLow);
			log.info("EXIT [{}]: {}", productId, reasoning);
			return buildSellDecision(productId, currentPrice, reasoning, ema50, atr);
		}

		log.debug("No exit condition met for [{}] — hold position", productId);
		return null;
	}

	// ------------------------------------------------------------------
	// Internal
	// ------------------------------------------------------------------

	private TradeDecision buildSellDecision(String productId, double currentPrice,
			String reasoning, double ema50, double atr) {
		return TradeDecision.builder()
				.productId(productId)
				.signal(Signal.SELL)
				.confidence(1.0)
				.reasoning(reasoning)
				.suggestedPrice(currentPrice)
				.ema50(ema50)
				.atr(atr)
				.build();
	}

	private double parseDoubleOrDefault(String value, double defaultValue) {
		if (value == null || value.isBlank()) return defaultValue;
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
}
