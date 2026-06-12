package com.techcobber.smarttrader.v1.strategy;

import java.util.List;

import com.techcobber.smarttrader.v1.models.MyCandle;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendDirection;
import com.techcobber.smarttrader.v1.strategy.TrendAnalyzer.TrendResult;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Aligns trend direction across three timeframes: LTF (1H), confirmation (4H),
 * and direction (1D).
 *
 * <p><b>Alignment rules:</b>
 * <ul>
 *   <li>The 1D trend must <em>not</em> be {@code DOWN} — if the daily trend is
 *       bearish, no BUY is allowed regardless of lower-timeframe signals.</li>
 *   <li>The 4H (confirmation) trend must agree with the 1H (entry) trend direction
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
        private final TrendDirection ltfTrend;          // 1H
        private final TrendDirection confirmTrend;      // 4H
        private final TrendDirection htfTrend;          // 1D
        private final boolean aligned;
        private final String description;
    }

    /**
     * Analyses trend alignment across LTF, confirmation, and HTF candle sets.
     *
     * @param ltfCandles     1H candles (entry timeframe)
     * @param confirmCandles 4H candles (confirmation timeframe)
     * @param htfCandles     1D candles (directional timeframe)
     * @return {@link MultiTimeframeResult}
     */
    public MultiTimeframeResult analyze(
            List<MyCandle> ltfCandles,
            List<MyCandle> confirmCandles,
            List<MyCandle> htfCandles) {

        TrendResult ltf     = analyzeSafe(ltfCandles,     LTF_LOOKBACK,     "LTF(1H)");
        TrendResult confirm = analyzeSafe(confirmCandles, CONFIRM_LOOKBACK, "Confirm(4H)");
        TrendResult htf     = analyzeSafe(htfCandles,     HTF_LOOKBACK,     "HTF(1D)");

        boolean aligned = isAligned(ltf.getDirection(), confirm.getDirection(), htf.getDirection());

        String description = String.format(
                "1H=%s | 4H=%s | 1D=%s | aligned=%s",
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
     * @param ltfTrend     already-computed 1H trend direction
     * @param confirmCandles 4H candles
     * @param htfCandles     1D candles
     */
    public MultiTimeframeResult analyzeWithKnownLtf(
            TrendDirection ltfTrend,
            List<MyCandle> confirmCandles,
            List<MyCandle> htfCandles) {

        TrendResult confirm = analyzeSafe(confirmCandles, CONFIRM_LOOKBACK, "Confirm(4H)");
        TrendResult htf     = analyzeSafe(htfCandles,     HTF_LOOKBACK,     "HTF(1D)");

        boolean aligned = isAligned(ltfTrend, confirm.getDirection(), htf.getDirection());

        String description = String.format(
                "1H=%s | 4H=%s | 1D=%s | aligned=%s",
                ltfTrend, confirm.getDirection(), htf.getDirection(), aligned);

        log.info("MultiTimeframe: {}", description);

        return new MultiTimeframeResult(ltfTrend, confirm.getDirection(), htf.getDirection(), aligned, description);
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /**
     * BUY alignment: 1D must not be DOWN, and 4H must not contradict 1H.
     * SELL alignment is handled by the exit evaluator separately (any signal with
     * deteriorating 4H/1D is sufficient to exit SPOT).
     */
    private boolean isAligned(TrendDirection ltf, TrendDirection confirm, TrendDirection htf) {
        if (htf == TrendDirection.DOWN) {
            log.debug("MTF: 1D is DOWN — BUY not aligned");
            return false;
        }
        // Confirm (4H) must not contradict LTF
        if (ltf == TrendDirection.UP && confirm == TrendDirection.DOWN) {
            log.debug("MTF: 4H DOWN contradicts 1H UP — BUY not aligned");
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
