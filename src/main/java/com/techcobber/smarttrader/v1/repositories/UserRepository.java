package com.techcobber.smarttrader.v1.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.techcobber.smarttrader.v1.models.User;

/**
 * Spring Data MongoDB repository for {@link User}.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

	Optional<User> findByUserId(String userId);

	boolean existsByUserId(String userId);

	void deleteByUserId(String userId);
}
