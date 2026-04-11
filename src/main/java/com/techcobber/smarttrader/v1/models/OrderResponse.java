package com.techcobber.smarttrader.v1.models;

import lombok.Builder;
import lombok.Data;

/**
 * Outbound DTO returned after an order placement attempt.
 */
@Data
@Builder
public class OrderResponse {

	private boolean success;
	private String orderId;
	private String coinbaseOrderId;
	private String productId;
	private String side;
	private String orderType;
	private String status;
	private String failureReason;
	private String message;
}
