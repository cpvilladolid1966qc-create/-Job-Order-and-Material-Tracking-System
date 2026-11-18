# t3 — Testing Strategy and Critical Acceptance Coverage

## Summary

Style Maria is a mixed Spring MVC/API application: static HTML and browser JavaScript consume same-origin server APIs. Validate the server with full-context Spring integration tests and validate browser journeys with Playwright whenever its prerequisites are available. Keep infrastructure and browser validation as independent capability axes.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t1-architect.md` — implementation boundaries, application shape, and preservation rules.
- `.github/modernize/rearchitecture/artifacts/data-model.md` — current database tables, customer/order relationships, and transaction gaps.
- `.github/modernize/rearchitecture/artifacts/tech-stack.md` — Maven, Spring MVC/JDBC, MySQL, and H2 test-mode configuration.
- `.github/modernize/rearchitecture/artifacts/units/inventory-management/behavior.yaml` — inventory behavior to replace and low-stock filtering behavior to preserve.
- `.github/modernize/rearchitecture/artifacts/units/identity-and-staff/behavior.yaml` — session, customer-authentication, and current staff state behavior.
- `.github/modernize/rearchitecture/artifacts/units/admin-order-workflow/behavior.yaml` — order-status, quote, and audit transaction requirements.
- `.github/modernize/rearchitecture/artifacts/units/wastage-workflow/behavior.yaml` — persisted wastage behavior and required inventory/ledger atomicity.
- `.github/modernize/rearchitecture/artifacts/units/pos-sales/behavior.yaml` — local-only POS behavior and cash-payment completion rule.
- `.github/modernize/rearchitecture/board.md` — selected role permissions, invite onboarding, empty inventory/staff, preserved custom orders, and validation task ownership.

## Evidence Mapping

- `t1-architect.md#Target implementation boundaries` and `board.md#User Input` → this strategy's customer-data preservation, no-local-data-import, role matrix, and transaction acceptance criteria.
- `tech-stack.md#Frameworks and Dependencies` / `#Runtime Configuration` → Maven/JUnit, MySQL primary database, H2 test profile, and externalized SMTP requirements.
- `data-model.md#Observed Transaction Boundaries` → rollback and same-transaction audit/payment/order/wastage assertions.
- `units/inventory-management/behavior.yaml#branches` → inventory catalog/filters and server-persisted stock behavior coverage.
- `units/identity-and-staff/behavior.yaml#side_effects` → invite/acceptance/authentication coverage while preserving session-based customer login.
- `units/admin-order-workflow/behavior.yaml#branches` and `#concurrency` → role-gated quote/payment/status workflows and atomic audit coverage.
- `units/wastage-workflow/behavior.yaml#side_effects` and `#concurrency` → stock movement + wastage log + audit transaction assertions.
- `units/pos-sales/behavior.yaml#branches` → payment amount validation and durable business state instead of browser-local state.

## Application and Validation Stack

- **appType:** Mixed server-rendered/static HTML + JavaScript UI and Spring MVC JSON APIs.
- **Canonical test command:** `.\mvnw.cmd test` from the repository root. Add all new JUnit tests to this Maven lifecycle; do not make a parallel test command the only way to run them.
- **Integration/API tier (primary):** `@SpringBootTest` with a real MySQL instance via Testcontainers when Docker is functional, exercising actual HTTP/session behavior and JDBC persistence. Keep MySQL schema/SQL coverage on this tier.
- **Integration/API tier (fallback):** existing H2 in-memory database in MySQL compatibility mode when Docker is unavailable, via the project's test configuration. Use the full Spring context and real JDBC; avoid mocked repositories for persistence assertions.
- **Browser tier (primary):** Playwright against the running application for rendered pages, login/session cookies, JavaScript-driven inventory CRUD/movements/history, and customer/staff/admin flows. Use the app's local test database; browser E2E is independent of Docker.
- **Browser tier (fallback):** Spring MVC integration tests (MockMvc or `TestRestTemplate`, according to whether the endpoint returns HTML or JSON) plus direct API-level assertions when Node.js or Playwright browser installation is unavailable. This does not prove that the rendered JavaScript UI behaves correctly.
- **Unit tier:** JUnit tests for pure validation, calculations, token hashing/expiry, permission policy, and workflow transitions. Unit coverage complements but does not replace database-backed integration tests.

## Capability Check and Fallback Matrix

The following checks were run for strategy design in this environment:

