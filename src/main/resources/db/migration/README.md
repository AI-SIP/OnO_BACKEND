# Flyway migrations

Flyway is enabled for `local`, `dev`, and `prod`.

The current production-compatible schema is treated as version `1`:

- Existing non-empty databases are baselined automatically through `baseline-on-migrate`.
- `V1__baseline_existing_schema.sql` is intentionally a no-op marker.
- New schema changes must start at `V2__*.sql`.

The older `.sql` files in this directory do not follow Flyway naming and are kept as historical/manual scripts. Do not rename them into `V*__*.sql` unless they are reviewed as production-safe, idempotent migrations.
