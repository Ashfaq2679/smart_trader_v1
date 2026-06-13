package com.techcobber.smarttrader.v1.models;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document storing per-user trading configuration.
 *
 * <p>Each user has at most one preferences document, uniquely identified
 * by {@code userId}. Default values are applied when a user has not yet
 * customised their preferences.</p>
 */
@Data
@Document("user_preferences")
public class UserPreferences {

	@Id
	private String id;

	@Indexed(unique = true)
	private String userId;

	private String strategy;
	private String granularity;
	private String baseAsset;
	private String quoteAsset;
	private String positionSize;
	private String maxDailyLoss;
	private String timezone;
	private boolean enabled;
	private Instant updatedAt;

	/** Maximum % above EMA50 a price may be and still qualify as a valid BUY entry. Default "3.0". */
	private String ema50ThresholdPct;

	/** Maximum % above nearest support a price may be and still qualify as a valid BUY entry. Default "2.0". */
	private String supportProximityPct;

	/** ATR multiplier (k) for the ATR component of stop-loss: stopLoss = entry - k × ATR. Default "1.5". */
	private String atrMultiplier;

	/** ATR spike threshold: if currentATR > spikeMultiplier × avgATR the entry is skipped. Default "2.0". */
	private String atrSpikeMultiplier;

	/** ATR multiplier (k) for the trailing stop: trailingStop = recentSwingHigh - k × ATR. Default "2.0". */
	private String trailingAtrMultiplier;

	/** Minimum confidence score required before a BUY is approved by the risk manager. Default "0.65". */
	private String minEntryScore;

	/**
	 * Maximum % below nearest resistance a price may be and still qualify as a valid BUY entry.
	 * If the gap between price and resistance is ≤ this threshold the entry is rejected — there
	 * is not enough room to run before hitting the resistance wall. Default "2.0" (2 %).
	 */
	private String resistanceProximityPct;

	/**
	 * Minimum actual reward as a percentage of entry price that a BUY must offer
	 * after the take-profit is capped at resistance. Filters out tight-spread trades
	 * where fees and slippage would eliminate any edge. Default "2.0" (2 %).
	 */
	private String minRewardPct;
}
