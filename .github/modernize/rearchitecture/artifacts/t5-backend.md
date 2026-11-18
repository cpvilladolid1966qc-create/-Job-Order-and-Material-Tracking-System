# t5 — Secure Backend Workflows

## Summary

Implemented session-based role authorization and database-backed staff, inventory, and POS workflows while preserving customer custom-order routes and existing records. Inventory balances and ledger entries are updated under transaction/row-lock boundaries, with audit events written in the same transaction as business mutations. Browser-local inventory/staff/order data is not imported; initial catalog, staff, and POS data remains empty.

## Deliverables

- `src/main/java/com/job_order_and_material_tracking_system/stylemaria/ApiSupport.java` — session actor resolution, role checks, password/token utilities, API errors, and audit event writing.
- `src/main/java/com/job_order_and_material_tracking_system/stylemaria/InventoryApi.java` — category, unit, material, stock movement, and history endpoints; locked balance mutation and actor-stamped ledger writes.
- `src/main/java/com/job_order_and_material_tracking_system/stylemaria/StaffApi.java` — admin staff directory/activation, email invitations, one-time token acceptance, and database-backed staff authentication support.
- `src/main/java/com/job_order_and_material_tracking_system/stylemaria/BusinessOrderApi.java` — database-backed operational orders, payments, and staff status transitions.
- `src/main/java/com/job_order_and_material_tracking_system/stylemaria/StylemariaApplication.java` — session authorization, disabled-staff invalidation, customer-order permissions, transactional audit integration for custom orders, quotes, payment verification, and wastage stock accounting.
- `src/main/resources/schema.sql` — additive POS order/payment persistence, invite display-name compatibility, and checkout submission classification alongside the upstream schema.
- `src/test/java/com/job_order_and_material_tracking_system/stylemaria/BackendApiTests.java`, `BackendApiRuntimeTests.java`, `CustomOrderControllerTests.java`, `DemoAuthControllerTests.java`, and `WastageControllerTests.java` — H2 workflow and MockMvc route/authorization coverage, including audit-failure rollback.
- `HELP.md` — setup, role boundaries, invitation/SMTP, API, auditing, and empty-data behavior.

## Behavior and Security Boundaries

- Roles are `ADMIN`, `STAFF`, and `USER`; authorization is enforced server-side. Admin-only operations include catalog/staff management, official quotes, and customer payment verification. Staff can record stock receipt/use/return/adjustment/wastage and handle operational orders. Customer custom-order access remains owner-scoped.
- Staff invitations persist only token hashes, expire after 24 hours, are one-use, and require email delivery plus password acceptance. Staff passwords use the existing PBKDF2 format. Deactivated staff are rejected on protected requests. The configured bootstrap admin remains external to staff tables and requires `ADMIN_EMAIL` / `ADMIN_PASSWORD`; no secret is committed.
- Inventory balance updates use row locking and persist balance, append-only movement, and audit event in one transaction. Wastage writes the wastage record, stock change, linked `WASTAGE` movement, and audit event atomically.
- Customer checkout payment submissions are classified separately from demo submissions. Only quote-based submissions can be verified as official payment; legacy unclassified rows are classified conservatively based on whether a quote was published.
- New POS tables (`pos_orders`, `pos_order_payments`) and invite display-name support are additive because the consumed DBA persistence artifact did not define those fields. No legacy customer/order/wastage data is deleted or imported into new inventory/staff/POS tables.
- Real SMTP delivery and a live MySQL deployment were not exercised. Configure SMTP before inviting staff; validate DDL against the production MySQL version and environment. Frontend wiring and browser-local state removal remain outside this backend task.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t4-dba.md` — upstream table definitions, invariants, additive schema approach, zero-start policy, and H2/MySQL compatibility requirements.

## Evidence Mapping

- `t4-dba.md#Schema and Migration Changes` → inventory/staff/audit persistence is used by the implemented APIs; additive POS tables and invite display name are added in `schema.sql`.
- `t4-dba.md#Compatibility and Migration Risks` → schema remains additive and tests run in H2 MySQL mode; production MySQL version/engine validation remains a rollout requirement.
- `t4-dba.md#Test Results` → extended existing H2 schema/workflow coverage; complete suite result is recorded below.
- `t4-dba.md#inventory_movements / audit_events invariants` → movement actor/time snapshots, stock locking, same-transaction audit, and audit-failure rollback are covered by implementation and runtime tests.

## Test Results

- Command: `.\mvnw.cmd test`
- Passed: 61
- Failed: 0
- Errors: 0
- Skipped: 0
- Failure details: none.
- Runtime API evidence: `BackendApiRuntimeTests` ran 4 Spring MVC/MockMvc tests, covering authorization and runtime responses for inventory, wastage, operational orders/payments, customer quote/payment, plus rollback of stock and ledger writes when audit insertion fails.
- Runtime: Maven wrapper tests used Java 25.0.5. Standalone `java` resolves Java 21.0.12, but a JDK 21 override was not needed because the wrapper resolved its configured Java 25 runtime.

## Downstream Boundaries / Risks

- Frontend inventory CRUD/history and staff/business localStorage removal are assigned to the frontend task; this change adds no frontend files.
- SMTP credentials/configuration and a live MySQL compatibility run are environment prerequisites before production staff onboarding/rollout.
- Keep the configured admin bootstrap secret in environment configuration, never in source.
