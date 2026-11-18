# Project Structure

## Classification

- **Project type:** Full-stack single-module web application: Spring Boot/JDBC backend plus static HTML/JavaScript frontend.
- **Build/deployable boundary:** One Maven project (`pom.xml`, artifact `stylemaria`); no frontend build or separate deployable module.
- **Package structure:** One Java package, `com.job_order_and_material_tracking_system.stylemaria`; no package-per-controller/service/repository/model layering.

## Top-Level Source Breakdown

| Area | Measured files | Notes |
|---|---:|---|
| Main Java | 1 | `StylemariaApplication.java` holds startup, controllers, auth, JDBC SQL, schema runners, interceptor/config, records, and calculator. |
| Test Java | 5 | Maven/JUnit Spring tests under the matching package. |
| Static HTML pages | 16 | Customer, admin, inventory, POS, reporting, wastage, and account pages in `src/main/resources/static`. |
| Static JavaScript | 5 | Four customer/order-focused files plus shared `store.js`; the shared file contains admin, inventory, POS, and wastage behavior. |
| CSS | 1 | Shared `static/css/style.css`. |
| Images | 27 | Static product/payment assets (binary assets, not source LOC). |
| Main resource config/schema files | 2 | `application.properties`, `schema.sql`. |
| Test resource files | 1 | H2 test profile `src/test/resources/application.properties`. |

Counts are filesystem counts collected from `src`; `target/` and Maven wrapper/build output are not source modules. The existing project profile separately reports 5,556 LOC; this analysis does not treat that profile snapshot as a newly measured source LOC total.

## Layers and Entry Points

1. **Runtime entry point:** `StylemariaApplication.main` at `src/main/java/com/job_order_and_material_tracking_system/stylemaria/StylemariaApplication.java:73-77`, annotated `@SpringBootApplication`.
2. **Web/controller layer:** package-private and public MVC controllers in that same Java file:
   - `DemoAuthController` at line 82: login/signup/password reset, customer profile, mail, session identity.
   - `DashboardController` at line 804: legacy redirects.
   - `CustomerCustomOrderController` at line 819: customer-owned custom-order reads.
   - `AdminCustomOrderController` at line 995: admin review, quote, and proof/image routes.
   - `WastageController` at line 1308: wastage log API.
   - `CustomOrderController` at line 1492: quote, custom request, checkout.
3. **Application support/config:** `CustomerAccountSchemaMigration` and `CustomOrderSchemaMigration` `ApplicationRunner`s; `DemoRoleInterceptor`; `DemoWebConfig`; `MirrorPriceCalculator` are declared in the same file.
4. **Persistence layer:** direct SQL through `JdbcTemplate` in controller/helper code; no repository classes or ORM entities.
5. **Presentation layer:** static HTML served from `src/main/resources/static`; shared scripts under `static/js`; shared CSS under `static/css`.

## Major Functional Areas

- Customer login/signup, profile, password reset, and customer-session ownership.
- Customer custom-order submission, image attachment, quote display, payment checkout/receipt, and tracking.
- Admin custom-request review and quote publishing.
- Admin dashboard/POS business-order UI currently backed by browser-local order collections.
- Inventory catalog UI currently DOM-only/local-page changes, without a material table in the SQL schema.
- Wastage logs stored in the database with a separate staff-facing page.
- Static mirror price calculator and reports/dashboard UI.

## Configuration and Routing

- `pom.xml`: single-module Spring Boot Maven build, Java version property `25`.
- `src/main/resources/application.properties`: app identity, admin credential properties, multipart sizes, MySQL JDBC settings, SQL init, SMTP settings, public URL, mail sender.
- `src/main/resources/schema.sql`: `CREATE TABLE IF NOT EXISTS` definitions for current business tables.
- `src/test/resources/application.properties`: H2 in-memory database with `MODE=MySQL`; SQL init enabled.
- Pages are static files; controller routes are annotation-based in one Java source file. `DemoWebConfig` applies `DemoRoleInterceptor`; most page/API role mapping is explicit in the interceptor.
- `src/main/resources/templates` exists but has no listed template files; current presentation is static HTML.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/project-profile.yaml` — single-module/full-stack classification and module/build context.

## Evidence Mapping

- `project-profile.yaml#project.structure` → classification and Maven boundary above.
- Source-tree inventory and `StylemariaApplication.java` type declarations → measured counts, controller list, and layer description.
- `schema.sql` and resource properties → persistence/configuration role.
