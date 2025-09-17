package com.loiane.ecommerce.product.controller;

import com.loiane.ecommerce.product.dto.category.*;
import com.loiane.ecommerce.product.exception.CategoryDeletionConflictException; // Specific conflict exception
import com.loiane.ecommerce.product.mapper.CategoryMapper;
import com.loiane.ecommerce.product.repository.CategoryRepository;
import com.loiane.ecommerce.product.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;
    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryService categoryService, CategoryMapper categoryMapper, CategoryRepository categoryRepository) {
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        var categories = categoryRepository.findAll(); // Use repository directly since service method returns hierarchy
        var response = categoryMapper.toResponseList(categories);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<CategoryResponse> getCategoryBySlug(@PathVariable String slug) {
        var category = categoryService.findBySlug(slug); // handled globally
        var response = categoryMapper.toResponse(category);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        var entity = categoryMapper.toEntity(request);
        var savedEntity = categoryService.createCategory(entity);
        var response = categoryMapper.toResponse(savedEntity);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable String id, 
            @Valid @RequestBody UpdateCategoryRequest request) {
        var existingCategory = categoryService.findById(id);
        categoryMapper.updateEntity(existingCategory, request);
        var updatedEntity = categoryService.updateCategory(id, existingCategory);
        var response = categoryMapper.toResponse(updatedEntity);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable String id) {
        // Count check & delete logic should move to service eventually
        var category = categoryService.findById(id);
        long productCount = categoryService.countActiveProductsInCategory(id);
        if (productCount > 0) {
            throw new CategoryDeletionConflictException("Cannot delete category with active products");
        }
        categoryRepository.delete(category);
        return ResponseEntity.noContent().build();
    }
}
