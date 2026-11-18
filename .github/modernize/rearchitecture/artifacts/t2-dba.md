# t2 — Persistence Schema and Migration Design

## Summary

Add normalized catalog and staff-invitation tables plus append-only inventory movement and audit ledgers. Keep existing customer, custom-order, checkout, and wastage records intact; do not import browser-local inventory, staff, or POS data. The design fits the existing Spring JDBC/JdbcTemplate, MySQL, and H2 MySQL-mode setup without introducing an ORM or a migration dependency.

## Schema Design

Use `BIGINT AUTO_INCREMENT` primary keys, `TIMESTAMP(6)` UTC timestamps, InnoDB tables in MySQL, and explicit foreign-key/index names. `schema.sql` remains the create-if-absent baseline for new objects.

| Table | Proposed columns and constraints |
|---|---|
| `inventory_categories` | `id BIGINT PK`, `name VARCHAR(80) NOT NULL UNIQUE`, `active BOOLEAN NOT NULL DEFAULT TRUE`, `created_at TIMESTAMP(6) NOT NULL`, `updated_at TIMESTAMP(6) NOT NULL`. Keep categories rather than category labels duplicated on each material. |
| `inventory_units` | `code VARCHAR(20) PK`, `display_name VARCHAR(50) NOT NULL UNIQUE`, `active BOOLEAN NOT NULL DEFAULT TRUE`. Seed only a small documented set of standard unit codes idempotently; keep codes stable once referenced. Do not treat these lookup rows as imported inventory. |
| `inventory_materials` | `id BIGINT PK`, `name VARCHAR(120) NOT NULL`, optional `sku VARCHAR(40) UNIQUE`, `category_id BIGINT NOT NULL FK`, `unit_code VARCHAR(20) NOT NULL FK`, `quantity_on_hand DECIMAL(14,3) NOT NULL DEFAULT 0`, `low_stock_threshold DECIMAL(14,3) NOT NULL DEFAULT 0`, `active BOOLEAN NOT NULL DEFAULT TRUE`, `created_at TIMESTAMP(6) NOT NULL`, `updated_at TIMESTAMP(6) NOT NULL`. Check nonnegative quantity/threshold. Add indexes on `(active, category_id)` and category/name lookup. A catalog create always begins at zero; all subsequent quantity changes use the movement flow. |
| `inventory_movements` | `id BIGINT PK`, `material_id BIGINT NOT NULL FK`, `movement_type VARCHAR(16) NOT NULL`, `quantity_delta DECIMAL(14,3) NOT NULL`, `balance_after DECIMAL(14,3) NOT NULL`, `actor_email VARCHAR(254) NOT NULL`, `actor_role VARCHAR(16) NOT NULL`, `occurred_at TIMESTAMP(6) NOT NULL`, optional `reference_id VARCHAR(100)`, optional `wastage_log_id BIGINT UNIQUE FK`, `reason VARCHAR(500) NOT NULL`. Allowed types: `RECEIPT`, `USE`, `RETURN`, `ADJUSTMENT`, `WASTAGE`; delta is signed and nonzero, balance is nonnegative. Add `(material_id, occurred_at, id)` and `(occurred_at)` indexes. |
| `staff_accounts` | `id BIGINT PK`, normalized lowercase `email VARCHAR(254) NOT NULL UNIQUE`, `display_name VARCHAR(100) NOT NULL`, `password_salt VARCHAR(64) NOT NULL`, `password_hash VARCHAR(255) NOT NULL`, `active BOOLEAN NOT NULL DEFAULT TRUE`, `created_at TIMESTAMP(6) NOT NULL`, `updated_at TIMESTAMP(6) NOT NULL`, `created_by_email VARCHAR(254) NOT NULL`. Only invited STAFF accounts are rows here; the configured bootstrap ADMIN remains authenticated from external `ADMIN_EMAIL`/`ADMIN_PASSWORD` and is not seeded into this table. Deactivate rather than delete accounts. |
| `staff_invites` | `id BIGINT PK`, normalized `email VARCHAR(254) NOT NULL`, `token_hash CHAR(64) NOT NULL UNIQUE`, `expires_at TIMESTAMP(6) NOT NULL`, `created_at TIMESTAMP(6) NOT NULL`, `created_by_email VARCHAR(254) NOT NULL`, nullable `accepted_at` and `revoked_at`. Add `(email, expires_at)` index. Store only SHA-256 token hashes; invalidate/revoke prior outstanding invitations for the same email before issuing a new one, within one transaction. |
| `audit_events` | `id BIGINT PK`, `event_type VARCHAR(64) NOT NULL`, `entity_type VARCHAR(40) NOT NULL`, `entity_id VARCHAR(100) NOT NULL`, `actor_email VARCHAR(254) NOT NULL`, `actor_role VARCHAR(16) NOT NULL`, `occurred_at TIMESTAMP(6) NOT NULL`, `details_json TEXT NOT NULL`. Add `(entity_type, entity_id, occurred_at, id)` and `(actor_email, occurred_at)` indexes. Polymorphic entity keys intentionally have no FK; event details contain only necessary before/after state and must exclude passwords, invite tokens, receipt/image bytes, and other secrets. |

