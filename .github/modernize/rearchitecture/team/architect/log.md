## [t1] Mapped Style Maria implementation boundaries
- Codebase/domain discoveries: one Spring Boot Java source file contains all controllers, session auth, role interceptor, JDBC SQL, and schema runners; inventory has no persisted catalog; wastage is DB-backed; staff/POS/dashboard order state uses browser storage.
- The customer combined `/api/v1/custom-orders/checkout` route is explicitly a `DEMO_SUBMITTED` flow, not a payment gateway or verified payment. Preserve it separately from the quote-required customer checkout route.
- `ADMIN_EMAIL`/`ADMIN_PASSWORD` and SMTP are externalized; ADMIN_PASSWORD's fallback is empty. Do not copy secret values into architecture artifacts.
- Corrections: staff directory/editor is on `reports.html`, not `admin.html`; dashboard order state is on `admin.html`.
- Missing optional context files: `context.md`, `decisions.md`, and initial role log/inbox were absent; relied on `board.md` and `project-profile.yaml` for the supplied requirements/decisions.
- Learnings consumed: (none)

## [t9] Verified full build and startup
- Codebase/domain discoveries: Maven wrapper resolves Java 25.0.5 even though bare `java` resolves Java 21.0.12; the app can be smoke-started with test-scoped H2 on the test classpath and isolated environment configuration.
- Wrong assumptions and corrections: an unquoted dotted `-Dspring-boot.run.useTestClasspath=true` argument was parsed as a Maven lifecycle phase in PowerShell; quoting the property argument fixed startup.
- Debugging dead-ends and what actually worked: the first launch command failed before application startup; the quoted Maven property invocation started the app, H2 initialized, and HTTP probes returned the expected statuses.
- Techniques/patterns worth reusing for future tasks: use wrapper `clean verify` for full coverage; keep startup smoke tests isolated from local MySQL by overriding DB URL/driver and use a temporary external admin credential; check both the public homepage and a protected route, then confirm the test process and port are closed.
- Learnings consumed: [architect/spring-jdbc-feature-boundaries]
