# Inventory Ledger Schema

For Style Maria's JDBC/MySQL/H2 application, keep inventory balances and append-only movements transactionally coupled, while leaving historic wastage/order rows unchanged.

## What Happened

Task t2 found an existing single-file Spring MVC/JdbcTemplate application with `schema.sql` initialization and hand-written metadata-based additive migrations. Existing wastage records have no inventory key, and browser-local inventory/staff/POS records are explicitly excluded from import. The schema design uses normalized category/unit lookups, zero-start materials, signed movement deltas with resulting balances, actor snapshots, hashed one-time staff invites, and a polymorphic audit table.

Task t4 implemented the proposed DDL and payment-verification compatibility migration. Six stable unit codes are seeded idempotently. H2 MySQL-mode constraint tests use `SingleConnectionDataSource`; the in-memory schema's string CHECK constraint comparison was invalidated when a `DriverManagerDataSource` test closed its creating connection.

## Takeaway

- Use `SELECT ... FOR UPDATE`, update the material's on-hand quantity, append the movement and audit row, and commit all together; enforce nonnegative stock in both service logic and database checks where supported.
- Preserve existing customer-order and wastage rows; do not invent historic stock movements or add retroactive foreign keys without proof the legacy data satisfies them.
- In a project without Flyway/Liquibase, use create-if-absent DDL for new tables and metadata-guarded additive migrations for existing tables; make each DDL step safe after partial startup failure.
- Store normalized email and actor snapshots; never store raw invite tokens or trust actor fields from requests.
- For H2 in-memory schema tests involving string CHECK constraints, keep a single connection alive for schema setup and validation; rerun the SQL initializer and guarded migration to test idempotence.

## History
- 2026-11-04 (stylemaria/t2): initial
- 2026-11-04 (stylemaria/t4): implemented DDL and recorded H2 schema-test connection handling
