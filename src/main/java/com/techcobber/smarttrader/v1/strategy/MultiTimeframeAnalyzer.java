package com.techcobber.smarttrader.v1.strategy;

import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendDirection;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendResult;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Aligns trend direction across three timeframes: LTF (15m), confirmation (1H),
 * and direction (4H).
 *
 * <p><b>Alignment rules:</b>
 * <ul>
 *   <li>The 4H trend must <em>not</em> be {@code DOWN} — if the 4H trend is
 *       bearish, no BUY is allowed regardless of lower-timeframe signals.</li>
 *   <li>The 1H (confirmation) trend must agree with the 15m (entry) trend direction
 *       (both UP, or both not DOWN for a BUY candidate).</li>
 * </ul>
 * When {@code isAligned} is {@code false}, the calling strategy should suppress
 * or downgrade any BUY signal to HOLD.
 * </p>
 */
@Slf4j
public class MultiTimeframeAnalyzer {

    private static final int LTF_LOOKBACK     = 20;
    private static final int CONFIRM_LOOKBACK = 20;
    private static final int HTF_LOOKBACK     = 20;

    private final TrendAnalyzer trendAnalyzer;

    public MultiTimeframeAnalyzer() {
        this.trendAnalyzer = new TrendAnalyzer();
    }

    MultiTimeframeAnalyzer(TrendAnalyzer trendAnalyzer) {
        this.trendAnalyzer = trendAnalyzer;
    }

    @Data
    public static class MultiTimeframeResult {
        private final TrendDirection ltfTrend;          // 15m
        private final TrendDirection confirmTrend;      // 1H
        private final TrendDirection htfTrend;          // 4H
        private final boolean aligned;
        private final String description;
    }

    /**
     * Analyses trend alignment across LTF, confirmation, and HTF candle sets.
     *
     * @param ltfCandles     15m candles (entry timeframe)
     * @param confirmCandles 1H candles (confirmation timeframe)
     * @param htfCandles     4H candles (directional timeframe)
     * @return {@link MultiTimeframeResult}
     */
    public MultiTimeframeResult analyze(
            List<MyCandle> ltfCandles,
            List<MyCandle> confirmCandles,
            List<MyCandle> htfCandles) {

        TrendResult ltf     = analyzeSafe(ltfCandles,     LTF_LOOKBACK,     "LTF(15m)");
        TrendResult confirm = analyzeSafe(confirmCandles, CONFIRM_LOOKBACK, "Confirm(1H)");
        TrendResult htf     = analyzeSafe(htfCandles,     HTF_LOOKBACK,     "HTF(4H)");

        boolean aligned = isAligned(ltf.getDirection(), confirm.getDirection(), htf.getDirection());

        String description = String.format(
                "15m=%s | 1H=%s | 4H=%s | aligned=%s",
                ltf.getDirection(), confirm.getDirection(), htf.getDirection(), aligned);

        log.info("MultiTimeframe: {}", description);

        return new MultiTimeframeResult(
                ltf.getDirection(),
                confirm.getDirection(),
                htf.getDirection(),
                aligned,
                description);
    }

    /**
     * Convenience overload when only HTF candles differ from the LTF candles already
     * used in the main analysis pass. Confirmation and HTF candles are fetched lazily.
     *
     * @param ltfTrend     already-computed 15m trend direction
     * @param confirmCandles 1H candles
     * @param htfCandles     4H candles
     */
    public MultiTimeframeResult analyzeWithKnownLtf(
            TrendDirection ltfTrend,
            List<MyCandle> confirmCandles,
            List<MyCandle> htfCandles) {

        TrendResult confirm = analyzeSafe(confirmCandles, CONFIRM_LOOKBACK, "Confirm(1H)");
        TrendResult htf     = analyzeSafe(htfCandles,     HTF_LOOKBACK,     "HTF(4H)");

        boolean aligned = isAligned(ltfTrend, confirm.getDirection(), htf.getDirection());

        String description = String.format(
                "15m=%s | 1H=%s | 4H=%s | aligned=%s",
                ltfTrend, confirm.getDirection(), htf.getDirection(), aligned);

        log.info("MultiTimeframe: {}", description);

        return new MultiTimeframeResult(ltfTrend, confirm.getDirection(), htf.getDirection(), aligned, description);
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /**
     * BUY alignment: 4H must not be DOWN, and 1H must not contradict 15m.
     * SELL alignment is handled by the exit evaluator separately (any signal with
     * deteriorating 1H/4H is sufficient to exit SPOT).
     */
    private boolean isAligned(TrendDirection ltf, TrendDirection confirm, TrendDirection htf) {
        if (htf == TrendDirection.DOWN) {
            log.debug("MTF: 4H is DOWN — BUY not aligned");
            return false;
        }
        // Confirm (1H) must not contradict LTF
        if (ltf == TrendDirection.UP && confirm == TrendDirection.DOWN) {
            log.debug("MTF: 1H DOWN contradicts 15m UP — BUY not aligned");
            return false;
        }
        return true;
    }

    private TrendResult analyzeSafe(List<MyCandle> candles, int lookback, String label) {
        if (candles == null || candles.isEmpty()) {
            log.warn("MultiTimeframeAnalyzer: no {} candles provided — defaulting to SIDEWAYS", label);
            return new TrendResult(TrendDirection.SIDEWAYS, 0.0, "No data");
        }
        return trendAnalyzer.analyzeTrend(candles, lookback);
    }
}
