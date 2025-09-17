package com.loiane.ecommerce.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loiane.ecommerce.product.dto.category.*;
import com.loiane.ecommerce.product.entity.Category;
import com.loiane.ecommerce.product.exception.CategoryNotFoundException;
import com.loiane.ecommerce.product.exception.DuplicateSlugException;
import com.loiane.ecommerce.product.factory.CategoryDTOTestFactory;
import com.loiane.ecommerce.product.factory.CategoryTestDataFactory;
import com.loiane.ecommerce.product.repository.CategoryRepository;
import com.loiane.ecommerce.product.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryRepository categoryRepository; // Still used indirectly via service for create/update flows
    
    @MockitoBean 
    private CategoryService categoryService;

    // Test data
    private Category rootCategoryEntity;
    private String rootCategoryId;
    private String nonExistentId;

    @BeforeEach
    void setUp() {
    reset(categoryRepository, categoryService);
        
        rootCategoryId = UUID.randomUUID().toString();
        nonExistentId = UUID.randomUUID().toString();
        
        rootCategoryEntity = CategoryTestDataFactory.createRoot("Electronics");
        rootCategoryEntity.setId(rootCategoryId);
        rootCategoryEntity.setSlug("electronics");
    }

    @Test
    @DisplayName("GET /api/v1/categories - Should return all categories")
    void testGetAllCategories() throws Exception {
        // Given
        Category subCategory = CategoryTestDataFactory.createChild("Smartphones", rootCategoryEntity);
    when(categoryService.listAll()).thenReturn(Arrays.asList(rootCategoryEntity, subCategory));

        // When & Then
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(rootCategoryEntity.getId()))
                .andExpect(jsonPath("$[0].name").value(rootCategoryEntity.getName()))
                .andExpect(jsonPath("$[0].slug").value(rootCategoryEntity.getSlug()))
                .andExpect(jsonPath("$[0].description").value(rootCategoryEntity.getDescription()))
                .andExpect(jsonPath("$[0].level").value(rootCategoryEntity.getLevel()))
                .andExpect(jsonPath("$[0].active").value(rootCategoryEntity.getIsActive()))
                .andExpect(jsonPath("$[1].id").value(subCategory.getId()))
                .andExpect(jsonPath("$[1].name").value(subCategory.getName()))
                .andExpect(jsonPath("$[1].slug").value(subCategory.getSlug()));

    verify(categoryService).listAll();
    }

    @Test
    @DisplayName("GET /api/v1/categories/{slug} - Should return category when found")
    void testGetCategoryBySlugSuccess() throws Exception {
        // Given
        when(categoryService.findBySlug("electronics")).thenReturn(rootCategoryEntity);

        // When & Then
        mockMvc.perform(get("/api/v1/categories/electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rootCategoryEntity.getId()))
                .andExpect(jsonPath("$.name").value(rootCategoryEntity.getName()))
                .andExpect(jsonPath("$.slug").value(rootCategoryEntity.getSlug()))
                .andExpect(jsonPath("$.description").value(rootCategoryEntity.getDescription()))
                .andExpect(jsonPath("$.level").value(rootCategoryEntity.getLevel()))
                .andExpect(jsonPath("$.active").value(rootCategoryEntity.getIsActive()));

        verify(categoryService).findBySlug("electronics");
    }

    @Test
    @DisplayName("GET /api/v1/categories/{slug} - Should return 404 when category not found")
    void testGetCategoryBySlugNotFound() throws Exception {
        // Given
        when(categoryService.findBySlug("nonexistent")).thenThrow(new CategoryNotFoundException("Category not found"));

        // When & Then
        mockMvc.perform(get("/api/v1/categories/nonexistent"))
                .andExpect(status().isNotFound());

        verify(categoryService).findBySlug("nonexistent");
    }

    @Test
    @DisplayName("POST /api/v1/categories - Should create category successfully")
    void testCreateCategorySuccess() throws Exception {
        // Given
        CreateCategoryRequest request = CategoryDTOTestFactory.createValidCreateRequest();
        Category savedCategory = CategoryTestDataFactory.createRoot(request.name());
        savedCategory.setId(UUID.randomUUID().toString());
        
        when(categoryService.createCategory(any(Category.class))).thenReturn(savedCategory);

        String requestJson = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(savedCategory.getId()))
                .andExpect(jsonPath("$.name").value(savedCategory.getName()))
                .andExpect(jsonPath("$.slug").value(savedCategory.getSlug()))
                .andExpect(jsonPath("$.active").value(savedCategory.getIsActive()));

        verify(categoryService).createCategory(any(Category.class));
    }

    @Test
    @DisplayName("POST /api/v1/categories - Should return 409 when slug already exists")
    void testCreateCategorySlugExists() throws Exception {
        // Given
        CreateCategoryRequest request = CategoryDTOTestFactory.createValidCreateRequest();
        when(categoryService.createCategory(any(Category.class))).thenThrow(new DuplicateSlugException("Slug already exists"));

        String requestJson = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict());

        verify(categoryService).createCategory(any(Category.class));
    }

    @Test
    @DisplayName("POST /api/v1/categories - Should return 400 for invalid request")
    void testCreateCategoryInvalidRequest() throws Exception {
        // Given - request with blank name and slug (using constructor directly)
        CreateCategoryRequest invalidRequest = new CreateCategoryRequest("", "", "", null, null);
        String requestJson = objectMapper.writeValueAsString(invalidRequest);

        // When & Then
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    @DisplayName("PUT /api/v1/categories/{id} - Should update category successfully")
    void testUpdateCategorySuccess() throws Exception {
        // Given
        UpdateCategoryRequest request = CategoryDTOTestFactory.createValidUpdateRequest();
        Category updatedCategory = CategoryTestDataFactory.createRoot("Updated Category");
        updatedCategory.setId(rootCategoryId);
        updatedCategory.setDescription("Updated description");
        updatedCategory.setDisplayOrder(10);

        when(categoryService.findById(rootCategoryId)).thenReturn(rootCategoryEntity);
        when(categoryService.updateCategory(eq(rootCategoryId), any(Category.class))).thenReturn(updatedCategory);

        String requestJson = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(put("/api/v1/categories/" + rootCategoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(updatedCategory.getId()))
                .andExpect(jsonPath("$.name").value(updatedCategory.getName()))
                .andExpect(jsonPath("$.description").value(updatedCategory.getDescription()))
                .andExpect(jsonPath("$.displayOrder").value(updatedCategory.getDisplayOrder()));

        verify(categoryService).findById(rootCategoryId);
        verify(categoryService).updateCategory(eq(rootCategoryId), any(Category.class));
    }

    @Test
    @DisplayName("PUT /api/v1/categories/{id} - Should return 404 when category not found")
    void testUpdateCategoryNotFound() throws Exception {
        // Given
        UpdateCategoryRequest request = CategoryDTOTestFactory.createValidUpdateRequest();
        when(categoryService.findById(nonExistentId)).thenThrow(new CategoryNotFoundException("Category not found"));

        String requestJson = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(put("/api/v1/categories/" + nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound());

        verify(categoryService).findById(nonExistentId);
        verify(categoryService, never()).updateCategory(anyString(), any(Category.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/categories/{id} - Should delete category successfully")
    void testDeleteCategorySuccess() throws Exception {
        // Given
    doNothing().when(categoryService).deleteCategory(rootCategoryId);

        // When & Then
        mockMvc.perform(delete("/api/v1/categories/" + rootCategoryId))
                .andExpect(status().isNoContent());

    verify(categoryService).deleteCategory(rootCategoryId);
    }

    @Test
    @DisplayName("DELETE /api/v1/categories/{id} - Should return 409 when category has active products")
    void testDeleteCategoryHasActiveProducts() throws Exception {
        // Given
    doThrow(new com.loiane.ecommerce.product.exception.CategoryDeletionConflictException("Cannot delete category with active products"))
        .when(categoryService).deleteCategory(rootCategoryId);

        // When & Then
        mockMvc.perform(delete("/api/v1/categories/" + rootCategoryId))
                .andExpect(status().isConflict());

    verify(categoryService).deleteCategory(rootCategoryId);
    }

    @Test
    @DisplayName("DELETE /api/v1/categories/{id} - Should return 404 when category not found")
    void testDeleteCategoryNotFound() throws Exception {
        // Given
    doThrow(new CategoryNotFoundException("Category not found")).when(categoryService).deleteCategory(nonExistentId);

        // When & Then
        mockMvc.perform(delete("/api/v1/categories/" + nonExistentId))
                .andExpect(status().isNotFound());

    verify(categoryService).deleteCategory(nonExistentId);
    }
}
