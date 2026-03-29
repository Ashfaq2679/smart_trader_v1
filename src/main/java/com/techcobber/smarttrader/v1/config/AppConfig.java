package com.techcobber.smarttrader.v1.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.techcobber.smarttrader.v1.services.ClientService;
import com.techcobber.smarttrader.v1.services.CoinbasePublicServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableCaching
@Slf4j
@RequiredArgsConstructor
public class AppConfig {
	private final ClientService clientService;
	@Bean
	CoinbaseAdvancedClient coinbaseAdvancedClient() {
		return clientService.getClient();
	}

	@Bean
	CoinbasePublicServiceImpl coinbasePublicServiceImpl() {
		return new CoinbasePublicServiceImpl(clientService.getClient());
	}

	@Bean
	Caffeine<Object, Object> caffeineConfig() {
		return Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1000);
	}

	@Bean
	CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
		CaffeineCacheManager manager = new CaffeineCacheManager();
		manager.setCaffeine(caffeine);
		return manager;
	}
}
