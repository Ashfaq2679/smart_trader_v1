package com.techcobber.smarttrader.v1.models;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document representing a trading order placed via the Coinbase
 * Advanced Trade API.
 *
 * <p>Every order placed through the application is persisted here to enable
 * algorithmic performance and P&amp;L analysis.</p>
 */
@Data
@Document("orders")
public class Order {

	@Id
	private String id;

	@Indexed
	private String userId;

	/** Coinbase-assigned order identifier returned after successful placement. */
	private String coinbaseOrderId;

	/** Client-generated idempotency key sent with the order request. */
	private String clientOrderId;

	/** Trading pair, e.g. "BTC-USDC". */
	private String productId;

	/** BUY or SELL. */
	private String side;

	/** MARKET or LIMIT. */
	private String orderType;

	/** Requested quantity (base size). */
	private double qty;

	/** Limit price — only applicable for LIMIT orders. */
	private Double limitPrice;

	/** Quote size — only applicable for MARKET orders sized in quote currency. */
	private Double quoteSize;

	/** Order status as reported by Coinbase (e.g. PENDING, OPEN, FILLED, CANCELLED, FAILED). */
	private String status;

	/** Quantity actually filled. */
	private String filledSize;

	/** Weighted average fill price. */
	private String averageFilledPrice;

	/** Total fees charged by the exchange. */
	private String totalFees;

	/** Failure or rejection reason, if any. */
	private String failureReason;

	/** Free-form decision factors that led to this order (strategy output, indicators, etc.). */
	private Map<String, String> decisionFactors;

	/** Optional human-readable comment or note. */
	private String comments;

	private Instant createdAt;
	private Instant updatedAt;
}