| Axis | Primary prerequisite/result | Strategy if unavailable | Known gap |
|---|---|---|---|
| Database infrastructure | Docker CLI was not found (`Get-Command docker`); Docker daemon availability cannot be established. | Use the configured H2 MySQL-mode test profile. The validation phase must recheck Docker independently and use Testcontainers/MySQL if available there. | H2 does not reproduce all MySQL locking, collation, constraint, and concurrency semantics. Run SQL/schema compatibility checks against MySQL whenever Docker is available. |
| Browser E2E | `node --version` could not run because Node.js was not found; therefore `npx playwright install --with-deps chromium` cannot be run in this environment. | Use full-context server/API integration tests; do not conflate this fallback with the Docker decision. The validation phase must recheck Node.js and attempt Playwright setup if available. | Actual browser rendering, JavaScript interactions, client-side state removal, and browser cookie behavior remain unverified. |
| Java/Maven | `.\mvnw.cmd --version` reported Maven 3.10.0 running on Java 25.0.5. Direct `java -version` resolved Java 21.0.12; Maven wrapper test execution succeeded. | Prefer the wrapper, which resolved Java 25 in this run; if that is unavailable downstream, follow the user-directed JDK 21 override and report it. | None for the baseline command; environment path and wrapper runtime differ, so record the actual Maven runtime in validation evidence. |

**Capability independence:** Docker unavailability only changes the database tier; it never justifies skipping Playwright when Node.js is available. Node.js unavailability only changes the browser tier; it never justifies skipping Docker/Testcontainers when Docker is available.

## Environment and Test Infrastructure

- **Database:** production MySQL; tests should exercise the full application with Testcontainers MySQL when possible. Fallback is the existing H2 in-memory MySQL-mode profile. Each test context must begin with deterministic tables and isolated data.
- **SMTP:** do not call a real SMTP server in automated tests. Replace the JavaMailSender at the boundary with a recording fake/mock configured by the test profile; assert invite mail recipient and acceptance URL/token delivery without exposing raw invitation tokens in persistent storage or logs.
- **HTTP/session:** exercise the actual session and interceptor/authorization behavior. Reuse an auth helper that logs in through the public login route and carries its cookie; do not directly set privileged session fields for permission acceptance tests. Keep test credentials isolated in test-only configuration.
- **Browser/API boundary:** Playwright should call the real app and use its actual UI, cookies, and `fetch` requests. API contract tests should use the running test app and assert status, content type/body, and persisted effects.
- **Test base/shared helpers:** one full-context test base should provide per-test data cleanup/reset, `JdbcTemplate`, unique-ID generation, login/session helpers, and mail fake setup. Use test fixtures with unique emails/order/material names; clean up in teardown or reset the test database per class. Avoid dependency on pre-existing records.
- **Per-feature test skeletons:** keep tests in the existing single Maven module. Add at least three runnable multi-step feature test files: inventory + stock ledger, staff invite/authz, and order/payment/wastage audit. Add one or more Playwright specs when browser prerequisites are usable.
- **Failure injection:** add integration-level failure cases at transaction boundaries (for example force an audit insert constraint failure) and assert that the corresponding catalog balance, order/payment state, or wastage record also remains unchanged. Do not rely solely on `@Transactional` test rollback to prove production service atomicity; ensure the mutation-under-test's own transaction is invoked.

## Critical Journeys and Acceptance Criteria

1. **AC-01 — Empty DB-backed setup and preserved customer records.** With a clean test database, inventory and staff APIs return empty collections (not browser fixture records); opening the inventory page loads server data. Existing DB-backed customer sign-up/custom-order retrieval still works, and existing custom orders are not replaced by a browser migration/import. Verify a write/read-back after creating a test record.
2. **AC-02 — Admin manages the catalog.** Admin creates, reads, updates, deactivates/reactivates, and filters materials by category/status/low-stock. Required category/unit/quantity/threshold constraints are enforced; invalid values return explicit 4xx responses and leave stored values unchanged. Low-stock state uses persisted quantity versus threshold.
3. **AC-03 — Inventory movements are durable and append-only.** Admin or staff performs receive, use, return, adjustment, and wastage flows. Re-read the material balance and ordered history from the database/API; each movement has the correct type, quantity effect, server-derived actor, and timestamp. History cannot be edited or deleted via public API. Reject invalid quantities and overdrafts without balance/history/audit changes.
4. **AC-04 — Authorization and session boundaries.** Anonymous API calls receive explicit 401; forbidden role calls receive explicit 403 (not page redirects). Admin-only catalog/staff/invite/official quote/payment-verification actions reject staff and customers. Staff may perform allowed stock movements, wastage, and operational order statuses, but not admin-only tasks. Customers can access only their own custom orders/payment submissions. Login rotates the session; client-supplied actor/role values do not override the authenticated identity.
5. **AC-05 — Invite onboarding and staff accounts.** Admin invites a new staff email; the configured mail boundary sends an invite; database stores a token digest and expiry rather than a reusable raw token. Test valid invite acceptance/password creation, login, and persistence; reject invalid, expired, reused, email-mismatched, or weak-password invitations with explicit errors and no account creation. Staff listing/status is server/database backed and never exposes password hashes or token digests.
6. **AC-06 — Order status and official quote audit.** Customer creates a custom order and can retrieve only their own record. Staff can transition permitted operational statuses; admin alone can draft/publish official quotes. Every accepted state change persists its audit event with server actor/time in the same transaction. Unknown orders, invalid status, or invalid quote amounts return explicit errors without partial order/audit changes.
7. **AC-07 — Payment submission and verification.** The customer submits payment proof/amount only for their own eligible quote/order; read back the persisted submission. Admin verifies/rejects payment; staff/customer cannot verify. Duplicate or invalid submissions are rejected consistently. Payment, order status, and audit writes commit or roll back together. Preserve existing customer custom-order checkout behavior and keep demo `DEMO_SUBMITTED` checkout distinct from official quote payment submission.
8. **AC-08 — Wastage updates are atomic and auditable.** An authorized staff/admin wastage submission records the wastage log, the associated inventory decrement/ledger movement, and audit event together. Verify amounts and server actor/time by read-back; if any coupled write fails, none of the coupled records/balance changes persist. Invalid material/quantity/context is rejected without side effects.
9. **AC-09 — UI state is server-backed.** In Playwright, reload inventory and staff views after mutations and confirm persisted state/history is shown. Browser storage must not be the source of staff or business-order records; a fresh browser context with empty storage still sees server state. Verify role-specific controls are hidden as a usability aid and direct forbidden API requests are still rejected by the server.

