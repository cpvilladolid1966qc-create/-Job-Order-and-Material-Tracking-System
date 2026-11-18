## [t10] Validating server-backed inventory and staff workflows
- Codebase/domain discoveries: the agreed canonical command is `.\mvnw.cmd test`; H2 is configured in test resources and supports the new full-context MockMvc flows. The test-source layout already contained backend API and runtime suites, so three focused tests were added in the same JUnit/Maven lifecycle.
- Wrong assumptions and corrections: the `MockitoBean` annotation is provided by `org.springframework.test.context.bean.override.mockito`, not a Boot package. The first standalone startup attempt still used the default MySQL URL despite activating the test profile; explicit process-only H2 URL, username, and driver settings made startup succeed.
- Debugging dead-ends and what actually worked: Docker, Node, and npx were absent. H2 integration plus a real HTTP smoke flow verified API auth and read-after-write persistence; Playwright was not installable. After the HTTP probes, stopping the concrete Spring Boot PID and confirming the port closed avoided leaving a test server running.
- Techniques/patterns worth reusing: use full-context MockMvc login through `/login`, assert unauthenticated 401 and staff/admin 403 boundaries, mock SMTP for invite acceptance, query back persisted balances/audits, and clean fixture tables before and after each flow.
- Learnings consumed: [(none)]

## [t10] Revalidating catalog-backed wastage regression
- Codebase/domain discoveries: `wastage.html` now starts with a loading placeholder; `store.js` requests catalog records, uses catalog ID and canonical unit in the payload, and disables fractions for `EA`. The integration flow accepts 1.125 M wastage and verifies persisted stock at 8.875 after receiving 10.
- Wrong assumptions and corrections: the repository snapshot has no `.git` metadata, so `git status`/diff cannot establish a changed-lines view; inspect the reported files and test coverage directly, and state this evidence limitation.
- Debugging dead-ends and what actually worked: targeted Maven tests and the full suite both ran successfully on H2; parsing Surefire XML reports supplied aggregate counts without relying on truncated command output.
- Techniques/patterns worth reusing: combine focused source invariant checks with an HTTP/JDBC regression test, but do not describe source-pattern checks as browser execution. Note that `legacyMaterialUnitCosts` remains a static estimate map separate from catalog identity/unit/stock.
- Learnings consumed: [tester/spring-mockito-bean-import]
