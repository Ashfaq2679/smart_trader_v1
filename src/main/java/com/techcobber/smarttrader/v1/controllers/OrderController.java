package com.techcobber.smarttrader.v1.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.techcobber.smarttrader.v1.models.Order;
import com.techcobber.smarttrader.v1.models.OrderRequest;
import com.techcobber.smarttrader.v1.models.OrderResponse;
import com.techcobber.smarttrader.v1.services.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for managing trading orders.
 *
 * <p>Provides endpoints for placing BUY/SELL orders on Coinbase, retrieving
 * order details, listing orders by user, cancelling open orders, and syncing
 * order status from Coinbase.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	/**
	 * Places a new BUY or SELL order.
	 *
	 * @param userId  the user placing the order (path variable)
	 * @param request order parameters
	 * @return the order result
	 */
	@PostMapping("/{userId}")
	public ResponseEntity<?> placeOrder(
			@PathVariable String userId,
			@Valid @RequestBody OrderRequest request) {
		try {
			OrderResponse response = orderService.placeOrder(userId, request);
			if (response.isSuccess()) {
				return ResponseEntity.ok(response);
			} else {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
			}
		} catch (IllegalArgumentException e) {
			log.warn("Invalid order request from user [{}]: {}", userId, e.getMessage());
			return ResponseEntity.badRequest()
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("Error placing order for user [{}]: {}", userId, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to place order: " + e.getMessage()));
		}
	}

	/**
	 * Retrieves a single order by its internal ID.
	 *
	 * @param orderId the internal database order ID
	 * @return the order or 404
	 */
	@GetMapping("/{orderId}")
	public ResponseEntity<?> getOrder(@PathVariable String orderId) {
		return orderService.getOrder(orderId)
				.<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("error", "Order not found: " + orderId)));
	}

	/**
	 * Lists all orders for a user, optionally filtered by product.
	 *
	 * @param userId    the user whose orders to list
	 * @param productId optional filter by trading pair
	 * @return list of orders
	 */
	@GetMapping("/user/{userId}")
	public ResponseEntity<List<Order>> getOrdersByUser(
			@PathVariable String userId,
			@RequestParam(required = false) String productId) {
		List<Order> orders;
		if (productId != null && !productId.isBlank()) {
			orders = orderService.getOrdersByUserAndProduct(userId, productId);
		} else {
			orders = orderService.getOrdersByUser(userId);
		}
		return ResponseEntity.ok(orders);
	}

	/**
	 * Cancels an open order on Coinbase and updates the local record.
	 *
	 * @param userId  the user who owns the order
	 * @param orderId the internal database order ID
	 * @return the cancellation result
	 */
	@PostMapping("/{userId}/cancel/{orderId}")
	public ResponseEntity<?> cancelOrder(
			@PathVariable String userId,
			@PathVariable String orderId) {
		try {
			OrderResponse response = orderService.cancelOrder(userId, orderId);
			if (response.isSuccess()) {
				return ResponseEntity.ok(response);
			} else {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
			}
		} catch (IllegalArgumentException e) {
			log.warn("Invalid cancel request from user [{}] for order [{}]: {}", userId, orderId, e.getMessage());
			return ResponseEntity.badRequest()
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("Error cancelling order [{}] for user [{}]: {}", orderId, userId, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to cancel order: " + e.getMessage()));
		}
	}

	/**
	 * Syncs the latest order status from Coinbase and returns the updated record.
	 *
	 * @param userId  the user who owns the order
	 * @param orderId the internal database order ID
	 * @return the updated order
	 */
	@PostMapping("/{userId}/sync/{orderId}")
	public ResponseEntity<?> syncOrderStatus(
			@PathVariable String userId,
			@PathVariable String orderId) {
		try {
			Order updated = orderService.syncOrderStatus(userId, orderId);
			return ResponseEntity.ok(updated);
		} catch (IllegalArgumentException e) {
			log.warn("Invalid sync request from user [{}] for order [{}]: {}", userId, orderId, e.getMessage());
			return ResponseEntity.badRequest()
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("Error syncing order [{}] for user [{}]: {}", orderId, userId, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to sync order status: " + e.getMessage()));
		}
	}
}
