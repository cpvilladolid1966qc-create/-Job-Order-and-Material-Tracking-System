# Style Maria — Implementation Guide

> **This index is not the full contract. Do not implement from this file alone; follow the artifact paths below.**

## Current Architecture and Scope

The project is a single-module Spring Boot web application. A single Java file, `src/main/java/com/job_order_and_material_tracking_system/stylemaria/StylemariaApplication.java`, contains application startup, MVC controllers, custom session authentication, role interception, schema-upgrade runners, direct JDBC operations, and pricing logic. Static pages live under `src/main/resources/static`; several pages share `src/main/resources/static/js/store.js`.

The browser inventory is currently nonpersistent: `inventory.html` has an empty material table body; `store.js` derives rows from the DOM, increments stock only in the current page, and reports that return/wastage actions did not change server stock. Staff records are stored in browser `localStorage` (`styleMariaStaff`). POS orders and dashboard business orders are also local (`styleMariaPosOrders`, `styleMariaDashboardOrders`). Conversely, customer accounts, custom orders, checkout submissions, customer password-reset tokens, and wastage logs are database-backed in `schema.sql`.

Staff management is on `/reports.html` (not `/admin.html`); `/admin.html` hosts the dashboard business-order surface.

The work is a bounded feature implementation, not a stack migration or full rewrite. Keep the existing single Maven application, JDBC persistence approach, routes used by customer custom-order pages, and current database rows. Add focused backend responsibilities as needed; the design/implementation owners may extract code from the existing controller monolith, but must not expand scope to unrelated static pages or cloud/deployment work.

## Target Implementation Boundaries

### Identity, roles, and invitation flow

- Continue the existing `HttpSession`/servlet-session model and environment-configured admin bootstrap. `ADMIN_EMAIL` and `ADMIN_PASSWORD` are externalized; the current password fallback is empty. Keep credentials out of source and artifacts.
- Persist staff accounts. Do not create sample staff rows. Admins invite staff by email; store only a token hash, enforce expiry and one-time use, and set the password on acceptance. SMTP settings are already externalized (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `APP_PUBLIC_URL`, `MAIL_FROM`); deployment must supply working SMTP configuration for invitation email.
- Permission roles are exactly `ADMIN`, `STAFF`, and `USER`; the current free-text staff “role” field must not grant permissions. Preserve any needed staff designation as display/job-title data separate from the authorization role. Persist invite + its audit event in one DB transaction, then send email after commit; if delivery fails, expire/revoke the pending invite and record that change transactionally.
- Proposed staff routes: `GET /api/v1/admin/staff`; `POST /api/v1/admin/staff/invitations` (ADMIN; creates/sends a STAFF invite); `PATCH /api/v1/admin/staff/{staffId}` (ADMIN; activate/deactivate); `POST /api/v1/staff/invitations/accept` (unauthenticated one-time token + new password). Do not return or persist the raw token.
- Treat the server session as the source of role and actor identity. Never trust a submitted `loggedBy`, actor, role, customer ID, or staff ID to authorize or attribute a mutation.
- Server-side permission baseline: `ADMIN` manages material catalog and staff/invitations, publishes official quotes, and verifies submitted payments; `STAFF` receives/uses/returns stock, records wastage, and performs operational order-status changes; `USER` can access only their own customer orders and submit their own payment. Enforce every protected API request on the server, not only in page navigation or JavaScript.
- API callers should receive explicit `401` (no authenticated session) or `403` (authenticated but insufficient role) responses with a stable JSON error shape; do not rely on browser redirects as API authorization.

### Inventory and audit boundaries

- Inventory pages consume server data; an empty database is rendered as empty inventory, not seeded/demo rows. The catalog owns material name/category/unit, active flag, and low-stock threshold. Catalog metadata edits do not rewrite transaction history.
- Use `GET/POST /api/v1/inventory/materials` for catalog list/create; `PATCH /api/v1/inventory/materials/{materialId}` for metadata and active/inactive changes; `GET /api/v1/inventory/movements` for history/filtering; and `POST /api/v1/inventory/movements` for stock actions. Catalog mutation is `ADMIN` only. Movement operations are allowed to `ADMIN` and `STAFF`; supported kinds are `RECEIPT`, `USAGE`, `RETURN`, `ADJUSTMENT`, and `WASTAGE`. Validate positive movement quantities and require an explicit adjustment direction/reason where applicable.
- Every stock change appends a movement row with server-derived actor and timestamp. Movement history is append-only; do not update/delete historical movement rows. Do not offer an unledgered balance-edit endpoint. The material quantity/balance and movement row must commit atomically; use the project’s JDBC transaction style and a database concurrency-safe update/locking strategy.
- Continue `/api/v1/wastage` as the UI-facing wastage route unless a compatibility alias is required. Wastage creation must atomically write the existing wastage record, the inventory movement of kind `WASTAGE`, and an audit event. Take the actor from the authenticated session; the current request’s `loggedBy` field must not remain authoritative.
- Record audit events in the same database transaction as each protected business mutation: material/staff changes, invitation issue/acceptance as applicable, stock movement, wastage, quote publish, payment verification, payment submission, and order-status updates. Audit failure must roll back the mutation.

