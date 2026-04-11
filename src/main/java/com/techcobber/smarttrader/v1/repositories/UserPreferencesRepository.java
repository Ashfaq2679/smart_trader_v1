package com.techcobber.smarttrader.v1.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.techcobber.smarttrader.v1.models.UserPreferences;

/**
 * Spring Data MongoDB repository for {@link UserPreferences}.
 */
@Repository
public interface UserPreferencesRepository extends MongoRepository<UserPreferences, String> {

	Optional<UserPreferences> findByUserId(String userId);

	boolean existsByUserId(String userId);

	void deleteByUserId(String userId);
}
