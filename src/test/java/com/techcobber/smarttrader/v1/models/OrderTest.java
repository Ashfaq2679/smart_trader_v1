package com.techcobber.smarttrader.v1.models;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
        LocalDateTime now = LocalDateTime.of(2025, 1, 15, 10, 30, 0);
        Map<String, String> factors = new HashMap<>();
        factors.put("rsi", "oversold");
        factors.put("macd", "bullish_crossover");

        order.setUserId("user-123");
        order.setOrderTS(now);
        order.setProductId("BTC-USD");
        order.setPrice(50000.50);
        order.setQty(0.5);
        order.setSide("BUY");
        order.setDecisionFactors(factors);
        order.setComments("Test order");

        assertThat(order.getUserId()).isEqualTo("user-123");
        assertThat(order.getOrderTS()).isEqualTo(now);
        assertThat(order.getProductId()).isEqualTo("BTC-USD");
        assertThat(order.getPrice()).isEqualTo(50000.50);
        assertThat(order.getQty()).isEqualTo(0.5);
        assertThat(order.getSide()).isEqualTo("BUY");
        assertThat(order.getDecisionFactors()).isEqualTo(factors);
        assertThat(order.getComments()).isEqualTo("Test order");
    }

    @Test
    void equals_sameFieldValues_areEqual() {
        LocalDateTime ts = LocalDateTime.of(2025, 6, 1, 12, 0);

        Order order1 = new Order();
        order1.setUserId("user-1");
        order1.setOrderTS(ts);
        order1.setProductId("ETH-USD");
        order1.setPrice(3000.0);
        order1.setQty(1.0);
        order1.setSide("SELL");

        Order order2 = new Order();
        order2.setUserId("user-1");
        order2.setOrderTS(ts);
        order2.setProductId("ETH-USD");
        order2.setPrice(3000.0);
        order2.setQty(1.0);
        order2.setSide("SELL");

        assertThat(order1).isEqualTo(order2);
        assertThat(order1.hashCode()).isEqualTo(order2.hashCode());
    }

    @Test
    void equals_differentFieldValues_areNotEqual() {
        Order order1 = new Order();
        order1.setUserId("user-1");
        order1.setProductId("BTC-USD");

        Order order2 = new Order();
        order2.setUserId("user-2");
        order2.setProductId("BTC-USD");

        assertThat(order1).isNotEqualTo(order2);
    }

    @Test
    void toString_containsFieldValues() {
        Order order = new Order();
        order.setUserId("user-abc");
        order.setProductId("SOL-USD");
        order.setPrice(150.0);

        String result = order.toString();

        // Lombok @Data toString includes class name and field values
        assertThat(result).contains("user-abc");
        assertThat(result).contains("SOL-USD");
        assertThat(result).contains("150.0");
    }
}
