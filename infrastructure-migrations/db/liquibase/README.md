# Liquibase Multi-Schema Migration Structure

This module manages all database migrations for the monorepo using Liquibase, supporting multiple schemas in a single PostgreSQL database.

## Structure
- `changelog-root.yaml`: Master changelog, includes all schemas in order.
- `schemas/<schema>/`: Per-domain migration scripts (tables, data, views, functions).
- `includes/preconditions/`: Precondition fragments (e.g., assert PostgreSQL).
- `snapshots/`: Baseline and diff snapshots.

## Workflow
1. Add new migration file in the appropriate schema folder.
2. Update the schema's changelog to include the new file.
3. Run migrations using the designated migrator service/module.
4. Validate with CI (dry-run, integration tests).

## Conventions
- Per-schema monotonic numbering (e.g., `product-001-...`).
- Use contexts/labels for environment-specific migrations.
- Provide rollback for DDL changesets.
- Never edit applied changesets; always add new ones.

See `changelog-root.yaml` for orchestration and ordering.
