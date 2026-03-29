package com.techcobber.smarttrader.v1.services;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.techcobber.smarttrader.v1.models.UserCredentials;
import com.techcobber.smarttrader.v1.repositories.UserCredentialsRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CoinbaseClientFactory}.
 */
@ExtendWith(MockitoExtension.class)
class CoinbaseClientFactoryTest {

    @Mock
    private UserCredentialsRepository credentialsRepository;

    @Mock
    private CredentialEncryptionService encryptionService;

    private CoinbaseClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CoinbaseClientFactory(credentialsRepository, encryptionService);
    }

    // -----------------------------------------------------------------------
    // registerCredentials
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("registerCredentials")
    class RegisterCredentials {

        @Test
        @DisplayName("saves encrypted credentials for a new user")
        void savesNewUserCredentials() {
            when(encryptionService.encrypt("raw-creds")).thenReturn("encrypted-creds");
            when(credentialsRepository.findByUserId("user-1")).thenReturn(Optional.empty());
            when(credentialsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            factory.registerCredentials("user-1", "raw-creds");

            ArgumentCaptor<UserCredentials> captor = ArgumentCaptor.forClass(UserCredentials.class);
            verify(credentialsRepository).save(captor.capture());

            UserCredentials saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo("user-1");
            assertThat(saved.getEncryptedCredentials()).isEqualTo("encrypted-creds");
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("updates existing credentials for a user")
        void updatesExistingCredentials() {
            UserCredentials existing = new UserCredentials();
            existing.setUserId("user-1");
            existing.setEncryptedCredentials("old-encrypted");
            existing.setCreatedAt(Instant.now().minusSeconds(3600));

            when(encryptionService.encrypt("new-raw")).thenReturn("new-encrypted");
            when(credentialsRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));
            when(credentialsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            factory.registerCredentials("user-1", "new-raw");

            ArgumentCaptor<UserCredentials> captor = ArgumentCaptor.forClass(UserCredentials.class);
            verify(credentialsRepository).save(captor.capture());

            UserCredentials saved = captor.getValue();
            assertThat(saved.getEncryptedCredentials()).isEqualTo("new-encrypted");
            assertThat(saved.getUpdatedAt()).isAfter(saved.getCreatedAt());
        }

        @Test
        @DisplayName("throws on null userId")
        void throwsOnNullUserId() {
            assertThatThrownBy(() -> factory.registerCredentials(null, "creds"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("userId");
        }

        @Test
        @DisplayName("throws on blank userId")
        void throwsOnBlankUserId() {
            assertThatThrownBy(() -> factory.registerCredentials("  ", "creds"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("userId");
        }

        @Test
        @DisplayName("throws on null rawCredentials")
        void throwsOnNullCreds() {
            assertThatThrownBy(() -> factory.registerCredentials("user-1", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rawCredentials");
        }

        @Test
        @DisplayName("throws on blank rawCredentials")
        void throwsOnBlankCreds() {
            assertThatThrownBy(() -> factory.registerCredentials("user-1", "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rawCredentials");
        }
    }

    // -----------------------------------------------------------------------
    // getClientForUser
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getClientForUser")
    class GetClientForUser {

        @Test
        @DisplayName("throws when no credentials exist for user")
        void throwsWhenNoCredentials() {
            when(credentialsRepository.findByUserId("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> factory.getClientForUser("unknown"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No credentials found");
        }

        @Test
        @DisplayName("throws when decrypted credentials are invalid")
        void throwsWhenCredentialsInvalid() {
            UserCredentials stored = new UserCredentials();
            stored.setUserId("user-1");
            stored.setEncryptedCredentials("enc");

            when(credentialsRepository.findByUserId("user-1")).thenReturn(Optional.of(stored));
            when(encryptionService.decrypt("enc")).thenReturn("not-valid-json");

            assertThatThrownBy(() -> factory.getClientForUser("user-1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create Coinbase client");
        }
    }

    // -----------------------------------------------------------------------
    // removeCredentials
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("removeCredentials")
    class RemoveCredentials {

        @Test
        @DisplayName("deletes credentials from repository")
        void deletesFromRepo() {
            factory.removeCredentials("user-1");

            verify(credentialsRepository).deleteByUserId("user-1");
        }
    }

    // -----------------------------------------------------------------------
    // hasCredentials
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("hasCredentials")
    class HasCredentials {

        @Test
        @DisplayName("returns true when credentials exist")
        void returnsTrue() {
            when(credentialsRepository.existsByUserId("user-1")).thenReturn(true);

            assertThat(factory.hasCredentials("user-1")).isTrue();
        }

        @Test
        @DisplayName("returns false when no credentials")
        void returnsFalse() {
            when(credentialsRepository.existsByUserId("user-1")).thenReturn(false);

            assertThat(factory.hasCredentials("user-1")).isFalse();
        }
    }
}
