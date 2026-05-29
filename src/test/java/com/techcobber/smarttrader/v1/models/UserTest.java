package com.techcobber.smarttrader.v1.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link User} model.
 */
class UserTest {

    @Test
    void setAndGetUserId() {
        User user = new User();
        user.setUserId("user-42");
        assertThat(user.getUserId()).isEqualTo("user-42");
    }

    @Test
    void setAndGetEmail() {
        User user = new User();
        user.setEmail("test@example.com");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void setAndGetDisplayName() {
        User user = new User();
        user.setUserName("John Doe");
        assertThat(user.getUserName()).isEqualTo("John Doe");
    }

    @Test
    void setAndGetTimestamps() {
        User user = new User();
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now.plusSeconds(60));

        assertThat(user.getCreatedAt()).isEqualTo(now);
        assertThat(user.getUpdatedAt()).isAfter(user.getCreatedAt());
    }

    @Test
    void defaultFieldsAreNull() {
        User user = new User();

        assertThat(user.getUserId()).isNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getUserName()).isNull();
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void equals_sameFieldValues_areEqual() {
        User user1 = new User();
        user1.setUserId("user-1");
        user1.setEmail("a@b.com");

        User user2 = new User();
        user2.setUserId("user-1");
        user2.setEmail("a@b.com");

        assertThat(user1).isEqualTo(user2);
        assertThat(user1.hashCode()).isEqualTo(user2.hashCode());
    }

    @Test
    void equals_differentFieldValues_areNotEqual() {
        User user1 = new User();
        user1.setUserId("user-1");

        User user2 = new User();
        user2.setUserId("user-2");

        assertThat(user1).isNotEqualTo(user2);
    }

    @Test
    void toString_containsFieldValues() {
        User user = new User();
        user.setUserId("user-xyz");
        user.setEmail("test@example.com");
        user.setUserName("Test");

        String result = user.toString();

        assertThat(result).contains("user-xyz");
        assertThat(result).contains("test@example.com");
        assertThat(result).contains("Test");
    }
}
