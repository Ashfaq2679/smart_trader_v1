package com.techcobber.smarttrader.v1.strategy;

import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;

/**
 * Template interface for trading strategies.
 *
 * <p><b>Design Pattern: Strategy</b> — Defines a family of algorithms (trading
 * strategies), encapsulates each one, and makes them interchangeable. Concrete
 * implementations (e.g. {@link PriceActionStrategy}) provide the actual decision
 * logic while consumers depend only on this contract.</p>
 *
 * <p>Every implementation must be <em>stateless</em> with respect to a single
 * {@link #analyze} invocation so that strategies can be shared safely across
 * threads in a cloud-native deployment.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 *   TradingStrategy strategy = new PriceActionStrategy();
 *   TradeSignal signal = strategy.analyze(candles);
 * </pre>
 */
public interface TradingStrategy {

	/**
	 * Analyses a list of candles and returns a trading signal.
	 *
	 * <p>The candle list must be ordered <b>chronologically</b> (oldest first,
	 * most-recent last). Implementations may require a minimum number of candles
	 * and should return {@link Signal#HOLD} with zero confidence when the input
	 * is insufficient.</p>
	 *
	 * @param candles chronologically ordered candle history; must not be {@code null}
	 * @return a {@link TradeSignal} representing the strategy's recommendation
	 */
	TradeSignal analyze(List<MyCandle> candles);

	/**
	 * Returns a unique, human-readable name for this strategy.
	 *
	 * @return strategy name, never {@code null}
	 */
	String getName();
}
