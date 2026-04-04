package com.techcobber.smarttrader.v1.controllers;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.techcobber.smarttrader.v1.services.ClientService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CredentialController}.
 * Uses Mockito — no database or API credentials required.
 */
@ExtendWith(MockitoExtension.class)
class CredentialControllerTest {

	@Mock
	private ClientService clientService;

	@InjectMocks
	private CredentialController controller;

	// =======================================================================
	// registerCredentials
	// =======================================================================

	@Nested
	@DisplayName("POST /{userId} — registerCredentials")
	class RegisterCredentialsTests {

		@Test
		@DisplayName("Returns 200 when credentials are valid")
		void returnsOkWhenCredentialsValid() {
			Map<String, String> body = Map.of("credentials", "raw-cred-blob");

			ResponseEntity<Map<String, String>> response =
					controller.registerCredentials("user-1", body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).containsEntry("message", "Credentials registered successfully");
			verify(clientService).registerCredentials("user-1", "raw-cred-blob");
		}

		@Test
		@DisplayName("Returns 400 when credentials field is missing")
		void returnsBadRequestWhenCredentialsMissing() {
			Map<String, String> body = Map.of("something", "irrelevant");

			ResponseEntity<Map<String, String>> response =
					controller.registerCredentials("user-1", body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody()).containsKey("error");
			verifyNoInteractions(clientService);
		}

		@Test
		@DisplayName("Returns 400 when credentials field is blank")
		void returnsBadRequestWhenCredentialsBlank() {
			Map<String, String> body = Map.of("credentials", "   ");

			ResponseEntity<Map<String, String>> response =
					controller.registerCredentials("user-1", body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody()).containsKey("error");
			verifyNoInteractions(clientService);
		}

		@Test
		@DisplayName("Returns 500 when service throws exception")
		void returnsServerErrorOnServiceFailure() {
			doThrow(new RuntimeException("encryption key missing"))
					.when(clientService).registerCredentials("user-1", "creds");

			ResponseEntity<Map<String, String>> response =
					controller.registerCredentials("user-1", Map.of("credentials", "creds"));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(response.getBody().get("error")).contains("encryption key missing");
		}
	}

	// =======================================================================
	// hasCredentials
	// =======================================================================

	@Nested
	@DisplayName("GET /{userId}/exists — hasCredentials")
	class HasCredentialsTests {

		@Test
		@DisplayName("Returns exists=true when credentials exist")
		void returnsTrueWhenExists() {
			when(clientService.hasCredentials("user-1")).thenReturn(true);

			ResponseEntity<Map<String, Boolean>> response =
					controller.hasCredentials("user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).containsEntry("exists", true);
		}

		@Test
		@DisplayName("Returns exists=false when credentials do not exist")
		void returnsFalseWhenNotExists() {
			when(clientService.hasCredentials("unknown")).thenReturn(false);

			ResponseEntity<Map<String, Boolean>> response =
					controller.hasCredentials("unknown");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).containsEntry("exists", false);
		}
	}

	// =======================================================================
	// removeCredentials
	// =======================================================================

	@Nested
	@DisplayName("DELETE /{userId} — removeCredentials")
	class RemoveCredentialsTests {

		@Test
		@DisplayName("Returns 200 on successful removal")
		void returnsOkOnSuccess() {
			ResponseEntity<Map<String, String>> response =
					controller.removeCredentials("user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).containsEntry("message", "Credentials removed successfully");
			verify(clientService).removeCredentials("user-1");
		}

		@Test
		@DisplayName("Returns 500 when service throws exception")
		void returnsServerErrorOnFailure() {
			doThrow(new RuntimeException("DB down"))
					.when(clientService).removeCredentials("user-1");

			ResponseEntity<Map<String, String>> response =
					controller.removeCredentials("user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(response.getBody().get("error")).contains("DB down");
		}
	}
}
