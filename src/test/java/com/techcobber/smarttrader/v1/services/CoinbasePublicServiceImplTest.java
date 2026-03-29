package com.techcobber.smarttrader.v1.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.model.products.GetProductResponse;
import com.coinbase.advanced.model.products.ListProductsResponse;
import com.coinbase.advanced.model.products.Product;

/**
 * Unit tests for {@link CoinbasePublicServiceImpl}.
 *
 * <p><b>Testing strategy:</b> Since CoinbasePublicServiceImpl extends
 * {@code PublicServiceImpl} from the Coinbase SDK (which requires a real HTTP client),
 * we use {@link Mockito#spy(Object)} on the service instance and stub the internal
 * API-calling methods ({@code listPublicProducts} and {@code fetchPublicProduct})
 * with {@code doReturn().when(spy)...} to avoid real network calls.</p>
 *
 * <p>All tests follow the <b>AAA pattern</b> (Arrange / Act / Assert).</p>
 */
@ExtendWith(MockitoExtension.class)
class CoinbasePublicServiceImplTest {

    @Mock
    private CoinbaseAdvancedClient mockClient;

    private CoinbasePublicServiceImpl spy;

    @BeforeEach
    void setUp() {
        // Create a real instance with the mocked client, then wrap it in a spy
        // so we can stub listPublicProducts() and fetchPublicProduct() without
        // hitting the actual Coinbase API.
        spy = Mockito.spy(new CoinbasePublicServiceImpl(mockClient));
    }

    // ------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------

    /**
     * Creates a test {@link Product} with the most commonly used fields.
     *
     * @param productId               e.g. "BTC-USDC"
     * @param volume24h               e.g. "1000000"
     * @param volumePercentageChange   e.g. "15.5"
     * @param pricePercentageChange    e.g. "5.2"
     * @param price                   e.g. "50000.00"
     * @return a fully populated Product instance
     */
    private Product createProduct(String productId, String volume24h,
                                  String volumePercentageChange, String pricePercentageChange,
                                  String price) {
        Product product = new Product();
        product.setProductId(productId);
        product.setVolume24h(volume24h);
        product.setVolumePercentageChange24h(volumePercentageChange);
        product.setPricePercentageChange24h(pricePercentageChange);
        product.setPrice(price);
        return product;
    }

    /**
     * Builds a {@link ListProductsResponse} containing the given products.
     */
    private ListProductsResponse createListProductsResponse(Product... products) {
        ListProductsResponse response = new ListProductsResponse();
        response.setProducts(new ArrayList<>(List.of(products)));
        return response;
    }

    // ==================================================================
    // getFilteredProducts
    // ==================================================================

    @Nested
    @DisplayName("getFilteredProducts")
    class GetFilteredProductsTests {

        @Test
        @DisplayName("returns products whose IDs contain the filter string")
        void matchingFilter_returnsFilteredProducts() throws CoinbaseAdvancedException {
            // Arrange
            Product btcUsdc = createProduct("BTC-USDC", "1000000", "10", "5", "50000");
            Product ethUsdc = createProduct("ETH-USDC", "500000", "8", "3", "3000");
            Product btcEur = createProduct("BTC-EUR", "200000", "6", "2", "49000");
            doReturn(createListProductsResponse(btcUsdc, ethUsdc, btcEur))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getFilteredProducts("USDC");

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Product::getProductId)
                    .containsExactly("BTC-USDC", "ETH-USDC");
        }

        @Test
        @DisplayName("returns empty list when no products match the filter")
        void noMatch_returnsEmptyList() throws CoinbaseAdvancedException {
            // Arrange
            Product btcUsdc = createProduct("BTC-USDC", "1000000", "10", "5", "50000");
            doReturn(createListProductsResponse(btcUsdc))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getFilteredProducts("XRP");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when response is null")
        void nullResponse_returnsEmptyList() throws CoinbaseAdvancedException {
            // Arrange
            doReturn(null).when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getFilteredProducts("USDC");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("filter is case-insensitive")
        void caseInsensitiveFilter() throws CoinbaseAdvancedException {
            // Arrange
            Product btcUsdc = createProduct("BTC-USDC", "1000000", "10", "5", "50000");
            Product ethUsdc = createProduct("ETH-USDC", "500000", "8", "3", "3000");
            doReturn(createListProductsResponse(btcUsdc, ethUsdc))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getFilteredProducts("usdc");

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Product::getProductId)
                    .containsExactly("BTC-USDC", "ETH-USDC");
        }
    }

