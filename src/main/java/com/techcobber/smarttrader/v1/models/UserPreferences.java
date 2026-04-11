package com.techcobber.smarttrader.v1.models;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document storing per-user trading configuration.
 *
 * <p>Each user has at most one preferences document, uniquely identified
 * by {@code userId}. Default values are applied when a user has not yet
 * customised their preferences.</p>
 */
@Data
@Document("user_preferences")
public class UserPreferences {

	@Id
	private String id;

	@Indexed(unique = true)
	private String userId;

	private String strategy;
	private String granularity;
	private String baseAsset;
	private String quoteAsset;
	private String positionSize;
	private String maxDailyLoss;
	private String timezone;
	private boolean enabled;
	private Instant updatedAt;
}
