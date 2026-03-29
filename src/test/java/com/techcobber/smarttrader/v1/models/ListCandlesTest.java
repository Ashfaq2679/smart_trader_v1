package com.techcobber.smarttrader.v1.models;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the ListCandles model.
 * Verifies getter/setter behavior with empty and populated candle lists.
 */
class ListCandlesTest {

    @Test
    void gettersAndSetters_candlesField() {
        ListCandles listCandles = new ListCandles();
        List<MyCandle> candles = new ArrayList<>();

        listCandles.setCandles(candles);

        assertThat(listCandles.getCandles()).isSameAs(candles);
    }

    @Test
    void candles_withEmptyList() {
        ListCandles listCandles = new ListCandles();
        listCandles.setCandles(new ArrayList<>());

        assertThat(listCandles.getCandles()).isNotNull();
        assertThat(listCandles.getCandles()).isEmpty();
    }

    @Test
    void candles_withPopulatedList() {
        ListCandles listCandles = new ListCandles();

        MyCandle candle1 = new MyCandle();
        candle1.setOpen(100.0);
        candle1.setClose(110.0);
        candle1.setHigh(115.0);
        candle1.setLow(95.0);
        candle1.setVolume(5000.0);
        candle1.setStart(1700000000L);

        MyCandle candle2 = new MyCandle();
        candle2.setOpen(110.0);
        candle2.setClose(105.0);
        candle2.setHigh(112.0);
        candle2.setLow(103.0);
        candle2.setVolume(3000.0);
        candle2.setStart(1700003600L);

        List<MyCandle> candles = new ArrayList<>();
        candles.add(candle1);
        candles.add(candle2);
        listCandles.setCandles(candles);

        assertThat(listCandles.getCandles()).hasSize(2);
        assertThat(listCandles.getCandles().get(0).getOpen()).isEqualTo(100.0);
        assertThat(listCandles.getCandles().get(1).getClose()).isEqualTo(105.0);
    }
}
