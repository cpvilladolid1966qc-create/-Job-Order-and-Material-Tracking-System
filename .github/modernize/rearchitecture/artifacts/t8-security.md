# t8 — Security Review: Authentication, Authorization, Invitations, and Sensitive APIs

## Summary

The reviewed snapshot has no confirmed exploitable authentication, authorization, invitation, or audit-integrity vulnerability in the identified sensitive routes. Session authentication, role gates, customer ownership checks, invitation token handling, password hashing, SQL parameterization, and transaction/audit coupling were evidenced in source. One low-severity hardening gap remains: unmatched routes are not denied by default.

## Findings

### LOW — Unmatched application routes do not default-deny

- **Evidence:** `src/main/java/com/job_order_and_material_tracking_system/stylemaria/StylemariaApplication.java:863-900` applies role requirements to recognized path patterns but leaves unmatched paths without a required role. The reviewed sensitive route families are explicitly gated (`:900-918`), and no currently mapped sensitive endpoint was found falling through those gates.
- **Risk:** A future sensitive API added outside the existing patterns may be reachable without authentication unless its author also updates the interceptor matcher. This is a defense-in-depth / route-registration risk, not a confirmed exploitable endpoint in the reviewed snapshot.
- **Recommended owner:** backend.
- **Recommendation:** Default-deny `/api/**` and explicitly allowlist public authentication, invitation acceptance, and intentionally public customer operations; add a route-coverage test for new API mappings.

## Verified Controls / Non-findings

- **Session and staff status:** Login invalidates an existing session before establishing the authenticated session; staff activity is checked at login and again on protected requests. Evidence: `StylemariaApplication.java:143-160,809-840`.
- **Role boundaries:** The interceptor assigns ADMIN-only, STAFF/ADMIN, and USER-only requirements to reviewed sensitive route families. Catalog changes and staff administration are ADMIN-only; inventory movements, wastage, and operational order handling allow STAFF/ADMIN. Evidence: `StylemariaApplication.java:863-918`. Official quote/payment-review routes are separated from operational POS payment submissions.
- **Customer ownership:** Custom-order reads and image retrieval constrain lookup using both the session customer ID and requested order ID. Evidence: `StylemariaApplication.java:948-1090`.
- **Invitation/password lifecycle:** Invite tokens use 32 random bytes, only SHA-256 token hashes are stored, and invitations expire after 24 hours. Acceptance checks expiry and used/revoked state under a row lock; account creation and invite consumption are transactional. Passwords use PBKDF2-HMAC-SHA256 with 210,000 iterations and constant-time comparison. Evidence: `ApiSupport.java:41-99`; `StaffApi.java:187-274`.
- **Input/SQL handling:** Reviewed SQL values are parameterized; dynamic update assignments are composed from fixed column names. Evidence: `ApiSupport.java:110-125`; `InventoryApi.java:80-170,292-350`.
- **Audit coupling:** Reviewed inventory, staff, order/payment, wastage, and custom-order mutation paths write audit records within their transaction. Evidence: `InventoryApi.java:80-170,292-350`; `BusinessOrderApi.java:57-195`; `StylemariaApplication.java:1244-1380,1554-1630`.
- **Secrets/dependencies:** No literal password was found in the inspected configuration; password settings are environment-backed with empty defaults. The supplied final dependency report is an empty findings array. The CVE scan was not independently rerun. Evidence: `pom.xml:31-32`; `.github/modernize/rearchitecture/artifacts/final-cve-report.json`.

## Review Limits

- The working directory has no Git metadata, so this is a review of the available source snapshot; changed lines could not be isolated.
- No build or test suite was run for this read-only audit. The upstream artifacts report `.\mvnw.cmd clean verify` passed 61 tests; that result was not independently reproduced here.
- Production HTTPS/proxy and session-cookie settings, live MySQL/SMTP configuration, and browser/runtime behavior were not exercised.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t6-frontend.md` — frontend API boundaries and reliance on server authorization.
- `.github/modernize/rearchitecture/artifacts/t7-devops.md` — dependency remediation summary and final scan references.
- `.github/modernize/rearchitecture/artifacts/cve-fix-summary.md` — scan methodology, patched dependency versions, and test evidence.
- `.github/modernize/rearchitecture/artifacts/final-cve-report.json` — final zero-finding dependency scan result.

## Evidence Mapping

- `t6-frontend.md#Behavior Boundaries` → this review verified that server authorization remains authoritative for reviewed route groups and documented the unmatched-route hardening gap.
- `t7-devops.md#Summary` and `cve-fix-summary.md#Scan Method and Results` → dependency remediation was reviewed; `final-cve-report.json` contains no findings, but the scan was not repeated.
- `t6-frontend.md#Test Results` and `cve-fix-summary.md#Test Results` → prior 61-test success is recorded as upstream evidence only, not as a test run performed by this audit.
