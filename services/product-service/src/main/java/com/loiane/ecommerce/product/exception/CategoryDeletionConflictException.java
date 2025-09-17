package com.loiane.ecommerce.product.exception;

/**
 * Exception thrown when attempting to delete a category that still has active products.
 */
public class CategoryDeletionConflictException extends RuntimeException {
    public CategoryDeletionConflictException(String message) {
        super(message);
    }
}
