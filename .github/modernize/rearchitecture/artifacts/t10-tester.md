# t10 — Runtime Validation

## Summary

The approved full-context H2/MySQL-mode integration fallback passed the full Maven suite: 64 tests, 0 failures, 0 errors, 0 skips. Three new runnable multi-step HTTP integration suites cover inventory, staff invitation, and order/payment/wastage flows. A separately started H2 application returned the public page, rejected an unauthenticated inventory request, and persisted an authenticated material receipt across a read-back.

The validation verdict is **NEEDS_SIGNOFF** because Docker/Testcontainers and Node/npx/Playwright are unavailable: the approved fallbacks were used, but production MySQL fidelity and browser rendering/client interactions remain unverified. Coordinator and PM notifications were sent with the specific blockers and gaps.

## Deliverables

- [runtime-validation-report.md](./runtime-validation-report.md) — environment evidence, test commands/counts, journey coverage, capability gaps, verdict, and issues.
- `src/test/java/com/job_order_and_material_tracking_system/stylemaria/InventoryFlowIntegrationTests.java` — catalog, authentication, stock ledger, active status, and persisted read-back flow.
- `src/test/java/com/job_order_and_material_tracking_system/stylemaria/StaffInvitationFlowIntegrationTests.java` — mocked email invitation, token-hash persistence, acceptance, staff login, and role denial flow.
- `src/test/java/com/job_order_and_material_tracking_system/stylemaria/OrderPaymentWastageFlowIntegrationTests.java` — linked stock/wastage, operational order status/payment persistence, and actor/audit flow.

## Test Results

- Command: `.\mvnw.cmd test`
- Passed: 64
- Failed: 0
- Errors: 0
- Skipped: 0
- Exit code: 0
- Failure details: none. Surefire recorded 11 test suites, including the three added suites; each added suite passed its multi-step flow.
- Toolchain: Maven wrapper 3.10.0 ran on Java 25.0.5. Bare `java` resolves Java 21.0.12; the requested JDK 21 override was unnecessary because the wrapper selected Java 25.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t3-teamlead.md` — approved critical journeys, Maven canonical command, H2 and MockMvc fallback matrix, and evidence expectations.
- `.github/modernize/rearchitecture/artifacts/t8-security.md` — permission boundaries, audit coupling, and the known low-severity unmatched-route hardening finding.
- `.github/modernize/rearchitecture/artifacts/t9-architect.md` — prior full build/startup evidence and H2 smoke configuration.
- `.github/modernize/rearchitecture/artifacts/t5-backend.md` — implemented endpoint/role contracts used to design runtime flow assertions.
- `.github/modernize/rearchitecture/artifacts/t6-frontend.md` — expected server-backed UI behavior and frontend validation boundary.

## Evidence Mapping

- `t3-teamlead.md#Application and Validation Stack` and `#Capability Check and Fallback Matrix` → H2/MySQL-mode full-context HTTP tests were used only after Docker was found unavailable; browser fallback is explicitly limited to MockMvc/live HTTP and does not claim browser coverage.
- `t3-teamlead.md#Suggested suite` and `#Validation Review Expectations` → three new flow suites plus existing runtime tests cover inventory, staff invite/auth, order/payment/wastage, authorization denial, persistence/read-back, audit behavior, and custom-order quote/payment behavior.
- `t8-security.md#Verified Controls / Non-findings` → runtime tests exercised unauthenticated denial, staff/admin separation, session-based staff acceptance, and actor attribution; the test report retains the reviewer’s known low finding.
- `t9-architect.md#Startup Evidence` → an independent H2 application startup and HTTP probe was rerun and cleaned up after validation.
- `t5-backend.md#Behavior and Security Boundaries` and `t6-frontend.md#Behavior Boundaries` → flow tests used the published server API and role limits; actual JavaScript UI behavior remains unverified without a browser runtime.

## Current Behavior Boundaries

- No application production source was changed by this tester; only test files, this validation report, tester log, and a tester learning were added.
- The new tests use temporary test-only admin/staff credentials and mock the mail sender; no production credential, raw invite token, live SMTP delivery, payment processor, or live MySQL service was used.
- Existing customer custom-order quote/payment verification and audit-failure rollback remain exercised by `BackendApiRuntimeTests` in the canonical suite.
- Static frontend localStorage-removal evidence is documented by t6; this task did not independently verify UI rendering, browser storage, or cookie behavior.

## Post-t10.1 Regression Revalidation

The canonical suite and the focused order/payment/wastage integration suite were rerun after the catalog-backed wastage update. The test profile used H2 in MySQL mode. No test or production source changes were made during this revalidation.

### Test Results

| Command/check | Exit code | Passed | Failed/errors | Skipped | Evidence |
|---|---:|---:|---:|---:|---|
| `.\mvnw.cmd -Dtest=OrderPaymentWastageFlowIntegrationTests test` | 0 | 1 | 0 | 0 | Focused `OrderPaymentWastageFlowIntegrationTests` Surefire report |
| `.\mvnw.cmd test` | 0 | 64 | 0 | 0 | 11 Surefire XML reports under `target/surefire-reports/` |
| Focused frontend/source regression assertions | 0 | 6 | 0 | n/a | Checked catalog-only material options, active-catalog request, selected ID/canonical unit payload, EA whole-number control, inventory Wastage link, and integration-test assertions |

