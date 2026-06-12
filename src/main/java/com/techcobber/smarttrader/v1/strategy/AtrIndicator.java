package com.techcobber.smarttrader.v1.strategy;

import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;

import lombok.extern.slf4j.Slf4j;

/**
 * Computes the Average True Range (ATR) and provides volatility utilities.
 *
 * <p>True Range for each candle is defined as the maximum of:
 * <ul>
 *   <li>High − Low</li>
 *   <li>|High − Previous Close|</li>
 *   <li>|Low  − Previous Close|</li>
 * </ul>
 * The first ATR is seeded with a Simple Moving Average of the first {@code period}
 * True Range values; subsequent values use Wilder's smoothing.</p>
 *
 * <p>Also provides swing-high / swing-low utilities used by stop-loss and
 * trailing-stop calculations in {@link RiskManager}.</p>
 */
@Slf4j
public class AtrIndicator {

    private static final int DEFAULT_PERIOD = 14;
    private static final int DEFAULT_AVG_LOOKBACK = 20;

    /**
     * Calculates ATR(14) at the most recent candle.
     */
    public double calculate(List<MyCandle> candles) {
        return calculate(candles, DEFAULT_PERIOD);
    }

    /**
     * Calculates ATR for a custom period at the most recent candle.
     *
     * @param candles list of candles (oldest first); needs at least {@code period + 1} candles
     * @param period  ATR period (commonly 14)
     * @return ATR value, or 0.0 if insufficient data
     */
    public double calculate(List<MyCandle> candles, int period) {
        if (candles == null || candles.size() < period + 1) {
            log.warn("ATR{}: insufficient candles ({})", period, candles == null ? 0 : candles.size());
            return 0.0;
        }

        // Seed ATR with SMA of first `period` True Ranges
        double atr = 0.0;
        for (int i = 1; i <= period; i++) {
            atr += trueRange(candles.get(i), candles.get(i - 1));
        }
        atr /= period;

        // Wilder smoothing for the rest
        for (int i = period + 1; i < candles.size(); i++) {
            double tr = trueRange(candles.get(i), candles.get(i - 1));
            atr = (atr * (period - 1) + tr) / period;
        }

        log.debug("ATR{}: {} (from {} candles)", period, String.format("%.4f", atr), candles.size());
        return atr;
    }

    /**
     * Calculates the average ATR over the most recent {@code lookback} candles.
     * Used to establish the baseline for spike detection.
     *
     * @param candles  list of candles
     * @param lookback number of recent ATR values to average
     * @return average ATR, or 0.0 if insufficient data
     */
    public double calculateAverage(List<MyCandle> candles, int lookback) {
        if (candles == null || candles.size() < DEFAULT_PERIOD + lookback + 1) {
            return calculate(candles);
        }

        int size = candles.size();
        double sum = 0.0;
        int count = 0;

        for (int end = size - lookback; end <= size; end++) {
            if (end < DEFAULT_PERIOD + 1) continue;
            double atr = calculate(candles.subList(0, end), DEFAULT_PERIOD);
            sum += atr;
            count++;
        }

        return count > 0 ? sum / count : calculate(candles);
    }

    /**
     * Returns {@code true} when the current ATR exceeds {@code spikeMultiplier} × the average ATR,
     * indicating unusually high volatility that should block new entries.
     *
     * @param candles         candle list
     * @param spikeMultiplier threshold multiplier (e.g. 2.0 means current > 2× average)
     */
    public boolean isSpike(List<MyCandle> candles, double spikeMultiplier) {
        double currentAtr = calculate(candles);
        double avgAtr = calculateAverage(candles, DEFAULT_AVG_LOOKBACK);

        if (avgAtr <= 0) return false;

        boolean spike = currentAtr > spikeMultiplier * avgAtr;
        if (spike) {
            log.info("ATR spike detected: current={} > {}×avg={}", 
                    String.format("%.4f", currentAtr),
                    spikeMultiplier,
                    String.format("%.4f", avgAtr));
        }
        return spike;
    }

    /**
     * Returns the highest high within the most recent {@code lookback} candles.
     * Used as the reference peak for trailing-stop calculations.
     */
    public double getRecentSwingHigh(List<MyCandle> candles, int lookback) {
        if (candles == null || candles.isEmpty()) return 0.0;
        int start = Math.max(0, candles.size() - lookback);
        return candles.subList(start, candles.size())
                .stream()
                .mapToDouble(MyCandle::getHigh)
                .max()
                .orElse(0.0);
    }

    /**
     * Returns the lowest low within the most recent {@code lookback} candles.
     * Used as the swing-low base for ATR stop-loss calculations.
     */
    public double getRecentSwingLow(List<MyCandle> candles, int lookback) {
        if (candles == null || candles.isEmpty()) return 0.0;
        int start = Math.max(0, candles.size() - lookback);
        return candles.subList(start, candles.size())
                .stream()
                .mapToDouble(MyCandle::getLow)
                .min()
                .orElse(0.0);
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private double trueRange(MyCandle current, MyCandle prev) {
        double highLow  = current.getHigh() - current.getLow();
        double highPrev = Math.abs(current.getHigh() - prev.getClose());
        double lowPrev  = Math.abs(current.getLow() - prev.getClose());
        return Math.max(highLow, Math.max(highPrev, lowPrev));
    }
}
