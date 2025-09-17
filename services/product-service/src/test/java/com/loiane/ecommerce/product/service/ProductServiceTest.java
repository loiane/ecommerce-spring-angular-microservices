package com.loiane.ecommerce.product.service;

import com.loiane.ecommerce.product.entity.Category;
import com.loiane.ecommerce.product.entity.Product;
import com.loiane.ecommerce.product.entity.ProductStatus;
import com.loiane.ecommerce.product.exception.DuplicateSkuException;
import com.loiane.ecommerce.product.exception.IllegalOperationException;
import com.loiane.ecommerce.product.exception.InactiveCategoryException;
import com.loiane.ecommerce.product.exception.InsufficientStockException;
import com.loiane.ecommerce.product.exception.ProductNotFoundException;
import com.loiane.ecommerce.product.factory.CategoryTestDataFactory;
import com.loiane.ecommerce.product.factory.ProductTestDataFactory;
import com.loiane.ecommerce.product.factory.TestDataFactory;
import com.loiane.ecommerce.product.repository.CategoryRepository;
import com.loiane.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private ProductService productService;

    private Product testProduct;
    private Category testCategory;
    private String productId;
    private String categoryId;

    @BeforeEach
    void setUp() {
    TestDataFactory.resetCounter();
    productId = "test-product-id";
    categoryId = "test-category-id";

    testCategory = CategoryTestDataFactory.aCategory()
        .withName("Electronics")
        .withSlug("electronics")
        .thatIsActive()
        .build();
    testCategory.setId(categoryId); // Set ID after building

    testProduct = ProductTestDataFactory.aProduct()
        .withName("Test Product")
        .withDescription("Test Description")
        .withSku("TEST-001")
        .withPrice("99.99")
        .withStock(100)
        .withReservedStock(0)
        .withLowStockThreshold(10)
        .thatIsActive()
        .withCategory(testCategory)
        .build();

    productService = new ProductService(productRepository, categoryRepository, new com.loiane.ecommerce.product.mapper.ProductMapper());
        testProduct.setId(productId); // Set ID after building
        // Set additional fields not in builder
        testProduct.setShortDescription("Short description");
        testProduct.setTrackInventory(true);
        testProduct.setPublishedAt(testProduct.getCreatedAt());
    }

    // CREATE OPERATIONS
    @Test
    @DisplayName("Should create product successfully")
    void shouldCreateProductSuccessfully() {
        // given
        when(productRepository.existsBySku(testProduct.getSku())).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // when
        Product created = productService.createProduct(testProduct);

        // then
        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo("Test Product");
        verify(productRepository).existsBySku("TEST-001");
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("Should throw exception when creating product with duplicate SKU")
    void shouldThrowExceptionWhenCreatingProductWithDuplicateSku() {
        // given
        when(productRepository.existsBySku(testProduct.getSku())).thenReturn(true);

        // when/then
        assertThatThrownBy(() -> productService.createProduct(testProduct))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessage("Product with SKU TEST-001 already exists");
        
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when creating product with inactive category")
    void shouldThrowExceptionWhenCreatingProductWithInactiveCategory() {
        // given
        testCategory.setIsActive(false);
        when(productRepository.existsBySku(testProduct.getSku())).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));

        // when/then
        assertThatThrownBy(() -> productService.createProduct(testProduct))
                .isInstanceOf(InactiveCategoryException.class)
                .hasMessage("Cannot add product to inactive category");
        
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when creating product with category that doesn't exist")
    void shouldThrowExceptionWhenCreatingProductWithNonExistentCategory() {
        // given
        when(productRepository.existsBySku(testProduct.getSku())).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.createProduct(testProduct))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Category not found"); // Actual message from service implementation
        
        verify(productRepository, never()).save(any());
    }

    // READ OPERATIONS
    @Test
    @DisplayName("Should find product by id")
    void shouldFindProductById() {
        // given
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

        // when
        Product found = productService.findById(productId);

        // then
        assertThat(found).isEqualTo(testProduct);
    }

    @Test
    @DisplayName("Should throw exception when product not found")
    void shouldThrowExceptionWhenProductNotFound() {
        // given
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> productService.findById(productId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found with id: " + productId);
    }

    @Test
    @DisplayName("Should find active products with pagination")
    void shouldFindActiveProductsWithPagination() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = Arrays.asList(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, 1);
        when(productRepository.findByStatus(ProductStatus.ACTIVE, pageable)).thenReturn(productPage);

        // when
        Page<Product> result = productService.findActiveProducts(pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(testProduct);
    }

    // UPDATE OPERATIONS
    @Test
    @DisplayName("Should update product successfully")
    void shouldUpdateProductSuccessfully() {
        // given
        Product updatedData = Product.builder()
                .name("Updated Product")
                .description("Updated Description")
                .basePrice(new BigDecimal("149.99"))
                .build();
        
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // when
        Product updated = productService.updateProduct(productId, updatedData);

        // then
        assertThat(updated.getName()).isEqualTo("Updated Product");
        assertThat(updated.getDescription()).isEqualTo("Updated Description");
        assertThat(updated.getBasePrice()).isEqualByComparingTo("149.99");
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("Should not allow SKU change during update")
    void shouldNotAllowSkuChangeDuringUpdate() {
        // given
        Product updatedData = Product.builder()
                .sku("NEW-SKU")
                .build();
        
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

        // when/then
        assertThatThrownBy(() -> productService.updateProduct(productId, updatedData))
                .isInstanceOf(IllegalOperationException.class)
                .hasMessage("SKU cannot be changed");
        
        verify(productRepository, never()).save(any());
    }

    // INVENTORY OPERATIONS
    @Test
    @DisplayName("Should reserve stock successfully")
    void shouldReserveStockSuccessfully() {
        // given
        int quantityToReserve = 10;
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // when
        productService.reserveStock(productId, quantityToReserve);

        // then
        assertThat(testProduct.getReservedQuantity()).isEqualTo(10);
        assertThat(testProduct.getAvailableQuantity()).isEqualTo(90);
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("Should throw exception when insufficient stock for reservation")
    void shouldThrowExceptionWhenInsufficientStockForReservation() {
        // given
        int quantityToReserve = 110; // more than available
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

        // when/then
        assertThatThrownBy(() -> productService.reserveStock(productId, quantityToReserve))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock. Available: 100, Requested: 110");
        
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should release stock successfully")
    void shouldReleaseStockSuccessfully() {
        // given
        testProduct.setReservedQuantity(20);
        int quantityToRelease = 10;
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // when
        productService.releaseStock(productId, quantityToRelease);

        // then
        assertThat(testProduct.getReservedQuantity()).isEqualTo(10);
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("Should throw exception when releasing more than reserved")
    void shouldThrowExceptionWhenReleasingMoreThanReserved() {
        // given
        testProduct.setReservedQuantity(5);
        int quantityToRelease = 10;
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

        // when/then
        assertThatThrownBy(() -> productService.releaseStock(productId, quantityToRelease))
                .isInstanceOf(IllegalOperationException.class)
                .hasMessage("Cannot release more than reserved. Reserved: 5, Requested: 10");
        
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should confirm stock successfully")
    void shouldConfirmStockSuccessfully() {
        // given
        testProduct.setReservedQuantity(20);
        int quantityToConfirm = 15;
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // when
        productService.confirmStock(productId, quantityToConfirm);

        // then
        assertThat(testProduct.getStockQuantity()).isEqualTo(85); // 100 - 15
        assertThat(testProduct.getReservedQuantity()).isEqualTo(5); // 20 - 15
        verify(productRepository).save(testProduct);
    }

    // BUSINESS OPERATIONS
    @Test
    @DisplayName("Should publish product successfully")
    void shouldPublishProductSuccessfully() {
        // given
        testProduct.setStatus(ProductStatus.INACTIVE);
        testProduct.setPublishedAt(null);
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // when
        Product published = productService.publishProduct(productId);

        // then
        assertThat(published.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(published.getPublishedAt()).isNotNull();
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("Should discontinue product successfully")
    void shouldDiscontinueProductSuccessfully() {
        // given
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // when
        Product discontinued = productService.discontinueProduct(productId);

        // then
        assertThat(discontinued.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("Should find low stock products")
    void shouldFindLowStockProducts() {
        // given
        List<Product> lowStockProducts = Arrays.asList(testProduct);
        when(productRepository.findProductsWithLowStock()).thenReturn(lowStockProducts);

        // when
        List<Product> result = productService.findLowStockProducts();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(testProduct);
    }

    @Test
    @DisplayName("Should search active products by name")
    void shouldSearchActiveProductsByName() {
        // given
        String searchTerm = "Test";
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = Arrays.asList(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, 1);
        when(productRepository.findActiveProductsByNameContainingWithPagination(searchTerm, pageable))
                .thenReturn(productPage);

        // when
        Page<Product> result = productService.searchActiveProducts(searchTerm, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).contains(searchTerm);
    }

    @Test
    @DisplayName("Should bulk update product status")
    void shouldBulkUpdateProductStatus() {
        // given
        List<String> productIds = Arrays.asList(productId, "another-id");
        ProductStatus newStatus = ProductStatus.INACTIVE;
        when(productRepository.findAllById(productIds)).thenReturn(Arrays.asList(testProduct));
        when(productRepository.saveAll(anyList())).thenReturn(Arrays.asList(testProduct));

        // when
        int updatedCount = productService.bulkUpdateStatus(productIds, newStatus);

        // then
        assertThat(updatedCount).isEqualTo(1);
        assertThat(testProduct.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        verify(productRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should handle empty product list in bulk update")
    void shouldHandleEmptyProductListInBulkUpdate() {
        // Given
        List<String> emptyIds = Collections.emptyList();
        ProductStatus newStatus = ProductStatus.INACTIVE;
        
        when(productRepository.findAllById(emptyIds)).thenReturn(Collections.emptyList());

        // When
        int updatedCount = productService.bulkUpdateStatus(emptyIds, newStatus);

        // Then
        assertThat(updatedCount).isZero();
        verify(productRepository).findAllById(emptyIds);
        verify(productRepository).saveAll(Collections.emptyList()); // Service still calls saveAll but with empty list
    }    @Test
    @DisplayName("Should throw exception when confirming more stock than reserved")
    void shouldThrowExceptionWhenConfirmingMoreThanReserved() {
        // given
        testProduct.setReservedQuantity(5);
        int quantityToConfirm = 10;
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

        // when & then
        assertThatThrownBy(() -> productService.confirmStock(productId, quantityToConfirm))
                .isInstanceOf(IllegalOperationException.class)
                .hasMessage("Cannot confirm more than reserved. Reserved: 5, Requested: 10");
        
        verify(productRepository, never()).save(any());
    }
}
