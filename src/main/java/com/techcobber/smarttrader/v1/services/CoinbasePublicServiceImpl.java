package com.techcobber.smarttrader.v1.services;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.model.common.Granularity;
import com.coinbase.advanced.model.products.GetProductResponse;
import com.coinbase.advanced.model.products.ListProductsResponse;
import com.coinbase.advanced.model.products.Product;
import com.coinbase.advanced.model.publics.ListPublicProductsRequest;
import com.coinbase.advanced.publics.PublicServiceImpl;
import com.coinbase.core.common.HttpMethod;
import com.fasterxml.jackson.core.type.TypeReference;
import com.techcobber.smarttrader.v1.models.ListCandles;

import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for Coinbase public market data.
 *
 * <p><b>Design Pattern: Template Method</b> — This class extends {@link PublicServiceImpl}
 * from the Coinbase SDK, inheriting the HTTP request infrastructure (the "template")
 * while overriding/extending behaviour with product-specific query methods.
 * The base class provides the {@code request()} template; this subclass defines
 * the concrete steps (URL construction, response filtering, sorting).</p>
 */
@Component
@Lazy
@Slf4j
public class CoinbasePublicServiceImpl extends PublicServiceImpl {

	public CoinbasePublicServiceImpl(CoinbaseAdvancedClient client) {
		super(client);
	}

	private final String baseUrl = "/brokerage/market/products";

	/**
	 * Fetches public product information for a specific product ID. The method
	 * constructs the API endpoint URL using the provided product ID and makes a GET
	 * request to retrieve the product information. The response is deserialized
	 * into a GetProductResponse object, which contains details about the specified
	 * product.
	 *
	 * @param productId The ID of the product for which to fetch information.
	 * @return A GetProductResponse object containing details about the specified
	 * product.
	 * @throws CoinbaseAdvancedException If there is an error fetching product
	 * information from the API.
	 */
	public GetProductResponse fetchPublicProduct(String productId) throws CoinbaseAdvancedException {
		return this.request(HttpMethod.GET, String.format(baseUrl + "/%s", productId), null, List.of(200),
				new TypeReference<GetProductResponse>() {
				});
	}

	/**
	 * Fetches candle data for a specific product within a given time range and
	 * granularity. The method constructs the API endpoint URL using the provided
	 * parameters and makes a GET request to retrieve the candle data. The response
	 * is deserialized into a ListCandles object, which contains a list of MyCandle
	 * instances representing the candle data.
	 *
	 * @param productId The ID of the product for which to fetch candle data.
	 * @param startTime The start time of the time range for which to fetch candles,
	 * specified as a Unix timestamp in milliseconds.
	 * @param endTime   The end time of the time range for which to fetch candles,
	 * specified as a Unix timestamp in milliseconds.
	 * @param gran      The granularity of the candles, specified as an instance of
	 * Granularity (e.g., Granularity.ONE_MINUTE, Granularity.FIVE_MINUTES).
	 * @return A ListCandles object containing the candle data for the specified
	 * product and time range.
	 * @throws CoinbaseAdvancedException If there is an error fetching candle data
	 * from the API.
	 */
	public ListCandles fetchCandles(String productId, long startTime, long endTime, Granularity gran)
			throws CoinbaseAdvancedException {
		return this.request(HttpMethod.GET, String.format(baseUrl + "/%s/candles?start=%s&end=%s&granularity=%s",
				productId, startTime, endTime, gran), null, List.of(200), new TypeReference<ListCandles>() {
				});
	}

	public Double getPrice(String productId) throws CoinbaseAdvancedException {
		Double price = 0.0;

		try {
			GetProductResponse response = fetchPublicProduct(productId);
			if (response != null) {
				price = Double.parseDouble(response.getPrice());
			}
		} catch (CoinbaseAdvancedException e) {

		} catch (Exception e) {
			price = Double.parseDouble("0.0");
		}
		return price;

	}

	/**
	 * Fetches products from CoinBase API and filters them based on the provided
	 * filter string like USDC pairs only. The filter is applied to the product ID, and the search is
	 * case-insensitive. If no products match the filter, an empty list is returned.
	 *
	 * @param filter The string to filter product IDs by.
	 * @return A list of products that match the filter criteria.
	 * @throws CoinbaseAdvancedException If there is an error fetching products from
	 *the API.
	 */
	public List<Product> getFilteredProducts(String filter) throws CoinbaseAdvancedException {
		log.info("Fetching products with filter: {}", filter);
		ListProductsResponse response = this.listPublicProducts();
		if (response != null) {
			return response.getProducts().stream()
					.filter(product -> product.getProductId().toUpperCase().contains(filter.toUpperCase())).toList();
		}
		log.info("No products found with filter: {}", filter);
		return List.of();
	}

	public List<Product> getTopVolumeProductsInLast24Hrs(int limit) throws CoinbaseAdvancedException {
		ListProductsResponse response = this.listPublicProducts();
		if (response != null) {
			return response.getProducts().stream()
					.filter(product -> product.getVolume24h() != null && !product.getVolume24h().isEmpty())
					.sorted((p1, p2) -> Double.compare(Double.parseDouble(p2.getVolume24h()),
							Double.parseDouble(p1.getVolume24h())))
					.limit(limit).toList();
		}
		return List.of();
	}
	
