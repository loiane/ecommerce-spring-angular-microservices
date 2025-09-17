package com.loiane.ecommerce.product.mapper;

import com.loiane.ecommerce.product.entity.Category;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoryMapperTests {
    @Test
    void merge_shouldUpdateNonNullFields() {
        Category target = new Category();
        target.setName("Old Name");
        target.setDescription("Old Desc");
        target.setDisplayOrder(1);

        Category patch = new Category();
        patch.setName("New Name");
        patch.setDescription(null); // Should not overwrite
        patch.setDisplayOrder(2);

        new CategoryMapper().merge(target, patch);

        assertEquals("New Name", target.getName());
        assertEquals("Old Desc", target.getDescription());
        assertEquals(2, target.getDisplayOrder());
    }
}
