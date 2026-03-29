package com.techcobber.smarttrader.v1.services;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ClientService using plain JUnit 5 (no Spring context).
 * Reflection is used to access and manipulate the private 'creds' field.
 */
class ClientServiceTest {

    @Test
    void getClient_beforeInit_returnsNull() {
        // A freshly constructed ClientService should have a null client
        ClientService service = new ClientService();
        assertThat(service.getClient()).isNull();
    }

    @Test
    void setCredentials_setsCredentialValue() throws Exception {
        ClientService service = new ClientService();
        String testCreds = "test-credential-value";

        service.setCredentials(testCreds);

        // Use reflection to verify the private 'creds' field was set
        Field credsField = ClientService.class.getDeclaredField("creds");
        credsField.setAccessible(true);
        String actualCreds = (String) credsField.get(service);

        assertThat(actualCreds).isEqualTo(testCreds);
    }

    @Test
    void init_withNullCredentials_throwsRuntimeException() throws Exception {
        ClientService service = new ClientService();

        // Ensure creds is null via reflection
        Field credsField = ClientService.class.getDeclaredField("creds");
        credsField.setAccessible(true);
        credsField.set(service, null);

        assertThatThrownBy(service::init)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void init_withInvalidCredentials_throwsRuntimeException() {
        // CoinbaseAdvancedCredentials should fail to parse invalid input
        ClientService service = new ClientService();
        service.setCredentials("this-is-not-valid-json-credentials");

        assertThatThrownBy(service::init)
                .isInstanceOf(RuntimeException.class);
    }
}
