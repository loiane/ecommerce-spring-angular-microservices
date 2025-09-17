package com.loiane.ecommerce.product.exception;

/**
 * Thrown when a bulk operation references product IDs that cannot be found.
 * Used to standardize 404 responses with an ApiError payload instead of an empty body.
 */
public class BulkProductsNotFoundException extends RuntimeException {
    public BulkProductsNotFoundException(String message) {
        super(message);
    }
}
