package com.loiane.ecommerce.product.mapper;

import com.loiane.ecommerce.product.dto.product.*;
import com.loiane.ecommerce.product.entity.Category;
import com.loiane.ecommerce.product.entity.Product;
import com.loiane.ecommerce.product.entity.ProductStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProductMapper {
    /**
     * Merges non-null fields from patch into target, respecting immutable fields (id, sku).
     */
    public void merge(Product target, Product patch) {
        if (target == null || patch == null) return;
        // Do not allow id/sku changes
        if (patch.getName() != null) target.setName(patch.getName());
        if (patch.getDescription() != null) target.setDescription(patch.getDescription());
        if (patch.getShortDescription() != null) target.setShortDescription(patch.getShortDescription());
        if (patch.getBasePrice() != null) target.setBasePrice(patch.getBasePrice());
        if (patch.getStockQuantity() != null) target.setStockQuantity(patch.getStockQuantity());
        if (patch.getLowStockThreshold() != null) target.setLowStockThreshold(patch.getLowStockThreshold());
        if (patch.getTrackInventory() != null) target.setTrackInventory(patch.getTrackInventory());
        // Category/status/publishedAt not patched here (business logic)
    }
    
    public ProductResponse toResponse(Product product) {
        if (product == null) return null;
        
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getBasePrice(),
                product.getStatus(),
                toCategorySummary(product.getCategory()),
                product.getStockQuantity(),
                product.getReservedQuantity(),
                product.getLowStockThreshold(),
                product.getTrackInventory(),
                product.getPublishedAt(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
    
    public CategorySummary toCategorySummary(Category category) {
        if (category == null) return null;
        
        return new CategorySummary(
                category.getId(),
                category.getName(),
                category.getSlug()
        );
    }
    
    public List<ProductResponse> toResponseList(List<Product> products) {
        if (products == null) return new ArrayList<>();
        
        return products.stream()
                .map(this::toResponse)
                .toList();
    }
    
    public Product toEntity(CreateProductRequest request) {
        if (request == null) return null;
        
        Product product = new Product();
        product.setName(request.name());
        product.setSku(request.sku());
        product.setDescription(request.description());
        product.setBasePrice(request.basePrice());
        product.setStatus(ProductStatus.INACTIVE); // New products start as inactive
        product.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0);
        product.setReservedQuantity(0); // Always start with 0 reserved
        product.setLowStockThreshold(request.lowStockThreshold() != null ? request.lowStockThreshold() : 10);
        product.setTrackInventory(request.trackInventory());
        
        // Category will be set by the service layer
        return product;
    }
    
    public void updateEntity(Product product, UpdateProductRequest request) {
        if (request == null || product == null) return;
        
        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.basePrice() != null) {
            product.setBasePrice(request.basePrice());
        }
        if (request.lowStockThreshold() != null) {
            product.setLowStockThreshold(request.lowStockThreshold());
        }
    }
}
