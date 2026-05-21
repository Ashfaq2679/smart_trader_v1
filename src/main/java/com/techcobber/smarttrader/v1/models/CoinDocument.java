package com.techcobber.smarttrader.v1.models;

import org.springframework.data.mongodb.core.mapping.Document;

@Document("coins")
public record CoinDocument(String coin, Integer rank, String productId) {

}
