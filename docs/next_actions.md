# Next Actions Roadmap (Product Service & UI Integration)

This document consolidates the prioritized backend + frontend next steps plus sample implementations you can copy into the codebase. Execute roughly in order; finish correctness (exceptions, mapping, persistence baseline) before optimization (caching) and DX enhancements.

---
## 1. Global Exception Handling & Standard Error Contract ✅ (Completed)
**Goal:** Eliminate duplicated try/catch blocks in controllers, provide consistent JSON error payloads for UI.

Status: Implemented (`ApiError` + `GlobalExceptionHandler` + controller cleanup). Follow-up refinement: ensure ALL 4xx responses (including bulk operations returning 404) consistently return an `ApiError` body instead of an empty response. (Standardization task tracked separately.)

### Action Items
- Create `ApiError` record.
- Add `GlobalExceptionHandler` with `@ControllerAdvice` mapping domain/runtime/validation exceptions.
- Remove per-endpoint try/catch logic from `ProductController` and `CategoryController`.

### Sample: `ApiError`
```java
package com.loiane.ecommerce.product.exception;

import java.time.OffsetDateTime;

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
```

### Sample: `GlobalExceptionHandler`
```java
package com.loiane.ecommerce.product.exception;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.stream.Collectors;

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

    @ExceptionHandler({ DuplicateSkuException.class, DuplicateSlugException.class })
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, ServletWebRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler({ IllegalOperationException.class, InactiveCategoryException.class, InsufficientStockException.class, IllegalArgumentException.class })
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

    private ResponseEntity<ApiError> build(HttpStatus status, String message, ServletWebRequest req) {
        var error = ApiError.of(status.value(), status.getReasonPhrase(), message, req.getRequest().getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
```

---
## 2. Controller → Service Cohesion
**Goal:** All business rules live in services. Controllers = thin request/response mappers.

### Action Items
- Remove repository injections from controllers.
- Delegate delete logic, existence checks, and validation to services.
- Ensure mappers convert DTO⇄Entity; controllers should not mutate entities directly.

### Sample: Category deletion in `CategoryService`
```java
@Transactional
public void deleteCategory(String id) {
    Category category = findById(id); // existing method or reuse repository + mapper
    long active = productRepository.countByCategoryAndStatus(category, ProductStatus.ACTIVE);
    if (active > 0) {
        throw new IllegalOperationException("Cannot delete category with active products");
    }
    categoryRepository.delete(category);
}
```

---
## 3. Complete Mapper Implementations
**Goal:** Centralize transformation logic; eliminate manual field mapping in controllers/services.

### Action Items
- Implement all TODO sections inside `CategoryMapper` and `ProductMapper`.
- Provide null-safety and defensive copies for collections where needed.
- Add unit tests covering mapping round-trips.

---
## 4. OpenAPI (springdoc) Documentation
**Goal:** Auto-generate API docs for internal + frontend consumption.

### Action Items
- Add `springdoc-openapi-starter-webmvc-ui` dependency.
- Create `OpenApiConfig` bean below.
- Optionally annotate endpoints with `@Operation`, `@Parameter`, `@ApiResponse` for clarity.

### Sample: `OpenApiConfig`
```java
package com.loiane.ecommerce.product.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI productServiceApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Service API")
                        .version("v1")
                        .description("Product & Category management endpoints"));
    }
}
```

---
## 5. Database Migrations (Flyway)
**Goal:** Deterministic schema evolution; disable implicit `ddl-auto` in production.

### Action Items
- Add Flyway dependency.
- Create `db/migration/V1__baseline.sql` aligned with current JPA entities.
- Remove/disable any `spring.jpa.hibernate.ddl-auto` once baseline applied.
- Future changes: new versioned migration files.

