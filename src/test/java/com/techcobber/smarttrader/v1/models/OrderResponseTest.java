package com.techcobber.smarttrader.v1.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OrderResponse} DTO.
 */
class OrderResponseTest {

	@Test
	@DisplayName("Builder creates response with all fields")
	void builderAllFields() {
		OrderResponse resp = OrderResponse.builder()
				.success(true)
				.orderId("db-1")
				.coinbaseOrderId("cb-1")
				.productId("BTC-USDC")
				.side("BUY")
				.orderType("MARKET")
				.status("PENDING")
				.failureReason(null)
				.message("Order placed successfully")
				.build();

		assertThat(resp.isSuccess()).isTrue();
		assertThat(resp.getOrderId()).isEqualTo("db-1");
		assertThat(resp.getCoinbaseOrderId()).isEqualTo("cb-1");
		assertThat(resp.getProductId()).isEqualTo("BTC-USDC");
		assertThat(resp.getSide()).isEqualTo("BUY");
		assertThat(resp.getOrderType()).isEqualTo("MARKET");
		assertThat(resp.getStatus()).isEqualTo("PENDING");
		assertThat(resp.getFailureReason()).isNull();
		assertThat(resp.getMessage()).isEqualTo("Order placed successfully");
	}

	@Test
	@DisplayName("Builder creates failure response")
	void builderFailure() {
		OrderResponse resp = OrderResponse.builder()
				.success(false)
				.status("FAILED")
				.failureReason("INSUFFICIENT_FUNDS")
				.message("Order placement failed")
				.build();

		assertThat(resp.isSuccess()).isFalse();
		assertThat(resp.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
	}
}
