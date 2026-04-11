package com.techcobber.smarttrader.v1.services;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.techcobber.smarttrader.v1.models.UserPreferences;
import com.techcobber.smarttrader.v1.repositories.UserPreferencesRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing per-user trading preferences.
 *
 * <p>Provides CRUD operations for {@link UserPreferences} documents in MongoDB.
 * Each user has at most one preferences document.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserPreferencesService {

	private final UserPreferencesRepository preferencesRepository;

	/**
	 * Retrieves the preferences for a user.
	 *
	 * @param userId unique user identifier
	 * @return an Optional containing the preferences, or empty if none are stored
	 */
	public Optional<UserPreferences> getPreferences(String userId) {
		return preferencesRepository.findByUserId(userId);
	}

	/**
	 * Creates or updates preferences for a user.
	 *
	 * <p>If preferences already exist for the user, the mutable fields are updated
	 * in place. Otherwise a new document is created.</p>
	 *
	 * @param userId  unique user identifier
	 * @param updates the preferences to save
	 * @return the persisted preferences
	 */
	public UserPreferences savePreferences(String userId, UserPreferences updates) {
		Optional<UserPreferences> existing = preferencesRepository.findByUserId(userId);

		UserPreferences prefs;
		if (existing.isPresent()) {
			prefs = existing.get();
		} else {
			prefs = new UserPreferences();
			prefs.setUserId(userId);
		}

		if (updates.getStrategy() != null) {
			prefs.setStrategy(updates.getStrategy());
		}
		if (updates.getGranularity() != null) {
			prefs.setGranularity(updates.getGranularity());
		}
		if (updates.getBaseAsset() != null) {
			prefs.setBaseAsset(updates.getBaseAsset());
		}
		if (updates.getQuoteAsset() != null) {
			prefs.setQuoteAsset(updates.getQuoteAsset());
		}
		if (updates.getPositionSize() != null) {
			prefs.setPositionSize(updates.getPositionSize());
		}
		if (updates.getMaxDailyLoss() != null) {
			prefs.setMaxDailyLoss(updates.getMaxDailyLoss());
		}
		if (updates.getTimezone() != null) {
			prefs.setTimezone(updates.getTimezone());
		}
		prefs.setEnabled(updates.isEnabled());
		prefs.setUpdatedAt(Instant.now());

		UserPreferences saved = preferencesRepository.save(prefs);
		log.info("Saved preferences for user [{}]", userId);
		return saved;
	}

	/**
	 * Deletes all preferences for a user.
	 *
	 * @param userId unique user identifier
	 * @throws IllegalArgumentException if no preferences exist for the user
	 */
	public void deletePreferences(String userId) {
		if (!preferencesRepository.existsByUserId(userId)) {
			throw new IllegalArgumentException("No preferences found for user: " + userId);
		}
		preferencesRepository.deleteByUserId(userId);
		log.info("Deleted preferences for user [{}]", userId);
	}
}
