package com.loiane.ecommerce.product.service;

import com.loiane.ecommerce.product.entity.Category;
import com.loiane.ecommerce.product.entity.ProductStatus;
import com.loiane.ecommerce.product.exception.CategoryNotFoundException;
import com.loiane.ecommerce.product.exception.DuplicateSlugException;
import com.loiane.ecommerce.product.exception.IllegalOperationException;
import com.loiane.ecommerce.product.factory.CategoryTestDataFactory;
import com.loiane.ecommerce.product.factory.TestDataFactory;
import com.loiane.ecommerce.product.repository.CategoryRepository;
import com.loiane.ecommerce.product.repository.ProductRepository;
import com.loiane.ecommerce.product.mapper.CategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private CategoryService categoryService; // will be manually instantiated with real mapper

    private Category rootCategory;
    private Category childCategory;
    private String rootId;
    private String childId;

    @BeforeEach
    void setUp() {
        TestDataFactory.resetCounter();
        
        rootId = "root-category-id";
        childId = "child-category-id";

        rootCategory = CategoryTestDataFactory.aCategory()
                .withName("Electronics")
                .withSlug("electronics")
                .withDescription("Electronic products")
                .thatIsActive()
                .withLevel(0)
                .withDisplayOrder(1)
                .build();
        rootCategory.setId(rootId); // Set ID after building

        childCategory = CategoryTestDataFactory.aCategory()
                .withName("Smartphones")
                .withSlug("smartphones")
                .withDescription("Mobile phones")
                .withParent(rootCategory)
                .thatIsActive()
                .withLevel(1)
                .withDisplayOrder(1)
                .build();
        childCategory.setId(childId); // Set ID after building

        // Manually construct service with real mapper instance
        categoryService = new CategoryService(categoryRepository, productRepository, new CategoryMapper());
    }

    // CREATE OPERATIONS
    @Test
    @DisplayName("Should create root category successfully")
    void shouldCreateRootCategorySuccessfully() {
        // given
        when(categoryRepository.existsBySlug(rootCategory.getSlug())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(rootCategory);

        // when
        Category created = categoryService.createCategory(rootCategory);

        // then
        assertThat(created).isNotNull();
        assertThat(created.getLevel()).isZero();
        assertThat(created.getParent()).isNull();
        verify(categoryRepository).save(rootCategory);
    }

    @Test
    @DisplayName("Should create child category with correct level")
    void shouldCreateChildCategoryWithCorrectLevel() {
        // given
        when(categoryRepository.existsBySlug(childCategory.getSlug())).thenReturn(false);
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.save(any(Category.class))).thenReturn(childCategory);

        // when
        Category created = categoryService.createCategory(childCategory);

        // then
        assertThat(created.getLevel()).isEqualTo(1);
        assertThat(created.getParent()).isEqualTo(rootCategory);
        verify(categoryRepository).save(childCategory);
    }

    @Test
    @DisplayName("Should throw exception when creating category with duplicate slug")
    void shouldThrowExceptionWhenCreatingCategoryWithDuplicateSlug() {
        // given
        when(categoryRepository.existsBySlug(rootCategory.getSlug())).thenReturn(true);

        // when/then
        assertThatThrownBy(() -> categoryService.createCategory(rootCategory))
                .isInstanceOf(DuplicateSlugException.class)
                .hasMessage("Category with slug 'electronics' already exists");
        
        verify(categoryRepository, never()).save(any());
    }

    // READ OPERATIONS
    @Test
    @DisplayName("Should find category by slug")
    void shouldFindCategoryBySlug() {
        // given
        when(categoryRepository.findBySlug("electronics")).thenReturn(Optional.of(rootCategory));

        // when
        Category found = categoryService.findBySlug("electronics");

        // then
        assertThat(found).isEqualTo(rootCategory);
    }

    @Test
    @DisplayName("Should throw exception when category not found by slug")
    void shouldThrowExceptionWhenCategoryNotFoundBySlug() {
        // given
        when(categoryRepository.findBySlug("non-existent")).thenReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> categoryService.findBySlug("non-existent"))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessage("Category not found with slug: non-existent");
    }

    @Test
    @DisplayName("Should get category hierarchy")
    void shouldGetCategoryHierarchy() {
        // given
        when(categoryRepository.findRootCategories()).thenReturn(Arrays.asList(rootCategory));

        // when
        List<Category> hierarchy = categoryService.getCategoryHierarchy();

        // then
        assertThat(hierarchy).hasSize(1);
        assertThat(hierarchy.get(0)).isEqualTo(rootCategory);
    }

    // UPDATE OPERATIONS
    @Test
    @DisplayName("Should update category successfully")
    void shouldUpdateCategorySuccessfully() {
        // given
        Category updateData = Category.builder()
                .name("Updated Electronics")
                .description("Updated description")
                .displayOrder(2)
                .build();
        
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.save(any(Category.class))).thenReturn(rootCategory);

        // when
        Category updated = categoryService.updateCategory(rootId, updateData);

        // then
        assertThat(updated.getName()).isEqualTo("Updated Electronics");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getDisplayOrder()).isEqualTo(2);
        verify(categoryRepository).save(rootCategory);
    }

    @Test
    @DisplayName("Should not allow slug change during update")
    void shouldNotAllowSlugChangeDuringUpdate() {
        // given
        Category updateData = Category.builder()
                .slug("new-slug")
                .build();
        
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));

        // when/then
        assertThatThrownBy(() -> categoryService.updateCategory(rootId, updateData))
                .isInstanceOf(IllegalOperationException.class)
                .hasMessage("Category slug cannot be changed");
        
        verify(categoryRepository, never()).save(any());
    }

    // BUSINESS OPERATIONS
    @Test
    @DisplayName("Should move category to new parent")
    void shouldMoveCategoryToNewParent() {
        // given
        String newParentId = "new-parent-id";
        Category newParent = Category.builder()
                .name("Home & Garden")
                .level(0)
                .build();
        newParent.setId(newParentId); // Set ID after building
        
        when(categoryRepository.findById(childId)).thenReturn(Optional.of(childCategory));
        when(categoryRepository.findById(newParentId)).thenReturn(Optional.of(newParent));
        when(categoryRepository.save(any(Category.class))).thenReturn(childCategory);

        // when
        Category moved = categoryService.moveCategory(childId, newParentId);

        // then
        assertThat(moved.getParent()).isEqualTo(newParent);
        assertThat(moved.getLevel()).isEqualTo(1);
        verify(categoryRepository).save(childCategory);
    }

    @Test
    @DisplayName("Should deactivate category without products")
    void shouldDeactivateCategoryWithoutProducts() {
        // given
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));
        when(productRepository.existsByCategoryAndStatus(rootCategory, ProductStatus.ACTIVE)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(rootCategory);

        // when
        Category deactivated = categoryService.deactivateCategory(rootId);

        // then
        assertThat(deactivated.getIsActive()).isFalse();
        verify(categoryRepository).save(rootCategory);
    }

    @Test
    @DisplayName("Should throw exception when deactivating category with active products")
    void shouldThrowExceptionWhenDeactivatingCategoryWithActiveProducts() {
        // given
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));
        when(productRepository.existsByCategoryAndStatus(rootCategory, ProductStatus.ACTIVE)).thenReturn(true);

        // when/then
        assertThatThrownBy(() -> categoryService.deactivateCategory(rootId))
                .isInstanceOf(IllegalOperationException.class)
                .hasMessage("Cannot deactivate category with active products");
        
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should find categories by partial name")
    void shouldFindCategoriesByPartialName() {
        // given
        when(categoryRepository.findByNameContainingIgnoreCase("elec"))
                .thenReturn(Arrays.asList(rootCategory));

        // when
        List<Category> found = categoryService.searchCategories("elec");

        // then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).containsIgnoringCase("Elec");
    }

    @Test
    @DisplayName("Should reorder categories")
    void shouldReorderCategories() {
        // given
        Map<String, Integer> newOrder = new HashMap<>();
        newOrder.put(rootId, 2);
        newOrder.put(childId, 1);
        
        when(categoryRepository.findAllById(newOrder.keySet()))
                .thenReturn(Arrays.asList(rootCategory, childCategory));
        when(categoryRepository.saveAll(anyList()))
                .thenReturn(Arrays.asList(rootCategory, childCategory));

        // when
        categoryService.reorderCategories(newOrder);

        // then
        assertThat(rootCategory.getDisplayOrder()).isEqualTo(2);
        assertThat(childCategory.getDisplayOrder()).isEqualTo(1);
        verify(categoryRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should count products in category")
    void shouldCountProductsInCategory() {
        // given
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(rootCategory));
        when(productRepository.countByCategoryAndStatus(rootCategory, ProductStatus.ACTIVE)).thenReturn(42L);

        // when
        long count = categoryService.countActiveProductsInCategory(rootId);

        // then
        assertThat(count).isEqualTo(42L);
    }

    @Test
    @DisplayName("Should throw exception when creating category with parent that doesn't exist")
    void shouldThrowExceptionWhenCreatingCategoryWithNonExistentParent() {
        // given
        when(categoryRepository.existsBySlug(childCategory.getSlug())).thenReturn(false);
        when(categoryRepository.findById(rootId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> categoryService.createCategory(childCategory))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessage("Parent category not found");
        
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle categories with no new order in reorder")
    void shouldHandleCategoriesWithNoNewOrderInReorder() {
        // given
        Map<String, Integer> newOrder = new HashMap<>();
        newOrder.put(rootId, 2);
        // childId is missing from the map
        
        when(categoryRepository.findAllById(newOrder.keySet()))
                .thenReturn(Arrays.asList(rootCategory));
        when(categoryRepository.saveAll(anyList()))
                .thenReturn(Arrays.asList(rootCategory));

        // when
        categoryService.reorderCategories(newOrder);

        // then
        assertThat(rootCategory.getDisplayOrder()).isEqualTo(2);
        verify(categoryRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should throw exception when finding category by id that doesn't exist")
    void shouldThrowExceptionWhenFindingCategoryByIdThatDoesntExist() {
        // given
        when(categoryRepository.findById(rootId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> categoryService.findById(rootId))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessage("Category not found with id: " + rootId);
    }
}
