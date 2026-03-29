package com.techcobber.smarttrader.v1.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.credentials.CoinbaseAdvancedCredentials;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the lifecycle of the Coinbase Advanced API client.
 *
 * <p><b>Design Pattern: Lazy Initialization</b> — The {@code @Lazy} annotation
 * ensures this bean is only created when first requested, deferring the expensive
 * credential parsing and client construction until actually needed.
 * Combined with {@code @PostConstruct}, the client is initialised exactly once
 * after dependency injection completes.</p>
 */
@Service
@Lazy
@Slf4j
public class ClientService {

	@Value("${ADVANCED_TRADE_CREDENTIALS}")
	private String creds;

	private CoinbaseAdvancedClient client;

	public CoinbaseAdvancedClient getClient() {
		return client;
	}

	public void setCredentials(String creds) {
		this.creds = creds;
	}

	public ClientService() {
	}

	@PostConstruct
	public void init() {

		try {
			String credsStringBlob = creds;
			if (credsStringBlob == null) {
				throw new RuntimeException("Invalid ADVANCED TRADE CREDENTIALS.");
			}

			CoinbaseAdvancedCredentials credentials = new CoinbaseAdvancedCredentials(credsStringBlob);
			this.client = new CoinbaseAdvancedClient(credentials);
			log.info("Advanced client created successfully. [{}]", client);
		} catch (Throwable t) {
			throw new RuntimeException(t.getMessage());
		}
	}

}
