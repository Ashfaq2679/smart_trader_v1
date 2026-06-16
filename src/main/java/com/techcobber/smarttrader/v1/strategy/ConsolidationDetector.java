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
	private static final double VOLUME_BREAKOUT_MULTIPLIER = 1.5;

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
        return detect(candles, DEFAULT_LOOKBACK);
    }

    /**
     * Detects consolidation using a custom lookback window and default thresholds.
     */
    public ConsolidationResult detect(List<MyCandle> candles, int lookback) {
        return detect(candles, lookback, DEFAULT_RANGE_THRESHOLD_PCT, DEFAULT_ATR_THRESHOLD_PCT, VOLUME_BREAKOUT_MULTIPLIER);
    }

    /**
	 * Detects consolidation using custom parameters.
	 *
	 * @param candles List of candles to analyze (must be at least lookback + 2 in size)
	 * @param lookback Number of prior candles to consider for consolidation (default 20)
	 * @param rangeThresholdPct Max range as % of mid-price to consider consolidating (default 2.5%)
	 * @param atrThresholdPct Max ATR as % of mid-price to consider consolidating (default 0.5%)
	 * @return ConsolidationResult with details on whether consolidating and relevant metrics
	 */
    public ConsolidationResult detect(
            List<MyCandle> candles, int lookback, double rangeThresholdPct, double atrThresholdPct, double breakoutVolumeMultiplier) {
        if (candles == null || candles.size() < lookback + 2) {
            return new ConsolidationResult(false, 0.0, 0.0, "Insufficient data");
        }

        MyCandle latest = candles.get(candles.size() - 1);

        int start = Math.max(0, candles.size() - lookback - 1);

        // exclude latest candle from consolidation window
        List<MyCandle> priorWindow = candles.subList(start, candles.size() - 1);

        double rangeHigh = priorWindow.stream()
                .mapToDouble(MyCandle::getHigh)
                .max()
                .orElse(0.0);

        double rangeLow = priorWindow.stream()
                .mapToDouble(MyCandle::getLow)
                .min()
                .orElse(0.0);

        double midPrice = (rangeHigh + rangeLow) / 2.0;

        if (midPrice <= 0) {
            return new ConsolidationResult(false, 0.0, 0.0, "Invalid price data");
        }

        double rangePercent = ((rangeHigh - rangeLow) / midPrice) * 100.0;

        double atr = atrIndicator.calculate(priorWindow);
        double atrPercent = (atr / midPrice) * 100.0;

        double avgVolume = priorWindow.stream()
                .mapToDouble(MyCandle::getVolume)
                .average()
                .orElse(0.0);

        boolean rangeTight = rangePercent < rangeThresholdPct;
        boolean atrLow = atrPercent < atrThresholdPct;
        boolean wasConsolidating = rangeTight && atrLow;

        boolean breakoutUp =
                wasConsolidating
                        && latest.getClose() > rangeHigh
                        && latest.getVolume() > avgVolume * breakoutVolumeMultiplier
                        && isStrongBullishCandle(latest);

        boolean breakoutDown =
                wasConsolidating
                        && latest.getClose() < rangeLow
                        && latest.getVolume() > avgVolume * breakoutVolumeMultiplier
                        && isStrongBearishCandle(latest);

        if (breakoutUp) {
            return new ConsolidationResult(
                    false,
                    rangePercent,
                    atrPercent,
                    String.format(
                            "BREAKOUT_UP: close=%.4f > rangeHigh=%.4f, volume spike %.2fx",
                            latest.getClose(),
                            rangeHigh,
                            latest.getVolume() / avgVolume
                    )
            );
        }

        if (breakoutDown) {
            return new ConsolidationResult(
                    false,
                    rangePercent,
                    atrPercent,
                    String.format(
                            "BREAKOUT_DOWN: close=%.4f < rangeLow=%.4f, volume spike %.2fx",
                            latest.getClose(),
                            rangeLow,
                            latest.getVolume() / avgVolume
                    )
            );
        }

        if (wasConsolidating) {
            return new ConsolidationResult(
                    true,
                    rangePercent,
                    atrPercent,
                    String.format(
                            "CONSOLIDATING: range=%.2f%%, ATR=%.2f%%",
                            rangePercent,
                            atrPercent
                    )
            );
        }

        return new ConsolidationResult(
                false,
                rangePercent,
                atrPercent,
                String.format("TRENDING: range=%.2f%%, ATR=%.2f%%", rangePercent, atrPercent)
        );
    }
    
    private boolean isStrongBullishCandle(MyCandle candle) {
        double range = candle.getHigh() - candle.getLow();
        if (range <= 0) return false;

        double body = candle.getClose() - candle.getOpen();
        double bodyPct = body / range;

        return candle.getClose() > candle.getOpen()
                && bodyPct >= 0.55;
    }

    private boolean isStrongBearishCandle(MyCandle candle) {
        double range = candle.getHigh() - candle.getLow();
        if (range <= 0) return false;

        double body = candle.getOpen() - candle.getClose();
        double bodyPct = body / range;

        return candle.getClose() < candle.getOpen()
                && bodyPct >= 0.55;
    }
}
