package com.loiane.ecommerce.product.mapper;

import com.loiane.ecommerce.product.dto.product.CategorySummary;
import com.loiane.ecommerce.product.dto.product.CreateProductRequest;
import com.loiane.ecommerce.product.dto.product.ProductResponse;
import com.loiane.ecommerce.product.dto.product.UpdateProductRequest;
import com.loiane.ecommerce.product.entity.Category;
import com.loiane.ecommerce.product.entity.Product;
import com.loiane.ecommerce.product.entity.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductMapperTests {
    @Test
    void merge_shouldUpdateNonNullFields() {
        Product target = new Product();
        target.setName("Old Name");
        target.setDescription("Old Desc");
        target.setShortDescription("Old Short");
        target.setBasePrice(java.math.BigDecimal.valueOf(10));
        target.setStockQuantity(5);
        target.setLowStockThreshold(2);
        target.setTrackInventory(false);
        target.setSku("SKU-1");
        target.setStatus(ProductStatus.INACTIVE);

        Product patch = new Product();
        patch.setName("New Name");
        patch.setDescription(null); // Should not overwrite
        patch.setShortDescription("New Short");
        patch.setBasePrice(java.math.BigDecimal.valueOf(20));
        patch.setStockQuantity(null); // Should not overwrite
        patch.setLowStockThreshold(3);
        patch.setTrackInventory(true);
        patch.setSku("NEW-SKU-SHOULD-IGNORE"); // immutable

        new ProductMapper().merge(target, patch);

        assertEquals("New Name", target.getName());
        assertEquals("Old Desc", target.getDescription());
        assertEquals("New Short", target.getShortDescription());
        assertEquals(java.math.BigDecimal.valueOf(20), target.getBasePrice());
        assertEquals(5, target.getStockQuantity());
        assertEquals(3, target.getLowStockThreshold());
        assertTrue(target.getTrackInventory());
        assertEquals("SKU-1", target.getSku(), "SKU should remain unchanged");
    }

    @Test
    void toEntity_shouldMapCreateRequest() {
    CreateProductRequest req = new CreateProductRequest(
        "Test Product",
        "TEST-123",
        "Desc",
        BigDecimal.valueOf(99.99),
        "cat-123",
        50,
        5,
        true
    );

        Product entity = new ProductMapper().toEntity(req);
        assertNotNull(entity);
        assertEquals("Test Product", entity.getName());
        assertEquals("TEST-123", entity.getSku());
        assertEquals("Desc", entity.getDescription());
        assertEquals(BigDecimal.valueOf(99.99), entity.getBasePrice());
        assertEquals(50, entity.getStockQuantity());
        assertEquals(0, entity.getReservedQuantity());
        assertEquals(5, entity.getLowStockThreshold());
        assertTrue(entity.getTrackInventory());
        assertEquals(ProductStatus.INACTIVE, entity.getStatus());
    }

    @Test
    void toEntity_shouldHandleNullsAndDefaults() {
    CreateProductRequest req = new CreateProductRequest(
        "Name",
        "SKU-X",
        null,
        BigDecimal.TEN,
        "cat-1",
        null,
        null,
        null
    );
        Product entity = new ProductMapper().toEntity(req);
        assertEquals(0, entity.getStockQuantity());
        assertEquals(10, entity.getLowStockThreshold()); // default
        assertNull(entity.getTrackInventory());
    }

    @Test
    void toResponse_shouldReturnNullForNullEntity() {
        assertNull(new ProductMapper().toResponse(null));
    }

    @Test
    void toResponse_shouldMapAllFields() {
        Product product = new Product();
        product.setId("id-1");
        product.setName("Prod");
        product.setSku("SKU-1");
        product.setDescription("Desc");
        product.setBasePrice(BigDecimal.valueOf(55));
        product.setStatus(ProductStatus.ACTIVE);
        product.setStockQuantity(12);
        product.setReservedQuantity(2);
        product.setLowStockThreshold(3);
        product.setTrackInventory(true);
        product.setCreatedAt(OffsetDateTime.now().minusDays(1));
        product.setUpdatedAt(OffsetDateTime.now());
        product.setPublishedAt(OffsetDateTime.now().minusHours(2));
        Category cat = new Category();
        cat.setId("cat-1");
        cat.setName("Cat");
        cat.setSlug("cat");
        product.setCategory(cat);

        ProductResponse resp = new ProductMapper().toResponse(product);
        assertEquals("id-1", resp.id());
        assertEquals("SKU-1", resp.sku());
        assertEquals(12, resp.stockQuantity());
        assertEquals(2, resp.reservedQuantity());
        CategorySummary summary = resp.category();
        assertNotNull(summary);
        assertEquals("cat-1", summary.id());
    }

    @Test
    void toResponseList_shouldReturnEmptyListOnNull() {
        assertTrue(new ProductMapper().toResponseList(null).isEmpty());
    }

    @Test
    void toResponseList_shouldMapMultiple() {
        Product p1 = new Product(); p1.setId("1");
        Product p2 = new Product(); p2.setId("2");
        List<ProductResponse> list = new ProductMapper().toResponseList(List.of(p1, p2));
        assertEquals(2, list.size());
    }

    @Test
    void updateEntity_shouldApplyNonNulls() {
        Product product = new Product();
        product.setName("Old");
        product.setDescription("Old Desc");
        product.setBasePrice(BigDecimal.TEN);
        product.setLowStockThreshold(5);

        UpdateProductRequest req = new UpdateProductRequest(
                "New Name",
                null, // description stays
                BigDecimal.valueOf(20),
                7
        );

        new ProductMapper().updateEntity(product, req);
        assertEquals("New Name", product.getName());
        assertEquals("Old Desc", product.getDescription());
        assertEquals(BigDecimal.valueOf(20), product.getBasePrice());
        assertEquals(7, product.getLowStockThreshold());
    }

    @Test
    void merge_withNullPatchOrTarget_shouldNotThrow() {
        Product target = new Product();
        new ProductMapper().merge(target, null); // no change
        new ProductMapper().merge(null, new Product()); // safe
    }

    @Test
    void merge_shouldNotChangeIdOrSku() {
        Product target = new Product();
        target.setId("id-123");
        target.setSku("SKU-ORIGINAL");
        target.setName("Old");

        Product patch = new Product();
        patch.setId("other-id");
        patch.setSku("NEW-SKU");
        patch.setName("New");

        new ProductMapper().merge(target, patch);
        assertEquals("id-123", target.getId(), "ID must remain unchanged");
        assertEquals("SKU-ORIGINAL", target.getSku(), "SKU must remain unchanged");
        assertEquals("New", target.getName());
    }
}
