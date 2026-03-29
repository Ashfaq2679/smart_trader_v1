package com.techcobber.smarttrader.v1.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.models.UserPreferences;
import com.techcobber.smarttrader.v1.strategy.PriceActionStrategy;
import com.techcobber.smarttrader.v1.strategy.RiskManager;
import com.techcobber.smarttrader.v1.strategy.RiskManager.RiskAssessment;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TradingOrchestrator {

	private static final double MIN_CONFIDENCE = 0.6;

	public TradeDecision executeAnalysis(List<MyCandle> candles, String productId) {
		log.info("========================================");
		log.info("Starting trading analysis for product: {}", productId);
		log.info("Candle count: {}", candles == null ? 0 : candles.size());
		log.info("========================================");

		PriceActionStrategy strategy = new PriceActionStrategy();
		log.info("Using strategy: {}", strategy.getName());

		TradeDecision decision = strategy.analyze(candles);
		decision.setProductId(productId);

		log.info("========================================");
		log.info("Analysis complete for {}", productId);
		log.info("Signal: {} | Confidence: {} | Trend: {}",
				decision.getSignal(), decision.getConfidence(), decision.getTrendDirection());
		log.info("Detected patterns: {}", decision.getDetectedPatterns());
		log.info("Support: {} | Resistance: {}",
				decision.getNearestSupport(), decision.getNearestResistance());
		log.info("Reasoning: {}", decision.getReasoning());
		log.info("========================================");

		return decision;
	}

	public Map<String, Object> executeWithRisk(List<MyCandle> candles, String productId,
			UserPreferences prefs, double accountBalance) {

		log.info("Starting full analysis with risk management for {} (balance: {})", productId, accountBalance);

		TradeDecision decision = executeAnalysis(candles, productId);

		Map<String, Object> result = new HashMap<>();
		result.put("decision", decision);

		if (decision.getSignal() != Signal.HOLD && decision.getConfidence() >= MIN_CONFIDENCE) {
			log.info("Confidence {} >= {} threshold — proceeding with risk assessment",
					decision.getConfidence(), MIN_CONFIDENCE);

			double currentPrice = candles.get(candles.size() - 1).getClose();
			RiskManager riskManager = new RiskManager();
			RiskAssessment risk = riskManager.assess(decision, prefs, currentPrice, accountBalance);

			log.info("Risk assessment result: approved={}, positionSize={}, SL={}, TP={}, R:R=1:{}",
					risk.isApproved(), risk.getPositionSize(), risk.getStopLoss(),
					risk.getTakeProfit(), risk.getRiskRewardRatio());

			result.put("riskAssessment", risk);
		} else {
			log.info("Skipping risk assessment — signal: {}, confidence: {} (threshold: {})",
					decision.getSignal(), decision.getConfidence(), MIN_CONFIDENCE);
		}

		log.info("Full analysis complete for {}", productId);
		return result;
	}
}
