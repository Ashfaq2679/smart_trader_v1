package com.techcobber.smarttrader.v1.services;

import static com.techcobber.smarttrader.v1.models.OrderConstants.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.factory.CoinbaseAdvancedServiceFactory;
import com.coinbase.advanced.model.orders.CancelOrdersRequest;
import com.coinbase.advanced.model.orders.CancelOrdersResponse;
import com.coinbase.advanced.model.orders.CancelResult;
import com.coinbase.advanced.model.orders.CreateOrderRequest;
import com.coinbase.advanced.model.orders.CreateOrderResponse;
import com.coinbase.advanced.model.orders.GetOrderRequest;
import com.coinbase.advanced.model.orders.GetOrderResponse;
import com.coinbase.advanced.model.orders.LimitGtc;
import com.coinbase.advanced.model.orders.MarketIoc;
import com.coinbase.advanced.model.orders.OrderConfiguration;
import com.coinbase.advanced.orders.OrdersService;
import com.techcobber.smarttrader.v1.models.Order;
import com.techcobber.smarttrader.v1.models.OrderRequest;
import com.techcobber.smarttrader.v1.models.OrderResponse;
import com.techcobber.smarttrader.v1.repositories.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for placing, retrieving, and cancelling orders via the Coinbase
 * Advanced Trade API. Every order is persisted in MongoDB for future
 * algorithmic-performance and P&amp;L analysis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

	private final ClientService clientService;
	private final OrderRepository orderRepository;

	/**
	 * Places a BUY or SELL order on Coinbase and persists the result.
	 *
	 * @param userId  the authenticated user
	 * @param request order parameters
	 * @return an {@link OrderResponse} describing the outcome
	 */
	public OrderResponse placeOrder(String userId, OrderRequest request) {
		validateOrderRequest(request);

		CoinbaseAdvancedClient client = clientService.getClientForUser(userId);
		OrdersService ordersService = CoinbaseAdvancedServiceFactory.createOrdersService(client);

		String clientOrderId = UUID.randomUUID().toString();
		OrderConfiguration orderConfig = buildOrderConfiguration(request);

		CreateOrderRequest createRequest = new CreateOrderRequest.Builder()
				.productId(request.getProductId())
				.side(request.getSide())
				.clientOrderId(clientOrderId)
				.orderConfiguration(orderConfig)
				.build();

		Order order = new Order();
		order.setUserId(userId);
		order.setClientOrderId(clientOrderId);
		order.setProductId(request.getProductId());
		order.setSide(request.getSide().toUpperCase());
		order.setOrderType(request.getOrderType().toUpperCase());
		order.setQty(request.getBaseSize() != null ? request.getBaseSize() : 0.0);
		order.setLimitPrice(request.getLimitPrice());
		order.setQuoteSize(request.getQuoteSize());
		order.setDecisionFactors(request.getDecisionFactors());
		order.setComments(request.getComments());
		order.setCreatedAt(Instant.now());
		order.setUpdatedAt(Instant.now());

		try {
			CreateOrderResponse response = ordersService.createOrder(createRequest);

			if (response.isSuccess()) {
				order.setCoinbaseOrderId(response.getOrderId());
				order.setStatus(STATUS_PENDING);
				orderRepository.save(order);
				log.info("Order placed successfully for user [{}]: coinbaseOrderId={}", userId, response.getOrderId());

				return buildOrderResponse(true, order.getId(), response.getOrderId(),
						request.getProductId(), request.getSide(), request.getOrderType(),
						STATUS_PENDING, null, MSG_ORDER_PLACED);
			} else {
				order.setStatus(STATUS_FAILED);
				order.setFailureReason(response.getFailureReason());
				orderRepository.save(order);
				log.warn("Order placement failed for user [{}]: {}", userId, response.getFailureReason());

				return buildOrderResponse(false, order.getId(), null,
						request.getProductId(), request.getSide(), request.getOrderType(),
						STATUS_FAILED, response.getFailureReason(), MSG_ORDER_FAILED);
			}
		} catch (CoinbaseAdvancedException e) {
			order.setStatus(STATUS_FAILED);
			order.setFailureReason(e.getMessage());
			orderRepository.save(order);
			log.error("Coinbase API error placing order for user [{}]: {}", userId, e.getMessage());

			return buildOrderResponse(false, order.getId(), null,
					request.getProductId(), request.getSide(), request.getOrderType(),
					STATUS_FAILED, e.getMessage(), MSG_COINBASE_ERROR);
		}
	}

	/**
	 * Retrieves a persisted order by its internal database ID.
	 */
	public Optional<Order> getOrder(String orderId) {
		return orderRepository.findById(orderId);
	}

	/**
	 * Retrieves all orders for a user, ordered by most recent first.
	 */
	public List<Order> getOrdersByUser(String userId) {
		return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
	}

	/**
	 * Retrieves all orders for a user and product, ordered by most recent first.
	 */
	public List<Order> getOrdersByUserAndProduct(String userId, String productId) {
		return orderRepository.findByUserIdAndProductIdOrderByCreatedAtDesc(userId, productId);
	}

	/**
	 * Cancels an order on Coinbase and updates the local record.
	 *
	 * @param userId  the authenticated user
	 * @param orderId the internal database order ID
	 * @return an {@link OrderResponse} describing the cancellation result
	 */
	public OrderResponse cancelOrder(String userId, String orderId) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

		if (!order.getUserId().equals(userId)) {
			throw new IllegalArgumentException("Order does not belong to user: " + userId);
		}

		if (order.getCoinbaseOrderId() == null) {
			throw new IllegalArgumentException("Order has no Coinbase order ID — cannot cancel");
		}

		CoinbaseAdvancedClient client = clientService.getClientForUser(userId);
		OrdersService ordersService = CoinbaseAdvancedServiceFactory.createOrdersService(client);

		try {
			CancelOrdersRequest cancelRequest = new CancelOrdersRequest.Builder()
					.orderIds(List.of(order.getCoinbaseOrderId()))
					.build();

			CancelOrdersResponse response = ordersService.cancelOrders(cancelRequest);

			if (response.getResults() != null && !response.getResults().isEmpty()) {
				CancelResult result = response.getResults().get(0);
				if (result.isSuccess()) {
					order.setStatus(STATUS_CANCELLED);
					order.setUpdatedAt(Instant.now());
					orderRepository.save(order);
					log.info("Order cancelled for user [{}]: orderId={}", userId, orderId);

					return buildOrderResponse(true, orderId, order.getCoinbaseOrderId(),
							null, null, null,
							STATUS_CANCELLED, null, MSG_ORDER_CANCELLED);
				} else {
					log.warn("Cancel failed for order [{}]: {}", orderId, result.getFailureReason());
					return buildOrderResponse(false, orderId, order.getCoinbaseOrderId(),
							null, null, null,
							null, result.getFailureReason(), MSG_CANCEL_FAILED);
				}
			}

			return buildOrderResponse(false, orderId, null,
					null, null, null,
					null, null, MSG_CANCEL_NO_RESULT);

		} catch (CoinbaseAdvancedException e) {
			log.error("Coinbase API error cancelling order [{}]: {}", orderId, e.getMessage());
			return buildOrderResponse(false, orderId, order.getCoinbaseOrderId(),
					null, null, null,
					null, e.getMessage(), MSG_CANCEL_API_ERROR);
		}
	}

	/**
	 * Fetches the latest order status from Coinbase and updates the local record.
	 *
	 * @param userId  the authenticated user
	 * @param orderId the internal database order ID
	 * @return the updated local {@link Order}
	 */
	public Order syncOrderStatus(String userId, String orderId) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

		if (!order.getUserId().equals(userId)) {
			throw new IllegalArgumentException("Order does not belong to user: " + userId);
		}

		if (order.getCoinbaseOrderId() == null) {
			throw new IllegalArgumentException("Order has no Coinbase order ID — cannot sync");
		}

		CoinbaseAdvancedClient client = clientService.getClientForUser(userId);
		OrdersService ordersService = CoinbaseAdvancedServiceFactory.createOrdersService(client);

		try {
			GetOrderRequest getRequest = new GetOrderRequest.Builder()
					.orderId(order.getCoinbaseOrderId())
					.build();

			GetOrderResponse response = ordersService.getOrder(getRequest);
			com.coinbase.advanced.model.orders.Order cbOrder = response.getOrder();

			if (cbOrder != null) {
				order.setStatus(cbOrder.getStatus());
				order.setFilledSize(cbOrder.getFilledSize());
				order.setAverageFilledPrice(cbOrder.getAverageFilledPrice());
				order.setTotalFees(cbOrder.getTotalFees());
				order.setUpdatedAt(Instant.now());
				orderRepository.save(order);
				log.info("Order status synced for [{}]: status={}", orderId, cbOrder.getStatus());
			}

			return order;
		} catch (CoinbaseAdvancedException e) {
			log.error("Coinbase API error syncing order [{}]: {}", orderId, e.getMessage());
			throw new RuntimeException("Failed to sync order status: " + e.getMessage(), e);
		}
	}

	// ------------------------------------------------------------------
	// Internal
	// ------------------------------------------------------------------

	/**
	 * Builds an {@link OrderResponse} with the given fields.
	 * Centralizes response construction to avoid duplication across success,
	 * failure, and exception paths.
	 */
	private OrderResponse buildOrderResponse(boolean success, String orderId,
			String coinbaseOrderId, String productId, String side,
			String orderType, String status, String failureReason, String message) {

		return OrderResponse.builder()
				.success(success)
				.orderId(orderId)
				.coinbaseOrderId(coinbaseOrderId)
				.productId(productId)
				.side(side)
				.orderType(orderType)
				.status(status)
				.failureReason(failureReason)
				.message(message)
				.build();
	}

	private void validateOrderRequest(OrderRequest request) {
		if (request.getProductId() == null || request.getProductId().isBlank()) {
			throw new IllegalArgumentException("productId is required");
		}
		if (request.getSide() == null || request.getSide().isBlank()) {
			throw new IllegalArgumentException("side is required");
		}
		String side = request.getSide().toUpperCase();
		if (!SIDE_BUY.equals(side) && !SIDE_SELL.equals(side)) {
			throw new IllegalArgumentException("side must be BUY or SELL");
		}
		if (request.getOrderType() == null || request.getOrderType().isBlank()) {
			throw new IllegalArgumentException("orderType is required");
		}
		String orderType = request.getOrderType().toUpperCase();
		if (!TYPE_MARKET.equals(orderType) && !TYPE_LIMIT.equals(orderType)) {
			throw new IllegalArgumentException("orderType must be MARKET or LIMIT");
		}
		if (TYPE_LIMIT.equals(orderType)) {
			if (request.getBaseSize() == null || request.getBaseSize() <= 0) {
				throw new IllegalArgumentException("baseSize is required and must be > 0 for LIMIT orders");
			}
			if (request.getLimitPrice() == null || request.getLimitPrice() <= 0) {
				throw new IllegalArgumentException("limitPrice is required and must be > 0 for LIMIT orders");
			}
		}
		if (TYPE_MARKET.equals(orderType)) {
			boolean hasBase = request.getBaseSize() != null && request.getBaseSize() > 0;
			boolean hasQuote = request.getQuoteSize() != null && request.getQuoteSize() > 0;
			if (!hasBase && !hasQuote) {
				throw new IllegalArgumentException("Either baseSize or quoteSize must be > 0 for MARKET orders");
			}
		}
	}

	private OrderConfiguration buildOrderConfiguration(OrderRequest request) {
		String orderType = request.getOrderType().toUpperCase();

		if (TYPE_MARKET.equals(orderType)) {
			MarketIoc.Builder marketBuilder = new MarketIoc.Builder();
			if (request.getBaseSize() != null && request.getBaseSize() > 0) {
				marketBuilder.baseSize(String.valueOf(request.getBaseSize()));
			} else {
				marketBuilder.quoteSize(String.valueOf(request.getQuoteSize()));
			}
			return new OrderConfiguration.Builder()
					.marketMarketIoc(marketBuilder.build())
					.build();
		} else {
			LimitGtc limitGtc = new LimitGtc.Builder()
					.baseSize(String.valueOf(request.getBaseSize()))
					.limitPrice(String.valueOf(request.getLimitPrice()))
					.postOnly(false)
					.build();
			return new OrderConfiguration.Builder()
					.limitLimitGtc(limitGtc)
					.build();
		}
	}
}
