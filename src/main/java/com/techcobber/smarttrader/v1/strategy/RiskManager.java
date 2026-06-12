package com.techcobber.smarttrader.v1.strategy;

import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.models.TradeDecision.Signal;
import com.techcobber.smarttrader.v1.models.UserPreferences;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Assesses risk and computes position sizing, stop-loss, take-profit, trailing stop,
 * and an effective exit floor for open BUY positions.
 *
 * <h3>Stop-Loss (BUY)</h3>
 * <pre>
 *   swingLowStop = recentSwingLow(10) × 0.995
 *   atrStop      = entryPrice − atrMultiplier × ATR
 *   stopLoss     = min(swingLowStop, atrStop)   ← tightest wins
 * </pre>
 *
 * <h3>Take-Profit (BUY)</h3>
 * <pre>
 *   rrTarget     = entryPrice + 2 × riskPerUnit
 *   resistanceCap= nearestResistance × 0.997
 *   takeProfit   = min(rrTarget, resistanceCap) ← book profit before wall
 * </pre>
 *
 * <h3>Effective Stop (exit floor for open positions)</h3>
 * <pre>
 *   effectiveStop = max(stopLoss, entryPrice × (1 − maxLoss%))
 * </pre>
 *
 * <h3>ATR Trailing Stop</h3>
 * <pre>
 *   trailingStop = recentSwingHigh(10) − trailingAtrMultiplier × ATR
 * </pre>
 *
 * <h3>Approval Gate</h3>
 * All of the following must be true:
 * <ul>
 *   <li>R:R ≥ 2.0  (computed against <em>actual</em> take-profit after resistance cap)</li>
 *   <li>actual reward ≥ minRewardPct % of entry price (default 2 %) — blocks tight-spread trades</li>
 *   <li>confidence ≥ minEntryScore (default 0.65)</li>
 *   <li>locationOK (price near EMA50 pullback or support — sourced from TradeDecision)</li>
 *   <li>!consolidationDetected</li>
 *   <li>potential loss ≤ maxDailyLoss</li>
 * </ul>
 */
@Slf4j
public class RiskManager {

	private static final double DEFAULT_RISK_REWARD_RATIO       = 2.0;
	private static final double DEFAULT_STOP_LOSS_PERCENT       = 0.02;
	private static final double DEFAULT_ATR_MULTIPLIER          = 1.5;
	private static final double DEFAULT_TRAILING_ATR_MULTIPLIER = 2.0;
	private static final double DEFAULT_MIN_ENTRY_SCORE         = 0.65;
	/** Minimum actual reward (as % of entry) after resistance cap. Blocks tight-spread trades. */
	private static final double DEFAULT_MIN_REWARD_PCT          = 2.0;
	private static final double SWING_LOW_BUFFER                = 0.005;  // 0.5 % below swing low
	private static final double RESISTANCE_BUFFER               = 0.003;  // 0.3 % below resistance

	private final AtrIndicator atrIndicator = new AtrIndicator();

	@Data
	public static class RiskAssessment {
		private final double positionSize;
		private final double stopLoss;
		private final double takeProfit;
		private final double riskRewardRatio;
		private final boolean approved;
		private final String reason;
		/** Unified exit floor: max(stopLoss, entryPrice × (1 − maxLoss%)). */
		private final double effectiveStop;
		/** ATR-based trailing stop: recentSwingHigh − k × ATR. */
		private final double trailingStop;

		public RiskAssessment(double positionSize, double stopLoss, double takeProfit,
				double riskRewardRatio, boolean approved, String reason) {
			this.positionSize = positionSize;
			this.stopLoss = stopLoss;
			this.takeProfit = takeProfit;
			this.riskRewardRatio = riskRewardRatio;
			this.approved = approved;
			this.reason = reason;
			this.effectiveStop = stopLoss;
			this.trailingStop = 0.0;
		}

		public RiskAssessment(double positionSize, double stopLoss, double takeProfit,
				double riskRewardRatio, boolean approved, String reason,
				double effectiveStop, double trailingStop) {
			this.positionSize = positionSize;
			this.stopLoss = stopLoss;
			this.takeProfit = takeProfit;
			this.riskRewardRatio = riskRewardRatio;
			this.approved = approved;
			this.reason = reason;
			this.effectiveStop = effectiveStop;
			this.trailingStop = trailingStop;
		}
	}

	/**
	 * Assesses trade risk without candle data (legacy overload — uses simple S/R stop).
	 */
	public RiskAssessment assess(TradeDecision decision, UserPreferences prefs,
			double currentPrice, double accountBalance) {
		return assess(decision, prefs, currentPrice, accountBalance, null, true, true);
	}

