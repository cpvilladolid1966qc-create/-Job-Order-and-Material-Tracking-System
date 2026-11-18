# t1 — Architecture Analysis and Implementation Boundaries

## Summary

Style Maria is a single-module Spring Boot application with one Java source file containing all current server controllers, authentication, JDBC persistence, and interceptor wiring. Static HTML/JavaScript pages share a large `store.js`; inventory, staff, POS, and dashboard-order state is currently browser-local or DOM-only. Customer custom orders, customer accounts, checkouts, password-reset tokens, and wastage logs are already persisted in MySQL.

The implementation must add database-backed inventory and staff access while preserving current customer custom-order data and its user-facing routes. Do not import browser-local staff or business-order data. Use the existing MySQL/JDBC setup and H2 MySQL-mode test profile; no cloud migration or deployment change is in scope.

## Deliverables

- [architecture_index.md](./architecture_index.md) — implementation guide and explicit technical boundaries.
- [project-structure.md](./project-structure.md) — application shape, entry points, layers, and measured file counts.
- [tech-stack.md](./tech-stack.md) — existing framework, runtime, dependencies, and externalized settings.
- [data-model.md](./data-model.md) — existing SQL tables, fields, links, and observed transaction boundaries.
- [unit_graph.yaml](./unit_graph.yaml) — current and required feature entry-point map.
- [migration_boundary.yaml](./migration_boundary.yaml) — smallest affected runtime boundary and explicit non-import/preservation rules.
- [wire_contracts.yaml](./wire_contracts.yaml) — existing HTTP and persistence contracts to preserve.
- [shared_modules.yaml](./shared_modules.yaml) — common source-file registry and shared-reference whitelist.
- [cross_unit_state.yaml](./cross_unit_state.yaml) — existing session flows.
- [units/identity-and-staff/behavior.yaml](./units/identity-and-staff/behavior.yaml) — current login/session behavior and local-only staff-state evidence.
- [units/identity-and-staff/bindings.yaml](./units/identity-and-staff/bindings.yaml) — MVC/session/interceptor/mail configuration bindings.
- [units/identity-and-staff/unit_decomposition.yaml](./units/identity-and-staff/unit_decomposition.yaml) — advisory split candidates; no commitment.
- [units/customer-custom-orders/behavior.yaml](./units/customer-custom-orders/behavior.yaml) — customer ownership, validation, payment, and transaction behavior.
- [units/customer-custom-orders/bindings.yaml](./units/customer-custom-orders/bindings.yaml) — existing customer routes and multipart bindings.
- [units/customer-custom-orders/unit_decomposition.yaml](./units/customer-custom-orders/unit_decomposition.yaml) — advisory split candidates; no commitment.
- [units/admin-order-workflow/behavior.yaml](./units/admin-order-workflow/behavior.yaml) — admin status/quote branches and required audit transaction boundary.
- [units/admin-order-workflow/bindings.yaml](./units/admin-order-workflow/bindings.yaml) — admin route/interceptor bindings.
- [units/admin-order-workflow/unit_decomposition.yaml](./units/admin-order-workflow/unit_decomposition.yaml) — advisory split candidates; no commitment.
- [units/inventory-management/behavior.yaml](./units/inventory-management/behavior.yaml) — current DOM-only inventory behavior and persistence boundary.
- [units/inventory-management/bindings.yaml](./units/inventory-management/bindings.yaml) — static inventory page and planned API wiring.
- [units/inventory-management/unit_decomposition.yaml](./units/inventory-management/unit_decomposition.yaml) — advisory split candidates; no commitment.
- [units/wastage-workflow/behavior.yaml](./units/wastage-workflow/behavior.yaml) — existing wastage persistence, validation, and transaction boundary.
- [units/wastage-workflow/bindings.yaml](./units/wastage-workflow/bindings.yaml) — wastage API and page bindings.
- [units/wastage-workflow/unit_decomposition.yaml](./units/wastage-workflow/unit_decomposition.yaml) — advisory split candidates; no commitment.
- [units/pos-sales/behavior.yaml](./units/pos-sales/behavior.yaml) — current local-only POS and payment behavior.
- [units/pos-sales/bindings.yaml](./units/pos-sales/bindings.yaml) — POS static page and planned server API wiring.
- [units/pos-sales/unit_decomposition.yaml](./units/pos-sales/unit_decomposition.yaml) — advisory split candidates; no commitment.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/project-profile.yaml` — confirmed single-module Spring Boot/static HTML/JavaScript project, H2 test mode, existing customer/custom-order preservation requirement, and absence of a Git repository.
- `.github/modernize/rearchitecture/board.md` — confirmed the selected requirements, role baseline, phase assignments, and that no separate `decisions.md` was present.

## Evidence Mapping

- `project-profile.yaml#project.structure` → `project-structure.md`, `tech-stack.md`, and `architecture_index.md#Implementation Guide`.
- `project-profile.yaml#project.notes` → `migration_boundary.yaml` (keep custom-order data, no demo-data seeding/import, H2 test mode).
- `board.md#User Input` → `architecture_index.md#Target implementation boundaries` (role split, invitation flow, empty inventory/staff, preserve DB-backed customer orders).
- Source evidence `src/main/java/.../StylemariaApplication.java:73-2058`, `src/main/resources/schema.sql:1-94`, and the listed static page/script anchors → the structured unit, contract, state, and data-model artifacts.

## Verification

- Parsed 24 YAML files, including the existing project profile; all six units have behavior/bindings/decomposition files; 122 source locations resolve; source anchors are unique; shared references, contract units, state flows, and scope conflict checks pass.
- No application build/runtime or test suite was run because t1 is analysis-only; independent build/startup verification is assigned to t9 and functional tests to t10.