	/**
	 * Fetches products from CoinBase API and filters them based on the provided
	 * filter string like USDC pairs only. The filter is applied to the product ID, and the search is
	 * case-insensitive. The filtered products are then sorted by their volume change
	 * percentage in the last 24 hours in descending order, and the top products are
	 * returned based on the specified limit. If no products match the filter or if
	 * there are no products with valid volume change data, an empty list is returned.
	 *
	 * @param limit  The maximum number of products to return.
	 * @param filter The string to filter product IDs by.
	 * @return A list of products that match the filter criteria and are sorted by
	 * volume change percentage in the last 24 hours.
	 * @throws CoinbaseAdvancedException If there is an error fetching products from
	 * the API.
	 */
	public List<Product> getTopVolumeChangeProductsInLast24Hrs(int limit, String filter) throws CoinbaseAdvancedException {
		ListProductsResponse response = this.listPublicProducts();
		if (response != null) {
			return response.getProducts().stream()
					.filter(product -> product.getProductId().toUpperCase().contains(filter.toUpperCase())
							&& product.getVolumePercentageChange24h() != null
							&& !product.getVolumePercentageChange24h().isEmpty())
					.sorted((p1, p2) -> Double.compare(Double.parseDouble(p2.getVolumePercentageChange24h()),
							Double.parseDouble(p1.getVolumePercentageChange24h())))
					.limit(limit).toList();
		}
		return List.of();
	}

	/**
	 * Fetches products from CoinBase API and filters them based on the provided
	 * filter string like USDC pairs only. The filter is applied to the product ID, and the search is
	 * case-insensitive. The filtered products are then sorted by their price change
	 * percentage in the last 24 hours in ascending order, and the top products are
	 * returned based on the specified limit. If no products match the filter or if
	 * there are no products with valid price change data, an empty list is returned.
	 *
	 * @param limit  The maximum number of products to return.
	 * @param filter The string to filter product IDs by.
	 * @return A list of products that match the filter criteria and are sorted by
	 * price change percentage in the last 24 hours.
	 * @throws CoinbaseAdvancedException If there is an error fetching products from
	 * the API.
	 */
	public List<Product> getBottomVolumeChangeProductsInLast24Hrs(int limit, String filter) throws CoinbaseAdvancedException {
		ListProductsResponse response = this.listPublicProducts();
		if (response != null) {
			return response.getProducts().stream()
					.filter(product -> product.getProductId().toUpperCase().contains(filter.toUpperCase())
							&& product.getVolumePercentageChange24h() != null
							&& !product.getVolumePercentageChange24h().isEmpty())
					.sorted((p1, p2) -> Double.compare(Double.parseDouble(p1.getVolumePercentageChange24h()),
							Double.parseDouble(p2.getVolumePercentageChange24h())))
					.limit(limit).toList();
		}
		return List.of();
	}

	public List<Product> getTopPriceChangeProductsInLast24Hrs(int limit, String filter) throws CoinbaseAdvancedException {
		ListProductsResponse response = this.listPublicProducts();
		if (response != null) {
			return response.getProducts().stream()
					.filter(product -> product.getProductId().toUpperCase().contains(filter.toUpperCase())
							&& product.getPricePercentageChange24h() != null
							&& !product.getPricePercentageChange24h().isEmpty())
					.sorted((p1, p2) -> Double.compare(Double.parseDouble(p2.getPricePercentageChange24h()),
							Double.parseDouble(p1.getPricePercentageChange24h())))
					.limit(limit).toList();
		}
		return List.of();
	}

	/**
	 * Fetches products from CoinBase API, filters them based on the provided filter
	 * string, and sorts them by price percentage change in the last 24 hours in
	 * ascending order (Worst performers in last 24 hours). The filter is applied to the product ID, and the search is
	 * case-insensitive. If no products match the filter or if there are no valid
	 * price percentage change values, an empty list is returned.
	 *
	 * @param limit              The maximum number of products to return.
	 * @param filter             The string to filter product IDs by.
	 * @param listPublicProducts A pre-fetched ListProductsResponse to avoid redundant API calls.
	 * @return A list of products that match the filter criteria and are sorted by
	 * price percentage change in ascending order.
	 * @throws CoinbaseAdvancedException If there is an error fetching products from
	 * the API.
	 */
	public List<Product> getBottomPriceChangeProductsInLast24Hrs(int limit, String filter) throws CoinbaseAdvancedException {
		ListProductsResponse response = this.listPublicProducts();
		if (response != null) {
			return response.getProducts().stream()
					.filter(product -> product.getProductId().toUpperCase().contains(filter.toUpperCase())
							&& product.getPricePercentageChange24h() != null
							&& !product.getPricePercentageChange24h().isEmpty())
					.sorted((p1, p2) -> Double.compare(Double.parseDouble(p1.getPricePercentageChange24h()),
							Double.parseDouble(p2.getPricePercentageChange24h())))
					.limit(limit).toList();
		}
		return List.of();
	}

	@Cacheable("publicProducts")
	public ListProductsResponse listPublicProducts() throws CoinbaseAdvancedException {
		log.info("Fetching public products from Coinbase API...");
		return this.request(HttpMethod.GET, baseUrl, new ListPublicProductsRequest(), List.of(200),
				new TypeReference<ListProductsResponse>() {
				});
	}
}
