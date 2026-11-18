## [t3] Define critical acceptance coverage and test strategy
- Codebase/domain discoveries: one Maven/Spring MVC/JDBC module; existing tests are five JUnit suites and no browser E2E suite exists. Customer custom-order/checkout behavior is DB-backed and must remain distinct from the demo checkout; inventory/staff/order dashboard behavior currently includes browser-local state.
- Wrong assumptions and corrections: the shell `java -version` resolves JDK 21.0.12, but the Maven wrapper's own `--version` output and successful test run use Java 25.0.5; record the actual wrapper runtime rather than assuming PATH Java is the one Maven uses.
- Debugging dead-ends and what actually worked: Docker and Node.js are not present, so strategy uses H2 MySQL mode and documents MockMvc/API fallback independently; baseline evidence came from `.\mvnw.cmd test` and Surefire XML reports.
- Techniques/patterns worth reusing for future tasks: separate Docker infrastructure and browser E2E capability axes; verify real DB persistence, authenticated server actor, append-only ledger, and coupled-write rollback in acceptance tests.
- Learnings consumed: [architect/spring-jdbc-feature-boundaries]
