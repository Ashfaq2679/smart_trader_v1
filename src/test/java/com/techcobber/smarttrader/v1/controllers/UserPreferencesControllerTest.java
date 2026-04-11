package com.techcobber.smarttrader.v1.controllers;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.techcobber.smarttrader.v1.models.UserPreferences;
import com.techcobber.smarttrader.v1.services.UserPreferencesService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserPreferencesController}.
 * Uses Mockito — no database required.
 */
@ExtendWith(MockitoExtension.class)
class UserPreferencesControllerTest {

	@Mock
	private UserPreferencesService preferencesService;

	@InjectMocks
	private UserPreferencesController controller;

	private static UserPreferences samplePrefs(String userId) {
		UserPreferences prefs = new UserPreferences();
		prefs.setUserId(userId);
		prefs.setStrategy("price_action");
		prefs.setGranularity("ONE_HOUR");
		prefs.setBaseAsset("BTC");
		prefs.setQuoteAsset("USDC");
		prefs.setPositionSize("0.05");
		prefs.setMaxDailyLoss("500");
		prefs.setTimezone("UTC");
		prefs.setEnabled(true);
		prefs.setUpdatedAt(Instant.now());
		return prefs;
	}

	// =======================================================================
	// getPreferences
	// =======================================================================

	@Nested
	@DisplayName("GET /api/users/{userId}/preferences — getPreferences")
	class GetPreferencesTests {

		@Test
		@DisplayName("Returns 200 when preferences found")
		void returnsOkWhenFound() {
			UserPreferences prefs = samplePrefs("user-1");
			when(preferencesService.getPreferences("user-1")).thenReturn(Optional.of(prefs));

			ResponseEntity<?> response = controller.getPreferences("user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isInstanceOf(UserPreferences.class);
			assertThat(((UserPreferences) response.getBody()).getStrategy()).isEqualTo("price_action");
		}

		@Test
		@DisplayName("Returns 404 when no preferences found")
		@SuppressWarnings("unchecked")
		void returnsNotFoundWhenMissing() {
			when(preferencesService.getPreferences("unknown")).thenReturn(Optional.empty());

			ResponseEntity<?> response = controller.getPreferences("unknown");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("No preferences found");
		}
	}

	// =======================================================================
	// savePreferences
	// =======================================================================

	@Nested
	@DisplayName("PUT /api/users/{userId}/preferences — savePreferences")
	class SavePreferencesTests {

		@Test
		@DisplayName("Returns 200 when preferences saved successfully")
		void returnsOkOnSuccess() {
			UserPreferences saved = samplePrefs("user-1");
			when(preferencesService.savePreferences(eq("user-1"), any(UserPreferences.class)))
					.thenReturn(saved);

			UserPreferences updates = new UserPreferences();
			updates.setStrategy("price_action");

			ResponseEntity<?> response = controller.savePreferences("user-1", updates);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isInstanceOf(UserPreferences.class);
		}

		@Test
		@DisplayName("Returns 500 on unexpected error")
		@SuppressWarnings("unchecked")
		void returnsServerErrorOnException() {
			when(preferencesService.savePreferences(eq("user-1"), any(UserPreferences.class)))
					.thenThrow(new RuntimeException("DB down"));

			ResponseEntity<?> response = controller.savePreferences("user-1", new UserPreferences());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("DB down");
		}
	}

	// =======================================================================
	// deletePreferences
	// =======================================================================

	@Nested
	@DisplayName("DELETE /api/users/{userId}/preferences — deletePreferences")
	class DeletePreferencesTests {

		@Test
		@DisplayName("Returns 200 on successful deletion")
		@SuppressWarnings("unchecked")
		void returnsOkOnSuccess() {
			ResponseEntity<?> response = controller.deletePreferences("user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(((Map<String, String>) response.getBody()).get("message"))
					.contains("deleted successfully");
			verify(preferencesService).deletePreferences("user-1");
		}

		@Test
		@DisplayName("Returns 404 when no preferences found")
		@SuppressWarnings("unchecked")
		void returnsNotFoundWhenMissing() {
			doThrow(new IllegalArgumentException("No preferences found for user: unknown"))
					.when(preferencesService).deletePreferences("unknown");

			ResponseEntity<?> response = controller.deletePreferences("unknown");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("No preferences found");
		}

		@Test
		@DisplayName("Returns 500 on unexpected error")
		@SuppressWarnings("unchecked")
		void returnsServerErrorOnException() {
			doThrow(new RuntimeException("DB down"))
					.when(preferencesService).deletePreferences("user-1");

			ResponseEntity<?> response = controller.deletePreferences("user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("DB down");
		}
	}
}
