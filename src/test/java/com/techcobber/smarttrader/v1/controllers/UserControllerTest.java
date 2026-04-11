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

import com.techcobber.smarttrader.v1.models.User;
import com.techcobber.smarttrader.v1.services.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserController}.
 * Uses Mockito — no database required.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

	@Mock
	private UserService userService;

	@InjectMocks
	private UserController controller;

	private static User sampleUser(String userId) {
		User user = new User();
		user.setUserId(userId);
		user.setEmail(userId + "@example.com");
		user.setDisplayName("Test User");
		user.setEnabled(true);
		user.setCreatedAt(Instant.now());
		user.setUpdatedAt(Instant.now());
		return user;
	}

	// =======================================================================
	// createUser
	// =======================================================================

	@Nested
	@DisplayName("POST /api/users — createUser")
	class CreateUserTests {

		@Test
		@DisplayName("Returns 201 when user is created successfully")
		void returnsCreatedOnSuccess() {
			User input = sampleUser("user-1");
			when(userService.createUser(any(User.class))).thenReturn(input);

			ResponseEntity<?> response = controller.createUser(input);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
			assertThat(response.getBody()).isInstanceOf(User.class);
			assertThat(((User) response.getBody()).getUserId()).isEqualTo("user-1");
		}

		@Test
		@DisplayName("Returns 409 when user already exists")
		@SuppressWarnings("unchecked")
		void returnsConflictWhenExists() {
			User input = sampleUser("user-1");
			when(userService.createUser(any(User.class)))
					.thenThrow(new IllegalArgumentException("User already exists: user-1"));

			ResponseEntity<?> response = controller.createUser(input);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("already exists");
		}

		@Test
		@DisplayName("Returns 400 when userId is blank")
		@SuppressWarnings("unchecked")
		void returnsBadRequestWhenBlank() {
			User input = new User();
			input.setUserId("  ");
			when(userService.createUser(any(User.class)))
					.thenThrow(new IllegalArgumentException("userId must not be blank"));

			ResponseEntity<?> response = controller.createUser(input);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("must not be blank");
		}

		@Test
		@DisplayName("Returns 500 on unexpected error")
		@SuppressWarnings("unchecked")
		void returnsServerErrorOnException() {
			User input = sampleUser("user-1");
			when(userService.createUser(any(User.class)))
					.thenThrow(new RuntimeException("DB down"));

			ResponseEntity<?> response = controller.createUser(input);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("DB down");
		}
	}

	// =======================================================================
	// getUser
	// =======================================================================

	@Nested
	@DisplayName("GET /api/users/{userId} — getUser")
	class GetUserTests {

		@Test
		@DisplayName("Returns 200 when user found")
		void returnsOkWhenFound() {
			User user = sampleUser("user-1");
			when(userService.getUser("user-1")).thenReturn(Optional.of(user));

			ResponseEntity<?> response = controller.getUser("user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isInstanceOf(User.class);
		}

		@Test
		@DisplayName("Returns 404 when user not found")
		@SuppressWarnings("unchecked")
		void returnsNotFoundWhenMissing() {
			when(userService.getUser("unknown")).thenReturn(Optional.empty());

			ResponseEntity<?> response = controller.getUser("unknown");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("not found");
		}
	}

	// =======================================================================
	// updateUser
	// =======================================================================

	@Nested
	@DisplayName("PUT /api/users/{userId} — updateUser")
	class UpdateUserTests {

		@Test
		@DisplayName("Returns 200 on successful update")
		void returnsOkOnSuccess() {
			User updated = sampleUser("user-1");
			updated.setEmail("new@example.com");
			when(userService.updateUser(eq("user-1"), any(User.class))).thenReturn(updated);

			User updates = new User();
			updates.setEmail("new@example.com");

			ResponseEntity<?> response = controller.updateUser("user-1", updates);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(((User) response.getBody()).getEmail()).isEqualTo("new@example.com");
		}

		@Test
		@DisplayName("Returns 404 when user not found")
		@SuppressWarnings("unchecked")
		void returnsNotFoundWhenMissing() {
			when(userService.updateUser(eq("unknown"), any(User.class)))
					.thenThrow(new IllegalArgumentException("User not found: unknown"));

			ResponseEntity<?> response = controller.updateUser("unknown", new User());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("not found");
		}

		@Test
		@DisplayName("Returns 500 on unexpected error")
		@SuppressWarnings("unchecked")
		void returnsServerErrorOnException() {
			when(userService.updateUser(eq("user-1"), any(User.class)))
					.thenThrow(new RuntimeException("DB timeout"));

			ResponseEntity<?> response = controller.updateUser("user-1", new User());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("DB timeout");
		}
	}

	// =======================================================================
	// deleteUser
	// =======================================================================

	@Nested
	@DisplayName("DELETE /api/users/{userId} — deleteUser")
	class DeleteUserTests {

		@Test
		@DisplayName("Returns 200 on successful deletion")
		@SuppressWarnings("unchecked")
		void returnsOkOnSuccess() {
			ResponseEntity<?> response = controller.deleteUser("user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(((Map<String, String>) response.getBody()).get("message"))
					.contains("deleted successfully");
			verify(userService).deleteUser("user-1");
		}

		@Test
		@DisplayName("Returns 404 when user not found")
		@SuppressWarnings("unchecked")
		void returnsNotFoundWhenMissing() {
			doThrow(new IllegalArgumentException("User not found: unknown"))
					.when(userService).deleteUser("unknown");

			ResponseEntity<?> response = controller.deleteUser("unknown");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("not found");
		}

		@Test
		@DisplayName("Returns 500 on unexpected error")
		@SuppressWarnings("unchecked")
		void returnsServerErrorOnException() {
			doThrow(new RuntimeException("DB down"))
					.when(userService).deleteUser("user-1");

			ResponseEntity<?> response = controller.deleteUser("user-1");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(((Map<String, String>) response.getBody()).get("error"))
					.contains("DB down");
		}
	}
}
