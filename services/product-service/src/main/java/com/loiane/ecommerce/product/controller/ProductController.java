package com.loiane.ecommerce.product.controller;

import com.loiane.ecommerce.product.dto.product.*;
import com.loiane.ecommerce.product.exception.BulkProductsNotFoundException;
import com.loiane.ecommerce.product.mapper.ProductMapper;
import com.loiane.ecommerce.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;
    public ProductController(ProductService productService, ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable String id) {
        var product = productService.findById(id); // ProductNotFoundException handled globally
        var response = productMapper.toResponse(product);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> findActiveProducts(
            @PageableDefault(size = 20) Pageable pageable) {
        var products = productService.findActiveProducts(pageable);
        var response = products.map(productMapper::toResponse);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchActiveProducts(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        var products = productService.searchActiveProducts(q, pageable);
        var response = products.map(productMapper::toResponse);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<ProductResponse>> findLowStockProducts() {
        var products = productService.findLowStockProducts();
        var response = productMapper.toResponseList(products);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        var savedEntity = productService.createProduct(request);
        var response = productMapper.toResponse(savedEntity);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody UpdateProductRequest request) {
        var updatedEntity = productService.updateProduct(id, request);
        var response = productMapper.toResponse(updatedEntity);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/publish")
    public ResponseEntity<ProductResponse> publishProduct(@PathVariable String id) {
        var updatedProduct = productService.publishProduct(id);
        var response = productMapper.toResponse(updatedProduct);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/discontinue")
    public ResponseEntity<ProductResponse> discontinueProduct(@PathVariable String id) {
        var updatedProduct = productService.discontinueProduct(id);
        var response = productMapper.toResponse(updatedProduct);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/stock/reserve")
    public ResponseEntity<Void> reserveStock(
            @PathVariable String id,
            @RequestParam int quantity) {
        productService.reserveStock(id, quantity);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/stock/release")
    public ResponseEntity<Void> releaseStock(
            @PathVariable String id,
            @RequestParam int quantity) {
        productService.releaseStock(id, quantity);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/stock/confirm")
    public ResponseEntity<Void> confirmStock(
            @PathVariable String id,
            @RequestParam int quantity) {
        productService.confirmStock(id, quantity);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/bulk/status")
    public ResponseEntity<Void> bulkUpdateStatus(@Valid @RequestBody BulkUpdateStatusRequest request) {
        int updatedCount = productService.bulkUpdateStatus(request.productIds(), request.status());
        if (updatedCount == 0) {
            throw new BulkProductsNotFoundException("No matching products found for provided IDs");
        }
        return ResponseEntity.ok().build();
    }
}
