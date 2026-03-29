package com.techcobber.smarttrader.v1.models;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document("orders")
public class Order {
	private String userId;
	private LocalDateTime orderTS;
	private String productId;
	private double price;
	private double qty;
	private String side;
	private Map<String, String> decisionFactors;
	private String comments;
}