### Relationships and invariants

- One category and one unit may each be referenced by many materials. Referenced categories/units are deactivated, not deleted; material foreign keys use `ON DELETE RESTRICT`.
- A material has many movement rows. Movements are append-only and reference an immutable material identity. Do not hard-delete materials; deactivate them.
- An optional unique `wastage_log_id` associates a newly-created wastage log with its one `WASTAGE` movement. Existing wastage rows stay unlinked; do not synthesize historical stock transactions.
- Staff invitations are consumed once; accepting a valid unexpired token creates a staff account and marks the invitation accepted in one transaction. Unique normalized email prevents duplicate staff accounts.
- Capture actor email/role from the authenticated server session in each ledger/audit row, not from request input. Snapshot actor values in the ledger so account deactivation does not erase attribution. No FK from audit rows to mutable staff accounts.
- Treat the movement and audit tables as append-only at the application boundary: application code exposes INSERT/SELECT only for them, with no update/delete path. In production, grant the application DB principal SELECT/INSERT but not UPDATE/DELETE on these tables if the existing privilege model permits. Never cascade-delete history.
- All mutations of a material balance lock the material row (`SELECT ... FOR UPDATE`), validate the resulting balance, update `quantity_on_hand`, append movement and audit rows, and commit/rollback together. Reject usage/wastage that would drive stock below zero. This protects concurrent writers beyond request-level validation.
- `RECEIPT` and `RETURN` require positive deltas; `USE` and `WASTAGE` require negative deltas; `ADJUSTMENT` may have either sign. Keep a single signed `quantity_delta` and `balance_after` so the ledger is the explanation for the current balance.

### Existing order, payment, and wastage data

- Keep `custom_orders` and `custom_order_checkouts` as the authoritative existing customer workflow. Do not replace their primary/unique keys or add customer foreign keys during this change; legacy rows may not satisfy newly-imposed relationships.
- Use `audit_events` for atomic records of order creation/status changes, quote draft/publication, payment submission/verification, and wastage changes. Insert each event in the same Spring transaction as its business row mutation.
- Add nullable `payment_verified_at TIMESTAMP(6)` and `payment_verified_by_email VARCHAR(254)` to `custom_order_checkouts` for queryable verification attribution; preserve existing `payment_status` and all existing checkout data. New submissions remain `SUBMITTED`; verification updates status and verifier metadata atomically with its audit event.
- Preserve `wastage_logs` shape and existing rows. New wastage recording must add the existing wastage record, decrement the selected material, append the linked `WASTAGE` movement, and insert audit event in one transaction. If a legacy client cannot identify an inventory material, it must not silently decrement an arbitrary catalog row; backend should return a validation error or retain a clearly separate non-stock path only if approved.

## Migration Strategy

1. Keep the existing SQL initializer and add only idempotent `CREATE TABLE IF NOT EXISTS` DDL for the new tables and indexes. Use syntax already compatible with the project's MySQL/H2 `MODE=MySQL` target (including `AUTO_INCREMENT`, `BOOLEAN`, `TIMESTAMP(6)`, `DECIMAL`, inline constraints, and `TEXT`).
2. Before adding verification columns to an existing checkout table, use an additive Java startup migration that checks `DatabaseMetaData` for each column and adds only missing columns, following the existing customer/custom-order compatibility migration pattern. It must safely rerun after partial completion. Do not rename, drop, narrow, or rewrite existing columns.
3. If a lightweight migration-version table is introduced, record a version only after its idempotent steps complete; MySQL DDL may auto-commit, so each individual DDL step must still be safe to repeat after process interruption. No Flyway/Liquibase dependency is currently configured.
4. No inventory or staff backfill is performed. New material balances start at zero and the movement ledger starts empty; old browser data is ignored. Do not backfill current balances from wastage logs or orders because their historical relationship to stock is not defined.
5. Preserve all customer accounts, password-reset tokens, custom orders, checkouts, and wastage logs. The new schema adds no destructive changes and no mandatory data conversion. Existing rows get `NULL` verification metadata and remain valid.
6. Normalize invite/account email in application code using trim + lowercase before persistence so unique-email behavior is consistent between MySQL and H2. Use parameterized JDBC statements for every value.
7. Keep schema checks for nonnegative balances, thresholds, valid deltas, and movement types where supported by the deployed MySQL version. Enforce the same invariants in backend validation because MySQL versions older than 8.0.16 parse but do not enforce `CHECK`.

