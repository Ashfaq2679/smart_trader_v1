package com.techcobber.smarttrader.v1.services;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ClientService} using Mockito.
 * ClientService now delegates to CoinbaseClientFactory for per-user client management.
 */
@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private CoinbaseClientFactory coinbaseClientFactory;

    @InjectMocks
    private ClientService clientService;

    @Test
    void getClientForUser_delegatesToFactory() {
        CoinbaseAdvancedClient mockClient = mock(CoinbaseAdvancedClient.class);
        when(coinbaseClientFactory.getClientForUser("user-1")).thenReturn(mockClient);

        CoinbaseAdvancedClient result = clientService.getClientForUser("user-1");

        assertThat(result).isSameAs(mockClient);
        verify(coinbaseClientFactory).getClientForUser("user-1");
    }

    @Test
    void registerCredentials_delegatesToFactory() {
        clientService.registerCredentials("user-1", "raw-creds");

        verify(coinbaseClientFactory).registerCredentials("user-1", "raw-creds");
    }

    @Test
    void removeCredentials_delegatesToFactory() {
        clientService.removeCredentials("user-1");

        verify(coinbaseClientFactory).removeCredentials("user-1");
    }

    @Test
    void hasCredentials_delegatesToFactory() {
        when(coinbaseClientFactory.hasCredentials("user-1")).thenReturn(true);

        assertThat(clientService.hasCredentials("user-1")).isTrue();
        verify(coinbaseClientFactory).hasCredentials("user-1");
    }

    @Test
    void hasCredentials_returnsFalseWhenNoCredentials() {
        when(coinbaseClientFactory.hasCredentials("unknown")).thenReturn(false);

        assertThat(clientService.hasCredentials("unknown")).isFalse();
    }

    @Test
    void differentUsers_getDifferentClients() {
        CoinbaseAdvancedClient client1 = mock(CoinbaseAdvancedClient.class);
        CoinbaseAdvancedClient client2 = mock(CoinbaseAdvancedClient.class);
        when(coinbaseClientFactory.getClientForUser("user-1")).thenReturn(client1);
        when(coinbaseClientFactory.getClientForUser("user-2")).thenReturn(client2);

        CoinbaseAdvancedClient result1 = clientService.getClientForUser("user-1");
        CoinbaseAdvancedClient result2 = clientService.getClientForUser("user-2");

        assertThat(result1).isNotSameAs(result2);
    }
}
