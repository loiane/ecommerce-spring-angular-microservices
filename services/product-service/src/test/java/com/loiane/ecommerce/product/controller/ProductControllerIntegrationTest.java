package com.loiane.ecommerce.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loiane.ecommerce.product.dto.product.*;
import com.loiane.ecommerce.product.entity.Category;
import com.loiane.ecommerce.product.entity.Product;
import com.loiane.ecommerce.product.entity.ProductStatus;
import com.loiane.ecommerce.product.exception.*;
import com.loiane.ecommerce.product.factory.CategoryTestDataFactory;
import com.loiane.ecommerce.product.repository.CategoryRepository;
import com.loiane.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ProductController.
 * Tests the controller and service layers with mocked repositories.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Product Controller Integration Tests")
class ProductControllerIntegrationTest {

    // Test data constants
    private static final String PRODUCT_ID = "product-123";
    private static final String CATEGORY_ID = "category-456";
    private static final String GAMING_LAPTOP_SKU = "LAPTOP-GAMING-001";
    private static final String GAMING_LAPTOP_NAME = "Gaming Laptop Pro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private CategoryRepository categoryRepository;

    private Product testProduct;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = CategoryTestDataFactory.createRoot("Electronics");
        testCategory.setId(CATEGORY_ID);

        testProduct = Product.builder()
                .name(GAMING_LAPTOP_NAME)
                .sku(GAMING_LAPTOP_SKU)
                .basePrice(new BigDecimal("1299.99"))
                .status(ProductStatus.ACTIVE)
                .category(testCategory)
                .stockQuantity(25)
                .reservedQuantity(5)
                .lowStockThreshold(10)
                .trackInventory(true)
                .description("High-performance gaming laptop")
                .shortDescription("Gaming laptop with RTX graphics")
                .build();
        testProduct.setId(PRODUCT_ID);
        testProduct.setCreatedAt(OffsetDateTime.now());
        testProduct.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Find product by ID - Success")
    void findByIdSuccess() throws Exception {
        // Given
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(testProduct));

