package com.loiane.ecommerce.product.integration;

import com.loiane.ecommerce.product.AbstractPostgresIT;
import com.loiane.ecommerce.product.entity.Category;
import com.loiane.ecommerce.product.entity.Product;
import com.loiane.ecommerce.product.entity.ProductStatus;
import com.loiane.ecommerce.product.repository.CategoryRepository;
import com.loiane.ecommerce.product.repository.ProductRepository;
import com.loiane.ecommerce.product.service.CategoryService;
import com.loiane.ecommerce.product.exception.CategoryDeletionConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Product & Category Integration Tests with Testcontainers")
class ProductServiceIntegrationTest extends AbstractPostgresIT {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category category;

    @Autowired
    private CategoryService categoryService;

    @BeforeEach
    void setup() {
        String randomSlug = "electronics-" + UUID.randomUUID();
        category = Category.builder()
                .name("Electronics")
                .slug(randomSlug)
                .level(0)
                .displayOrder(1)
                .build();
        category = categoryRepository.save(category);
    }

    @Test
    void testCreateProduct() {
        String randomSku = "SKU-" + UUID.randomUUID();
        Product product = Product.builder()
                .name("Laptop")
                .sku(randomSku)
                .basePrice(BigDecimal.valueOf(1000))
                .status(ProductStatus.ACTIVE)
                .category(category)
                .stockQuantity(10)
                .reservedQuantity(0)
                .build();
        Product saved = productRepository.save(product);
        assertNotNull(saved.getId());
        assertEquals(randomSku, saved.getSku());
    }

    @Test
    void testDuplicateSkuThrows() {
    String randomSku = "SKU-" + UUID.randomUUID();
    Product product1 = Product.builder()
        .name("Laptop")
        .sku(randomSku)
        .basePrice(BigDecimal.valueOf(1000))
        .status(ProductStatus.ACTIVE)
        .category(category)
        .build();
    productRepository.save(product1);
    Product product2 = Product.builder()
        .name("Monitor")
        .sku(randomSku)
        .basePrice(BigDecimal.valueOf(200))
        .status(ProductStatus.ACTIVE)
        .category(category)
        .build();
    assertThrows(Exception.class, () -> productRepository.save(product2));
    }

    @Test
    void testStockIncreaseDecrease() {
        String randomSku = "SKU-" + UUID.randomUUID();
        Product product = Product.builder()
                .name("Mouse")
                .sku(randomSku)
                .basePrice(BigDecimal.valueOf(50))
                .status(ProductStatus.ACTIVE)
                .category(category)
                .stockQuantity(5)
                .reservedQuantity(0)
                .build();
        product = productRepository.save(product);
        // Increase stock
        product.setStockQuantity(product.getStockQuantity() + 5);
        product = productRepository.save(product);
        assertEquals(10, product.getStockQuantity());
        // Decrease stock
        product.setStockQuantity(product.getStockQuantity() - 3);
        product = productRepository.save(product);
        assertEquals(7, product.getStockQuantity());
        // Insufficient stock
        product.setStockQuantity(product.getStockQuantity() - 10);
        assertTrue(product.getStockQuantity() < 0);
    }

    @Test
    @Transactional
    void testCategoryDeletionBlockedByActiveProducts() {
        String randomSku = "SKU-" + UUID.randomUUID();
        Product product = Product.builder()
                .name("Keyboard")
                .sku(randomSku)
                .basePrice(BigDecimal.valueOf(80))
                .status(ProductStatus.ACTIVE)
                .category(category)
                .build();
        productRepository.save(product);
        assertThrows(CategoryDeletionConflictException.class, () -> categoryService.deleteCategory(category.getId()));
    }
}
