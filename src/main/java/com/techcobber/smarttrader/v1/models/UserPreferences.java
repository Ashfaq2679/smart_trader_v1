package com.techcobber.smarttrader.v1.models;

import lombok.Data;

@Data
public class UserPreferences {
	private String userId;// PK,
	private String strategy;// VARCHAR, -- e.g. "mean-reversion"
	private String granularity;// VARCHAR, -- e.g. "1m", "5m"
	private String baseAsset;// VARCHAR, -- e.g. "BTC"
	private String quoteAsset;// VARCHAR, -- e.g. "USDT"
	private String positionSize;// NUMERIC,
	private String maxDailyLoss;// NUMERIC,
	private String timezone;// VARCHAR,
	private boolean enabled;// BOOLEAN,
	private String updatedAt;
}
