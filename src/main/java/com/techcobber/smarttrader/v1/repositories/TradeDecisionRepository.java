package com.techcobber.smarttrader.v1.repositories;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.techcobber.smarttrader.v1.models.TradeDecision;

public interface TradeDecisionRepository extends MongoRepository<TradeDecision, String> {
    List<TradeDecision> findByProductIdOrderByTimestampDesc(String productId);
}