### Orders and local-state replacement

- Preserve the existing customer API paths: `/api/v1/custom-orders` (including `/quote`, `/{orderId}/checkout`, and the combined `/checkout` route used by the current demo UI) and `/api/v1/customer/custom-orders` for a signed-in customer’s own saved requests. The quote-based `/{orderId}/checkout` requires a published quote; the combined `/checkout` flow currently returns `DEMO_SUBMITTED` and tells the customer that QR payment is not verified by this site. Preserve that boundary—do not imply a gateway or turn the demo submission into verified payment. Continue existing database rows in `custom_orders`, `custom_order_checkouts`, and `customer_accounts`; preserve customer ownership filtering.
- Keep current admin custom-order endpoints under `/api/v1/admin/custom-orders` for request review and quote draft/publish. Add a server-side payment-verification action, and allow `STAFF` only the defined operational status transitions. Keep quote authoring and payment verification restricted to `ADMIN`.
- Add `POST /api/v1/admin/custom-orders/{requestId}/payment-verification` (ADMIN) to verify/reject the submitted proof with notes; use `PATCH /api/v1/orders/{orderId}/status` for allowed operational order-state changes by ADMIN/STAFF. Back the existing POS order list/create/payment workflow with `GET/POST /api/v1/orders`; do not import the current browser records.
- Replace `styleMariaDashboardOrders` and `styleMariaPosOrders` client persistence with server-backed business/order operations. Do not import the old browser values; use DB-backed records only. Do not discard existing DB-backed customer custom orders to achieve an empty POS/dashboard view.
- A payment submission, payment verification, order-status transition, and associated audit entry must share a transaction. Preserve current customer custom-order request and checkout routes so `customize-order.js`, `payment.js`, and `track-order.js` can continue to call them.

### Persistence and compatibility boundary

- Keep the single Maven module, Spring MVC/JDBC, MySQL runtime, and H2 test mode. Avoid introducing a new persistence framework or production service.
- Extend the current schema using additive, MySQL/H2-compatible SQL. Preserve existing customer and custom-order tables/rows; inventory/staff begin empty. Existing setup uses `schema.sql` with `spring.sql.init.mode=always` and Java `ApplicationRunner` schema-column checks; coordinate new tables/constraints and any compatibility work with the DBA.
- Do not add demo inventory, demo staff, or local-storage import/migration. Existing browser keys are explicitly excluded from import.
- No request expands scope to Azure/cloud migration, containerization, deployment infrastructure, or redesign of the custom-order domain.

## Implementation Guide

### Global artifacts

- `unit_graph.yaml`
  - Who reads: all implementation workers.
  - Use for: current entry points, shared-module references, route signatures, and feature-unit boundaries.
  - How to consume: find your assigned unit; source anchors prove existing behavior and are not, by themselves, rewrite targets.
- `migration_boundary.yaml`
  - Who reads: all implementation workers.
  - Use for: the exact affected-file set and explicit keep/defer/no-import constraints.
  - How to consume: follow `must_rewrite`; leave `copy_as_is` untouched unless a verified contract change requires it; do not infer scope from every file in `unit_graph.yaml`.
- `wire_contracts.yaml`
  - Who reads: backend/frontend workers and reviewers.
  - Use for: existing methods/paths and explicitly proposed target routes, request/response semantics, and authorization.
  - How to consume: filter by the assigned unit; preserve current customer contract details where marked `frozen`/`must_preserve`; target rows marked `may_change` are proposed additions, not existing APIs.
- `shared_modules.yaml`
  - Who reads: workers touching the listed common files.
  - Use for: monolith/shared-JS reference points and split/extract cautions.
  - How to consume: filter `used_by_units`; use only the field/method names whitelisted by each unit’s `shared_refs`.
- `cross_unit_state.yaml`
  - Who reads: identity, customer-order, and admin-order workers.
  - Use for: current session role/customer ownership state.
  - How to consume: filter flows where the assigned unit is the writer or reader; preserve server-side ownership boundaries.
- `project-structure.md`, `tech-stack.md`, and `data-model.md`
  - Who reads: all workers as needed.
  - Use for: current project layers/runtime, dependency and configuration facts, and the existing SQL schema.
- `seams.yaml`
  - Not produced: no subsystem is being held frozen while another side is rewritten; the customer/order and front-end/server changes are in the same phase.

### Unit: `identity-and-staff`

- External triggers: customer `/login`, `/signup`, `/forgot-password`, `/reset-password`, `/logout`; admin staff management at `/reports.html` and invitation operations.
- Must read:
  - `units/identity-and-staff/behavior.yaml` — current account/session behavior and required staff-invite behavior.
  - `units/identity-and-staff/bindings.yaml` — current MVC/login/mail/configuration bindings.
  - `units/identity-and-staff/unit_decomposition.yaml` — advisory only; `commit: false`.
