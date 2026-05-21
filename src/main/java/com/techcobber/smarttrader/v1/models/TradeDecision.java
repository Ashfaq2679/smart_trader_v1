package com.techcobber.smarttrader.v1.models;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "trade_decisions")
@CompoundIndex(name = "idx_product_ts", def = "{ 'productId': 1, 'timestamp': -1 }")
public class TradeDecision {

	@Id
	private String id;

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

	@CreatedDate
	private LocalDateTime timestamp;
}