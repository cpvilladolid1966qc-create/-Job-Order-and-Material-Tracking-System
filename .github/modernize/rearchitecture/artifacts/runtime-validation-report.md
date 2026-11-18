# Runtime Validation Report

**Generated**: 2026-11-04T15:56Z  
**Target**: `C:\Users\Ck Villadolid\Downloads\stylemaria-20261026T182031Z-1-001\stylemaria`

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | n/a after intentional shutdown | Spring Boot 4.1.1 started on port 18083 against H2 MySQL mode in 2.656s; `/` returned 200 and anonymous inventory returned 401. |
| Integration tests | PASS | 0 | `.\mvnw.cmd test`: 64 passed, 0 failed, 0 errors, 0 skipped across 11 Surefire suites. |
| E2E tests | PARTIAL | 0 for live HTTP smoke | Live HTTP tested page availability, anonymous denial, admin login, catalog/material creation, receipt, and persisted read-back. Browser E2E not run: Node/npx unavailable. |

**Overall**: NEEDS_SIGNOFF — approved H2 and MockMvc/API fallbacks passed, but browser UI behavior and production MySQL fidelity could not be verified in this environment.

## Environment and Capability Verdict

```text
environment:
  docker: UNAVAILABLE — Get-Command docker -ErrorAction SilentlyContinue found no executable; output: "docker: command not found"
  node: UNAVAILABLE — Get-Command node found no executable; output: "node: command not found"
  playwright: UNAVAILABLE — `npx playwright install --with-deps chromium` exited 1: "`npx` is not recognized as the name of a cmdlet, function, script file, or operable program."
  infra-tier: FALLBACK(embedded/in-memory) — no Docker CLI; the approved H2 in-memory database in MySQL mode was used.
  browser-tier: FALLBACK(MockMvc) — Node and npx are absent; three full-context MockMvc feature-flow tests plus existing HTTP/session tests and a live HTTP API smoke flow ran instead.
startup: PASS — `.\mvnw.cmd -B '-Dspring-boot.run.useTestClasspath=true' '-Dspring-boot.run.profiles=test' '-Dspring-boot.run.arguments=--server.port=18083' spring-boot:run`; H2 configured through process environment; Tomcat readiness log, 2.656s; `/` 200 and anonymous inventory API 401. The app process was stopped after probes and port 18083 confirmed closed.
integration: PASS — exit_code: 0, passed: 64, failed: 0, skipped: 0; all 11 Surefire suites; full-context H2/JDBC, HTTP/session auth, inventory, invite acceptance, order/payment/wastage, custom-order quote/payment, audit rollback; gap: no real MySQL/Testcontainers.
e2e: PARTIAL — exit_code: 0 for live HTTP smoke; public page, unauthenticated denial, login, create category/material, stock receipt, and quantity read-back verified; boundaries exercised: session auth, server persistence, ledger actor; gaps: no browser rendering, JS interactions, browser storage, or browser-cookie validation.
overall: NEEDS_SIGNOFF — integration and live API smoke pass; browser and MySQL capability gaps were escalated for acceptance.
```

### Capability attempts and gaps

- **Database axis:** the independent Docker probe found no executable (`Get-Command docker`); therefore `docker info` could not run. H2 MySQL-mode full-context tests passed. MySQL engine, DDL behavior, row-lock/concurrency behavior, and production collation are not proven by H2.
- **Browser axis:** `Get-Command node` and `Get-Command npx` found neither executable. The explicit `npx playwright install --with-deps chromium` attempt failed because `npx` is not recognized. This independently activates the approved MockMvc fallback; it does not depend on Docker availability. Real browser DOM/rendering, JavaScript interactions, client storage, and cookie behavior remain unverified.
- **SMTP:** invite acceptance tests replaced `JavaMailSender` with a mock and inspected the outgoing message/token hash; delivery to a live SMTP service remains unverified as required by the strategy.
- **Toolchain:** `.\mvnw.cmd --version` reported Maven 3.10.0 on Java 25.0.5 (Eclipse Adoptium, `.jdk/jdk-25`). Bare `java -version` resolves Java 21.0.12. Java 25 was available through the wrapper, so no JDK 21 override was required.
- **Startup setup correction:** the first launch attempt used the test profile without an explicit datasource override and failed against default MySQL (`Access denied for user 'root'@'localhost' (using password: NO)`, exit 1). The retry supplied an ephemeral H2 URL/driver and temporary bootstrap-admin environment values; the app then started successfully. No source/config file was changed for this correction.
- **Shutdown:** after successful probes the specific Spring Boot PID was stopped; process and port 18083 were confirmed closed. Maven reported exit `-1` after the intentional child-process termination; this does not change the observed startup or HTTP readiness result.

## Test Execution

### Canonical Maven suite

