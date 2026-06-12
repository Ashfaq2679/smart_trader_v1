package com.techcobber.smarttrader.v1.strategy;

import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects whether the market is in a consolidation (ranging) phase.
 *
 * <p>Two criteria must both be true for the market to be considered consolidating:
 * <ol>
 *   <li><b>Range compression</b> — the price range over the lookback window
 *       (highest high − lowest low) is less than {@code rangeThresholdPct} percent
 *       of the mid-price (default 2.5 %).</li>
 *   <li><b>Low ATR</b> — the current ATR as a percentage of the mid-price is below
 *       {@code atrThresholdPct} (default 0.5 %).</li>
 * </ol>
 * When both conditions hold, the market is ranging and new entries should be skipped.
 * </p>
 */
@Slf4j
public class ConsolidationDetector {

    private static final double DEFAULT_RANGE_THRESHOLD_PCT = 2.5;
    private static final double DEFAULT_ATR_THRESHOLD_PCT = 0.5;
    private static final int DEFAULT_LOOKBACK = 20;

    private final AtrIndicator atrIndicator;

    public ConsolidationDetector() {
        this.atrIndicator = new AtrIndicator();
    }

    ConsolidationDetector(AtrIndicator atrIndicator) {
        this.atrIndicator = atrIndicator;
    }

    @Data
    public static class ConsolidationResult {
        private final boolean consolidating;
        private final double rangePercent;
        private final double atrPercent;
        private final String description;
    }

    /**
     * Detects consolidation using the default 20-candle lookback and default thresholds.
     */
    public ConsolidationResult detect(List<MyCandle> candles) {
        return detect(candles, DEFAULT_LOOKBACK, DEFAULT_RANGE_THRESHOLD_PCT, DEFAULT_ATR_THRESHOLD_PCT);
    }

    /**
     * Detects consolidation using a custom lookback window and default thresholds.
     */
    public ConsolidationResult detect(List<MyCandle> candles, int lookback) {
        return detect(candles, lookback, DEFAULT_RANGE_THRESHOLD_PCT, DEFAULT_ATR_THRESHOLD_PCT);
    }

    /**
     * Detects consolidation with fully configurable thresholds.
     *
     * @param candles            candle list (oldest first)
     * @param lookback           number of recent candles to inspect
     * @param rangeThresholdPct  max range-to-price % to classify as consolidating
     * @param atrThresholdPct    max ATR-to-price % to classify as consolidating
     * @return {@link ConsolidationResult}
     */
    public ConsolidationResult detect(List<MyCandle> candles, int lookback,
            double rangeThresholdPct, double atrThresholdPct) {

        if (candles == null || candles.size() < 5) {
            log.warn("ConsolidationDetector: insufficient candles ({})", candles == null ? 0 : candles.size());
            return new ConsolidationResult(false, 0.0, 0.0, "Insufficient data");
        }

        int start = Math.max(0, candles.size() - lookback);
        List<MyCandle> window = candles.subList(start, candles.size());

        double highestHigh = window.stream().mapToDouble(MyCandle::getHigh).max().orElse(0.0);
        double lowestLow   = window.stream().mapToDouble(MyCandle::getLow).min().orElse(0.0);
        double midPrice    = (highestHigh + lowestLow) / 2.0;

        if (midPrice <= 0) {
            return new ConsolidationResult(false, 0.0, 0.0, "Invalid price data");
        }

        double rangePercent = ((highestHigh - lowestLow) / midPrice) * 100.0;
        double atr = atrIndicator.calculate(candles);
        double atrPercent = (atr / midPrice) * 100.0;

        boolean rangeTight = rangePercent < rangeThresholdPct;
        boolean atrLow     = atrPercent < atrThresholdPct;
        boolean consolidating = rangeTight && atrLow;

        String description = consolidating
                ? String.format("Consolidating: range=%.2f%% (<%.1f%%), ATR=%.2f%% (<%.1f%%)",
                        rangePercent, rangeThresholdPct, atrPercent, atrThresholdPct)
                : String.format("Trending: range=%.2f%%, ATR=%.2f%%", rangePercent, atrPercent);

        log.info("ConsolidationDetector: {} — {}", consolidating ? "CONSOLIDATING" : "TRENDING", description);

        return new ConsolidationResult(consolidating, rangePercent, atrPercent, description);
    }
}
