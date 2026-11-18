# Existing Technology Stack

## Languages and Runtime

- Java application; `pom.xml` sets `java.version` to `25`.
- Static frontend uses HTML, CSS, and browser JavaScript; no frontend package manager/build file is present.
- Runtime/build packaging: Maven Spring Boot application; local Maven wrapper files are present.

## Frameworks and Dependencies

Versions below are those declared directly in the project or inherited through the Spring Boot parent where the project does not pin an individual version.

| Dependency | Version/scope | Current use |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-parent` | 4.1.1 | Parent/BOM and Maven plugin defaults. |
| `spring-boot-starter-webmvc` | Managed by Boot 4.1.1 | MVC controllers, static resources, JSON/multipart HTTP. |
| `spring-boot-starter-jdbc` | Managed by Boot 4.1.1 | `JdbcTemplate` access to SQL tables. |
| `spring-boot-starter-mail` | Managed by Boot 4.1.1 | Password-reset email support and configured SMTP sender. |
| `com.mysql:mysql-connector-j` | Managed by Boot 4.1.1; runtime | Production database driver. |
| `spring-boot-starter-webmvc-test` | Managed by Boot 4.1.1; test | Spring MVC test dependencies. |
| `com.h2database:h2` | Managed by Boot 4.1.1; test | In-memory test database in MySQL compatibility mode. |

No separate security framework, JPA/Hibernate entity layer, frontend framework, API specification, or migration tool is declared in `pom.xml`.

## Framework Wiring

- Spring component/annotation scanning starts from `StylemariaApplication`.
- Controllers, component `ApplicationRunner`s, and the `WebMvcConfigurer` are annotated in `StylemariaApplication.java`.
- SQL is written directly in `JdbcTemplate` calls. Transactions use Spring `@Transactional` on selected methods.
- Custom authorization uses `HttpSession` attributes and a hand-written `HandlerInterceptor`; it is not Spring Security.
- Static resources are served from Spring Boot's standard `static` resource directory.

## Runtime Configuration

`application.properties` externalizes these settings. This summary records property/environment names only; it does not copy secret values.

- Admin bootstrap: `app.admin.email` from `ADMIN_EMAIL`; `app.admin.password` from `ADMIN_PASSWORD`.
- Database: `spring.datasource.url` from `DB_URL`; username from `DB_USERNAME`; password from `DB_PASSWORD`; driver is MySQL in the main profile.
- SMTP: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`.
- Mail links/sender: `APP_PUBLIC_URL`, `MAIL_FROM`.
- Multipart: Spring servlet max-file and max-request properties.
- SQL initialization: `spring.sql.init.mode=always`.
- Tests override datasource to H2 and retain SQL init; this is the current MySQL/H2 compatibility target.

## Version/Compatibility Notes

- The project requests Java 25 through Maven; the task board separately directs implementation verification to use JDK 21 override only if local Java 25 is unavailable. This analysis task did not build or change the version.
- MySQL-specific schema constructs include `AUTO_INCREMENT`, `LONGBLOB`/`MEDIUMBLOB`, `TIMESTAMP(6)`, and inline indexes/constraints. The H2 test profile enables MySQL mode.
- The source uses Java records, `List.getFirst()`, and text blocks; preserve compatibility with the configured compiler/runtime when extending it.
- The current schema startup combines SQL initialization with hand-written metadata-based additive column runners; no migration framework is configured.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/project-profile.yaml` — source/target stack classification and Java application context.

## Evidence Mapping

- `pom.xml#parent/properties/dependencies` → versions, runtime, and dependency table.
- `application.properties` and test `application.properties` → externalized settings and MySQL/H2 behavior.
- `StylemariaApplication.java` annotations/imports → MVC, JDBC, mail, session, and transaction wiring.
