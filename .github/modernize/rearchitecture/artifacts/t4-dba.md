# t4 — Inventory, Staff, and Audit Persistence Schema

## Summary

Implemented additive, create-if-absent schema for inventory catalog and movements, invite-managed staff accounts, and audit events. Added metadata-guarded checkout payment-verification columns. Existing customer orders, checkout data, and wastage rows are preserved; no browser-local inventory/staff data is imported.

## Schema and Migration Changes

- `schema.sql` creates normalized `inventory_categories`, `inventory_units`, and `inventory_materials` tables, with foreign keys, lookup indexes, nonnegative balance/threshold checks, and zero-start material balances.
- Six stable lookup units (`EA`, `M`, `CM`, `MM`, `KG`, `G`) are seeded with repeatable `INSERT ... SELECT ... WHERE NOT EXISTS`.
- `inventory_movements` stores signed deltas, balance-after snapshots, movement type, reason, actor email/role, timestamp, and optional uniquely linked wastage ID. Table checks constrain movement kinds, direction, and nonnegative balances.
- `staff_accounts` and `staff_invites` support persisted invited accounts and hashed one-time tokens. The configured bootstrap admin remains external to these tables.
- `audit_events` stores event/entity identifiers, actor snapshots, timestamps, and JSON-text details. The schema provides history indexes; append-only access is an application/database-privilege boundary, not a SQL trigger.
- New checkout schemas include nullable `payment_verified_at` and `payment_verified_by_email`. `CustomOrderSchemaMigration` adds either column only when missing, so legacy checkout tables remain compatible and migration reruns are safe.

## Compatibility and Migration Risks

- DDL is additive and uses the existing SQL initializer; no existing column is dropped, narrowed, or rewritten, and no existing business rows are backfilled.
- H2 `MODE=MySQL` initialization, repeat execution, checks, and the legacy-column migration were tested. A live MySQL server/version was not available for execution verification; verify the production MySQL version and default engine before rollout. MySQL versions before 8.0.16 may parse but not enforce `CHECK` constraints, so backend validation remains required.
- Because MySQL DDL may auto-commit and initialization runs at startup, avoid concurrent application startup against the same schema during the first rollout where operationally practical.
- No migration framework or version table was introduced. New history tables should not be dropped as a rollback shortcut.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t2-dba.md` — schema design, migration strategy, table invariants, and compatibility requirements.

## Evidence Mapping

- `t2-dba.md#Schema Design` → new catalog, movement, staff, invite, and audit tables in `src/main/resources/schema.sql`.
- `t2-dba.md#Relationships and invariants` → foreign keys, nonnegative checks, actor snapshots, soft-state fields, and unique wastage association in the DDL.
- `t2-dba.md#Existing order, payment, and wastage data` and `#Migration Strategy` → nullable verification metadata and repeatable guarded additions in `CustomOrderSchemaMigration`; no legacy-row conversion.
- `t2-dba.md#Migration risk and compatibility` → rollout caveats recorded above.

## Test Results

- Command: `.\mvnw.cmd test`
- Passed: 48
- Failed: 0
- Skipped: 0
- Failure details: none.
- Runtime: Maven wrapper resolved Java 25.0.5. The standalone shell `java` command resolves Java 21.0.12; the requested Java 21 override was unnecessary because the wrapper's configured Java 25 runtime was available.
- The schema suite runs against H2 MySQL mode, re-applies `schema.sql`, verifies zero-start business tables and unit seeding, checks stock/movement constraints, and drops/re-adds checkout verification columns through the migration twice.