The focused HTTP flow verified that an unauthenticated catalog request is denied; an authenticated user receives the active material and not the inactive one; a wastage submission with the catalog `materialId`, canonical unit `M`, and quantity `1.125` is accepted; the server attributes the actor from the session; and persisted stock reads back as `8.875` after receiving 10 and recording wastage of 1.125. The complete suite also passed the custom-order, inventory, staff invitation, and backend runtime suites.

### Source and Test Inspection

- The available working tree has no `.git` metadata (`git -C stylemaria status --short` returned “not a git repository”), so a repository diff could not be produced. Reviewed the current source and test files identified by `t10.1-frontend.md` directly instead.
- `wastage.html` now has a loading-only material option and a select named `materialId`; it does not contain fixed material choices.
- `store.js` requests `/api/v1/inventory/materials`, builds choices from returned catalog IDs/names/unit codes, sends the selected ID and canonical unit, and resets/disables fractional quantity for `EA`. The fractional selector remains available for non-count units.
- `inventory.html` links Wastage to `/wastage.html`.
- `OrderPaymentWastageFlowIntegrationTests` covers active/inactive catalog filtering and fractional metric-unit stock deduction/read-back, but does not execute the browser JavaScript itself.

### Changed Behavior Boundaries and Remaining Gaps

- The changed behavior is limited to material selection, unit/quantity handling, submission identity, and navigation in the wastage UI. Existing server-side role checks, audit coupling, and database workflows were not changed by this tester; the current test uses an authenticated admin plus an unauthenticated denial check.
- A fixed `legacyMaterialUnitCosts` map remains in `store.js` for the financial-impact estimate only; it is no longer the source of material choices or units. Catalog materials not present in that map may show a zero/stale estimated material cost. This is not a stock quantity or ledger value.
- The prior environment report records Docker/Testcontainers and Node/npx/Playwright as unavailable. Per task instructions they were not reattempted. Live MySQL was not used. H2-backed integration behavior passed, but MySQL-specific locking/schema semantics remain unverified.
- Browser rendering, JavaScript execution, staff click-through/navigation, fractional-control interaction, browser storage, and real session-cookie behavior remain unverified. The static assertions and MockMvc tests do not close this browser gap.
- SMTP delivery remains unverified; the existing automated invite test uses a mock.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t3-teamlead.md` — approved critical journeys, test command, H2/browser fallback matrix, and evidence expectations.
- `.github/modernize/rearchitecture/artifacts/t8-security.md` — reviewed server role boundaries, audit coupling, and the known low-severity default-deny gap.
- `.github/modernize/rearchitecture/artifacts/t9-architect.md` — previous H2 startup and API smoke evidence.
- `.github/modernize/rearchitecture/artifacts/t10.1-frontend.md` — claimed wastage catalog fix and its intended regression coverage.

## Evidence Mapping

- `t3-teamlead.md#AC-03` and `#AC-08` → focused integration evidence verifies active material selection at the API boundary, canonical unit and ID submission, fractional stock reduction, and persisted balance.
- `t8-security.md#Verified Controls / Non-findings` → unauthenticated catalog denial and server-derived actor are reconfirmed; UI role behavior remains outside this non-browser run.
- `t9-architect.md#Startup Evidence` → prior H2 application smoke evidence provides context; this rerun independently exercised the full Spring test context, but did not repeat a standalone server startup.
- `t10.1-frontend.md#Summary` and `#Test Results` → current HTML/JS/navigation were inspected and six focused static checks passed; the API integration flow and complete 64-test suite passed after the change.

## Validation Verdict

```text
environment:
  docker: UNAVAILABLE — prior documented probe found no Docker executable; not reattempted per task instruction
  node: UNAVAILABLE — prior documented probe found no Node.js; not reattempted per task instruction
  playwright: UNAVAILABLE — prior documented npx invocation could not resolve; not reattempted per task instruction
  infra-tier: FALLBACK(embedded/in-memory) — H2 MySQL mode for Maven integration tests; live MySQL not attempted
  browser-tier: FALLBACK(MockMvc) — focused source assertions plus full-context integration tests; no browser execution
startup: PASS — full Spring context started in the test profile within both Maven test runs; standalone HTTP startup was not repeated
integration: PASS — exit_code: 0, passed: 64, failed: 0, skipped: 0; focused wastage case: exit_code: 0, passed: 1, failed: 0, skipped: 0; H2/JDBC, auth denial, catalog filtering, wastage write and stock read-back; MySQL fidelity gap remains
e2e: UNVERIFIED — exit_code: n/a (no browser run); six static source checks exited 0 and the focused MockMvc persistence flow passed, but browser-rendered behavior was not exercised
overall: NEEDS_SIGNOFF — regression and integration checks pass, but browser UI behavior and live MySQL compatibility remain unverified as previously documented
```
