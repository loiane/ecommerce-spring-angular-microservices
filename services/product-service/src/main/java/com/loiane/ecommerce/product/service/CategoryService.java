package com.loiane.ecommerce.product.service;

import com.loiane.ecommerce.product.entity.Category;
import com.loiane.ecommerce.product.entity.ProductStatus;
import com.loiane.ecommerce.product.exception.CategoryNotFoundException;
import com.loiane.ecommerce.product.exception.CategoryDeletionConflictException;
import com.loiane.ecommerce.product.exception.DuplicateSlugException;
import com.loiane.ecommerce.product.exception.IllegalOperationException;
import com.loiane.ecommerce.product.repository.CategoryRepository;
import com.loiane.ecommerce.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    // CREATE OPERATIONS
    @Transactional
    public Category createCategory(Category category) {
        // Check slug uniqueness
        if (categoryRepository.existsBySlug(category.getSlug())) {
            throw new DuplicateSlugException("Category with slug '" + category.getSlug() + "' already exists");
        }

        // Set level based on parent
        if (category.getParent() != null) {
            Category parent = categoryRepository.findById(category.getParent().getId())
                    .orElseThrow(() -> new CategoryNotFoundException("Parent category not found"));
            category.setLevel(parent.getLevel() + 1);
        } else {
            category.setLevel(0);
        }

        // Set timestamps
        OffsetDateTime now = OffsetDateTime.now();
        category.setCreatedAt(now);
        category.setUpdatedAt(now);

        return categoryRepository.save(category);
    }

    // READ OPERATIONS
    public Category findBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with slug: " + slug));
    }

    public Category findById(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + id));
    }

    public List<Category> getCategoryHierarchy() {
        // For simplicity, return root categories
        // In a more complex implementation, you might want to load children recursively
        return categoryRepository.findRootCategories();
    }

    /**
     * Returns all categories (flat) – used by controller list endpoint previously calling the repository directly.
     */
    public List<Category> listAll() {
        return categoryRepository.findAll();
    }

    // UPDATE OPERATIONS
    @Transactional
    public Category updateCategory(String id, Category updateData) {
        Category existingCategory = findById(id);

        // Validate slug cannot be changed
        if (updateData.getSlug() != null && !updateData.getSlug().equals(existingCategory.getSlug())) {
            throw new IllegalOperationException("Category slug cannot be changed");
        }

        // Update allowed fields
        if (updateData.getName() != null) {
            existingCategory.setName(updateData.getName());
        }
        if (updateData.getDescription() != null) {
            existingCategory.setDescription(updateData.getDescription());
        }
        if (updateData.getDisplayOrder() != null) {
            existingCategory.setDisplayOrder(updateData.getDisplayOrder());
        }

        existingCategory.setUpdatedAt(OffsetDateTime.now());
        return categoryRepository.save(existingCategory);
    }

    // BUSINESS OPERATIONS
    @Transactional
    public Category moveCategory(String categoryId, String newParentId) {
        Category category = findById(categoryId);
        Category newParent = findById(newParentId);

        category.setParent(newParent);
        category.setLevel(newParent.getLevel() + 1);
        category.setUpdatedAt(OffsetDateTime.now());

        return categoryRepository.save(category);
    }

    @Transactional
    public Category deactivateCategory(String categoryId) {
        Category category = findById(categoryId);

        // Check if category has active products
        boolean hasActiveProducts = productRepository.existsByCategoryAndStatus(category, ProductStatus.ACTIVE);
        if (hasActiveProducts) {
            throw new IllegalOperationException("Cannot deactivate category with active products");
        }

        category.setIsActive(false);
        category.setUpdatedAt(OffsetDateTime.now());
        
        return categoryRepository.save(category);
    }

    public List<Category> searchCategories(String searchTerm) {
        return categoryRepository.findByNameContainingIgnoreCase(searchTerm);
    }

    @Transactional
    public void reorderCategories(Map<String, Integer> newOrder) {
        List<Category> categories = categoryRepository.findAllById(newOrder.keySet());
        
        OffsetDateTime now = OffsetDateTime.now();
        for (Category category : categories) {
            Integer newDisplayOrder = newOrder.get(category.getId());
            if (newDisplayOrder != null) {
                category.setDisplayOrder(newDisplayOrder);
                category.setUpdatedAt(now);
            }
        }
        
        categoryRepository.saveAll(categories);
    }

    public long countActiveProductsInCategory(String categoryId) {
        Category category = findById(categoryId);
        return productRepository.countByCategoryAndStatus(category, ProductStatus.ACTIVE);
    }

    // DELETE OPERATIONS
    @Transactional
    public void deleteCategory(String id) {
        Category category = findById(id);
        long activeProducts = productRepository.countByCategoryAndStatus(category, ProductStatus.ACTIVE);
        if (activeProducts > 0) {
            throw new CategoryDeletionConflictException("Cannot delete category with active products");
        }
        categoryRepository.delete(category);
    }
}
