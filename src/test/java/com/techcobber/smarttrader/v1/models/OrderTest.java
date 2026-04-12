package com.techcobber.smarttrader.v1.models;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Order model.
 * Verifies Lombok-generated getters, setters, equals, hashCode, and toString.
 */
class OrderTest {

    @Test
    void gettersAndSetters_allFields() {
        Order order = new Order();
        Instant now = Instant.now();
        Map<String, String> factors = new HashMap<>();
        factors.put("rsi", "oversold");
        factors.put("macd", "bullish_crossover");

        order.setId("order-id-1");
        order.setUserId("user-123");
        order.setCoinbaseOrderId("cb-order-123");
        order.setClientOrderId("client-uuid");
        order.setProductId("BTC-USDC");
        order.setSide("BUY");
        order.setOrderType("MARKET");
        order.setQty(0.5);
        order.setLimitPrice(50000.50);
        order.setQuoteSize(25000.0);
        order.setStatus("PENDING");
        order.setFilledSize("0.5");
        order.setAverageFilledPrice("50000.00");
        order.setTotalFees("12.50");
        order.setFailureReason(null);
        order.setDecisionFactors(factors);
        order.setComments("Test order");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        assertThat(order.getId()).isEqualTo("order-id-1");
        assertThat(order.getUserId()).isEqualTo("user-123");
        assertThat(order.getCoinbaseOrderId()).isEqualTo("cb-order-123");
        assertThat(order.getClientOrderId()).isEqualTo("client-uuid");
        assertThat(order.getProductId()).isEqualTo("BTC-USDC");
        assertThat(order.getSide()).isEqualTo("BUY");
        assertThat(order.getOrderType()).isEqualTo("MARKET");
        assertThat(order.getQty()).isEqualTo(0.5);
        assertThat(order.getLimitPrice()).isEqualTo(50000.50);
        assertThat(order.getQuoteSize()).isEqualTo(25000.0);
        assertThat(order.getStatus()).isEqualTo("PENDING");
        assertThat(order.getFilledSize()).isEqualTo("0.5");
        assertThat(order.getAverageFilledPrice()).isEqualTo("50000.00");
        assertThat(order.getTotalFees()).isEqualTo("12.50");
        assertThat(order.getFailureReason()).isNull();
        assertThat(order.getDecisionFactors()).isEqualTo(factors);
        assertThat(order.getComments()).isEqualTo("Test order");
        assertThat(order.getCreatedAt()).isEqualTo(now);
        assertThat(order.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void equals_sameFieldValues_areEqual() {
        Instant ts = Instant.parse("2025-06-01T12:00:00Z");

        Order order1 = new Order();
        order1.setId("id-1");
        order1.setUserId("user-1");
        order1.setProductId("ETH-USDC");
        order1.setSide("SELL");
        order1.setOrderType("LIMIT");
        order1.setQty(1.0);
        order1.setLimitPrice(3000.0);
        order1.setCreatedAt(ts);

        Order order2 = new Order();
        order2.setId("id-1");
        order2.setUserId("user-1");
        order2.setProductId("ETH-USDC");
        order2.setSide("SELL");
        order2.setOrderType("LIMIT");
        order2.setQty(1.0);
        order2.setLimitPrice(3000.0);
        order2.setCreatedAt(ts);

        assertThat(order1).isEqualTo(order2);
        assertThat(order1.hashCode()).isEqualTo(order2.hashCode());
    }

    @Test
    void equals_differentFieldValues_areNotEqual() {
        Order order1 = new Order();
        order1.setId("id-1");
        order1.setUserId("user-1");
        order1.setProductId("BTC-USDC");

        Order order2 = new Order();
        order2.setId("id-2");
        order2.setUserId("user-2");
        order2.setProductId("BTC-USDC");

        assertThat(order1).isNotEqualTo(order2);
    }

    @Test
    void toString_containsFieldValues() {
        Order order = new Order();
        order.setUserId("user-abc");
        order.setProductId("SOL-USDC");
        order.setQty(10.0);
        order.setOrderType("MARKET");
        order.setSide("BUY");

        String result = order.toString();

        assertThat(result).contains("user-abc");
        assertThat(result).contains("SOL-USDC");
        assertThat(result).contains("10.0");
        assertThat(result).contains("MARKET");
        assertThat(result).contains("BUY");
    }
}
