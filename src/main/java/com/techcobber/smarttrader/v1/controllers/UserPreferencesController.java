package com.techcobber.smarttrader.v1.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.techcobber.smarttrader.v1.models.UserPreferences;
import com.techcobber.smarttrader.v1.services.UserPreferencesService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * REST controller for managing per-user trading preferences.
 */
@Slf4j
@RestController
@RequestMapping("/api/users/{userId}/preferences")
@RequiredArgsConstructor
@Tag(name = "Preferences", description = "Per-user trading preferences")
public class UserPreferencesController {

	private final UserPreferencesService preferencesService;

	@Operation(summary = "Get preferences", description = "Get stored preferences for a user")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK"),
			@ApiResponse(responseCode = "404", description = "Not found")
	})
	@GetMapping
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<?> getPreferences(@PathVariable String userId) {
		return preferencesService.getPreferences(userId)
				.<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("error", "No preferences found for user: " + userId)));
	}

	@Operation(summary = "Save preferences", description = "Create or update preferences for a user")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Saved"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	@PutMapping
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<?> savePreferences(
			@PathVariable String userId,
			@RequestBody UserPreferences updates) {
		try {
			UserPreferences saved = preferencesService.savePreferences(userId, updates);
			log.info("Preferences saved for user [{}]", userId);
			return ResponseEntity.ok(saved);
		} catch (Exception e) {
			log.error("Failed to save preferences for user [{}]: {}", userId, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to save preferences: " + e.getMessage()));
		}
	}

	@Operation(summary = "Delete preferences", description = "Delete (reset) preferences for a user")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Deleted"),
			@ApiResponse(responseCode = "404", description = "Not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	@DeleteMapping
	@RateLimiter(name = "apiRateLimiter")
	public ResponseEntity<?> deletePreferences(@PathVariable String userId) {
		try {
			preferencesService.deletePreferences(userId);
			log.info("Preferences deleted for user [{}]", userId);
			return ResponseEntity.ok(Map.of("message", "Preferences deleted successfully"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("Failed to delete preferences for user [{}]: {}", userId, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to delete preferences: " + e.getMessage()));
		}
	}
}
