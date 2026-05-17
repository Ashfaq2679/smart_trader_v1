package com.techcobber.smarttrader.v1.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.techcobber.smarttrader.v1.models.User;
import com.techcobber.smarttrader.v1.services.UserService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * REST controller for managing users.
 *
 * <p>Provides endpoints for creating, retrieving, updating, and deleting
 * platform users.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Operations on platform users")
public class UserController {

	private final UserService userService;

	@Operation(summary = "Create user", description = "Creates a new platform user")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "User created"),
			@ApiResponse(responseCode = "400", description = "Invalid input"),
			@ApiResponse(responseCode = "409", description = "User already exists"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	@PostMapping
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<?> createUser(@Valid @RequestBody User user) {
		try {
			User created = userService.createUser(user);
			log.info("User created: [{}]", created.getUserId());
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		} catch (IllegalArgumentException e) {
			log.warn("User creation failed: {}", e.getMessage());
			HttpStatus status = e.getMessage().contains("already exists")
					? HttpStatus.CONFLICT
					: HttpStatus.BAD_REQUEST;
			return ResponseEntity.status(status)
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("User creation error: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to create user: " + e.getMessage()));
		}
	}

	@Operation(summary = "Get user", description = "Retrieve a user by their userId")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK"),
			@ApiResponse(responseCode = "404", description = "User not found")
	})
	@GetMapping("/{userId}")
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<?> getUser(@PathVariable String userId) {
		return userService.getUser(userId)
				.<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("error", "User not found: " + userId)));
	}

	@Operation(summary = "Update user", description = "Update mutable fields for a user")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated"),
			@ApiResponse(responseCode = "404", description = "User not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	@PutMapping("/{userId}")
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<?> updateUser(@PathVariable String userId, @RequestBody User updates) {
		try {
			User updated = userService.updateUser(userId, updates);
			log.info("User updated: [{}]", userId);
			return ResponseEntity.ok(updated);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("User update error for [{}]: {}", userId, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to update user: " + e.getMessage()));
		}
	}

	@Operation(summary = "Delete user", description = "Deletes a user by userId")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Deleted"),
			@ApiResponse(responseCode = "404", description = "User not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	@DeleteMapping("/{userId}")
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<?> deleteUser(@PathVariable String userId) {
		try {
			userService.deleteUser(userId);
			log.info("User deleted: [{}]", userId);
			return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("User delete error for [{}]: {}", userId, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to delete user: " + e.getMessage()));
		}
	}
}
