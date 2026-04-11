package com.techcobber.smarttrader.v1.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.techcobber.smarttrader.v1.models.Order;

/**
 * Spring Data MongoDB repository for {@link Order} documents.
 */
public interface OrderRepository extends MongoRepository<Order, String> {

	List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

	List<Order> findByUserIdAndProductIdOrderByCreatedAtDesc(String userId, String productId);

	Optional<Order> findByCoinbaseOrderId(String coinbaseOrderId);
}
