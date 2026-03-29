package com.techcobber.smarttrader.v1.models;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserCredentials} model.
 */
class UserCredentialsTest {

    @Test
    void setAndGetUserId() {
        UserCredentials uc = new UserCredentials();
        uc.setUserId("user-42");
        assertThat(uc.getUserId()).isEqualTo("user-42");
    }

    @Test
    void setAndGetEncryptedCredentials() {
        UserCredentials uc = new UserCredentials();
        uc.setEncryptedCredentials("encrypted-blob");
        assertThat(uc.getEncryptedCredentials()).isEqualTo("encrypted-blob");
    }

    @Test
    void setAndGetTimestamps() {
        UserCredentials uc = new UserCredentials();
        Instant now = Instant.now();
        uc.setCreatedAt(now);
        uc.setUpdatedAt(now.plusSeconds(60));

        assertThat(uc.getCreatedAt()).isEqualTo(now);
        assertThat(uc.getUpdatedAt()).isAfter(uc.getCreatedAt());
    }

    @Test
    void defaultFieldsAreNull() {
        UserCredentials uc = new UserCredentials();

        assertThat(uc.getId()).isNull();
        assertThat(uc.getUserId()).isNull();
        assertThat(uc.getEncryptedCredentials()).isNull();
        assertThat(uc.getCreatedAt()).isNull();
        assertThat(uc.getUpdatedAt()).isNull();
    }
}