- Relevant global rows: `wire_contracts.yaml` where `unit: identity-and-staff`; `shared_modules.yaml` rows referenced by `unit_graph.yaml`; `cross_unit_state.yaml` flows where writer/reader is this unit.
- Before DONE report: artifacts read; DB staff account and invite-token persistence; admin seed/config behavior; server-side role checks; audit and invitation atomicity; test evidence for invalid/expired/reused tokens and login roles.

### Unit: `customer-custom-orders`

- External triggers: `GET /api/v1/customer/custom-orders`, order detail/image routes, `GET/POST /api/v1/custom-orders`, `POST /api/v1/custom-orders/{orderId}/checkout`, and the combined `/api/v1/custom-orders/checkout` route.
- Must read:
  - `units/customer-custom-orders/behavior.yaml` — user ownership, validation, published-quote requirement for the standard checkout route, and the separate `DEMO_SUBMITTED` combined checkout behavior.
  - `units/customer-custom-orders/bindings.yaml` — Spring routes, multipart requests, and MVC/JDBC bindings.
  - `units/customer-custom-orders/unit_decomposition.yaml` — advisory only.
- Relevant global rows: `wire_contracts.yaml` for this unit; shared Java/schema rows used by the unit; `cross_unit_state.yaml` for customer ID/role ownership flow.
- Before DONE report: preserved customer routes/data; payment-submission audit in the same transaction; errors/ownership checks and regression tests.

### Unit: `admin-order-workflow`

- External triggers: `/api/v1/admin/custom-orders` review, detail, quote draft/publish, payment proof, and order workflow updates; admin dashboard order controls.
- Must read:
  - `units/admin-order-workflow/behavior.yaml` — current status/quote branches and required payment-verification/status audit boundary.
  - `units/admin-order-workflow/bindings.yaml` — admin routes and role interception.
  - `units/admin-order-workflow/unit_decomposition.yaml` — advisory only.
- Relevant global rows: `wire_contracts.yaml` and shared-module references for this unit; role/session flow in `cross_unit_state.yaml`.
- Before DONE report: admin-only quote/payment verification, staff-limited operational status changes, no client-trusted actor, transaction-coupled audit evidence, and permission tests.

### Unit: `inventory-management`

- External trigger: `/inventory.html` materials catalog and movement/history UI.
- Must read:
  - `units/inventory-management/behavior.yaml` — current DOM-only boundary and required server-persistence behavior.
  - `units/inventory-management/bindings.yaml` — current static page/script and planned API wiring.
  - `units/inventory-management/unit_decomposition.yaml` — advisory only.
- Relevant global rows: `wire_contracts.yaml` target rows `target-inventory-catalog` and `target-inventory-movements`; `shared_modules.yaml` store/schema rows referenced by the unit. No inventory REST route is currently implemented.
- Before DONE report: empty DB renders empty catalog; role-gated catalog/movement APIs; append-only ledger, actor/timestamp attribution, low-stock behavior, concurrent stock integrity, audit transaction evidence, and API/page tests.

### Unit: `wastage-workflow`

- External triggers: `/wastage.html`, `GET/POST /api/v1/wastage`.
- Must read:
  - `units/wastage-workflow/behavior.yaml` — current server-side wastage log and transaction behavior.
  - `units/wastage-workflow/bindings.yaml` — route, page, and transaction wiring.
  - `units/wastage-workflow/unit_decomposition.yaml` — advisory only.
- Relevant global rows: `wire_contracts.yaml` for current wastage routes; shared JDBC/schema/store rows.
- Before DONE report: authenticated staff/admin authorization, session-derived actor, wastage log + stock ledger + audit atomicity, and validation/error tests.

### Unit: `pos-sales`

- External trigger: `/pos.html` pending-order and payment workflow.
- Must read:
  - `units/pos-sales/behavior.yaml` — current browser-local state and payment UI branches.
  - `units/pos-sales/bindings.yaml` — current static page and planned server wiring.
  - `units/pos-sales/unit_decomposition.yaml` — advisory only.
- Relevant global rows: `migration_boundary.yaml` local-data no-import rule; `shared_modules.yaml` store/schema rows; proposed `target-pos-orders` row in `wire_contracts.yaml`. Current POS flow has no implemented server route.
- Before DONE report: no local-order read/write remains; old browser values were not imported; server-side role checks, transaction/audit behavior, and POS API/UI tests.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/project-profile.yaml` — project/runtime shape and compatibility scope.
- `.github/modernize/rearchitecture/board.md` — selected requirements and permission baseline.

## Evidence Mapping

- `project-profile.yaml#project.structure` → project/layer and stack references in this index and the global prose artifacts.
- `board.md#User Input` → target boundaries above and the six unit artifacts linked below.
- `StylemariaApplication.java` route annotations/session checks and `schema.sql` table definitions → `wire_contracts.yaml`, `cross_unit_state.yaml`, `data-model.md`, and per-unit behavior/bindings.
