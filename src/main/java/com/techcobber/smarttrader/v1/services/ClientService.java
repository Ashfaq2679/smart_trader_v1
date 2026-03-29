package com.techcobber.smarttrader.v1.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.credentials.CoinbaseAdvancedCredentials;

import jakarta.annotation.PostConstruct;

@Service
@Lazy
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
		// This constructor is not used, but it is here to demonstrate that it cannot be
		// instantiated without credentials.
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
			System.out.println("Advanced client created successfully. [" + client + "]");
		} catch (Throwable t) {
			throw new RuntimeException(t.getMessage());
		}
	}

}