### Migration risk and compatibility

- The project has no Flyway/Liquibase and schema initialization runs on startup. Additive DDL is appropriate for this scope, but concurrent app startup against the same schema and MySQL DDL auto-commit behavior should be considered; use one deployment instance during first schema rollout if operationally practical.
- Confirm production MySQL version and default table engine before relying on enforced `CHECK` constraints or InnoDB behavior. H2 test mode is not proof of MySQL DDL/version parity.
- Adding `payment_verified_at` and `payment_verified_by_email` is reversible while values remain null, but dropping them after they contain data loses audit conveniences; do not drop during rollback. New catalog, movement, invite, and audit tables may contain business history and must not be dropped as a rollback shortcut.
- Enforcing append-only behavior against a broad-privilege legacy DB user cannot be guaranteed solely by table DDL; database grants are the strongest boundary. Never update/delete rows in application code.
- Large or long-running backfills are not needed. Foreign-key addition only applies to new tables; no new constraint is imposed on existing customer/order/wastage records.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t1-architect.md` — implementation boundaries, existing MySQL/JDBC/H2 mode, no browser-data import, actor/session authorization, and transaction requirements.
- `.github/modernize/rearchitecture/artifacts/data-model.md` — authoritative existing table shapes, existing foreign keys, and observed mutation transaction boundaries.
- `.github/modernize/rearchitecture/artifacts/tech-stack.md` — JDBC/no ORM, SQL initializer, current lack of migration framework, and H2 MySQL-mode compatibility target.
- `.github/modernize/rearchitecture/artifacts/project-profile.yaml` — single-module scope, existing customer/custom-order preservation, and user-confirmed empty inventory/staff start.
- `.github/modernize/rearchitecture/artifacts/units/identity-and-staff/behavior.yaml` — configured admin bootstrap behavior and invite-backed staff persistence need.
- `.github/modernize/rearchitecture/artifacts/units/wastage-workflow/behavior.yaml` — existing wastage behavior and required atomic stock-movement boundary.
- `.github/modernize/rearchitecture/artifacts/units/admin-order-workflow/behavior.yaml` — status, quote, and audit updates must commit atomically.
- `.github/modernize/rearchitecture/artifacts/units/customer-custom-orders/behavior.yaml` — preserve customer ownership, checkout uniqueness, and custom-order transaction behavior.

## Evidence Mapping

- `t1-architect.md#Summary` and `#Upstream Artifacts Consumed` → retain single-module JDBC + MySQL/H2 design, preserve database customer flows, and do not import browser state.
- `data-model.md#Tables` and `#Relationships and Persistence Boundaries` → retain existing tables and known relationship limitations; proposed optional wastage link applies only to new rows.
- `tech-stack.md#Runtime Configuration` and `#Version/Compatibility Notes` → additive schema approach and H2 MySQL-mode compatibility caveats.
- `identity-and-staff/behavior.yaml#branches` → external-config ADMIN bootstrap plus persisted, invite-accepted staff rows.
- `wastage-workflow/behavior.yaml#concurrency.tx_boundary` → stock update, movement, wastage row, and audit event share one transaction.
- `admin-order-workflow/behavior.yaml#concurrency.tx_boundary` and `customer-custom-orders/behavior.yaml#concurrency.tx_boundary` → same-transaction audit rows for future order, quote, payment, and wastage mutations without breaking current customer order contracts.

## Verification

- Reviewed current `schema.sql`, production and test `application.properties`, existing schema migration runners, and task dependency artifacts.
- This is a design-only deliverable: no DDL was applied and no build/database tests were run. The schema implementation task must verify startup against H2 MySQL mode, rerun startup/migrations for idempotence, and test fresh MySQL-compatible DDL plus existing-schema preservation.
