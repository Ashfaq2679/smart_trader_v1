package com.techcobber.smarttrader.v1.strategy;

import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.models.TradeDecision;

public interface TradingStrategy {

	TradeDecision analyze(List<MyCandle> candles);

	String getName();
}
