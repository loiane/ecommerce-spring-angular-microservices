package com.loiane.ecommerce.product.mapper;

import com.loiane.ecommerce.product.dto.category.CategoryResponse;
import com.loiane.ecommerce.product.dto.category.CategorySummary;
import com.loiane.ecommerce.product.dto.category.CreateCategoryRequest;
import com.loiane.ecommerce.product.dto.category.UpdateCategoryRequest;
import com.loiane.ecommerce.product.entity.Category;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

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

    @Test
    void toEntity_shouldMapCreateRequest() {
        CreateCategoryRequest req = new CreateCategoryRequest(
                "Electronics",
                "electronics",
                "Desc",
                null,
                5
        );
        Category entity = new CategoryMapper().toEntity(req);
        assertEquals("Electronics", entity.getName());
        assertEquals("electronics", entity.getSlug());
        assertEquals("Desc", entity.getDescription());
        assertEquals(5, entity.getDisplayOrder());
        assertTrue(entity.getIsActive());
        assertEquals(0, entity.getLevel());
    }

    @Test
    void toEntity_shouldApplyDefaults() {
        CreateCategoryRequest req = new CreateCategoryRequest(
                "Name",
                "name",
                null,
                null,
                null
        );
        Category entity = new CategoryMapper().toEntity(req);
        assertEquals(0, entity.getDisplayOrder());
        assertEquals(0, entity.getLevel());
        assertTrue(entity.getIsActive());
    }

    @Test
    void toResponse_shouldReturnNullForNullEntity() {
        assertNull(new CategoryMapper().toResponse(null));
    }

    @Test
    void toResponse_shouldMapAllFields() {
        Category parent = new Category();
        parent.setId("p1");
        parent.setName("Parent");
        parent.setSlug("parent");

        Category child = new Category();
        child.setId("c1");
        child.setName("Child");
        child.setSlug("child");
        child.setParent(parent);
        child.setDisplayOrder(3);
        child.setIsActive(true);
        child.setLevel(1);
        child.setCreatedAt(OffsetDateTime.now().minusDays(1));
        child.setUpdatedAt(OffsetDateTime.now());

        CategoryResponse resp = new CategoryMapper().toResponse(child);
        assertEquals("c1", resp.id());
        CategorySummary parentSummary = resp.parent();
        assertNotNull(parentSummary);
        assertEquals("p1", parentSummary.id());
    }

    @Test
    void toResponseList_shouldReturnEmptyListOnNull() {
        assertTrue(new CategoryMapper().toResponseList(null).isEmpty());
    }

    @Test
    void toResponseList_shouldMapMultiple() {
        Category c1 = new Category(); c1.setId("1");
        Category c2 = new Category(); c2.setId("2");
        assertEquals(2, new CategoryMapper().toResponseList(List.of(c1, c2)).size());
    }

    @Test
    void updateEntity_shouldApplyNonNulls() {
        Category category = new Category();
        category.setName("Old");
        category.setDescription("Old Desc");
        category.setDisplayOrder(1);

        UpdateCategoryRequest req = new UpdateCategoryRequest(
                "New",
                null,
                7
        );
        new CategoryMapper().updateEntity(category, req);
        assertEquals("New", category.getName());
        assertEquals("Old Desc", category.getDescription());
        assertEquals(7, category.getDisplayOrder());
    }

    @Test
    void merge_withNullPatchOrTarget_shouldBeSafe() {
        CategoryMapper mapper = new CategoryMapper();
        mapper.merge(new Category(), null);
        mapper.merge(null, new Category());
    }

    @Test
    void merge_shouldNotChangeIdOrSlug() {
        Category target = new Category();
        target.setId("cat-1");
        target.setSlug("original-slug");
        target.setName("Old");

        Category patch = new Category();
        patch.setId("cat-2");
        patch.setSlug("new-slug");
        patch.setName("New");

        new CategoryMapper().merge(target, patch);
        assertEquals("cat-1", target.getId(), "ID must remain unchanged");
        assertEquals("original-slug", target.getSlug(), "Slug must remain unchanged");
        assertEquals("New", target.getName());
    }
}
