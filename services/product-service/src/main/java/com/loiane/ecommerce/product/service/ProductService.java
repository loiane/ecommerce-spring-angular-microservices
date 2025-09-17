package com.loiane.ecommerce.product.service;

import com.loiane.ecommerce.product.dto.product.CreateProductRequest;
import com.loiane.ecommerce.product.dto.product.UpdateProductRequest;
import com.loiane.ecommerce.product.entity.Category;
import com.loiane.ecommerce.product.entity.Product;
import com.loiane.ecommerce.product.entity.ProductStatus;
import com.loiane.ecommerce.product.exception.CategoryNotFoundException;
import com.loiane.ecommerce.product.exception.DuplicateSkuException;
import com.loiane.ecommerce.product.exception.IllegalOperationException;
import com.loiane.ecommerce.product.exception.InactiveCategoryException;
import com.loiane.ecommerce.product.exception.InsufficientStockException;
import com.loiane.ecommerce.product.exception.ProductNotFoundException;
import com.loiane.ecommerce.product.repository.CategoryRepository;
import com.loiane.ecommerce.product.repository.ProductRepository;
import com.loiane.ecommerce.product.mapper.ProductMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
    }

    // CREATE OPERATIONS
    /**
     * Convenience overload that accepts a validated {@link CreateProductRequest} and performs
     * all mapping, category resolution and business validations inside the service layer.
     * Controllers should prefer this method to keep them thin.
     */
    @Transactional
    public Product createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException("Product with SKU " + request.sku() + " already exists");
        }

        // Resolve & validate category
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + request.categoryId()));
        if (Boolean.FALSE.equals(category.getIsActive())) {
            throw new InactiveCategoryException("Cannot add product to inactive category");
        }

        Product product = productMapper.toEntity(request);
        product.setCategory(category);

        OffsetDateTime now = OffsetDateTime.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        return productRepository.save(product);
    }

    @Transactional
    public Product createProduct(Product product) {
        // Check SKU uniqueness
        if (productRepository.existsBySku(product.getSku())) {
            throw new DuplicateSkuException("Product with SKU " + product.getSku() + " already exists");
        }

        // Validate category is active
        if (product.getCategory() != null) {
            Category category = categoryRepository.findById(product.getCategory().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            
            if (Boolean.FALSE.equals(category.getIsActive())) {
                throw new InactiveCategoryException("Cannot add product to inactive category");
            }
        }

        // Set timestamps
        OffsetDateTime now = OffsetDateTime.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        return productRepository.save(product);
    }

    // READ OPERATIONS
    public Product findById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
    }

    public Page<Product> findActiveProducts(Pageable pageable) {
        return productRepository.findByStatus(ProductStatus.ACTIVE, pageable);
    }

    // UPDATE OPERATIONS
    /**
     * Overload that accepts an {@link UpdateProductRequest}. It fetches the existing entity,
     * applies allowed updates and persists changes. This encapsulates mapping logic that was
     * previously in the controller.
     */
    @Transactional
    public Product updateProduct(String id, UpdateProductRequest request) {
        Product existingProduct = findById(id);
        productMapper.updateEntity(existingProduct, request);
        existingProduct.setUpdatedAt(OffsetDateTime.now());
        return productRepository.save(existingProduct);
    }

    @Transactional
    public Product updateProduct(String id, Product updatedData) {
        Product existingProduct = findById(id);

        // Validate SKU cannot be changed
        if (updatedData.getSku() != null && !updatedData.getSku().equals(existingProduct.getSku())) {
            throw new IllegalOperationException("SKU cannot be changed");
        }

        // Update allowed fields
        if (updatedData.getName() != null) existingProduct.setName(updatedData.getName());
        if (updatedData.getDescription() != null) existingProduct.setDescription(updatedData.getDescription());
        if (updatedData.getShortDescription() != null) existingProduct.setShortDescription(updatedData.getShortDescription());
        if (updatedData.getBasePrice() != null) existingProduct.setBasePrice(updatedData.getBasePrice());
        if (updatedData.getStockQuantity() != null) existingProduct.setStockQuantity(updatedData.getStockQuantity());
        if (updatedData.getLowStockThreshold() != null) existingProduct.setLowStockThreshold(updatedData.getLowStockThreshold());
        if (updatedData.getTrackInventory() != null) existingProduct.setTrackInventory(updatedData.getTrackInventory());

        existingProduct.setUpdatedAt(OffsetDateTime.now());
        return productRepository.save(existingProduct);
    }

    // INVENTORY OPERATIONS
    @Transactional
    public void reserveStock(String productId, int quantity) {
        Product product = findById(productId);
        
        int availableQuantity = product.getAvailableQuantity();
        if (availableQuantity < quantity) {
            throw new InsufficientStockException(
                String.format("Insufficient stock. Available: %d, Requested: %d", availableQuantity, quantity)
            );
        }

        product.setReservedQuantity(product.getReservedQuantity() + quantity);
        product.setUpdatedAt(OffsetDateTime.now());
        productRepository.save(product);
    }

    @Transactional
    public void releaseStock(String productId, int quantity) {
        Product product = findById(productId);
        
        if (product.getReservedQuantity() < quantity) {
            throw new IllegalOperationException(
                String.format("Cannot release more than reserved. Reserved: %d, Requested: %d", 
                    product.getReservedQuantity(), quantity)
            );
        }

        product.setReservedQuantity(product.getReservedQuantity() - quantity);
        product.setUpdatedAt(OffsetDateTime.now());
        productRepository.save(product);
    }

    @Transactional
    public void confirmStock(String productId, int quantity) {
        Product product = findById(productId);
        
        if (product.getReservedQuantity() < quantity) {
            throw new IllegalOperationException(
                String.format("Cannot confirm more than reserved. Reserved: %d, Requested: %d", 
                    product.getReservedQuantity(), quantity)
            );
        }

        // Reduce both stock quantity and reserved quantity
        product.setStockQuantity(product.getStockQuantity() - quantity);
        product.setReservedQuantity(product.getReservedQuantity() - quantity);
        product.setUpdatedAt(OffsetDateTime.now());
        productRepository.save(product);
    }

    // BUSINESS OPERATIONS
    @Transactional
    public Product publishProduct(String productId) {
        Product product = findById(productId);
        
        product.setStatus(ProductStatus.ACTIVE);
        product.setPublishedAt(OffsetDateTime.now());
        product.setUpdatedAt(OffsetDateTime.now());
        
        return productRepository.save(product);
    }

    @Transactional
    public Product discontinueProduct(String productId) {
        Product product = findById(productId);
        
        product.setStatus(ProductStatus.DISCONTINUED);
        product.setUpdatedAt(OffsetDateTime.now());
        
        return productRepository.save(product);
    }

    public List<Product> findLowStockProducts() {
        return productRepository.findProductsWithLowStock();
    }

    public Page<Product> searchActiveProducts(String searchTerm, Pageable pageable) {
        return productRepository.findActiveProductsByNameContainingWithPagination(searchTerm, pageable);
    }

    @Transactional
    public int bulkUpdateStatus(List<String> productIds, ProductStatus newStatus) {
        List<Product> products = productRepository.findAllById(productIds);
        
        OffsetDateTime now = OffsetDateTime.now();
        for (Product product : products) {
            product.setStatus(newStatus);
            product.setUpdatedAt(now);
            
            // If activating, set publishedAt
            if (newStatus == ProductStatus.ACTIVE && product.getPublishedAt() == null) {
                product.setPublishedAt(now);
            }
        }
        
        productRepository.saveAll(products);
        return products.size();
    }
}
