package com.techcobber.smarttrader.v1.services;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.techcobber.smarttrader.v1.models.User;
import com.techcobber.smarttrader.v1.repositories.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 * Uses Mockito — no database required.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserService userService;

	private static User sampleUser(String userId) {
		User user = new User();
		user.setUserId(userId);
		user.setEmail(userId + "@example.com");
		user.setDisplayName("Test User");
		user.setEnabled(true);
		return user;
	}

	// =======================================================================
	// createUser
	// =======================================================================

	@Nested
	@DisplayName("createUser")
	class CreateUserTests {

		@Test
		@DisplayName("Creates a user and sets timestamps")
		void createsUserSuccessfully() {
			User input = sampleUser("user-1");
			when(userRepository.existsById("user-1")).thenReturn(false);
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			User result = userService.createUser(input);

			assertThat(result.getUserId()).isEqualTo("user-1");
			assertThat(result.getCreatedAt()).isNotNull();
			assertThat(result.getUpdatedAt()).isNotNull();
			verify(userRepository).save(input);
		}

		@Test
		@DisplayName("Throws when userId is blank")
		void throwsWhenUserIdBlank() {
			User input = new User();
			input.setUserId("  ");

			assertThatThrownBy(() -> userService.createUser(input))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("must not be blank");
		}

		@Test
		@DisplayName("Throws when userId is null")
		void throwsWhenUserIdNull() {
			User input = new User();

			assertThatThrownBy(() -> userService.createUser(input))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("must not be blank");
		}

		@Test
		@DisplayName("Throws when user already exists")
		void throwsWhenUserAlreadyExists() {
			User input = sampleUser("user-1");
			when(userRepository.existsById("user-1")).thenReturn(true);

			assertThatThrownBy(() -> userService.createUser(input))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("already exists");
		}
	}

	// =======================================================================
	// getUser
	// =======================================================================

	@Nested
	@DisplayName("getUser")
	class GetUserTests {

		@Test
		@DisplayName("Returns user when found")
		void returnsUserWhenFound() {
			User user = sampleUser("user-1");
			when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

			Optional<User> result = userService.getUser("user-1");

			assertThat(result).isPresent();
			assertThat(result.get().getUserId()).isEqualTo("user-1");
		}

		@Test
		@DisplayName("Returns empty when not found")
		void returnsEmptyWhenNotFound() {
			when(userRepository.findById("unknown")).thenReturn(Optional.empty());

			Optional<User> result = userService.getUser("unknown");

			assertThat(result).isEmpty();
		}
	}

	// =======================================================================
	// updateUser
	// =======================================================================

	@Nested
	@DisplayName("updateUser")
	class UpdateUserTests {

		@Test
		@DisplayName("Updates mutable fields")
		void updatesMutableFields() {
			User existing = sampleUser("user-1");
			existing.setCreatedAt(Instant.now().minusSeconds(3600));
			when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			User updates = new User();
			updates.setEmail("new@example.com");
			updates.setDisplayName("New Name");
			updates.setEnabled(false);

			User result = userService.updateUser("user-1", updates);

			assertThat(result.getEmail()).isEqualTo("new@example.com");
			assertThat(result.getDisplayName()).isEqualTo("New Name");
			assertThat(result.isEnabled()).isFalse();
			assertThat(result.getUpdatedAt()).isAfter(result.getCreatedAt());
		}

		@Test
		@DisplayName("Preserves fields when updates are null")
		void preservesFieldsWhenNull() {
			User existing = sampleUser("user-1");
			existing.setEmail("original@example.com");
			existing.setDisplayName("Original Name");
			when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			User updates = new User();
			// email and displayName are null — should preserve originals

			User result = userService.updateUser("user-1", updates);

			assertThat(result.getEmail()).isEqualTo("original@example.com");
			assertThat(result.getDisplayName()).isEqualTo("Original Name");
		}

		@Test
		@DisplayName("Throws when user not found")
		void throwsWhenUserNotFound() {
			when(userRepository.findById("unknown")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> userService.updateUser("unknown", new User()))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("not found");
		}
	}

	// =======================================================================
	// deleteUser
	// =======================================================================

	@Nested
	@DisplayName("deleteUser")
	class DeleteUserTests {

		@Test
		@DisplayName("Deletes when user exists")
		void deletesWhenExists() {
			when(userRepository.existsById("user-1")).thenReturn(true);

			userService.deleteUser("user-1");

			verify(userRepository).deleteById("user-1");
		}

		@Test
		@DisplayName("Throws when user not found")
		void throwsWhenNotFound() {
			when(userRepository.existsById("unknown")).thenReturn(false);

			assertThatThrownBy(() -> userService.deleteUser("unknown"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("not found");
		}
	}

	// =======================================================================
	// userExists
	// =======================================================================

	@Nested
	@DisplayName("userExists")
	class UserExistsTests {

		@Test
		@DisplayName("Returns true when user exists")
		void returnsTrueWhenExists() {
			when(userRepository.existsById("user-1")).thenReturn(true);

			assertThat(userService.userExists("user-1")).isTrue();
		}

		@Test
		@DisplayName("Returns false when user does not exist")
		void returnsFalseWhenNotExists() {
			when(userRepository.existsById("unknown")).thenReturn(false);

			assertThat(userService.userExists("unknown")).isFalse();
		}
	}
}
