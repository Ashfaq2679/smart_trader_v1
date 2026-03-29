package com.techcobber.smarttrader.v1.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ListCandles {

	@JsonProperty("candles")
	private List<MyCandle> candles;
}