    // ==================================================================
    // getTopVolumeProductsInLast24Hrs
    // ==================================================================

    @Nested
    @DisplayName("getTopVolumeProductsInLast24Hrs")
    class GetTopVolumeProductsTests {

        @Test
        @DisplayName("returns products sorted by volume24h descending")
        void sortedByVolumeDescending() throws CoinbaseAdvancedException {
            // Arrange
            Product low = createProduct("LOW-USDC", "100", "1", "1", "1");
            Product mid = createProduct("MID-USDC", "5000", "5", "5", "50");
            Product high = createProduct("HIGH-USDC", "1000000", "10", "10", "500");
            doReturn(createListProductsResponse(low, mid, high))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopVolumeProductsInLast24Hrs(3);

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result).extracting(Product::getProductId)
                    .containsExactly("HIGH-USDC", "MID-USDC", "LOW-USDC");
        }

        @Test
        @DisplayName("skips products with null volume24h")
        void skipsNullVolume() throws CoinbaseAdvancedException {
            // Arrange
            Product valid = createProduct("BTC-USDC", "1000000", "10", "5", "50000");
            Product nullVol = createProduct("NULL-USDC", null, "5", "3", "100");
            doReturn(createListProductsResponse(valid, nullVol))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopVolumeProductsInLast24Hrs(10);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo("BTC-USDC");
        }

        @Test
        @DisplayName("skips products with empty volume24h")
        void skipsEmptyVolume() throws CoinbaseAdvancedException {
            // Arrange
            Product valid = createProduct("BTC-USDC", "1000000", "10", "5", "50000");
            Product emptyVol = createProduct("EMPTY-USDC", "", "5", "3", "100");
            doReturn(createListProductsResponse(valid, emptyVol))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopVolumeProductsInLast24Hrs(10);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo("BTC-USDC");
        }

