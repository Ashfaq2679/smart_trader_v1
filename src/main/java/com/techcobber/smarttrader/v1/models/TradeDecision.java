package com.techcobber.smarttrader.v1.models;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TradeDecision {

	public enum Signal {
		BUY, SELL, HOLD
	}

	private Signal signal;
	private double confidence;
	private String reasoning;
	private List<String> detectedPatterns;
	private String trendDirection;
	private Double nearestSupport;
	private Double nearestResistance;
	private String productId;
	private LocalDateTime timestamp;
}