- Exact command: `.\mvnw.cmd test`
- Exit code: 0
- Passed: 64
- Failed: 0
- Errors: 0
- Skipped: 0
- Surefire evidence: `target/surefire-reports/TEST-*.xml`
- Suites: `BackendApiRuntimeTests` (4), `BackendApiTests` (5), `CustomOrderControllerTests` (23), `DashboardControllerTests` (2), `DemoAuthControllerTests` (21), `InventoryFlowIntegrationTests` (1), `OrderPaymentWastageFlowIntegrationTests` (1), `PersistenceSchemaTests` (3), `StaffInvitationFlowIntegrationTests` (1), `StylemariaApplicationTests` (1), `WastageControllerTests` (2).
- The pre-addition baseline also passed: `.\mvnw.cmd test`, exit 0, 61 passed, 0 failed, 0 skipped. The three added suites raise the final count to 64.
- A first targeted compile attempt exited 1 due to an incorrect test-only `MockitoBean` import. The import was corrected to `org.springframework.test.context.bean.override.mockito.MockitoBean`; the targeted suites then passed, and the final full canonical suite passed with the correction and cleanup hooks included.

### New runnable validation suites

1. `InventoryFlowIntegrationTests` — anonymous API denial; login; catalog/category/material creation; zero opening quantity and low-stock status; receipt and usage; movement actor and balance; read-after-write quantity; inactive filtering; movement rows remain.
2. `StaffInvitationFlowIntegrationTests` — unauthenticated directory denial; admin email-invite request through mocked mail; API response excludes token; stored invitation value equals SHA-256 token hash and not raw token; acceptance creates persistent staff; staff login permits inventory but denies admin directory; admin can read accepted account.
3. `OrderPaymentWastageFlowIntegrationTests` — anonymous order denial; receive stock; submit wastage and confirm actor comes from session rather than client; stock and linked wastage movement persist; create/update operational order; record payment; verify read-back and same-actor audit events.

New test data is cleared before and after each suite flow. Existing `BackendApiRuntimeTests` also cover auth/role separation, quote-based customer payment submission vs admin verification, wastage/order APIs, and audit-failure rollback. SMTP is mocked; no raw invite token was written to persistent storage or the report.

## Live HTTP Smoke Evidence

Against the independently started test-profile application:

- `GET /` → 200.
- Anonymous `GET /api/v1/inventory/materials` → 401.
- Form login followed by authenticated catalog/material creation succeeded.
- Authenticated `RECEIPT` movement returned balance `4.000` and actor `validation-admin@example.com`.
- Subsequent authenticated material listing returned quantity `4.000`.
- The app was stopped after the probes; the process and port were confirmed closed.

This is HTTP/API evidence, not browser E2E evidence.

## Legacy Test Migration

| Decision | Source | Details |
|---|---|---|
| N/A — retain and extend | Discovered JUnit/MockMvc suite | No Selenium, Cypress, or existing Playwright browser suite was present. Existing JUnit tests remain in the canonical Maven lifecycle; three new HTTP integration-flow test classes were added. |

## Issues Found

| # | Severity | Description | Escalated To |
|---|---|---|---|
| 1 | WARNING / validation gap | Docker/Testcontainers unavailable; H2 fallback does not prove live MySQL locking, schema, or collation behavior. | Coordinator |
| 2 | WARNING / validation gap | Node/npx unavailable; Playwright could not install or run. Browser rendering and client-side interactions remain unverified. | Coordinator, PM |
| 3 | LOW (upstream finding) | t8 reports unmatched `/api/**` paths are not denied by default; no currently tested protected route bypass was observed. | Recorded from t8; backend hardening/sign-off remains separate from this runtime test |

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t3-teamlead.md` — the validation strategy and critical journey contracts.
- `.github/modernize/rearchitecture/artifacts/t8-security.md` — permission/audit evidence and existing low hardening finding.
- `.github/modernize/rearchitecture/artifacts/t9-architect.md` — previous full build/startup evidence.
- `.github/modernize/rearchitecture/artifacts/t5-backend.md` — API and role behavior contracts.
- `.github/modernize/rearchitecture/artifacts/t6-frontend.md` — expected frontend-server behavior and browser validation limits.

## Evidence Mapping

- `t3-teamlead.md#Critical Journeys` and `#Suggested suite` → the new integration files and 64-test result demonstrate persisted inventory and movement history, invitation/role flow, order/payment/wastage, invalid-session behavior, and transaction/audit checks.
- `t3-teamlead.md#Capability Check and Fallback Matrix` → environment checks independently selected H2 for missing Docker and MockMvc/live HTTP for missing Node; each axis’s gap is recorded above.
- `t3-teamlead.md#Validation Review Expectations` → Surefire counts, runtime/version evidence, flow data, temporary SMTP mock, and per-axis gaps are recorded in this report.
- `t8-security.md#Verified Controls / Non-findings` → unauthenticated 401, staff/admin denial, session-derived actor, invitation acceptance, and transactional audit behavior were exercised by tests; the existing unmatched-route LOW is retained as a separate finding.
- `t9-architect.md#Startup Evidence` → this run independently confirmed H2 startup and HTTP readiness; this report records actual local toolchain and environment.
- `t5-backend.md#Behavior and Security Boundaries` → the tests exercise the documented inventory, staff, operational order/payment, wastage, and customer custom-order role boundaries.
- `t6-frontend.md#Behavior Boundaries` → backend API operations work through real HTTP; browser-side interface behavior and storage removal are not independently validated here.
