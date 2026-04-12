package com.techcobber.smarttrader.v1.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CredentialEncryptionService}.
 */
class CredentialEncryptionServiceTest {

    /** A valid Base64-encoded 256-bit key for testing. */
    private static final String TEST_KEY_BASE64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Nested
    @DisplayName("encrypt / decrypt round-trip")
    class RoundTrip {

        @Test
        @DisplayName("decrypting an encrypted value returns the original")
        void encryptThenDecrypt_returnsOriginal() {
            CredentialEncryptionService service = new CredentialEncryptionService(TEST_KEY_BASE64);

            String original = "{\"apiKey\":\"abc\",\"apiSecret\":\"xyz\"}";
            String encrypted = service.encrypt(original);
            String decrypted = service.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        @DisplayName("encrypting the same value twice produces different ciphertexts (unique IV)")
        void encrypt_producesDifferentCiphertextsForSameInput() {
            CredentialEncryptionService service = new CredentialEncryptionService(TEST_KEY_BASE64);

            String original = "my-secret";
            String encrypted1 = service.encrypt(original);
            String encrypted2 = service.encrypt(original);

            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }

        @Test
        @DisplayName("handles empty string credentials")
        void encryptThenDecrypt_emptyString() {
            CredentialEncryptionService service = new CredentialEncryptionService(TEST_KEY_BASE64);

            String encrypted = service.encrypt("");
            String decrypted = service.decrypt(encrypted);

            assertThat(decrypted).isEmpty();
        }

        @Test
        @DisplayName("handles long credential strings")
        void encryptThenDecrypt_longString() {
            CredentialEncryptionService service = new CredentialEncryptionService(TEST_KEY_BASE64);

            String original = "x".repeat(10_000);
            String encrypted = service.encrypt(original);
            String decrypted = service.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("missing key — fail-fast")
    class MissingKey {

        @Test
        @DisplayName("constructor throws when key is null")
        void constructor_throwsWhenKeyIsNull() {
            assertThatThrownBy(() -> new CredentialEncryptionService(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CREDENTIAL_ENCRYPTION_KEY");
        }

        @Test
        @DisplayName("constructor throws when key is blank")
        void constructor_throwsWhenKeyIsBlank() {
            assertThatThrownBy(() -> new CredentialEncryptionService("  "))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CREDENTIAL_ENCRYPTION_KEY");
        }

        @Test
        @DisplayName("constructor throws when key is empty string")
        void constructor_throwsWhenKeyIsEmpty() {
            assertThatThrownBy(() -> new CredentialEncryptionService(""))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CREDENTIAL_ENCRYPTION_KEY");
        }
    }

    @Nested
    @DisplayName("tampered ciphertext")
    class TamperedCiphertext {

        @Test
        @DisplayName("decrypt throws on invalid Base64 input")
        void decrypt_throwsOnInvalidBase64() {
            CredentialEncryptionService service = new CredentialEncryptionService(TEST_KEY_BASE64);

            assertThatThrownBy(() -> service.decrypt("not-valid-base64!!!"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("decrypt throws on tampered ciphertext")
        void decrypt_throwsOnTamperedData() {
            CredentialEncryptionService service = new CredentialEncryptionService(TEST_KEY_BASE64);

            String encrypted = service.encrypt("secret");
            // Flip a byte in the ciphertext
            byte[] raw = java.util.Base64.getDecoder().decode(encrypted);
            raw[raw.length - 1] ^= 0xFF;
            String tampered = java.util.Base64.getEncoder().encodeToString(raw);

            assertThatThrownBy(() -> service.decrypt(tampered))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
