package com.techcobber.smarttrader.v1.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OrderRequest} DTO.
 */
class OrderRequestTest {

	@Test
	@DisplayName("All getters and setters work correctly")
	void gettersAndSetters() {
		OrderRequest req = new OrderRequest();
		req.setProductId("BTC-USDC");
		req.setSide("BUY");
		req.setOrderType("MARKET");
		req.setBaseSize(0.5);
		req.setLimitPrice(50000.0);
		req.setQuoteSize(25000.0);
		req.setDecisionFactors(Map.of("strategy", "price_action"));
		req.setComments("test");

		assertThat(req.getProductId()).isEqualTo("BTC-USDC");
		assertThat(req.getSide()).isEqualTo("BUY");
		assertThat(req.getOrderType()).isEqualTo("MARKET");
		assertThat(req.getBaseSize()).isEqualTo(0.5);
		assertThat(req.getLimitPrice()).isEqualTo(50000.0);
		assertThat(req.getQuoteSize()).isEqualTo(25000.0);
		assertThat(req.getDecisionFactors()).containsEntry("strategy", "price_action");
		assertThat(req.getComments()).isEqualTo("test");
	}

	@Test
	@DisplayName("Default values are null")
	void defaultsAreNull() {
		OrderRequest req = new OrderRequest();
		assertThat(req.getProductId()).isNull();
		assertThat(req.getSide()).isNull();
		assertThat(req.getOrderType()).isNull();
		assertThat(req.getBaseSize()).isNull();
		assertThat(req.getLimitPrice()).isNull();
		assertThat(req.getQuoteSize()).isNull();
		assertThat(req.getDecisionFactors()).isNull();
		assertThat(req.getComments()).isNull();
	}

	@Test
	@DisplayName("Equals and hashCode work")
	void equalsAndHashCode() {
		OrderRequest req1 = new OrderRequest();
		req1.setProductId("BTC-USDC");
		req1.setSide("BUY");

		OrderRequest req2 = new OrderRequest();
		req2.setProductId("BTC-USDC");
		req2.setSide("BUY");

		assertThat(req1).isEqualTo(req2);
		assertThat(req1.hashCode()).isEqualTo(req2.hashCode());
	}
}
