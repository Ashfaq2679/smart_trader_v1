package com.techcobber.smarttrader.v1.models;

import lombok.Data;

@Data
public class UserPreferences {
	private String userId;
	private String strategy;
	private String granularity;
	private String baseAsset;
	private String quoteAsset;
	private String positionSize;
	private String maxDailyLoss;
	private String timezone;
	private boolean enabled;
	private String updatedAt;
}
