package com.techcobber.smarttrader.v1.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the UserPreferences model.
 * Verifies Lombok-generated getters, setters, equals, hashCode, and toString.
 */
class UserPreferencesTest {

    @Test
    void gettersAndSetters_allFields() {
        UserPreferences prefs = new UserPreferences();

        prefs.setUserId("user-456");
        prefs.setStrategy("momentum");
        prefs.setGranularity("ONE_HOUR");
        prefs.setBaseAsset("BTC");
        prefs.setQuoteAsset("USD");
        prefs.setPositionSize("0.01");
        prefs.setMaxDailyLoss("500");
        prefs.setTimezone("America/New_York");
        prefs.setEnabled(true);
        prefs.setUpdatedAt("2025-01-15T10:30:00Z");

        assertThat(prefs.getUserId()).isEqualTo("user-456");
        assertThat(prefs.getStrategy()).isEqualTo("momentum");
        assertThat(prefs.getGranularity()).isEqualTo("ONE_HOUR");
        assertThat(prefs.getBaseAsset()).isEqualTo("BTC");
        assertThat(prefs.getQuoteAsset()).isEqualTo("USD");
        assertThat(prefs.getPositionSize()).isEqualTo("0.01");
        assertThat(prefs.getMaxDailyLoss()).isEqualTo("500");
        assertThat(prefs.getTimezone()).isEqualTo("America/New_York");
        assertThat(prefs.isEnabled()).isTrue();
        assertThat(prefs.getUpdatedAt()).isEqualTo("2025-01-15T10:30:00Z");
    }

    @Test
    void equals_sameFieldValues_areEqual() {
        UserPreferences prefs1 = new UserPreferences();
        prefs1.setUserId("user-1");
        prefs1.setStrategy("scalp");
        prefs1.setEnabled(true);

        UserPreferences prefs2 = new UserPreferences();
        prefs2.setUserId("user-1");
        prefs2.setStrategy("scalp");
        prefs2.setEnabled(true);

        assertThat(prefs1).isEqualTo(prefs2);
        assertThat(prefs1.hashCode()).isEqualTo(prefs2.hashCode());
    }

    @Test
    void equals_differentFieldValues_areNotEqual() {
        UserPreferences prefs1 = new UserPreferences();
        prefs1.setUserId("user-1");
        prefs1.setStrategy("momentum");

        UserPreferences prefs2 = new UserPreferences();
        prefs2.setUserId("user-1");
        prefs2.setStrategy("mean_reversion");

        assertThat(prefs1).isNotEqualTo(prefs2);
    }

    @Test
    void toString_containsFieldValues() {
        UserPreferences prefs = new UserPreferences();
        prefs.setUserId("user-xyz");
        prefs.setStrategy("breakout");
        prefs.setBaseAsset("ETH");
        prefs.setEnabled(false);

        String result = prefs.toString();

        // Lombok @Data toString includes class name and field values
        assertThat(result).contains("user-xyz");
        assertThat(result).contains("breakout");
        assertThat(result).contains("ETH");
        assertThat(result).contains("false");
    }
}
