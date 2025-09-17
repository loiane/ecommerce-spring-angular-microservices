package com.loiane.ecommerce.product.exception;

import java.time.OffsetDateTime;

/**
 * Standard API error payload returned by the Product Service when a request fails.
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path);
    }
}
