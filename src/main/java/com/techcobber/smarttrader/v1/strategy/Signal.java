package com.techcobber.smarttrader.v1.strategy;

/**
 * Represents the possible trading signal directions.
 *
 * <p>A trading strategy evaluates market data and produces one of these
 * signals to indicate the recommended action.</p>
 */
public enum Signal {

	/** Indicates a buy opportunity — the strategy detects bullish conditions. */
	BUY,

	/** Indicates a sell opportunity — the strategy detects bearish conditions. */
	SELL,

	/** Indicates no clear opportunity — the strategy advises staying out. */
	HOLD
}