        // When & Then
        mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(PRODUCT_ID)))
                .andExpect(jsonPath("$.name", is(GAMING_LAPTOP_NAME)))
                .andExpect(jsonPath("$.sku", is(GAMING_LAPTOP_SKU)));

        verify(productRepository).findById(PRODUCT_ID);
    }

    @Test
    @DisplayName("Find product by ID - Not Found")
    void findByIdNotFound() throws Exception {
        // Given
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status", is(404)))
                                .andExpect(jsonPath("$.error", is("Not Found")))
                                .andExpect(jsonPath("$.message", containsString(PRODUCT_ID)))
                                .andExpect(jsonPath("$.path", is("/api/v1/products/" + PRODUCT_ID)))
                                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findById(PRODUCT_ID);
    }

    @Test
    @DisplayName("Create product - Success")
    void createProductSuccess() throws Exception {
        // Given
        CreateProductRequest request = new CreateProductRequest(
                "New Gaming Laptop",
                "NEW-LAPTOP-001",
                "Latest gaming laptop with RTX graphics",
                new BigDecimal("1599.99"),
                CATEGORY_ID,
                30,
                10,
                true
        );

        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsBySku("NEW-LAPTOP-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // When & Then
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(PRODUCT_ID)))
                .andExpect(jsonPath("$.name", is(GAMING_LAPTOP_NAME)));

        verify(categoryRepository, times(1)).findById(CATEGORY_ID); // Now only service layer performs lookup
        verify(productRepository).existsBySku("NEW-LAPTOP-001");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("Create product - Duplicate SKU")
    void createProductDuplicateSku() throws Exception {
        // Given
        CreateProductRequest request = new CreateProductRequest(
                "New Gaming Laptop",
                GAMING_LAPTOP_SKU, // Duplicate SKU
                "Latest gaming laptop with RTX graphics",
                new BigDecimal("1599.99"),
                CATEGORY_ID,
                30,
                10,
                true
        );

        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsBySku(GAMING_LAPTOP_SKU)).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("SKU")))
                .andExpect(jsonPath("$.path", is("/api/v1/products")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        // Service short-circuits on duplicate SKU before category lookup
        verify(categoryRepository, never()).findById(CATEGORY_ID);
        verify(productRepository).existsBySku(GAMING_LAPTOP_SKU);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Update product - Success")
    void updateProductSuccess() throws Exception {
        // Given
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Gaming Laptop",
                "Updated detailed description",
                new BigDecimal("1399.99"),
                15
        );

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(PRODUCT_ID)));

        verify(productRepository, times(1)).findById(PRODUCT_ID); // Single lookup in service layer
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("Find active products with pagination")
    void findActiveProductsWithPagination() throws Exception {
        // Given
        List<Product> products = Arrays.asList(testProduct);
        PageImpl<Product> page = new PageImpl<>(products, PageRequest.of(0, 10), 1);
        when(productRepository.findByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/products")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(PRODUCT_ID)));

        verify(productRepository).findByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class));
    }

    @Test
    @DisplayName("Find low stock products")
    void findLowStockProducts() throws Exception {
        // Given
        Product lowStockProduct = Product.builder()
                .name("Low Stock Product")
                .sku("LOW-STOCK-001")
                .basePrice(new BigDecimal("99.99"))
                .status(ProductStatus.ACTIVE)
                .stockQuantity(5)
                .lowStockThreshold(10)
                .trackInventory(true)
                .build();
        lowStockProduct.setId("low-stock-id");

        when(productRepository.findProductsWithLowStock()).thenReturn(Arrays.asList(lowStockProduct));

        // When & Then
        mockMvc.perform(get("/api/v1/products/low-stock"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("low-stock-id")));

        verify(productRepository).findProductsWithLowStock();
    }

    @Test
    @DisplayName("Reserve stock - Success")
    void reserveStockSuccess() throws Exception {
        // Given
        String productId = "product-123";
        Product existingProduct = Product.builder()
                .name("Gaming Laptop Pro")
                .sku("LAPTOP-GAMING-001")
                .description("High-performance gaming laptop")
                .basePrice(new BigDecimal("1299.99"))
                .stockQuantity(50)
                .lowStockThreshold(10)
                .trackInventory(true)
                .build();
        existingProduct.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/stock/reserve", productId)
                        .param("quantity", "5"))
                .andExpect(status().isOk());

        verify(productRepository).findById(productId);
        verify(productRepository).save(existingProduct);
    }

    @Test
    @DisplayName("Release stock - Success")
    void releaseStockSuccess() throws Exception {
        // Given
        String productId = "product-123";
        Product existingProduct = Product.builder()
                .name("Gaming Laptop Pro")
                .sku("LAPTOP-GAMING-001")
                .description("High-performance gaming laptop")
                .basePrice(new BigDecimal("1299.99"))
                .stockQuantity(45)
                .reservedQuantity(5)
                .lowStockThreshold(10)
                .trackInventory(true)
                .build();
        existingProduct.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/stock/release", productId)
                        .param("quantity", "3"))
                .andExpect(status().isOk());

        verify(productRepository).findById(productId);
        verify(productRepository).save(existingProduct);
    }

    @Test
    @DisplayName("Confirm stock - Success")
    void confirmStockSuccess() throws Exception {
        // Given
        String productId = "product-123";
        Product existingProduct = Product.builder()
                .name("Gaming Laptop Pro")
                .sku("LAPTOP-GAMING-001")
                .description("High-performance gaming laptop")
                .basePrice(new BigDecimal("1299.99"))
                .stockQuantity(45)
                .reservedQuantity(5)
                .lowStockThreshold(10)
                .trackInventory(true)
                .build();
        existingProduct.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/stock/confirm", productId)
                        .param("quantity", "2"))
                .andExpect(status().isOk());

        verify(productRepository).findById(productId);
        verify(productRepository).save(existingProduct);
    }

    @Test
    @DisplayName("Publish product - Success")
    void publishProductSuccess() throws Exception {
        // Given
        String productId = "product-123";
        Product existingProduct = Product.builder()
                .name("Gaming Laptop Pro")
                .sku("LAPTOP-GAMING-001")
                .description("High-performance gaming laptop")
                .basePrice(new BigDecimal("1299.99"))
                .stockQuantity(50)
                .lowStockThreshold(10)
                .trackInventory(true)
                .status(ProductStatus.INACTIVE)
                .build();
        existingProduct.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/publish", productId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(productId)))
                .andExpect(jsonPath("$.name", is("Gaming Laptop Pro")));

        verify(productRepository).findById(productId);
        verify(productRepository).save(existingProduct);
    }

    @Test
    @DisplayName("Discontinue product - Success")
    void discontinueProductSuccess() throws Exception {
        // Given
        String productId = "product-123";
        Product existingProduct = Product.builder()
                .name("Gaming Laptop Pro")
                .sku("LAPTOP-GAMING-001")
                .description("High-performance gaming laptop")
                .basePrice(new BigDecimal("1299.99"))
                .stockQuantity(50)
                .lowStockThreshold(10)
                .trackInventory(true)
                .status(ProductStatus.ACTIVE)
                .build();
        existingProduct.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/discontinue", productId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(productId)))
                .andExpect(jsonPath("$.name", is("Gaming Laptop Pro")));

        verify(productRepository).findById(productId);
        verify(productRepository).save(existingProduct);
    }

    // Exception path tests for 100% coverage

    @Test
    @DisplayName("Create product with invalid category - Returns 400")
    void createProductWithInvalidCategory() throws Exception {
        // Given
        var request = new CreateProductRequest(
                "Test Product",
                "test-sku-123",
                "Test description",
                BigDecimal.valueOf(99.99),
                "invalid-category-id",
                10,
                5,
                true
        );

        when(productRepository.existsBySku("test-sku-123")).thenReturn(false);
        when(categoryRepository.findById("invalid-category-id")).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Category not found")))
                .andExpect(jsonPath("$.path", is("/api/v1/products")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

                verify(productRepository).existsBySku("test-sku-123");
                verify(categoryRepository).findById("invalid-category-id");
    }

    @Test
    @DisplayName("Create product with inactive category - Returns 400")
    void createProductWithInactiveCategory() throws Exception {
        // Given
        var inactiveCategory = Category.builder()
                .name("Inactive Category")
                .slug("inactive-category")
                .description("Inactive category")
                .active(false)
                .displayOrder(1)
                .level(0)
                .build();
        inactiveCategory.setId("cat-1");

        var request = new CreateProductRequest(
                "Test Product",
                "test-sku-123",
                "Test description",
                BigDecimal.valueOf(99.99),
                "cat-1",
                10,
                5,
                true
        );

        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(inactiveCategory));

        // When & Then
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("inactive category")))
                .andExpect(jsonPath("$.path", is("/api/v1/products")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(categoryRepository, atLeast(1)).findById("cat-1");
    }

    @Test
    @DisplayName("Update product not found - Returns 404")
    void updateProductNotFound() throws Exception {
        // Given
        var productId = "non-existent-id";
        var request = new UpdateProductRequest(
                "Updated Product",
                "Updated description",
                BigDecimal.valueOf(129.99),
                8
        );

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString(productId)))
                .andExpect(jsonPath("$.path", is("/api/v1/products/" + productId)))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findById(productId);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Update product duplicate SKU - Returns 409")
    void updateProductDuplicateSku() throws Exception {
        // Given
        var productId = "prod-1";
        var existingProduct = buildSampleProduct(productId);
        var request = new UpdateProductRequest(
                "Updated Product",
                "Updated description",
                BigDecimal.valueOf(129.99),
                8
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenThrow(new DuplicateSkuException("SKU already exists"));

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("SKU")))
                .andExpect(jsonPath("$.path", is("/api/v1/products/" + productId)))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository, atLeast(1)).findById(productId);
    }

    @Test
    @DisplayName("Publish product not found - Returns 404")
    void publishProductNotFound() throws Exception {
        // Given
        var productId = "non-existent-id";
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/publish", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString(productId)))
                .andExpect(jsonPath("$.path", is("/api/v1/products/" + productId + "/publish")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findById(productId);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Publish product illegal operation - Returns 400")
    void publishProductIllegalOperation() throws Exception {
        // Given
        var productId = "prod-1";
        var product = buildSampleProduct(productId);
        product.setStatus(ProductStatus.DISCONTINUED);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenThrow(new IllegalOperationException("Cannot publish discontinued product"));

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/publish", productId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Cannot publish")))
                .andExpect(jsonPath("$.path", is("/api/v1/products/" + productId + "/publish")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findById(productId);
    }

    @Test
    @DisplayName("Discontinue product not found - Returns 404")
    void discontinueProductNotFound() throws Exception {
        // Given
        var productId = "non-existent-id";
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/discontinue", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString(productId)))
                .andExpect(jsonPath("$.path", is("/api/v1/products/" + productId + "/discontinue")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findById(productId);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Reserve stock insufficient stock - Returns 409")
    void reserveStockInsufficientStock() throws Exception {
        // Given
        var productId = "prod-1";
        var product = buildSampleProduct(productId);
        product.setStockQuantity(5);
        int requestedQuantity = 10; // More than available

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/stock/reserve", productId)
                        .param("quantity", String.valueOf(requestedQuantity)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("Insufficient")))
                .andExpect(jsonPath("$.path", is("/api/v1/products/" + productId + "/stock/reserve")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findById(productId);
    }

        @Test
        @DisplayName("Release stock invalid argument - Returns 400")
        void releaseStockInvalidArgument() throws Exception {
                // Given
                var productId = "prod-1";
                var product = buildSampleProduct(productId);
                product.setReservedQuantity(5);
                int requestedQuantity = 10; // More than reserved

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));

                // When & Then - Now handled by GlobalExceptionHandler mapping to 400
                mockMvc.perform(put("/api/v1/products/{id}/stock/release", productId)
                                                .param("quantity", String.valueOf(requestedQuantity)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status", is(400)))
                                .andExpect(jsonPath("$.error", is("Bad Request")))
                                .andExpect(jsonPath("$.message", containsString("Cannot release")))
                                .andExpect(jsonPath("$.path", is("/api/v1/products/" + productId + "/stock/release")))
                                .andExpect(jsonPath("$.timestamp", notNullValue()));

                verify(productRepository).findById(productId);
        }

    @Test
    @DisplayName("Bulk update status no products found - Returns 404 ApiError")
    void bulkUpdateStatusNoProductsFound() throws Exception {
        // Given
        var request = new BulkUpdateStatusRequest(
                List.of("non-existent-1", "non-existent-2"),
                ProductStatus.ACTIVE
        );

        when(productRepository.findAllById(request.productIds())).thenReturn(Collections.emptyList());

        // When & Then - Service returns 0 updated products, controller now throws BulkProductsNotFoundException -> 404 ApiError
        mockMvc.perform(put("/api/v1/products/bulk/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("No matching products")))
                .andExpect(jsonPath("$.path", is("/api/v1/products/bulk/status")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findAllById(request.productIds());
        // Service may legitimately call saveAll with an empty list (no-op) – do not assert 'never'.
    }

    @Test
    @DisplayName("Bulk update status repository exception - Returns 400")
    void bulkUpdateStatusException() throws Exception {
        // Given
        var request = new BulkUpdateStatusRequest(
                List.of("prod-1", "prod-2"),
                ProductStatus.ACTIVE
        );

        when(productRepository.findAllById(request.productIds())).thenThrow(new RuntimeException("Database error"));

        // When & Then - Repository exception during findAllById results in 400
        mockMvc.perform(put("/api/v1/products/bulk/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Database error")))
                .andExpect(jsonPath("$.path", is("/api/v1/products/bulk/status")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findAllById(request.productIds());
    }

    @Test
    @DisplayName("Reserve stock product not found - Returns 404")
    void reserveStockProductNotFound() throws Exception {
        // Given
        var productId = "non-existent-id";
        int quantity = 5;

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/stock/reserve", productId)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString(productId)))
                .andExpect(jsonPath("$.path", is("/api/v1/products/" + productId + "/stock/reserve")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findById(productId);
    }

    @Test
    @DisplayName("Release stock product not found - Returns 404")
    void releaseStockProductNotFound() throws Exception {
        // Given
        var productId = "non-existent-id";
        int quantity = 5;

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(put("/api/v1/products/{id}/stock/release", productId)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString(productId)))
                .andExpect(jsonPath("$.path", is("/api/v1/products/" + productId + "/stock/release")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findById(productId);
    }

        // Individual confirmStockProductNotFound test removed in favor of parameterized variant below

    @ParameterizedTest(name = "{0} product not found - Returns 404")
    @CsvSource({
            "reserve,reserve",
            "release,release",
            "confirm,confirm"
    })
    void reserveReleaseConfirmNotFoundParameterized(String operation, String pathSuffix) throws Exception {
        var productId = "non-existent-id";
        int quantity = 5;
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/products/{id}/stock/" + operation, productId)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString(productId)))
                .andExpect(jsonPath("$.path", is("/api/v1/products/" + productId + "/stock/" + pathSuffix)))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verify(productRepository).findById(productId);
    }

    /**
     * Helper method to build a sample product for testing
     */
    private Product buildSampleProduct(String productId) {
        var product = Product.builder()
                .name("Sample Product")
                .sku("SAMPLE-SKU-001")
                .description("Sample product description")
                .basePrice(new BigDecimal("99.99"))
                .stockQuantity(100)
                .lowStockThreshold(10)
                .trackInventory(true)
                .status(ProductStatus.ACTIVE)
                .build();
        product.setId(productId);
        return product;
    }
}
