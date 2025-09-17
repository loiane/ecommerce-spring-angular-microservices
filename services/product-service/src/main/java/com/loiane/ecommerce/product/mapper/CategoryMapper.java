package com.loiane.ecommerce.product.mapper;

import com.loiane.ecommerce.product.dto.category.*;
import com.loiane.ecommerce.product.entity.Category;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CategoryMapper {
    /**
     * Merges non-null fields from patch into target, respecting immutable fields (id, slug).
     */
    public void merge(Category target, Category patch) {
        if (target == null || patch == null) return;
        if (patch.getName() != null) target.setName(patch.getName());
        if (patch.getDescription() != null) target.setDescription(patch.getDescription());
        if (patch.getDisplayOrder() != null) target.setDisplayOrder(patch.getDisplayOrder());
        // Parent/level/isActive not patched here (business logic)
    }
    
    public CategoryResponse toResponse(Category category) {
        if (category == null) return null;
        
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getLevel(),
                category.getDisplayOrder(),
                category.getIsActive(),
                toSummary(category.getParent()),
                toResponseList(category.getChildren()),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
    
    public CategorySummary toSummary(Category category) {
        if (category == null) return null;
        
        return new CategorySummary(
                category.getId(),
                category.getName(),
                category.getSlug()
        );
    }
    
    public List<CategoryResponse> toResponseList(List<Category> categories) {
        if (categories == null) return new ArrayList<>();
        
        return categories.stream()
                .map(this::toResponse)
                .toList();
    }
    
    public Category toEntity(CreateCategoryRequest request) {
        if (request == null) return null;
        
        Category category = new Category();
        category.setName(request.name());
        category.setSlug(request.slug());
        category.setDescription(request.description());
        category.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        category.setIsActive(true); // New categories are active by default
        category.setLevel(0); // Will be set properly when parent is assigned
        
        return category;
    }
    
    public void updateEntity(Category category, UpdateCategoryRequest request) {
        if (request == null || category == null) return;
        
        if (request.name() != null) {
            category.setName(request.name());
        }
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
    }
}
