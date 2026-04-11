package com.techcobber.smarttrader.v1.models;

import java.util.Map;

import lombok.Data;

/**
 * Inbound DTO for placing a new order.
 *
 * <p>The caller specifies the trading pair, side (BUY/SELL), order type
 * (MARKET/LIMIT), quantity, and — for limit orders — the limit price.
 * Optional {@code quoteSize} can be used for market orders sized in the
 * quote currency.</p>
 */
@Data
public class OrderRequest {

	/** Trading pair, e.g. "BTC-USDC". Required. */
	private String productId;

	/** BUY or SELL. Required. */
	private String side;

	/** MARKET or LIMIT. Required. */
	private String orderType;

	/** Base-currency quantity. Required for LIMIT orders and base-sized MARKET orders. */
	private Double baseSize;

	/** Limit price. Required for LIMIT orders. */
	private Double limitPrice;

	/** Quote-currency size. Optional; used for quote-sized MARKET orders. */
	private Double quoteSize;

	/** Optional decision factors or strategy metadata. */
	private Map<String, String> decisionFactors;

	/** Optional free-form comment. */
	private String comments;
}
