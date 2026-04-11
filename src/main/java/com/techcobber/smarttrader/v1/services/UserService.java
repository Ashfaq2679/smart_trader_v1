package com.techcobber.smarttrader.v1.services;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.techcobber.smarttrader.v1.models.User;
import com.techcobber.smarttrader.v1.repositories.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing user registration and lifecycle.
 *
 * <p>Provides CRUD operations for {@link User} documents in MongoDB.
 * User IDs must be unique; attempting to create a duplicate throws
 * {@link IllegalArgumentException}.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	/**
	 * Creates a new user.
	 *
	 * @param user the user to create (userId must be non-blank and unique)
	 * @return the persisted user with timestamps set
	 * @throws IllegalArgumentException if userId is blank or already exists
	 */
	public User createUser(User user) {
		if (user.getUserId() == null || user.getUserId().isBlank()) {
			throw new IllegalArgumentException("userId must not be blank");
		}
		if (userRepository.existsByUserId(user.getUserId())) {
			throw new IllegalArgumentException("User already exists: " + user.getUserId());
		}
		Instant now = Instant.now();
		user.setCreatedAt(now);
		user.setUpdatedAt(now);
		User saved = userRepository.save(user);
		log.info("Created user [{}]", saved.getUserId());
		return saved;
	}

	/**
	 * Retrieves a user by userId.
	 *
	 * @param userId unique user identifier
	 * @return an Optional containing the user, or empty if not found
	 */
	public Optional<User> getUser(String userId) {
		return userRepository.findByUserId(userId);
	}

	/**
	 * Updates an existing user's mutable fields (email, displayName, enabled).
	 *
	 * @param userId  the user to update
	 * @param updates a User object carrying the new field values
	 * @return the updated user
	 * @throws IllegalArgumentException if the user does not exist
	 */
	public User updateUser(String userId, User updates) {
		User existing = userRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

		if (updates.getEmail() != null) {
			existing.setEmail(updates.getEmail());
		}
		if (updates.getDisplayName() != null) {
			existing.setDisplayName(updates.getDisplayName());
		}
		existing.setEnabled(updates.isEnabled());
		existing.setUpdatedAt(Instant.now());

		User saved = userRepository.save(existing);
		log.info("Updated user [{}]", userId);
		return saved;
	}

	/**
	 * Deletes a user by userId.
	 *
	 * @param userId unique user identifier
	 * @throws IllegalArgumentException if the user does not exist
	 */
	public void deleteUser(String userId) {
		if (!userRepository.existsByUserId(userId)) {
			throw new IllegalArgumentException("User not found: " + userId);
		}
		userRepository.deleteByUserId(userId);
		log.info("Deleted user [{}]", userId);
	}

	/**
	 * Checks whether a user exists.
	 *
	 * @param userId unique user identifier
	 * @return true if the user exists
	 */
	public boolean userExists(String userId) {
		return userRepository.existsByUserId(userId);
	}
}
