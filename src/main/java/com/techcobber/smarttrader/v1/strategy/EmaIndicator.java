package com.techcobber.smarttrader.v1.strategy;

import java.util.ArrayList;
import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;

import lombok.extern.slf4j.Slf4j;

/**
 * Computes the Exponential Moving Average (EMA) for a given period.
 *
 * <p>The first EMA value is seeded from a Simple Moving Average of the first
 * {@code period} closes. Subsequent values use the standard EMA multiplier:
 * {@code multiplier = 2 / (period + 1)}.</p>
 *
 * <p>Primary uses:
 * <ul>
 *   <li>EMA50 — location filter (is price near an EMA pullback?)</li>
 *   <li>EMA9 / EMA21 — crossover detection for momentum exit signals</li>
 * </ul>
 * </p>
 */
@Slf4j
public class EmaIndicator {

    /**
     * Calculates the EMA at the most recent candle.
     *
     * @param candles list of candles (oldest first)
     * @param period  EMA period (e.g. 9, 21, 50)
     * @return EMA value at the last candle, or the simple average of available closes
     *         if there are fewer candles than the period
     */
    public double calculate(List<MyCandle> candles, int period) {
        if (candles == null || candles.isEmpty()) {
            log.warn("EMA{}: empty candle list", period);
            return 0.0;
        }

        if (candles.size() < period) {
            double avg = candles.stream()
                    .mapToDouble(MyCandle::getClose)
                    .average()
                    .orElse(0.0);
            log.debug("EMA{}: insufficient candles ({}) — returning simple avg {}", period, candles.size(), avg);
            return avg;
        }

        // Seed with SMA of first `period` values
        double ema = candles.subList(0, period)
                .stream()
                .mapToDouble(MyCandle::getClose)
                .average()
                .orElse(0.0);

        double multiplier = 2.0 / (period + 1.0);

        for (int i = period; i < candles.size(); i++) {
            double close = candles.get(i).getClose();
            ema = (close - ema) * multiplier + ema;
        }

        log.debug("EMA{}: {} (from {} candles)", period, String.format("%.4f", ema), candles.size());
        return ema;
    }

    /**
     * Calculates the full EMA series (one value per candle from index {@code period - 1} onward).
     *
     * @param candles list of candles (oldest first)
     * @param period  EMA period
     * @return list of EMA values aligned to candles from index {@code period - 1}; empty if insufficient data
     */
    public List<Double> calculateSeries(List<MyCandle> candles, int period) {
        List<Double> series = new ArrayList<>();
        if (candles == null || candles.size() < period) {
            return series;
        }

        double ema = candles.subList(0, period)
                .stream()
                .mapToDouble(MyCandle::getClose)
                .average()
                .orElse(0.0);

        series.add(ema);

        double multiplier = 2.0 / (period + 1.0);
        for (int i = period; i < candles.size(); i++) {
            ema = (candles.get(i).getClose() - ema) * multiplier + ema;
            series.add(ema);
        }

        return series;
    }
}
