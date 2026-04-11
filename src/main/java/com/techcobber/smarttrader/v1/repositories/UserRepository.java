package com.techcobber.smarttrader.v1.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.techcobber.smarttrader.v1.models.User;

/**
 * Spring Data MongoDB repository for {@link User}.
 *
 * <p>Since {@code userId} is the {@code @Id} field, the built-in
 * {@code findById}, {@code existsById}, and {@code deleteById} methods
 * operate directly on the user's unique identifier.</p>
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {
}
