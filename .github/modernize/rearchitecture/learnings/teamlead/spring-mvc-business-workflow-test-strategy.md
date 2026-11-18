# Spring MVC Business Workflow Test Strategy

For this single-module Spring MVC/JDBC application, keep database integration and browser E2E as independent validation tiers, with persistence, role boundaries, and transaction atomicity proven by read-back.

## What Happened

For Style Maria task t3, source artifacts and the test baseline showed a mixed static-HTML/JavaScript plus Spring MVC application, five existing JUnit suites, no browser automation suite, and coupled inventory/order/wastage mutations requiring transaction evidence. Docker and Node.js were absent in the design environment, while the Maven wrapper ran successfully on Java 25.0.5.

## Takeaway

- Use full-context Spring/JDBC tests with MySQL Testcontainers when Docker is available; H2 MySQL mode is a fallback with an explicit fidelity gap.
- Keep Playwright as the browser primary when Node.js is available; lack of Docker does not justify omitting it.
- Test workflow persistence with write/read-back, authenticated actor, and rollback of all coupled writes; do not treat UI-only or unit-only assertions as transaction evidence.
- Record Maven wrapper's runtime separately from `java -version` if they resolve different JDKs.

## History
- 2026-11-04 (stylemaria/t3): initial
