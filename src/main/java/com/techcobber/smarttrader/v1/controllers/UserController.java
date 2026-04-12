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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
public class UserController {

	private final UserService userService;

	/**
	 * Creates a new user.
	 *
	 * @param user the user data (must contain a non-blank userId)
	 * @return the created user with 201 status, or 400/409 on validation errors
	 */
	@PostMapping
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

	/**
	 * Retrieves a user by userId.
	 *
	 * @param userId unique user identifier
	 * @return the user or 404 if not found
	 */
	@GetMapping("/{userId}")
	public ResponseEntity<?> getUser(@PathVariable String userId) {
		return userService.getUser(userId)
				.<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("error", "User not found: " + userId)));
	}

	/**
	 * Updates a user's mutable fields (email, displayName, enabled).
	 *
	 * @param userId  unique user identifier
	 * @param updates user object carrying updated values
	 * @return the updated user or 404 if not found
	 */
	@PutMapping("/{userId}")
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

	/**
	 * Deletes a user by userId.
	 *
	 * @param userId unique user identifier
	 * @return 200 on success or 404 if not found
	 */
	@DeleteMapping("/{userId}")
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