        @Test
        @DisplayName("respects the limit parameter")
        void respectsLimit() throws CoinbaseAdvancedException {
            // Arrange
            Product p1 = createProduct("P1-USDC", "3000", "1", "1", "1");
            Product p2 = createProduct("P2-USDC", "2000", "1", "1", "1");
            Product p3 = createProduct("P3-USDC", "1000", "1", "1", "1");
            doReturn(createListProductsResponse(p1, p2, p3))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopVolumeProductsInLast24Hrs(2);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Product::getProductId)
                    .containsExactly("P1-USDC", "P2-USDC");
        }

        @Test
        @DisplayName("returns empty list when response is null")
        void nullResponse_returnsEmptyList() throws CoinbaseAdvancedException {
            // Arrange
            doReturn(null).when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopVolumeProductsInLast24Hrs(5);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ==================================================================
    // getTopVolumeChangeProductsInLast24Hrs
    // ==================================================================

    @Nested
    @DisplayName("getTopVolumeChangeProductsInLast24Hrs")
    class GetTopVolumeChangeProductsTests {

        @Test
        @DisplayName("returns USDC products sorted by volumePercentageChange24h descending")
        void sortedByVolumeChangeDescending() throws CoinbaseAdvancedException {
            // Arrange
            Product high = createProduct("BTC-USDC", "1000000", "50.5", "5", "50000");
            Product mid = createProduct("ETH-USDC", "500000", "25.0", "3", "3000");
            Product low = createProduct("SOL-USDC", "200000", "10.0", "2", "100");
            Product nonUsdc = createProduct("BTC-EUR", "300000", "80.0", "8", "49000");
            doReturn(createListProductsResponse(low, high, nonUsdc, mid))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopVolumeChangeProductsInLast24Hrs(3, "USDC");

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result).extracting(Product::getProductId)
                    .containsExactly("BTC-USDC", "ETH-USDC", "SOL-USDC");
        }

        @Test
        @DisplayName("excludes products with null volumePercentageChange24h")
        void excludesNullVolumeChange() throws CoinbaseAdvancedException {
            // Arrange
            Product valid = createProduct("BTC-USDC", "1000000", "50.0", "5", "50000");
            Product nullChange = createProduct("ETH-USDC", "500000", null, "3", "3000");
            doReturn(createListProductsResponse(valid, nullChange))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopVolumeChangeProductsInLast24Hrs(10, "USDC");

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo("BTC-USDC");
        }

        @Test
        @DisplayName("returns empty list when response is null")
        void nullResponse_returnsEmptyList() throws CoinbaseAdvancedException {
            // Arrange
            doReturn(null).when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopVolumeChangeProductsInLast24Hrs(5, "USDC");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ==================================================================
    // getBottomVolumeChangeProductsInLast24Hrs
    // ==================================================================

    @Nested
    @DisplayName("getBottomVolumeChangeProductsInLast24Hrs")
    class GetBottomVolumeChangeProductsTests {

        @Test
        @DisplayName("returns USDC products sorted by volumePercentageChange24h ascending")
        void sortedByVolumeChangeAscending() throws CoinbaseAdvancedException {
            // Arrange
            Product low = createProduct("SOL-USDC", "200000", "-5.0", "2", "100");
            Product mid = createProduct("ETH-USDC", "500000", "10.0", "3", "3000");
            Product high = createProduct("BTC-USDC", "1000000", "50.0", "5", "50000");
            Product nonUsdc = createProduct("BTC-EUR", "300000", "-20.0", "8", "49000");
            doReturn(createListProductsResponse(high, low, nonUsdc, mid))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getBottomVolumeChangeProductsInLast24Hrs(3, "USDC");

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result).extracting(Product::getProductId)
                    .containsExactly("SOL-USDC", "ETH-USDC", "BTC-USDC");
        }

        @Test
        @DisplayName("excludes products with empty volumePercentageChange24h")
        void excludesEmptyVolumeChange() throws CoinbaseAdvancedException {
            // Arrange
            Product valid = createProduct("BTC-USDC", "1000000", "50.0", "5", "50000");
            Product emptyChange = createProduct("ETH-USDC", "500000", "", "3", "3000");
            doReturn(createListProductsResponse(valid, emptyChange))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getBottomVolumeChangeProductsInLast24Hrs(10, "USDC");

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo("BTC-USDC");
        }

        @Test
        @DisplayName("returns empty list when response is null")
        void nullResponse_returnsEmptyList() throws CoinbaseAdvancedException {
            // Arrange
            doReturn(null).when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getBottomVolumeChangeProductsInLast24Hrs(5, "USDC");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ==================================================================
    // getTopPriceChangeProductsInLast24Hrs
    // ==================================================================

    @Nested
    @DisplayName("getTopPriceChangeProductsInLast24Hrs")
    class GetTopPriceChangeProductsTests {

        @Test
        @DisplayName("returns products sorted by pricePercentageChange24h descending")
        void sortedByPriceChangeDescending() throws CoinbaseAdvancedException {
            // Arrange
            Product top = createProduct("BTC-USDC", "1000000", "10", "25.5", "50000");
            Product mid = createProduct("ETH-USDC", "500000", "8", "10.0", "3000");
            Product bottom = createProduct("SOL-USDC", "200000", "6", "-5.0", "100");
            doReturn(createListProductsResponse(bottom, top, mid))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopPriceChangeProductsInLast24Hrs(3, "USDC");

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result).extracting(Product::getProductId)
                    .containsExactly("BTC-USDC", "ETH-USDC", "SOL-USDC");
        }

        @Test
        @DisplayName("excludes products with null pricePercentageChange24h")
        void excludesNullPriceChange() throws CoinbaseAdvancedException {
            // Arrange
            Product valid = createProduct("BTC-USDC", "1000000", "10", "25.5", "50000");
            Product nullChange = createProduct("ETH-USDC", "500000", "8", null, "3000");
            doReturn(createListProductsResponse(valid, nullChange))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopPriceChangeProductsInLast24Hrs(10, "USDC");

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo("BTC-USDC");
        }

        @Test
        @DisplayName("excludes products with empty pricePercentageChange24h")
        void excludesEmptyPriceChange() throws CoinbaseAdvancedException {
            // Arrange
            Product valid = createProduct("BTC-USDC", "1000000", "10", "25.5", "50000");
            Product emptyChange = createProduct("ETH-USDC", "500000", "8", "", "3000");
            doReturn(createListProductsResponse(valid, emptyChange))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopPriceChangeProductsInLast24Hrs(10, "USDC");

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo("BTC-USDC");
        }

        @Test
        @DisplayName("returns empty list when response is null")
        void nullResponse_returnsEmptyList() throws CoinbaseAdvancedException {
            // Arrange
            doReturn(null).when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getTopPriceChangeProductsInLast24Hrs(5, "USDC");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ==================================================================
    // getBottomPriceChangeProductsInLast24Hrs
    // ==================================================================

    @Nested
    @DisplayName("getBottomPriceChangeProductsInLast24Hrs")
    class GetBottomPriceChangeProductsTests {

        @Test
        @DisplayName("returns products sorted by pricePercentageChange24h ascending")
        void sortedByPriceChangeAscending() throws CoinbaseAdvancedException {
            // Arrange
            Product worst = createProduct("SOL-USDC", "200000", "6", "-15.0", "100");
            Product mid = createProduct("ETH-USDC", "500000", "8", "2.0", "3000");
            Product best = createProduct("BTC-USDC", "1000000", "10", "25.5", "50000");
            doReturn(createListProductsResponse(best, worst, mid))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getBottomPriceChangeProductsInLast24Hrs(3, "USDC");

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result).extracting(Product::getProductId)
                    .containsExactly("SOL-USDC", "ETH-USDC", "BTC-USDC");
        }

        @Test
        @DisplayName("excludes products with null pricePercentageChange24h")
        void excludesNullPriceChange() throws CoinbaseAdvancedException {
            // Arrange
            Product valid = createProduct("BTC-USDC", "1000000", "10", "5.0", "50000");
            Product nullChange = createProduct("ETH-USDC", "500000", "8", null, "3000");
            doReturn(createListProductsResponse(valid, nullChange))
                    .when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getBottomPriceChangeProductsInLast24Hrs(10, "USDC");

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo("BTC-USDC");
        }

        @Test
        @DisplayName("returns empty list when response is null")
        void nullResponse_returnsEmptyList() throws CoinbaseAdvancedException {
            // Arrange
            doReturn(null).when(spy).listPublicProducts();

            // Act
            List<Product> result = spy.getBottomPriceChangeProductsInLast24Hrs(5, "USDC");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ==================================================================
    // getPrice
    // ==================================================================

    @Nested
    @DisplayName("getPrice")
    class GetPriceTests {

        @Test
        @DisplayName("returns correct price for a valid product")
        void validProduct_returnsPrice() throws CoinbaseAdvancedException {
            // Arrange
            GetProductResponse mockResponse = new GetProductResponse.Builder()
                    .price("50000.00")
                    .build();
            doReturn(mockResponse).when(spy).fetchPublicProduct(anyString());

            // Act
            Double price = spy.getPrice("BTC-USDC");

            // Assert
            assertThat(price).isEqualTo(50000.00);
        }

        @Test
        @DisplayName("returns 0.0 when CoinbaseAdvancedException is thrown")
        void coinbaseException_returnsZero() throws CoinbaseAdvancedException {
            // Arrange
            doThrow(new CoinbaseAdvancedException(500, "Server Error"))
                    .when(spy).fetchPublicProduct(anyString());

            // Act
            Double price = spy.getPrice("INVALID-PRODUCT");

            // Assert
            assertThat(price).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0.0 when fetchPublicProduct returns null")
        void nullResponse_returnsZero() throws CoinbaseAdvancedException {
            // Arrange
            doReturn(null).when(spy).fetchPublicProduct(anyString());

            // Act
            Double price = spy.getPrice("BTC-USDC");

            // Assert
            assertThat(price).isEqualTo(0.0);
        }
    }
}
