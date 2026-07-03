package com.techcobber.smarttrader.v1.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.techcobber.smarttrader.v1.repositories.CoinsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoinsService {
	private final CoinsRepository coinsRepository;
	@Value("${candles.ignore.names:BTC-USDC,ETH-USDC}")
	private List<String> ignoreProductIds;

	@Cacheable(cacheNames = "productIdsToProcess", key = "'productIds'")
	public List<String> findProductIdToProcess() {
		log.info("Finding product IDs to process, ignoring: {}", ignoreProductIds);
		List<String> productIds = coinsRepository.findAll().stream()
				.filter(coin -> coin.productId() != null
						&& (ignoreProductIds == null || !ignoreProductIds.contains(coin.productId())))
				.map(coin -> coin.productId()).toList();
		return productIds;
	}
}
