package com.techcobber.smarttrader.v1.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.techcobber.smarttrader.v1.services.ClientService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for managing user Coinbase credentials.
 *
 * <p>Provides endpoints for registering, checking, and removing
 * per-user Coinbase Advanced Trade API credentials.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
public class CredentialController {

	private final ClientService clientService;

	/**
	 * Registers (or updates) Coinbase credentials for a user.
	 *
	 * @param userId      unique user identifier
	 * @param requestBody must contain a {@code credentials} key with the raw credential blob
	 * @return 200 on success, 400 if input is invalid
	 */
	@PostMapping("/{userId}")
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<Map<String, String>> registerCredentials(
			@PathVariable String userId,
			@RequestBody Map<String, String> requestBody) {

		String credentials = requestBody.get("credentials");
		if (credentials == null || credentials.isBlank()) {
			log.warn("Register request for user [{}] missing credentials", userId);
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Request body must contain a non-blank 'credentials' field"));
		}

		try {
			clientService.registerCredentials(userId, credentials);
			log.info("Credentials registered for user [{}]", userId);
			return ResponseEntity.ok(Map.of("message", "Credentials registered successfully"));
		} catch (Exception e) {
			log.error("Failed to register credentials for user [{}]: {}", userId, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to register credentials: " + e.getMessage()));
		}
	}

	/**
	 * Checks whether credentials exist for a user.
	 *
	 * @param userId unique user identifier
	 * @return JSON with {@code exists: true/false}
	 */
	@GetMapping("/{userId}/exists")
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<Map<String, Boolean>> hasCredentials(@PathVariable String userId) {
		boolean exists = clientService.hasCredentials(userId);
		return ResponseEntity.ok(Map.of("exists", exists));
	}

	/**
	 * Removes the stored credentials for a user.
	 *
	 * @param userId unique user identifier
	 * @return 200 on success
	 */
	@DeleteMapping("/{userId}")
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<Map<String, String>> removeCredentials(@PathVariable String userId) {
		try {
			clientService.removeCredentials(userId);
			log.info("Credentials removed for user [{}]", userId);
			return ResponseEntity.ok(Map.of("message", "Credentials removed successfully"));
		} catch (Exception e) {
			log.error("Failed to remove credentials for user [{}]: {}", userId, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to remove credentials: " + e.getMessage()));
		}
	}
}
