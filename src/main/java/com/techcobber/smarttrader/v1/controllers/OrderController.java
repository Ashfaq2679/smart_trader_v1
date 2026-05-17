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

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * REST controller for managing trading orders.
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Place and query trading orders")
public class OrderController {

	private final OrderService orderService;

	@Operation(summary = "Place order", description = "Place a BUY or SELL order for a user")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Order placed"),
			@ApiResponse(responseCode = "400", description = "Invalid request or Coinbase error"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	@PostMapping("/{userId}")
	@RateLimiter(name = "apiRateLimiter")
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

	@Operation(summary = "Get order", description = "Retrieve order by internal orderId")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK"),
			@ApiResponse(responseCode = "404", description = "Order not found")
	})
	@GetMapping("/{orderId}")
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<?> getOrder(@PathVariable String orderId) {
		return orderService.getOrder(orderId)
				.<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("error", "Order not found: " + orderId)));
	}

	@Operation(summary = "List orders by user", description = "List all orders for a user; optional product filter")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@GetMapping("/user/{userId}")
	@RateLimiter(name = "apiRateLimiter")
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
}
