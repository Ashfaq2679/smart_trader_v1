package com.techcobber.smarttrader.v1.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.techcobber.smarttrader.v1.models.Order;
import com.techcobber.smarttrader.v1.models.OrderRequest;
import com.techcobber.smarttrader.v1.models.OrderResponse;
import com.techcobber.smarttrader.v1.services.OrderService;

/**
 * Unit tests for {@link OrderController}.
 * Uses Mockito — no database or API credentials required.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

	@Mock
	private OrderService orderService;

	@InjectMocks
	private OrderController controller;

	// =======================================================================
	// placeOrder
	// =======================================================================

	@Nested
	@DisplayName("POST /{userId} — placeOrder")
	class PlaceOrderTests {

		@Test
		@DisplayName("Returns 200 on successful order")
		void returnsOkOnSuccess() {
			OrderRequest request = buildMarketBuyRequest();
			OrderResponse serviceResponse = OrderResponse.builder()
					.success(true)
					.orderId("db-1")
					.coinbaseOrderId("cb-1")
					.productId("BTC-USDC")
					.side("BUY")
					.orderType("MARKET")
					.status("PENDING")
					.message("Order placed successfully")
					.build();

			when(orderService.placeOrder("user-1", request)).thenReturn(serviceResponse);

			ResponseEntity<?> response = controller.placeOrder("user-1", request);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isInstanceOf(OrderResponse.class);
			OrderResponse body = (OrderResponse) response.getBody();
			assertThat(body.isSuccess()).isTrue();
			assertThat(body.getCoinbaseOrderId()).isEqualTo("cb-1");
		}

		@Test
		@DisplayName("Returns 400 when order fails on Coinbase")
		void returnsBadRequestOnFailure() {
			OrderRequest request = buildMarketBuyRequest();
			OrderResponse serviceResponse = OrderResponse.builder()
					.success(false)
					.status("FAILED")
					.failureReason("INSUFFICIENT_FUNDS")
					.message("Order placement failed")
					.build();

			when(orderService.placeOrder("user-1", request)).thenReturn(serviceResponse);

			ResponseEntity<?> response = controller.placeOrder("user-1", request);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}

		@Test
		@DisplayName("Returns 400 on validation error")
		void returnsBadRequestOnValidationError() {
			OrderRequest request = new OrderRequest();
			when(orderService.placeOrder(eq("user-1"), any()))
					.thenThrow(new IllegalArgumentException("productId is required"));

			ResponseEntity<?> response = controller.placeOrder("user-1", request);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			@SuppressWarnings("unchecked")
			Map<String, String> body = (Map<String, String>) response.getBody();
			assertThat(body.get("error")).contains("productId");
		}

		@Test
		@DisplayName("Returns 500 on unexpected error")
		void returnsServerErrorOnException() {
			OrderRequest request = buildMarketBuyRequest();
			when(orderService.placeOrder("user-1", request))
					.thenThrow(new RuntimeException("DB down"));

			ResponseEntity<?> response = controller.placeOrder("user-1", request);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	// =======================================================================
	// getOrder
	// =======================================================================

	@Nested
	@DisplayName("GET /{orderId} — getOrder")
	class GetOrderTests {

		@Test
		@DisplayName("Returns 200 when order exists")
		void returnsOkWhenFound() {
			Order order = new Order();
			order.setId("db-1");
			order.setProductId("BTC-USDC");
			when(orderService.getOrder("db-1")).thenReturn(Optional.of(order));

			ResponseEntity<?> response = controller.getOrder("db-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isInstanceOf(Order.class);
		}

		@Test
		@DisplayName("Returns 404 when order not found")
		void returnsNotFoundWhenMissing() {
			when(orderService.getOrder("none")).thenReturn(Optional.empty());

			ResponseEntity<?> response = controller.getOrder("none");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// =======================================================================
	// getOrdersByUser
	// =======================================================================

	@Nested
	@DisplayName("GET /user/{userId} — getOrdersByUser")
	class GetOrdersByUserTests {

		@Test
		@DisplayName("Returns all orders for user")
		void returnsAllOrders() {
			Order o1 = new Order();
			o1.setId("1");
			Order o2 = new Order();
			o2.setId("2");
			when(orderService.getOrdersByUser("user-1")).thenReturn(List.of(o1, o2));

			ResponseEntity<List<Order>> response = controller.getOrdersByUser("user-1", null);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).hasSize(2);
		}

		@Test
		@DisplayName("Returns orders filtered by product")
		void returnsFilteredOrders() {
			Order o1 = new Order();
			o1.setId("1");
			when(orderService.getOrdersByUserAndProduct("user-1", "BTC-USDC"))
					.thenReturn(List.of(o1));

			ResponseEntity<List<Order>> response =
					controller.getOrdersByUser("user-1", "BTC-USDC");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).hasSize(1);
		}

		@Test
		@DisplayName("Returns empty list when no orders")
		void returnsEmptyList() {
			when(orderService.getOrdersByUser("user-1")).thenReturn(List.of());

			ResponseEntity<List<Order>> response = controller.getOrdersByUser("user-1", null);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isEmpty();
		}
	}

	// =======================================================================
	// cancelOrder
	// =======================================================================

	@Nested
	@DisplayName("POST /{userId}/cancel/{orderId} — cancelOrder")
	class CancelOrderTests {

		@Test
		@DisplayName("Returns 200 on successful cancellation")
		void returnsOkOnSuccess() {
			OrderResponse serviceResponse = OrderResponse.builder()
					.success(true)
					.orderId("db-1")
					.coinbaseOrderId("cb-1")
					.status("CANCELLED")
					.message("Order cancelled successfully")
					.build();

			when(orderService.cancelOrder("user-1", "db-1")).thenReturn(serviceResponse);

			ResponseEntity<?> response = controller.cancelOrder("user-1", "db-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		}

		@Test
		@DisplayName("Returns 400 when cancel fails")
		void returnsBadRequestOnFailure() {
			OrderResponse serviceResponse = OrderResponse.builder()
					.success(false)
					.failureReason("ALREADY_FILLED")
					.build();

			when(orderService.cancelOrder("user-1", "db-1")).thenReturn(serviceResponse);

			ResponseEntity<?> response = controller.cancelOrder("user-1", "db-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}

		@Test
		@DisplayName("Returns 400 when order not found")
		void returnsBadRequestWhenNotFound() {
			when(orderService.cancelOrder("user-1", "none"))
					.thenThrow(new IllegalArgumentException("Order not found: none"));

			ResponseEntity<?> response = controller.cancelOrder("user-1", "none");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}

		@Test
		@DisplayName("Returns 500 on unexpected error")
		void returnsServerErrorOnException() {
			when(orderService.cancelOrder("user-1", "db-1"))
					.thenThrow(new RuntimeException("API error"));

			ResponseEntity<?> response = controller.cancelOrder("user-1", "db-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	// =======================================================================
	// syncOrderStatus
	// =======================================================================

	@Nested
	@DisplayName("POST /{userId}/sync/{orderId} — syncOrderStatus")
	class SyncOrderStatusTests {

		@Test
		@DisplayName("Returns 200 with updated order")
		void returnsOkOnSuccess() {
			Order updated = new Order();
			updated.setId("db-1");
			updated.setStatus("FILLED");
			updated.setFilledSize("0.5");
			when(orderService.syncOrderStatus("user-1", "db-1")).thenReturn(updated);

			ResponseEntity<?> response = controller.syncOrderStatus("user-1", "db-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isInstanceOf(Order.class);
		}

		@Test
		@DisplayName("Returns 400 when order not found")
		void returnsBadRequestWhenNotFound() {
			when(orderService.syncOrderStatus("user-1", "none"))
					.thenThrow(new IllegalArgumentException("Order not found"));

			ResponseEntity<?> response = controller.syncOrderStatus("user-1", "none");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}

		@Test
		@DisplayName("Returns 500 on Coinbase API error")
		void returnsServerErrorOnApiFailure() {
			when(orderService.syncOrderStatus("user-1", "db-1"))
					.thenThrow(new RuntimeException("Failed to sync"));

			ResponseEntity<?> response = controller.syncOrderStatus("user-1", "db-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	// =======================================================================
	// Helpers
	// =======================================================================

	private OrderRequest buildMarketBuyRequest() {
		OrderRequest req = new OrderRequest();
		req.setProductId("BTC-USDC");
		req.setSide("BUY");
		req.setOrderType("MARKET");
		req.setBaseSize(0.5);
		return req;
	}
}
