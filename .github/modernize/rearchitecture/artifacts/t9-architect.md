# t9 — Full Build and Startup Smoke Test

## Summary

The full Maven `clean verify` build passed, and the Spring Boot application started with the test classpath and an isolated in-memory H2 database. The application bound to port `18081`; the homepage returned HTTP 200 and an unauthenticated inventory request returned HTTP 401, consistent with the server-side authorization boundary.

## Smoke Test Verdict
- install_command: `n/a for non-JS/TS`
- install_returncode: n/a
- build_command: `.\mvnw.cmd -B clean verify`
- returncode: 0
- covers_all_modules: yes
- startup_http_status: 200 (`/`; unauthenticated `/api/v1/inventory/materials` returned 401)
- test_script_present: n/a
- test_returncode: n/a

## Test Results
- Command: `.\mvnw.cmd -B clean verify`
- Passed: 61
- Failed: 0
- Skipped: 0
- Failure details: none; Maven reported `BUILD SUCCESS`. No plugin/build failures were reported.
- Build duration: 30.586 seconds.

## Startup Evidence
- Command: `.\mvnw.cmd -B '-Dspring-boot.run.useTestClasspath=true' spring-boot:run`
- Runtime settings: ephemeral H2 database in MySQL mode, test classpath enabled for the test-scoped H2 driver, port `18081`, and temporary externally supplied bootstrap-admin credentials. No credentials were recorded.
- Readiness signal: Spring Boot logged Tomcat started on port `18081` and application started in 3.732 seconds.
- HTTP checks: `GET /` → 200; unauthenticated `GET /api/v1/inventory/materials` → 401.
- Cleanup: the app process was stopped after probing; its process and listening port were confirmed closed.
- Toolchain: the Maven wrapper resolved Java 25.0.5 (Eclipse Adoptium, `.jdk/jdk-25`). Bare `java` resolves Java 21.0.12. A JDK 21 override was not needed for this run.
- Scope boundary: no live MySQL compatibility, SMTP delivery, or payment-provider behavior was exercised. The smoke run made no production-source/configuration edits.
- Command note: an initial startup invocation without quoting the dotted `-Dspring-boot.run.useTestClasspath` option was parsed by Windows PowerShell/Maven as an invalid lifecycle phase. Quoting the property argument fixed the invocation; the application then started normally.

## Upstream Artifacts Consumed
- `.github/modernize/rearchitecture/artifacts/t4-dba.md` — schema shape, H2 MySQL-mode expectation, and prior schema test evidence.
- `.github/modernize/rearchitecture/artifacts/t5-backend.md` — backend workflow, role boundary, and prior API/runtime test evidence.
- `.github/modernize/rearchitecture/artifacts/t6-frontend.md` — server-backed UI behavior and prior integrated browser smoke evidence.
- `.github/modernize/rearchitecture/artifacts/t7-devops.md` — post-remediation full-build result and dependency-change context.

## Evidence Mapping
- `t4-dba.md#Compatibility and Migration Risks` → application startup against isolated H2 MySQL mode completed; this does not replace live MySQL/version verification.
- `t5-backend.md#Behavior and Security Boundaries` → unauthenticated inventory API returned 401 during the running application smoke check.
- `t6-frontend.md#Summary` → static homepage remained available at HTTP 200 after backend and frontend changes.
- `t7-devops.md#Test Results` → independent full `clean verify` rerun succeeded with 61 passed, 0 failed, and 0 skipped.

## Findings and Boundaries
- Findings: none.
- The protected API probe confirmed an unauthenticated response only; authenticated role-specific API behavior is covered by the backend/runtime suite and is not re-exercised by this architect smoke test.
- A production MySQL server and SMTP service were not used.