## Minimum Test Coverage by File/Flow

The validation phase should add at least three runnable, multi-step flows (setup → action → read-back/assertion → cleanup), each with an unauthenticated/forbidden check and persistence assertions as relevant:

| Suggested suite | Core coverage |
|---|---|
| `InventoryApiIntegrationTests` | Admin catalog CRUD; staff receipt/use/return/adjustment; movement history, low-stock calculation, append-only behavior, invalid/overdraft handling, forbidden customer/anonymous requests, transaction rollback. |
| `StaffInviteIntegrationTests` | Admin invite and mocked mail; token hash/expiry/single-use; acceptance/password persistence; staff login; admin-only API denial; no credential/token leakage from API responses. |
| `OrderPaymentWastageIntegrationTests` | Customer order ownership; staff operational status/admin quote and payment verification; customer payment submission; wastage stock reduction; audit actor/time and rollback on a coupled-write failure. |
| Playwright `inventory-staff.spec.ts` (when available) | Login as admin/staff, create and adjust catalog stock, reload and inspect movement history, verify staff cannot open/use admin-only functions, and confirm fresh browser storage still reads server data. |
| Playwright `customer-order-payment.spec.ts` (when available) | Customer submits an order/payment, another customer is denied access, staff/admin actions respect roles, and the UI displays durable state after reload. |

Keep test suites independent by seeding unique data and cleaning up. Include invalid/missing session checks in each feature group rather than testing only happy paths. The implementation task owning each mutation must run its module-level Maven tests and report pass/fail/skip counts; failures mean that implementation task is not complete.

## Legacy Test Assets and Baseline

- **legacyTestAssets:** discovered 5 JUnit test files: `CustomOrderControllerTests.java`, `DashboardControllerTests.java`, `DemoAuthControllerTests.java`, `StylemariaApplicationTests.java`, and `WastageControllerTests.java`.
- **Classification:** existing JUnit/controller/application tests; no Selenium, Cypress, Playwright, or browser E2E suite was found.
- **source:** discovered.
- **migrationDecision:** N/A for browser-test migration; retain and extend the existing Maven test suite. New browser journeys are additions, not rewrites of legacy UI tests.
- **Baseline command:** `.\mvnw.cmd test`.
- **Baseline result:** exit code 0; 45 passed, 0 failed, 0 skipped across 5 Surefire suites.

## Validation Review Expectations

Final validation/conformance review must confirm:

1. The exact Maven command and exit code, test counts, test runtime, Java/Maven versions, and Surefire evidence are recorded; all new tests are included by the canonical Maven command.
2. Docker/Testcontainers and Node/Playwright were probed independently in the validation environment. Any fallback includes the exact attempted command/error, the affected capability axis only, and the matching gap.
3. The critical journeys above have runnable evidence, including negative authorization checks, read-after-write persistence, transaction atomicity, and the preserved custom-order boundary.
4. At least one real database path has been exercised; if only H2 is available, the reduced MySQL fidelity is explicit and MySQL compatibility of schema/SQL is reviewed.
5. If browser prerequisites are available, Playwright runs against the real application and verifies both browser behavior and server enforcement. If not, report browser rendering/client-state validation as unverified, not passed.
6. SMTP is mocked and no secret values/raw invitation tokens appear in test logs or evidence.

## Test Results

- Command: `.\mvnw.cmd test`
- Passed: 45
- Failed: 0
- Skipped: 0
- Exit code: 0
- Evidence: `target/surefire-reports/TEST-*.xml` (five suite reports).
