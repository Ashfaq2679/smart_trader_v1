package com.techcobber.smarttrader.v1.models;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
	private Double suggestedPrice;
	private String productId;

	@CreatedDate
	private LocalDateTime timestamp;

	@Override
	public String toString() {
		return String.format(
				"\nTradeDecision{id:'%s',\n signal=%s,\n confidence=%.2f,\n reasoning='%s',\n patterns=%s,\n trend='%s',"
				+ "\n support=%.2f,\n resistance=%.2f,\n price=%.2f,\n productId='%s',\n timestamp=%s\n}",
				id, signal, confidence, reasoning, detectedPatterns, trendDirection, nearestSupport, nearestResistance,
				suggestedPrice, productId, timestamp);
	}
}