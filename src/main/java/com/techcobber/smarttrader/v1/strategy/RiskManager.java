package com.techcobber.smarttrader.v1.strategy;

import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.models.UserPreferences;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RiskManager {

	private static final double DEFAULT_RISK_REWARD_RATIO = 2.0;
	private static final double DEFAULT_STOP_LOSS_PERCENT = 0.02;

	@Data
	public static class RiskAssessment {
		private final double positionSize;
		private final double stopLoss;
		private final double takeProfit;
		private final double riskRewardRatio;
		private final boolean approved;
		private final String reason;

		public RiskAssessment(double positionSize, double stopLoss, double takeProfit,
				double riskRewardRatio, boolean approved, String reason) {
			this.positionSize = positionSize;
			this.stopLoss = stopLoss;
			this.takeProfit = takeProfit;
			this.riskRewardRatio = riskRewardRatio;
			this.approved = approved;
			this.reason = reason;
		}
	}

	public RiskAssessment assess(TradeDecision decision, UserPreferences prefs,
			double currentPrice, double accountBalance) {

		log.info("=== Risk Assessment ===");
		log.info("Decision: {} | Confidence: {} | Price: {}",
				decision.getSignal(), decision.getConfidence(), currentPrice);

		if (decision.getSignal() == Signal.HOLD) {
			log.info("HOLD signal — no risk assessment needed");
			return new RiskAssessment(0, 0, 0, 0, false, "HOLD signal — no trade");
		}

		// Position sizing
		double positionPct = parseDoubleOrDefault(prefs.getPositionSize(), 5.0) / 100.0;
		double positionValue = accountBalance * positionPct;
		double positionSize = positionValue / currentPrice;

		log.info("Position sizing: {}% of {} = {} units (value: {})",
				positionPct * 100, accountBalance, positionSize, positionValue);

		// Stop-loss calculation
		double stopLoss;
		if (decision.getSignal() == Signal.BUY) {
			if (decision.getNearestSupport() != null) {
				stopLoss = decision.getNearestSupport() * 0.995;
				log.info("Stop-loss set below nearest support: {}", stopLoss);
			} else {
				stopLoss = currentPrice * (1 - DEFAULT_STOP_LOSS_PERCENT);
				log.info("Stop-loss set at default {}% below price: {}", DEFAULT_STOP_LOSS_PERCENT * 100, stopLoss);
			}
		} else {
			if (decision.getNearestResistance() != null) {
				stopLoss = decision.getNearestResistance() * 1.005;
				log.info("Stop-loss set above nearest resistance: {}", stopLoss);
			} else {
				stopLoss = currentPrice * (1 + DEFAULT_STOP_LOSS_PERCENT);
				log.info("Stop-loss set at default {}% above price: {}", DEFAULT_STOP_LOSS_PERCENT * 100, stopLoss);
			}
		}

		// Take-profit calculation
		double riskPerUnit = Math.abs(currentPrice - stopLoss);
		double rewardPerUnit = riskPerUnit * DEFAULT_RISK_REWARD_RATIO;
		double takeProfit;
		if (decision.getSignal() == Signal.BUY) {
			takeProfit = currentPrice + rewardPerUnit;
		} else {
			takeProfit = currentPrice - rewardPerUnit;
		}

		double riskRewardRatio = riskPerUnit > 0 ? rewardPerUnit / riskPerUnit : 0;

		log.info("Take-profit: {} | Risk/unit: {} | Reward/unit: {} | R:R = 1:{}",
				takeProfit, riskPerUnit, rewardPerUnit, riskRewardRatio);

		// Max daily loss check
		double maxDailyLoss = parseDoubleOrDefault(prefs.getMaxDailyLoss(), Double.MAX_VALUE);
		double potentialLoss = positionSize * riskPerUnit;

		boolean approved;
		String reason;

		if (potentialLoss > maxDailyLoss) {
			approved = false;
			reason = String.format("Potential loss (%.2f) exceeds max daily loss (%.2f)", potentialLoss, maxDailyLoss);
			log.warn("Risk REJECTED: {}", reason);
		} else {
			approved = true;
			reason = String.format("Trade approved. Position: %.4f units, SL: %.2f, TP: %.2f, R:R 1:%.1f",
					positionSize, stopLoss, takeProfit, riskRewardRatio);
			log.info("Risk APPROVED: {}", reason);
		}

		log.info("=== Risk Assessment Complete: {} ===", approved ? "APPROVED" : "REJECTED");

		return new RiskAssessment(
				Math.round(positionSize * 10000.0) / 10000.0,
				Math.round(stopLoss * 100.0) / 100.0,
				Math.round(takeProfit * 100.0) / 100.0,
				Math.round(riskRewardRatio * 100.0) / 100.0,
				approved,
				reason);
	}

	private double parseDoubleOrDefault(String value, double defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			log.warn("Failed to parse '{}' as double, using default: {}", value, defaultValue);
			return defaultValue;
		}
	}
}