### Sample Baseline (trim / adjust to actual entity fields)
```sql
-- V1__baseline.sql
CREATE TABLE categories (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    description     VARCHAR(500),
    parent_id       VARCHAR(36),
    level           INT NOT NULL,
    display_order   INT NOT NULL,
    is_active       BOOLEAN NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES categories(id)
);

CREATE TABLE products (
    id                 VARCHAR(36) PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    sku                VARCHAR(100) NOT NULL UNIQUE,
    description        TEXT,
    base_price         NUMERIC(15,2) NOT NULL,
    status             VARCHAR(30) NOT NULL,
    category_id        VARCHAR(36),
    stock_quantity     INT,
    reserved_quantity  INT,
    low_stock_threshold INT,
    track_inventory    BOOLEAN,
    published_at       TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_status ON products(status);
```

---
## 6. Integration Testing (Testcontainers)
**Goal:** Validate repository + service behaviors (SKU uniqueness, stock transitions, deletion rules) against real PostgreSQL.

### Action Items
- Add Testcontainers dependencies (`postgresql`, `junit-jupiter`).
- Create reusable abstract test class starting/disposing container.
- Write tests for: create product, duplicate SKU, stock increase/decrease (including insufficient stock path), category deletion blocked by active products.

### Sample Abstract Test Skeleton
```java
@Testcontainers
@SpringBootTest
public abstract class AbstractPostgresIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

---
## 7. Standard Error Response Consumption (Frontend)
**Goal:** Angular apps surface backend errors consistently (toast/snackbar, form errors).

### Action Items
- Add an `HttpErrorInterceptor` interpreting `ApiError` shape.
- Map `validation` messages into form-level errors.

---
## 8. Caching (Phase 2 Optimization)
**Goal:** Reduce repeated reads for mostly static data (category tree, active product detail).

### Action Items
- Add Spring Cache + simple in-memory (Caffeine) first; later externalize to Redis.
- Annotate `CategoryService#getActiveHierarchy()` and product detail retrieval.
- Add `CacheEvict` on mutations (create/update/delete category/product).

---
## 9. Enhanced Query & Filtering
**Goal:** Support richer catalog browsing & admin filtering.

### Action Items
- Add query params: `status`, `categoryId`, `minPrice`, `maxPrice`, `q` (search name/sku), `page`, `size`, `sort`.
- Use `Specification` or QueryDSL / criteria builder for composition.
- Update OpenAPI docs accordingly.

---
## 10. Future: Observability & Metrics
**Goal:** Visibility into product operations.

### Action Items
- Add Micrometer timers for stock adjustments.
- Count metrics for product creations, deletions, low-stock events.
- Expose `/actuator/prometheus` for scraping.

---
## Suggested Implementation Order
1. Exception layer & controller slimming.
2. Complete mappers.
3. Flyway baseline + adjust configs.
4. OpenAPI docs.
5. Integration tests (iterate until green).
6. Frontend error interceptor + consume endpoints.
7. Filtering & pagination enhancements.
8. Caching.
9. Metrics / observability.

---
## Quick Commands (Reference)
```bash
# Add springdoc + flyway (adjust versions as needed in pom.xml)
# (Open pom.xml and add dependencies – sample, not an executable command)

# Run backend tests
./mvnw -f services/product-service/pom.xml clean test

# Start only product-service via Docker (if compose defined)
docker compose -f services/product-service/docker-compose.yml up --build

# Start full stack (root compose if configured)
docker compose up --build
```

---
## Validation Checklist (tick as you implement)
- [x] Global exception handler active
- [ ] Controllers free of repository access
- [ ] Mappers fully implemented & tested
- [ ] Flyway baseline applied successfully
- [ ] OpenAPI UI reachable (`/swagger-ui.html` or `/swagger-ui/index.html`)
- [ ] Integration tests green in CI
- [ ] Angular consumes live product list
- [ ] Error interceptor shows validation errors
- [ ] Filtering endpoints implemented
- [ ] Caching layer in place
- [ ] Metrics exposed

---
Feel free to extend or annotate this file with progress notes as tasks complete.
