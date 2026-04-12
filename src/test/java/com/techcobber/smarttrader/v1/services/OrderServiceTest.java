package com.techcobber.smarttrader.v1.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.factory.CoinbaseAdvancedServiceFactory;
import com.coinbase.advanced.model.orders.CancelOrdersResponse;
import com.coinbase.advanced.model.orders.CancelResult;
import com.coinbase.advanced.model.orders.CreateOrderResponse;
import com.coinbase.advanced.model.orders.GetOrderResponse;
import com.coinbase.advanced.orders.OrdersService;
import com.techcobber.smarttrader.v1.models.Order;
import com.techcobber.smarttrader.v1.models.OrderRequest;
import com.techcobber.smarttrader.v1.models.OrderResponse;
import com.techcobber.smarttrader.v1.repositories.OrderRepository;

/**
 * Unit tests for {@link OrderService}.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	private ClientService clientService;

	@Mock
	private OrderRepository orderRepository;

	@InjectMocks
	private OrderService orderService;

	// =======================================================================
	// Validation tests
	// =======================================================================

	@Nested
	@DisplayName("Order request validation")
	class ValidationTests {

		@Test
		@DisplayName("Throws when productId is missing")
		void throwsWhenProductIdMissing() {
			OrderRequest req = new OrderRequest();
			req.setSide("BUY");
			req.setOrderType("MARKET");
			req.setBaseSize(1.0);

			assertThatThrownBy(() -> orderService.placeOrder("user-1", req))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("productId");
		}

		@Test
		@DisplayName("Throws when side is invalid")
		void throwsWhenSideInvalid() {
			OrderRequest req = new OrderRequest();
			req.setProductId("BTC-USDC");
			req.setSide("HOLD");
			req.setOrderType("MARKET");
			req.setBaseSize(1.0);

			assertThatThrownBy(() -> orderService.placeOrder("user-1", req))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("side must be BUY or SELL");
		}

		@Test
		@DisplayName("Throws when orderType is invalid")
		void throwsWhenOrderTypeInvalid() {
			OrderRequest req = new OrderRequest();
			req.setProductId("BTC-USDC");
			req.setSide("BUY");
			req.setOrderType("STOP");
			req.setBaseSize(1.0);

			assertThatThrownBy(() -> orderService.placeOrder("user-1", req))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("orderType must be MARKET or LIMIT");
		}

		@Test
		@DisplayName("Throws when LIMIT order missing baseSize")
		void throwsWhenLimitMissingBaseSize() {
			OrderRequest req = new OrderRequest();
			req.setProductId("BTC-USDC");
			req.setSide("BUY");
			req.setOrderType("LIMIT");
			req.setLimitPrice(50000.0);

			assertThatThrownBy(() -> orderService.placeOrder("user-1", req))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("baseSize");
		}

		@Test
		@DisplayName("Throws when LIMIT order missing limitPrice")
		void throwsWhenLimitMissingPrice() {
			OrderRequest req = new OrderRequest();
			req.setProductId("BTC-USDC");
			req.setSide("BUY");
			req.setOrderType("LIMIT");
			req.setBaseSize(1.0);

			assertThatThrownBy(() -> orderService.placeOrder("user-1", req))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("limitPrice");
		}

		@Test
		@DisplayName("Throws when MARKET order has no size")
		void throwsWhenMarketNoSize() {
			OrderRequest req = new OrderRequest();
			req.setProductId("BTC-USDC");
			req.setSide("BUY");
			req.setOrderType("MARKET");

			assertThatThrownBy(() -> orderService.placeOrder("user-1", req))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("baseSize or quoteSize");
		}
	}

	// =======================================================================
	// placeOrder — success
	// =======================================================================

	@Nested
	@DisplayName("placeOrder — successful placement")
	class PlaceOrderSuccessTests {

		@Test
		@DisplayName("Market BUY order with baseSize succeeds")
		void marketBuyBaseSize() throws Exception {
			OrderRequest req = buildMarketBuyRequest();
			CreateOrderResponse cbResponse = mockSuccessResponse("cb-order-1");

			try (MockedStatic<CoinbaseAdvancedServiceFactory> factory =
						 mockStatic(CoinbaseAdvancedServiceFactory.class)) {
				OrdersService ordersService = mock(OrdersService.class);
				CoinbaseAdvancedClient client = mock(CoinbaseAdvancedClient.class);
				when(clientService.getClientForUser("user-1")).thenReturn(client);
				factory.when(() -> CoinbaseAdvancedServiceFactory.createOrdersService(client))
						.thenReturn(ordersService);
				when(ordersService.createOrder(any())).thenReturn(cbResponse);
				when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
					Order o = inv.getArgument(0);
					o.setId("db-order-1");
					return o;
				});

				OrderResponse response = orderService.placeOrder("user-1", req);

				assertThat(response.isSuccess()).isTrue();
				assertThat(response.getCoinbaseOrderId()).isEqualTo("cb-order-1");
				assertThat(response.getSide()).isEqualTo("BUY");
				assertThat(response.getOrderType()).isEqualTo("MARKET");

				ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
				verify(orderRepository).save(captor.capture());
				Order saved = captor.getValue();
				assertThat(saved.getUserId()).isEqualTo("user-1");
				assertThat(saved.getProductId()).isEqualTo("BTC-USDC");
				assertThat(saved.getSide()).isEqualTo("BUY");
				assertThat(saved.getOrderType()).isEqualTo("MARKET");
				assertThat(saved.getStatus()).isEqualTo("PENDING");
			}
		}

		@Test
		@DisplayName("Market BUY order with quoteSize succeeds")
		void marketBuyQuoteSize() throws Exception {
			OrderRequest req = new OrderRequest();
			req.setProductId("ETH-USDC");
			req.setSide("BUY");
			req.setOrderType("MARKET");
			req.setQuoteSize(1000.0);

			CreateOrderResponse cbResponse = mockSuccessResponse("cb-order-2");

			try (MockedStatic<CoinbaseAdvancedServiceFactory> factory =
						 mockStatic(CoinbaseAdvancedServiceFactory.class)) {
				OrdersService ordersService = mock(OrdersService.class);
				CoinbaseAdvancedClient client = mock(CoinbaseAdvancedClient.class);
				when(clientService.getClientForUser("user-1")).thenReturn(client);
				factory.when(() -> CoinbaseAdvancedServiceFactory.createOrdersService(client))
						.thenReturn(ordersService);
				when(ordersService.createOrder(any())).thenReturn(cbResponse);
				when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

				OrderResponse response = orderService.placeOrder("user-1", req);

				assertThat(response.isSuccess()).isTrue();
				assertThat(response.getCoinbaseOrderId()).isEqualTo("cb-order-2");
			}
		}

		@Test
		@DisplayName("Limit SELL order succeeds")
		void limitSellOrder() throws Exception {
			OrderRequest req = new OrderRequest();
			req.setProductId("BTC-USDC");
			req.setSide("SELL");
			req.setOrderType("LIMIT");
			req.setBaseSize(0.1);
			req.setLimitPrice(60000.0);
			req.setDecisionFactors(Map.of("strategy", "price_action"));
			req.setComments("Take profit");

			CreateOrderResponse cbResponse = mockSuccessResponse("cb-order-3");

			try (MockedStatic<CoinbaseAdvancedServiceFactory> factory =
						 mockStatic(CoinbaseAdvancedServiceFactory.class)) {
				OrdersService ordersService = mock(OrdersService.class);
				CoinbaseAdvancedClient client = mock(CoinbaseAdvancedClient.class);
				when(clientService.getClientForUser("user-1")).thenReturn(client);
				factory.when(() -> CoinbaseAdvancedServiceFactory.createOrdersService(client))
						.thenReturn(ordersService);
				when(ordersService.createOrder(any())).thenReturn(cbResponse);
				when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

				OrderResponse response = orderService.placeOrder("user-1", req);

				assertThat(response.isSuccess()).isTrue();
				assertThat(response.getSide()).isEqualTo("SELL");
				assertThat(response.getOrderType()).isEqualTo("LIMIT");

				ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
				verify(orderRepository).save(captor.capture());
				Order saved = captor.getValue();
				assertThat(saved.getLimitPrice()).isEqualTo(60000.0);
				assertThat(saved.getDecisionFactors()).containsEntry("strategy", "price_action");
				assertThat(saved.getComments()).isEqualTo("Take profit");
			}
		}
	}

	// =======================================================================
	// placeOrder — failure
	// =======================================================================

	@Nested
	@DisplayName("placeOrder — failure scenarios")
	class PlaceOrderFailureTests {

		@Test
		@DisplayName("Returns failure when Coinbase rejects the order")
		void coinbaseRejectsOrder() throws Exception {
			OrderRequest req = buildMarketBuyRequest();
			CreateOrderResponse cbResponse = mockFailureResponse("INSUFFICIENT_FUNDS");

			try (MockedStatic<CoinbaseAdvancedServiceFactory> factory =
						 mockStatic(CoinbaseAdvancedServiceFactory.class)) {
				OrdersService ordersService = mock(OrdersService.class);
				CoinbaseAdvancedClient client = mock(CoinbaseAdvancedClient.class);
				when(clientService.getClientForUser("user-1")).thenReturn(client);
				factory.when(() -> CoinbaseAdvancedServiceFactory.createOrdersService(client))
						.thenReturn(ordersService);
				when(ordersService.createOrder(any())).thenReturn(cbResponse);
				when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

				OrderResponse response = orderService.placeOrder("user-1", req);

				assertThat(response.isSuccess()).isFalse();
				assertThat(response.getStatus()).isEqualTo("FAILED");
				assertThat(response.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");

				ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
				verify(orderRepository).save(captor.capture());
				assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
			}
		}

		@Test
		@DisplayName("Returns failure when Coinbase throws exception")
		void coinbaseThrowsException() throws Exception {
			OrderRequest req = buildMarketBuyRequest();

			try (MockedStatic<CoinbaseAdvancedServiceFactory> factory =
						 mockStatic(CoinbaseAdvancedServiceFactory.class)) {
				OrdersService ordersService = mock(OrdersService.class);
				CoinbaseAdvancedClient client = mock(CoinbaseAdvancedClient.class);
				when(clientService.getClientForUser("user-1")).thenReturn(client);
				factory.when(() -> CoinbaseAdvancedServiceFactory.createOrdersService(client))
						.thenReturn(ordersService);
				when(ordersService.createOrder(any()))
						.thenThrow(new CoinbaseAdvancedException(500, "Internal Server Error"));
				when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

				OrderResponse response = orderService.placeOrder("user-1", req);

				assertThat(response.isSuccess()).isFalse();
				assertThat(response.getStatus()).isEqualTo("FAILED");
				assertThat(response.getMessage()).isEqualTo("Coinbase API error");
				verify(orderRepository).save(any(Order.class));
			}
		}
	}

	// =======================================================================
	// getOrder / getOrdersByUser
	// =======================================================================

	@Nested
	@DisplayName("Order retrieval")
	class OrderRetrievalTests {

		@Test
		@DisplayName("getOrder returns order when found")
		void getOrderFound() {
			Order order = new Order();
			order.setId("db-1");
			when(orderRepository.findById("db-1")).thenReturn(Optional.of(order));

			Optional<Order> result = orderService.getOrder("db-1");
			assertThat(result).isPresent();
			assertThat(result.get().getId()).isEqualTo("db-1");
		}

		@Test
		@DisplayName("getOrder returns empty when not found")
		void getOrderNotFound() {
			when(orderRepository.findById("none")).thenReturn(Optional.empty());

			assertThat(orderService.getOrder("none")).isEmpty();
		}

		@Test
		@DisplayName("getOrdersByUser returns list")
		void getOrdersByUser() {
			Order o1 = new Order();
			o1.setId("1");
			Order o2 = new Order();
			o2.setId("2");
			when(orderRepository.findByUserIdOrderByCreatedAtDesc("user-1"))
					.thenReturn(List.of(o1, o2));

			List<Order> result = orderService.getOrdersByUser("user-1");
			assertThat(result).hasSize(2);
		}

		@Test
		@DisplayName("getOrdersByUserAndProduct returns filtered list")
		void getOrdersByUserAndProduct() {
			Order o1 = new Order();
			o1.setId("1");
			when(orderRepository.findByUserIdAndProductIdOrderByCreatedAtDesc("user-1", "BTC-USDC"))
					.thenReturn(List.of(o1));

			List<Order> result = orderService.getOrdersByUserAndProduct("user-1", "BTC-USDC");
			assertThat(result).hasSize(1);
		}
	}

	// =======================================================================
	// cancelOrder
	// =======================================================================

	@Nested
	@DisplayName("cancelOrder")
	class CancelOrderTests {

		@Test
		@DisplayName("Cancels order successfully")
		void cancelSuccess() throws Exception {
			Order order = buildPersistedOrder("user-1", "cb-100");

			CancelResult cancelResult = new CancelResult();
			cancelResult.setSuccess(true);
			cancelResult.setOrderId("cb-100");
			CancelOrdersResponse cancelResponse = new CancelOrdersResponse();
			cancelResponse.setResults(List.of(cancelResult));

			when(orderRepository.findById("db-1")).thenReturn(Optional.of(order));

			try (MockedStatic<CoinbaseAdvancedServiceFactory> factory =
						 mockStatic(CoinbaseAdvancedServiceFactory.class)) {
				OrdersService ordersService = mock(OrdersService.class);
				CoinbaseAdvancedClient client = mock(CoinbaseAdvancedClient.class);
				when(clientService.getClientForUser("user-1")).thenReturn(client);
				factory.when(() -> CoinbaseAdvancedServiceFactory.createOrdersService(client))
						.thenReturn(ordersService);
				when(ordersService.cancelOrders(any())).thenReturn(cancelResponse);
				when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

				OrderResponse response = orderService.cancelOrder("user-1", "db-1");

				assertThat(response.isSuccess()).isTrue();
				assertThat(response.getStatus()).isEqualTo("CANCELLED");
				verify(orderRepository).save(any(Order.class));
			}
		}

		@Test
		@DisplayName("Throws when order not found")
		void orderNotFound() {
			when(orderRepository.findById("none")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> orderService.cancelOrder("user-1", "none"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Order not found");
		}

		@Test
		@DisplayName("Throws when order belongs to different user")
		void wrongUser() {
			Order order = buildPersistedOrder("user-2", "cb-100");
			when(orderRepository.findById("db-1")).thenReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.cancelOrder("user-1", "db-1"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("does not belong to user");
		}

		@Test
		@DisplayName("Throws when order has no Coinbase ID")
		void noCoinbaseId() {
			Order order = new Order();
			order.setId("db-1");
			order.setUserId("user-1");
			when(orderRepository.findById("db-1")).thenReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.cancelOrder("user-1", "db-1"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("no Coinbase order ID");
		}

		@Test
		@DisplayName("Returns failure when cancel is rejected by Coinbase")
		void cancelRejected() throws Exception {
			Order order = buildPersistedOrder("user-1", "cb-100");

			CancelResult cancelResult = new CancelResult();
			cancelResult.setSuccess(false);
			cancelResult.setFailureReason("ALREADY_FILLED");
			CancelOrdersResponse cancelResponse = new CancelOrdersResponse();
			cancelResponse.setResults(List.of(cancelResult));

			when(orderRepository.findById("db-1")).thenReturn(Optional.of(order));

			try (MockedStatic<CoinbaseAdvancedServiceFactory> factory =
						 mockStatic(CoinbaseAdvancedServiceFactory.class)) {
				OrdersService ordersService = mock(OrdersService.class);
				CoinbaseAdvancedClient client = mock(CoinbaseAdvancedClient.class);
				when(clientService.getClientForUser("user-1")).thenReturn(client);
				factory.when(() -> CoinbaseAdvancedServiceFactory.createOrdersService(client))
						.thenReturn(ordersService);
				when(ordersService.cancelOrders(any())).thenReturn(cancelResponse);

				OrderResponse response = orderService.cancelOrder("user-1", "db-1");

				assertThat(response.isSuccess()).isFalse();
				assertThat(response.getFailureReason()).isEqualTo("ALREADY_FILLED");
			}
		}
	}

	// =======================================================================
	// syncOrderStatus
	// =======================================================================

	@Nested
	@DisplayName("syncOrderStatus")
	class SyncOrderStatusTests {

		@Test
		@DisplayName("Syncs order status from Coinbase")
		void syncSuccess() throws Exception {
			Order order = buildPersistedOrder("user-1", "cb-100");

			com.coinbase.advanced.model.orders.Order cbOrder = new com.coinbase.advanced.model.orders.Order();
			cbOrder.setStatus("FILLED");
			cbOrder.setFilledSize("0.5");
			cbOrder.setAverageFilledPrice("50000.00");
			cbOrder.setTotalFees("12.50");

			GetOrderResponse getResponse = new GetOrderResponse();
			getResponse.setOrder(cbOrder);

			when(orderRepository.findById("db-1")).thenReturn(Optional.of(order));

			try (MockedStatic<CoinbaseAdvancedServiceFactory> factory =
						 mockStatic(CoinbaseAdvancedServiceFactory.class)) {
				OrdersService ordersService = mock(OrdersService.class);
				CoinbaseAdvancedClient client = mock(CoinbaseAdvancedClient.class);
				when(clientService.getClientForUser("user-1")).thenReturn(client);
				factory.when(() -> CoinbaseAdvancedServiceFactory.createOrdersService(client))
						.thenReturn(ordersService);
				when(ordersService.getOrder(any())).thenReturn(getResponse);
				when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

				Order result = orderService.syncOrderStatus("user-1", "db-1");

				assertThat(result.getStatus()).isEqualTo("FILLED");
				assertThat(result.getFilledSize()).isEqualTo("0.5");
				assertThat(result.getAverageFilledPrice()).isEqualTo("50000.00");
				assertThat(result.getTotalFees()).isEqualTo("12.50");
				verify(orderRepository).save(any(Order.class));
			}
		}

		@Test
		@DisplayName("Throws when Coinbase API fails")
		void coinbaseApiFails() throws Exception {
			Order order = buildPersistedOrder("user-1", "cb-100");
			when(orderRepository.findById("db-1")).thenReturn(Optional.of(order));

			try (MockedStatic<CoinbaseAdvancedServiceFactory> factory =
						 mockStatic(CoinbaseAdvancedServiceFactory.class)) {
				OrdersService ordersService = mock(OrdersService.class);
				CoinbaseAdvancedClient client = mock(CoinbaseAdvancedClient.class);
				when(clientService.getClientForUser("user-1")).thenReturn(client);
				factory.when(() -> CoinbaseAdvancedServiceFactory.createOrdersService(client))
						.thenReturn(ordersService);
				when(ordersService.getOrder(any()))
						.thenThrow(new CoinbaseAdvancedException(404, "Not Found"));

				assertThatThrownBy(() -> orderService.syncOrderStatus("user-1", "db-1"))
						.isInstanceOf(RuntimeException.class)
						.hasMessageContaining("Failed to sync order status");
			}
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

	private CreateOrderResponse mockSuccessResponse(String orderId) {
		CreateOrderResponse response = mock(CreateOrderResponse.class);
		when(response.isSuccess()).thenReturn(true);
		when(response.getOrderId()).thenReturn(orderId);
		return response;
	}

	private CreateOrderResponse mockFailureResponse(String reason) {
		CreateOrderResponse response = mock(CreateOrderResponse.class);
		when(response.isSuccess()).thenReturn(false);
		when(response.getFailureReason()).thenReturn(reason);
		return response;
	}

	private Order buildPersistedOrder(String userId, String cbOrderId) {
		Order order = new Order();
		order.setId("db-1");
		order.setUserId(userId);
		order.setCoinbaseOrderId(cbOrderId);
		order.setProductId("BTC-USDC");
		order.setSide("BUY");
		order.setOrderType("MARKET");
		order.setStatus("PENDING");
		order.setCreatedAt(Instant.now());
		return order;
	}
}
