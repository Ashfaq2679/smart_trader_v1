package com.techcobber.smarttrader.v1.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.techcobber.smarttrader.v1.models.TradeDecision;
import com.techcobber.smarttrader.v1.repositories.TradeDecisionRepository;

@Service
public class TradeDecisionService {

    private final TradeDecisionRepository repository;

    public TradeDecisionService(TradeDecisionRepository repository) {
        this.repository = repository;
    }

    public TradeDecision save(TradeDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }
        if (!StringUtils.hasText(decision.getProductId())) {
            throw new IllegalArgumentException("productId is required");
        }
        if (decision.getTimestamp() == null) {
            decision.setTimestamp(LocalDateTime.now());
        }
        return repository.save(decision);
    }

    public List<TradeDecision> findByProductId(String productId) {
        if (!StringUtils.hasText(productId)) {
            throw new IllegalArgumentException("productId is required");
        }
        return repository.findByProductIdOrderByTimestampDesc(productId);
    }
}
