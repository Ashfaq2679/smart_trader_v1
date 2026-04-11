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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for managing per-user trading preferences.
 *
 * <p>Preferences are scoped to a user and support create-or-update semantics
 * via {@code PUT}. A {@code DELETE} resets the user's preferences.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/users/{userId}/preferences")
@RequiredArgsConstructor
public class UserPreferencesController {

	private final UserPreferencesService preferencesService;

	/**
	 * Retrieves the preferences for a user.
	 *
	 * @param userId unique user identifier
	 * @return the preferences or 404 if none are stored
	 */
	@GetMapping
	public ResponseEntity<?> getPreferences(@PathVariable String userId) {
		return preferencesService.getPreferences(userId)
				.<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("error", "No preferences found for user: " + userId)));
	}

	/**
	 * Creates or updates preferences for a user.
	 *
	 * @param userId  unique user identifier
	 * @param updates the preference values to set
	 * @return the saved preferences
	 */
	@PutMapping
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

	/**
	 * Deletes (resets) the preferences for a user.
	 *
	 * @param userId unique user identifier
	 * @return 200 on success or 404 if no preferences exist
	 */
	@DeleteMapping
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
