package com.loiane.ecommerce.product.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.stream.Collectors;

/**
 * Centralized exception to HTTP response translation.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFound(ProductNotFoundException ex, ServletWebRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiError> handleCategoryNotFound(CategoryNotFoundException ex, ServletWebRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(BulkProductsNotFoundException.class)
    public ResponseEntity<ApiError> handleBulkProductsNotFound(BulkProductsNotFoundException ex, ServletWebRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler({ DuplicateSkuException.class, DuplicateSlugException.class, InsufficientStockException.class, CategoryDeletionConflictException.class })
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, ServletWebRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler({ IllegalOperationException.class, InactiveCategoryException.class, IllegalArgumentException.class })
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex, ServletWebRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, ServletWebRequest req) {
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, req);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleGeneric(RuntimeException ex, ServletWebRequest req) {
        // Fallback: maintain prior controller behavior (generic exception -> 400)
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, ServletWebRequest req) {
        var error = ApiError.of(status.value(), status.getReasonPhrase(), message, req.getRequest().getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
