package com.techcobber.smarttrader.v1.services;

import static com.techcobber.smarttrader.v1.models.OrderConstants.MSG_CANCEL_API_ERROR;
import static com.techcobber.smarttrader.v1.models.OrderConstants.MSG_CANCEL_FAILED;
import static com.techcobber.smarttrader.v1.models.OrderConstants.MSG_CANCEL_NO_RESULT;
import static com.techcobber.smarttrader.v1.models.OrderConstants.MSG_COINBASE_ERROR;
import static com.techcobber.smarttrader.v1.models.OrderConstants.MSG_ORDER_CANCELLED;
import static com.techcobber.smarttrader.v1.models.OrderConstants.MSG_ORDER_FAILED;
import static com.techcobber.smarttrader.v1.models.OrderConstants.MSG_ORDER_PLACED;
import static com.techcobber.smarttrader.v1.models.OrderConstants.SIDE_BUY;
import static com.techcobber.smarttrader.v1.models.OrderConstants.SIDE_SELL;
import static com.techcobber.smarttrader.v1.models.OrderConstants.STATUS_CANCELLED;
import static com.techcobber.smarttrader.v1.models.OrderConstants.STATUS_FAILED;
import static com.techcobber.smarttrader.v1.models.OrderConstants.STATUS_PENDING;
import static com.techcobber.smarttrader.v1.models.OrderConstants.STATUS_PLACED;
import static com.techcobber.smarttrader.v1.models.OrderConstants.TYPE_LIMIT;
import static com.techcobber.smarttrader.v1.models.OrderConstants.TYPE_MARKET;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techcobber.smarttrader.v1.common.Constants;
import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.Order;
import com.techcobber.smarttrader.v1.models.OrderRequest;
import com.techcobber.smarttrader.v1.models.OrderResponse;
import com.techcobber.smarttrader.v1.repositories.OrderRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

	private static final double MAX_USD_PER_ORDER = 30.00;
	private final ClientService clientService;
	private final OrderRepository orderRepository;
	private final UserService userService;
    private Map<CoinbaseAdvancedClient, OrdersService> orderServiceCache = new HashMap<>();

	/**
	 * Places a BUY or SELL order on Coinbase and persists the result.
	 *
	 * @param userId  the authenticated user
	 * @param request order parameters
	 * @return an {@link OrderResponse} describing the outcome
	 */
	@CircuitBreaker(name = "orderService")
	public OrderResponse placeOrder(String userId, OrderRequest request) {
		try {
			validateOrderRequest(request);
		} catch (Exception e) {
			log.warn("Invalid order request for user [{}]: {}", userId, e.getMessage());
			return buildOrderResponse(false, null, null,
					request.getProductId(), request.getSide(), request.getOrderType(),
					STATUS_FAILED, e.getMessage(), MSG_ORDER_FAILED);
		}

		CoinbaseAdvancedClient client = clientService.getCoinbaseClientForUserFromCache(userId);
		// Populate the cache first at service startup.
		if(orderServiceCache.size() == 0) {
			orderServiceCache.put(client, CoinbaseAdvancedServiceFactory.createOrdersService(client));
		} else if (!orderServiceCache.containsKey(client)) {
			orderServiceCache.put(client, CoinbaseAdvancedServiceFactory.createOrdersService(client));
		} 
		OrdersService ordersService = orderServiceCache.get(client);

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
		order.setCreatedAt(LocalDateTime.now());
		order.setUpdatedAt(LocalDateTime.now());
		
		if ("SELL".equalsIgnoreCase(order.getSide())
				&& !validateAvailableQuantityForSell(request.getProductId(), order.getQty())) {
			log.info("Not enough quantity to sell for product: " + request.getProductId());
			return OrderResponse.builder()
					.success(false)
					.message("Not enough quantity to sell for product: " + request.getProductId())
					.build();
		}

		try {
			log.info("Placing order for user [{}]: productId={}, side={}, orderType={}, REQUEST={}",
					userId, request.getProductId(), request.getSide(), request.getOrderType(),
					request);
			CreateOrderResponse response = ordersService.createOrder(createRequest);

			if (response.isSuccess()) {
				order.setCoinbaseOrderId(response.getSuccessResponse().getOrderId());
				order.setStatus(STATUS_PLACED);
				order.setCoinbaseOrderId(response.getOrderId());	// Store CoinBase order ID to query order status later on exchange.
				orderRepository.save(order);
                try {
                    updateUserFundsAfterOrder(userId, order);
                } catch (Exception ex) {
                    log.warn("Failed to update user funds after placing order: {}", ex.getMessage());
                }
				log.info("Order placed successfully for user [{}]: coinbaseOrderId={}", userId, response.getOrderId());

				return buildOrderResponse(true, order.getId(), response.getOrderId(),
						request.getProductId(), request.getSide(), request.getOrderType(),
						STATUS_PENDING, null, MSG_ORDER_PLACED);
			} else {
				order.setStatus(STATUS_FAILED);
				order.setFailureReason(response.getFailureReason());
				orderRepository.save(order);
				log.warn("Order placement failed for user [{}]: {}", userId, response.getErrorResponse().getMessage());

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

	private void updateUserFundsAfterOrder(String userId, Order order) {
		double orderValue = order.getLimitPrice() * order.getQty();
		if (SIDE_BUY.equalsIgnoreCase(order.getSide())) {
			userService.updateUserFunds(userId, -orderValue);
		} else if (SIDE_SELL.equalsIgnoreCase(order.getSide())) {
			userService.updateUserFunds(userId, orderValue);
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

		CoinbaseAdvancedClient client = clientService.getCoinbaseClientForUserFromCache(userId);
		// Populate the cache first at service startup.
		if(orderServiceCache.size() == 0) {
			orderServiceCache.put(client, CoinbaseAdvancedServiceFactory.createOrdersService(client));
		} else if (!orderServiceCache.containsKey(client)) {
			orderServiceCache.put(client, CoinbaseAdvancedServiceFactory.createOrdersService(client));
		} 
		OrdersService ordersService = orderServiceCache.get(client);

		try {
			CancelOrdersRequest cancelRequest = new CancelOrdersRequest.Builder()
					.orderIds(List.of(order.getCoinbaseOrderId()))
					.build();

			CancelOrdersResponse response = ordersService.cancelOrders(cancelRequest);

			if (response.getResults() != null && !response.getResults().isEmpty()) {
				CancelResult result = response.getResults().get(0);
				if (result.isSuccess()) {
					order.setStatus(STATUS_CANCELLED);
					order.setUpdatedAt(LocalDateTime.now());
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

		CoinbaseAdvancedClient client = clientService.getCoinbaseClientForUserFromCache(userId);
		// Populate the cache first at service startup.
		if(orderServiceCache.size() == 0) {
			orderServiceCache.put(client, CoinbaseAdvancedServiceFactory.createOrdersService(client));
		} else if (!orderServiceCache.containsKey(client)) {
			orderServiceCache.put(client, CoinbaseAdvancedServiceFactory.createOrdersService(client));
		} 
		OrdersService ordersService = orderServiceCache.get(client);

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
				order.setUpdatedAt(LocalDateTime.now());
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
		String productId = request.getProductId();
		Order order = prepareOrder(productId, request.getSide(), "SMART-TREADER-V1",
				request.getLimitPrice(), decideOrderQty(request.getLimitPrice()), "ADMIN",
				null, null);
		validateOrder(order);
	}
	
	private Order prepareOrder(String productId, String side, String comments, double price, double qty,
				String userName, MyCandle lastCandle, MyCandle firstCandle) {
		// Use cache to compute available quantities instead of direct DB call
		Map<String, Double> qtys = getQtyBySideFromCache(productId);
		double buyQty = qtys.getOrDefault(SIDE_BUY, 0.0);
		double sellQty = qtys.getOrDefault(SIDE_SELL, 0.0);
		double qtyInHand = buyQty - sellQty;
		
		double originalQty = qty;
		
		if (userName != null && !userName.isBlank()) {
			double requiredFunds = price * qty;
			double currentFunds = userService.findByUserName(userName).getCurrentFunds();
			double availableFunds = currentFunds - requiredFunds;
			if (qty > qtyInHand && side.equalsIgnoreCase(SIDE_SELL)) {
				qty = qtyInHand; // Adjust quantity to available quantity for sell
			}
			// We need 20 and have 10, so we can only buy 10 / price worth of quantity
			if (availableFunds < requiredFunds && side.equalsIgnoreCase(SIDE_BUY)) {
				qty = Math.floor(currentFunds / price); // Adjust quantity based on available funds
				log.warn("Adjusted order quantity from {} to {} for product: {} due to insufficient funds.",
						originalQty, qty, productId);
			}
		} else {
			log.error("User name is null or blank, skipping funds validation for order preparation.");
			throw new IllegalArgumentException("User name is required for order preparation to validate funds.");
		}
		// Defensive validation: ensure qty is still positive after adjustments
		if (qty <= 0) {
			throw new IllegalArgumentException("Order quantity adjusted to zero or negative for product: " + productId);
		}
		
		Order order = new Order();
		order.setProductId(productId);
		order.setLimitPrice(price);
		order.setUserId(userName);
		order.setQty(qty); // Example quantity, adjust as needed
		order.setSide(side);
		order.setComments(comments);
		order.setCreatedAt(LocalDateTime.now());
		return order;
	}
	
	private boolean validateOrder(Order order) {
		// Use cache to determine total available quantity instead of DB call
		// Funds and quantity validation is duplicate.
		Map<String, Double> qtys = getQtyBySideFromCache(order.getProductId());
		Double availableFunds = userService.findByUserName(order.getUserId()).getCurrentFunds();
		double totalAvailableQty = qtys.getOrDefault(SIDE_BUY, 0.0) - qtys.getOrDefault(SIDE_SELL, 0.0);
		if(availableFunds != null && availableFunds <= 0) {
			throw new IllegalArgumentException(
					"User has insufficient funds to place BUY order. Available funds: " + availableFunds);
		}
		if (order.getQty() <= 0 || order.getLimitPrice() <= 0) {
			throw new IllegalArgumentException(
					"Order quantity must be greater than zero for orders. Given Qty: " + order.getQty());
		} else if ("SELL".equalsIgnoreCase(order.getSide().toUpperCase())
				&& (totalAvailableQty <= 0 || order.getQty() > totalAvailableQty)) {
			throw new IllegalArgumentException("Not enought qty for product: " + order.getProductId()
					+ " to SELL. Available Qty: " + totalAvailableQty + ", Requested Qty: " + order.getQty());
		} else if (isDuplicateOrder(order.getProductId(), order.getSide(), order.getLimitPrice(), order.getQty())) {
			throw new IllegalArgumentException("Duplicate order detected for product: " + order.getProductId()
					+ ", side: " + order.getSide() + ", price: " + order.getLimitPrice() + ", qty: " + order.getQty()
					+ ". A similar order was placed within the last minute.");
		} else {
			return true; // Order is valid
		}
	}

	private boolean isDuplicateOrder(String productId, String side, double price, double qty) {
		List<Order> orders = orderRepository.findByProductIdAndSide(productId, side);
		log.info("Checking for duplicate orders for product: {}, side: {}, price: {}, qty: {}, found:{}", productId,
					side, price, qty, orders);
		Order order = orders.stream().filter(o -> o.getLimitPrice() == price && o.getQty() == qty)
				.sorted((o2, o1) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())).limit(1).findFirst().orElse(null);
		LocalDateTime orderTS = order != null ? order.getCreatedAt() : null;
		if (orderTS != null) {
			return orderTS.getDayOfYear() == LocalDateTime.now().getDayOfYear()
					&& orderTS.getMonth() == LocalDateTime.now().getMonth()
					&& orderTS.getHour() == LocalDateTime.now().getHour()
					&& (orderTS.getMinute() == LocalDateTime.now().getMinute()) && order.getLimitPrice() == price
					&& order.getQty() == qty && order.getSide().equalsIgnoreCase(side);
		} else {
			return false; // No previous order found, so not a duplicate
		}
	}
	
	private double decideOrderQty(double price) {
		if (price <= 0) {
			log.warn("Price is zero or negative, cannot decide order quantity. Defaulting to qty=0.");
			return 0; // Default quantity
		}
		double qty = Constants.DEFAULT_ORDER_VALUE_IN_USD / price; // Default quantity
		return qty > 0 ? qty : 1; // Ensure quantity is at least 1
	}

	private boolean validateAvailableQuantityForSell(String productId, double qty) {
		// Use cache to compute available quantity by side
		Map<String, Double> mapOfProductsBySide = getQtyBySideFromCache(productId);

		Double buyQty = mapOfProductsBySide.getOrDefault(SIDE_BUY, 0.0);
		Double sellQty = mapOfProductsBySide.getOrDefault(SIDE_SELL, 0.0);
		Double avlblQty = buyQty - sellQty;
		log.info("Product: " + productId + ", Bought Qty: " + buyQty + ", Sold Qty: " + sellQty + " Available Qty:"
				+ avlblQty);
		return avlblQty >= qty;
	}

	private OrderConfiguration buildOrderConfiguration(OrderRequest request) {
		String orderType = request.getOrderType().toUpperCase();
		Double baseSize = request.getBaseSize();

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
			Double limitPrice = request.getLimitPrice();
			// find 0.5% of limit price and subtract from limit price to set as stop price,
			// this is to make sure the post only orders won't fail.
			if (request.getSide().equalsIgnoreCase(SIDE_SELL)) {
				limitPrice = request.getLimitPrice() + (request.getLimitPrice() * 0.005);
				Double availableQty = findAvailableQtyForProduct(request.getProductId());
				if (availableQty != null && availableQty > 0 && request.getBaseSize() > availableQty) {
					log.info("Adjusting sell order quantity from {} to {} for product: {} based on available quantity.",
							request.getBaseSize(), availableQty, request.getProductId());
					 baseSize = availableQty;
				}
			} else {
				limitPrice = request.getLimitPrice() - (request.getLimitPrice() * 0.005);
				// Adjust qty i.e baseSize to MAX_USD_PER_ORDER if order value exceeds max allowed per order.
				double orderValue = request.getBaseSize() * request.getLimitPrice();
				if (orderValue > MAX_USD_PER_ORDER) {
					double adjustedBaseSize = Math.floor(MAX_USD_PER_ORDER / request.getLimitPrice() * 1e8) / 1e8; // avoid floating precision
					log.info("Adjusting buy order quantity from {} to {} for product: {} to enforce max order value of ${}.",
							request.getBaseSize(), adjustedBaseSize, request.getProductId(), MAX_USD_PER_ORDER);
					baseSize = adjustedBaseSize;
				}
			}
			LimitGtc limitGtc = new LimitGtc.Builder()
					.baseSize(String.valueOf(baseSize))
					.limitPrice(String.format("%.2f", limitPrice))	//Must be a string with 2 decimal places to avoid CoinBase API validation error.
					.postOnly(true)
					.build();
			return new OrderConfiguration.Builder()
					.limitLimitGtc(limitGtc)
					.build();
		}
	}

	private Double findAvailableQtyForProduct(String productId) {
		Map<String, Double> result = getQtyBySideFromCache(productId);
		double buyQty = result.get(SIDE_BUY);
		double sellQty = result.get(SIDE_SELL);
		return buyQty - sellQty;
	}
	
	public List<Order> findAllOrders() {
		log.info("Fetching all orders from DB.");
		return orderRepository.findAll();
	}

	public List<Order> findByProductId(String productId) {
		return orderRepository.findByProductId(productId);
	}

	// ---------------- Redis cache helpers -----------------
	private Map<String, Double> getQtyBySideFromCache(String productId) {
        Map<String, Double> result = new HashMap<>();
        List<Order> orders = orderRepository.findByProductId(productId);
        double buy = orders.stream().filter(o -> SIDE_BUY.equalsIgnoreCase(o.getSide())).mapToDouble(Order::getQty).sum();
        double sell = orders.stream().filter(o -> SIDE_SELL.equalsIgnoreCase(o.getSide())).mapToDouble(Order::getQty).sum();
        result.put(SIDE_BUY, buy);
        result.put(SIDE_SELL, sell);
        return result;
    }
}