	/**
	 * Full risk assessment with ATR-based calculations.
	 *
	 * @param decision      trade decision from PriceActionStrategy
	 * @param prefs         user preferences (thresholds, sizing)
	 * @param currentPrice  current market price
	 * @param accountBalance account balance in quote currency
	 * @param candles       candle list for ATR / swing-level computation (may be null)
	 * @param locationOK    whether the BUY location filter passed
	 * @param htfAligned    whether HTF trend alignment passed
	 */
	public RiskAssessment assess(TradeDecision decision, UserPreferences prefs,
			double currentPrice, double accountBalance,
			List<MyCandle> candles, boolean locationOK, boolean htfAligned) {

		log.info("=== Risk Assessment ===");
		log.info("Decision: {} | Confidence: {} | Price: {}",
				decision.getSignal(), decision.getConfidence(), currentPrice);

		if (decision.getSignal() == Signal.HOLD) {
			log.info("HOLD signal — no risk assessment needed");
			return new RiskAssessment(0, 0, 0, 0, false, "HOLD signal — no trade");
		}

		// --- Pre-checks ---
		double minEntryScore = parseDoubleOrDefault(prefs != null ? prefs.getMinEntryScore() : null, DEFAULT_MIN_ENTRY_SCORE);
		boolean consolidating = Boolean.TRUE.equals(decision.getConsolidationDetected());

		if (decision.getSignal() == Signal.BUY) {
			if (decision.getConfidence() < minEntryScore) {
				String reason = String.format("BUY rejected: confidence %.2f < minEntryScore %.2f",
						decision.getConfidence(), minEntryScore);
				log.warn(reason);
				return new RiskAssessment(0, 0, 0, 0, false, reason);
			}
			if (consolidating) {
				log.warn("BUY rejected: consolidation detected");
				return new RiskAssessment(0, 0, 0, 0, false, "BUY rejected: consolidating market");
			}
			if (!locationOK) {
				log.warn("BUY rejected: location filter not met");
				return new RiskAssessment(0, 0, 0, 0, false, "BUY rejected: price not near EMA50 pullback or support");
			}
			if (!htfAligned) {
				log.warn("BUY rejected: HTF trend not aligned");
				return new RiskAssessment(0, 0, 0, 0, false, "BUY rejected: HTF trend not aligned");
			}
		}

		// --- Position sizing ---
		double positionPct   = parseDoubleOrDefault(prefs != null ? prefs.getPositionSize() : null, 5.0) / 100.0;
		double positionValue = accountBalance * positionPct;
		double positionSize  = positionValue / currentPrice;

		log.info("Position sizing: {}% of {} = {} units (value: {})",
				positionPct * 100, accountBalance, positionSize, positionValue);

		// --- ATR values (if candles available) ---
		double atr             = (candles != null && !candles.isEmpty()) ? atrIndicator.calculate(candles) : 0.0;
		double atrMultiplier   = parseDoubleOrDefault(prefs != null ? prefs.getAtrMultiplier() : null, DEFAULT_ATR_MULTIPLIER);
		double trailingK       = parseDoubleOrDefault(prefs != null ? prefs.getTrailingAtrMultiplier() : null, DEFAULT_TRAILING_ATR_MULTIPLIER);
		double recentSwingLow  = (candles != null && !candles.isEmpty()) ? atrIndicator.getRecentSwingLow(candles, 10) : 0.0;
		double recentSwingHigh = (candles != null && !candles.isEmpty()) ? atrIndicator.getRecentSwingHigh(candles, 10) : 0.0;

		// --- Stop-loss ---
		double stopLoss;
		if (decision.getSignal() == Signal.BUY) {
			if (atr > 0 && recentSwingLow > 0) {
				double swingLowStop = recentSwingLow * (1.0 - SWING_LOW_BUFFER);
				double atrStop      = currentPrice - (atrMultiplier * atr);
				stopLoss = Math.min(swingLowStop, atrStop);
				log.info("ATR stop-loss: swingLowStop={} | atrStop={} → using {}",
						String.format("%.4f", swingLowStop), String.format("%.4f", atrStop), String.format("%.4f", stopLoss));
			} else if (decision.getNearestSupport() != null) {
				stopLoss = decision.getNearestSupport() * (1.0 - SWING_LOW_BUFFER);
				log.info("S/R stop-loss set below nearest support: {}", stopLoss);
			} else {
				stopLoss = currentPrice * (1.0 - DEFAULT_STOP_LOSS_PERCENT);
				log.info("Default stop-loss at {}% below price: {}", DEFAULT_STOP_LOSS_PERCENT * 100, stopLoss);
			}
		} else {
			if (decision.getNearestResistance() != null) {
				stopLoss = decision.getNearestResistance() * 1.005;
				log.info("SELL stop-loss set above nearest resistance: {}", stopLoss);
			} else {
				stopLoss = currentPrice * (1.0 + DEFAULT_STOP_LOSS_PERCENT);
				log.info("Default SELL stop-loss at {}% above price: {}", DEFAULT_STOP_LOSS_PERCENT * 100, stopLoss);
			}
		}

		// --- Take-profit (capped at resistance) ---
		double riskPerUnit   = Math.abs(currentPrice - stopLoss);
		double rewardPerUnit = riskPerUnit * DEFAULT_RISK_REWARD_RATIO;  // used only to compute rrTarget
		double takeProfit;
		if (decision.getSignal() == Signal.BUY) {
			double rrTarget = currentPrice + rewardPerUnit;
			if (decision.getNearestResistance() != null) {
				double resistanceCap = decision.getNearestResistance() * (1.0 - RESISTANCE_BUFFER);
				takeProfit = Math.min(rrTarget, resistanceCap);
				log.info("Take-profit: rrTarget={} | resistanceCap={} → using {}",
						String.format("%.4f", rrTarget), String.format("%.4f", resistanceCap), String.format("%.4f", takeProfit));
			} else {
				takeProfit = rrTarget;
				log.info("Take-profit (no resistance cap): {}", takeProfit);
			}
		} else {
			takeProfit = currentPrice - rewardPerUnit;
		}

		// R:R is computed from the ACTUAL (post-cap) reward so that a resistance-capped TP
		// that shrinks reward below 2×risk is correctly detected and rejected.
		double actualReward    = decision.getSignal() == Signal.BUY
				? takeProfit - currentPrice
				: currentPrice - takeProfit;
		double riskRewardRatio = riskPerUnit > 0 ? actualReward / riskPerUnit : 0;

		// --- Effective stop (unified exit floor for open positions) ---
		double maxDailyLossNum = parseDoubleOrDefault(prefs != null ? prefs.getMaxDailyLoss() : null, Double.MAX_VALUE);
		double maxLossPct      = maxDailyLossNum < Double.MAX_VALUE ? maxDailyLossNum : 5.0;
		double effectiveStop   = Math.max(stopLoss, currentPrice * (1.0 - maxLossPct / 100.0));

		// --- ATR trailing stop ---
		double trailingStop = 0.0;
		if (atr > 0 && recentSwingHigh > 0) {
			trailingStop = recentSwingHigh - (trailingK * atr);
			log.info("Trailing stop: swingHigh={} - {}×ATR({}) = {}",
					String.format("%.4f", recentSwingHigh), trailingK,
					String.format("%.4f", atr), String.format("%.4f", trailingStop));
		}

		// --- Minimum reward % gate (blocks tight-spread trades where fees kill the edge) ---
		double minRewardPct    = parseDoubleOrDefault(prefs != null ? prefs.getMinRewardPct() : null, DEFAULT_MIN_REWARD_PCT);
		double actualRewardPct = currentPrice > 0 ? (actualReward / currentPrice) * 100.0 : 0;
		if (decision.getSignal() == Signal.BUY && actualRewardPct < minRewardPct) {
			String minRewardReason = String.format(
					"BUY rejected: actual reward %.2f%% < minRewardPct %.2f%% (TP=%.4f, entry=%.4f)",
					actualRewardPct, minRewardPct, takeProfit, currentPrice);
			log.warn(minRewardReason);
			return new RiskAssessment(0, round(stopLoss), round(takeProfit),
					round(riskRewardRatio), false, minRewardReason, round(effectiveStop), round(trailingStop));
		}

		// --- R:R gate (require >= 2.0) ---
		if (decision.getSignal() == Signal.BUY && riskRewardRatio < DEFAULT_RISK_REWARD_RATIO) {
			String reason = String.format("BUY rejected: R:R %.2f < required %.1f", riskRewardRatio, DEFAULT_RISK_REWARD_RATIO);
			log.warn(reason);
			return new RiskAssessment(0, round(stopLoss), round(takeProfit),
					round(riskRewardRatio), false, reason, round(effectiveStop), round(trailingStop));
		}

		// --- Max daily loss check ---
		double potentialLoss = positionSize * riskPerUnit;
		boolean approved;
		String reason;

		if (maxDailyLossNum < Double.MAX_VALUE && potentialLoss > maxDailyLossNum) {
			approved = false;
			reason = String.format("Potential loss (%.2f) exceeds max daily loss (%.2f)", potentialLoss, maxDailyLossNum);
			log.warn("Risk REJECTED: {}", reason);
		} else {
			approved = true;
			reason = String.format(
					"Trade approved. Position: %.4f units, SL: %.4f, TP: %.4f, R:R 1:%.1f, EffectiveStop: %.4f, TrailingStop: %.4f",
					positionSize, stopLoss, takeProfit, riskRewardRatio, effectiveStop, trailingStop);
			log.info("Risk APPROVED: {}", reason);
		}

		log.info("=== Risk Assessment Complete: {} ===", approved ? "APPROVED" : "REJECTED");

		return new RiskAssessment(
				round4(positionSize),
				round(stopLoss),
				round(takeProfit),
				round(riskRewardRatio),
				approved,
				reason,
				round(effectiveStop),
				round(trailingStop));
	}

	// ------------------------------------------------------------------
	// Internal
	// ------------------------------------------------------------------

	private double parseDoubleOrDefault(String value, double defaultValue) {
		if (value == null || value.isBlank()) return defaultValue;
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			log.warn("Failed to parse '{}' as double, using default: {}", value, defaultValue);
			return defaultValue;
		}
	}

	private double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private double round4(double value) {
		return Math.round(value * 10000.0) / 10000.0;
	}
}
