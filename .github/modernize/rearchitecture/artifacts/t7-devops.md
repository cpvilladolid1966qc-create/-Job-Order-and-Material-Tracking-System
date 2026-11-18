# t7 — Maven Dependency CVE Remediation

## Summary

The initial GitHub Security Advisories scan found 11 known vulnerabilities in Jackson 3.1.5 and embedded Tomcat 11.0.24. The Maven dependency BOM overrides were advanced to Jackson 3.1.8 and Tomcat 11.0.27 without changing the Spring Boot version. A full corrected rescan returned no findings; `clean verify` passed all 61 tests.

## Deliverables

- [cve-fix-summary.md](./cve-fix-summary.md) — remediation rationale, affected advisories, validation, and environment details.
- [cve-report-1.json](./cve-report-1.json) — initial CVE scan.
- [cve-report-2.json](./cve-report-2.json) — full dependency rescan after remediation.
- [final-cve-report.json](./final-cve-report.json) — final full dependency scan; empty findings list.
- `pom.xml` — Jackson and Tomcat BOM version overrides.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t5-backend.md` — reviewed backend dependency/runtime context and preserved API behavior while limiting changes to dependency management.
- `.github/modernize/rearchitecture/artifacts/t6-frontend.md` — reviewed frontend validation context; no frontend files were changed.

## Evidence Mapping

- `t5-backend.md#Test Results` → the existing Maven test suite was rerun after the dependency patch; `clean verify` passed 61 tests.
- `t5-backend.md#Behavior and Security Boundaries` and `t6-frontend.md#Behavior Boundaries` → dependency-only changes retained application behavior and did not alter backend authorization or frontend workflow code.
- `cve-report-1.json` → 11 initial advisories across Jackson databind/core and Tomcat.
- `final-cve-report.json` → full resolved dependency rescan reports zero findings.

## Test Results

- Command: `.\mvnw.cmd clean verify`
- Passed: 61
- Failed: 0
- Errors: 0
- Skipped: 0
- Failure details: none; Maven reported `BUILD SUCCESS`.
- Runtime: Maven wrapper used Java 25.0.5. The ordinary `java` command resolves Java 21.0.12, but a JDK 21 override was not needed because the wrapper had Java 25 configured.

## Boundaries

- This task changes dependency management only. No application behavior, schema, deployment, or cloud migration changes were made.
- The task workspace is not a Git repository, so Git status/diff could not be used; only the POM was intentionally edited.
