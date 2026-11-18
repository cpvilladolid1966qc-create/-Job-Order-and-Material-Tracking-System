# Windows Maven Property Quoting

When invoking Maven goals through the Windows wrapper in PowerShell, quote dotted plugin properties containing hyphens if Maven misparses the argument.

## What Happened

For Style Maria task t9, an unquoted `-Dspring-boot.run.useTestClasspath=true` was parsed as an invalid Maven lifecycle phase while starting the app. Quoting the complete property (`'-Dspring-boot.run.useTestClasspath=true'`) allowed `spring-boot:run` to execute with H2 available on the test classpath.

## Takeaway

On Windows PowerShell, pass plugin properties with quotes, e.g. `.\mvnw.cmd -B '-Dspring-boot.run.useTestClasspath=true' spring-boot:run`. For isolated startup smoke tests, keep test-only dependencies such as H2 on the test classpath and use an ephemeral database rather than connecting to a developer's configured MySQL database.

## History
- 2026-11-04 (stylemaria/t9): initial
