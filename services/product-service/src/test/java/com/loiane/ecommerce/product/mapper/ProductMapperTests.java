package com.loiane.ecommerce.product.mapper;

import com.loiane.ecommerce.product.entity.Product;
import org.junit.jupiter.api.Test;

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

        Product patch = new Product();
        patch.setName("New Name");
        patch.setDescription(null); // Should not overwrite
        patch.setShortDescription("New Short");
        patch.setBasePrice(java.math.BigDecimal.valueOf(20));
        patch.setStockQuantity(null); // Should not overwrite
        patch.setLowStockThreshold(3);
        patch.setTrackInventory(true);

        new ProductMapper().merge(target, patch);

        assertEquals("New Name", target.getName());
        assertEquals("Old Desc", target.getDescription());
        assertEquals("New Short", target.getShortDescription());
        assertEquals(java.math.BigDecimal.valueOf(20), target.getBasePrice());
        assertEquals(5, target.getStockQuantity());
        assertEquals(3, target.getLowStockThreshold());
        assertTrue(target.getTrackInventory());
    }
}
