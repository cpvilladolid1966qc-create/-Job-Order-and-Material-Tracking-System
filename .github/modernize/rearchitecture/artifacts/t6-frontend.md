# t6 — Server-Backed Frontend Workflows

## Summary

Inventory, staff onboarding, operational POS orders/payments, and the admin customer custom-order dashboard now use the server APIs. Staff/inventory/business-order workflows no longer read or write browser-local storage. Existing customer custom-order review remains connected to the existing database-backed endpoints.

## Deliverables

- `src/main/resources/static/js/api-client.js` — shared same-origin JSON request/error handling.
- `src/main/resources/static/js/inventory.js` and `inventory.html` — catalog, category, stock movement, and append-only history UI.
- `src/main/resources/static/js/staff.js`, `reports.html`, `staff-invitation.html`, and `js/staff-invitation.js` — staff directory, email invitations, activation controls, and invite-token/password acceptance.
- `src/main/resources/static/js/pos.js` and `pos.html` — server-backed operational orders, order status transitions, payment submissions, and receipts.
- `src/main/resources/static/js/admin-orders.js` and `admin.html` — database-backed customer request dashboard and review links.
- `src/main/resources/static/css/style.css` — inventory and invitation-page styles.
- `src/main/resources/static/js/store.js` — removed local-only staff/inventory/POS/custom-order dashboard handlers while retaining shared page behavior, wastage workflow, and calculator.
- `HELP.md` — documented database-backed behavior, staff onboarding, and payment/persistence boundaries.

## Behavior Boundaries

- Inventory pages create materials with zero opening quantity; stock is added through a receipt movement. Returns can select inactive materials. Wastage continues through the existing wastage workflow so the stock deduction stays linked to its wastage/audit records.
- Staff onboarding sends an email invitation; accepted accounts are read from the server. Invitation delivery requires configured SMTP. Real email delivery was not exercised.
- Operational POS payment submissions are recorded by the server; no payment processor is connected.
- Server-side authorization remains authoritative. Catalog controls are visible on staff-accessible inventory pages, but catalog mutations remain admin-only and are rejected by the server for staff. No frontend session-role endpoint was available in the consumed API contract to conditionally hide those controls.
- Existing customer custom-order detail, official quote, and payment-review operations continue to use their existing endpoints; customer ownership and admin permissions remain server-enforced.
- No old browser data is imported. The isolated UI smoke test used a temporary in-memory H2 database and temporary admin credentials only.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t5-backend.md` — API operations, request/response behavior, role boundaries, empty-start policy, and transaction/audit guarantees.

## Evidence Mapping

- `t5-backend.md#Behavior and Security Boundaries` → inventory and staff/POS pages submit server requests and rely on server authorization; wastage remains linked to stock/audit data.
- `t5-backend.md#Summary` → the new inventory/staff/POS browser workflows start with empty database-backed state and preserve customer custom-order routes.
- `t5-backend.md#Test Results` → this frontend consumes the documented runtime API behavior; full backend suite is rerun below.

## Test Results

- Command: `.\mvnw.cmd test`
- Passed: 61
- Failed: 0
- Errors: 0
- Skipped: 0
- Failure details: none.
- Browser/API smoke: Spring Boot started successfully on H2 at `http://localhost:18080/` using an isolated in-memory database. Browser UI successfully created a category and material, recorded a receipt and showed the updated balance/ledger actor, loaded the staff directory, created a POS order, and recorded a cash payment with a visible receipt.
- Static checks: Python standard-library HTML parsing found no duplicate IDs or missing local script files on the five updated pages. A source scan found no `localStorage` use in the new inventory/staff/invitation/POS/admin-order modules or the shared `store.js`.
- JavaScript syntax lint was not available because Node.js is not installed. Runtime browser execution exercised the new frontend modules.
- Environment: Maven used Java 25.0.5. The ordinary shell `java` resolves Java 21.0.12; a JDK 21 override was unnecessary for the test command.

## Downstream Boundaries / Risks

- Configure SMTP before exercising staff invitations in a deployed environment.
- Validate the additive schema against the production MySQL version and confirm deployment-specific session/security settings before rollout.
- Payment UI records submissions only; it does not charge or verify through an external payment provider.
