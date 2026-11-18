# Spring JDBC Feature Boundaries

When extending this single-module Spring MVC/JDBC application, preserve existing HTTP/session contracts while moving new business state behind server-side, transaction-scoped APIs.

## What Happened

For Style Maria task t1, source review found controllers, role interception, and direct `JdbcTemplate` writes co-located in `StylemariaApplication.java`; static pages share a large DOM-conditional `store.js`. Customer custom orders and checkouts already use the database, but inventory actions are DOM-only and staff/POS/dashboard business state uses browser storage. The existing session stores role and customer identity, while the interceptor handles only a partial set of routes. The combined customer `/checkout` endpoint is a demo record, distinct from the published-quote payment-submission flow.

## Takeaway

- Keep the existing Maven/Spring MVC/JDBC/H2-MySQL-mode architecture; add focused service/controller boundaries only where new features need them rather than rewriting the application wholesale.
- Preserve DB-backed customer custom-order routes and ownership checks. Treat demo checkout separately from verified payment.
- Authorize every mutation on the server from the session; derive actor identity from authenticated server state, never from request fields.
- For stock, wastage, order/payment, and audit changes, put all related database writes in the same transaction. Keep stock movement history append-only.
- Remove localStorage reads/writes for business records, but do not import the old browser values or confuse them with existing database customer orders.
- Keep admin password and SMTP secrets externalized; never insert fallback credentials into source or task artifacts.

## History
- 2026-11-04 (stylemaria/t1): initial
