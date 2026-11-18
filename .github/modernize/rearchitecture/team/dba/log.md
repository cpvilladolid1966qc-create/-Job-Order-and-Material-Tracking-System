## [t2] Designing additive inventory, staff, and audit schema
- Codebase/domain discoveries: Persistence is plain Spring JDBC; startup runs `schema.sql` and separate metadata-checked compatibility migrations. Existing wastage has no inventory foreign key and must stay intact. Customer order/checkouts already have ownership and uniqueness behaviors that should not be rewritten.
- Wrong assumptions and corrections: New browser-local stock/staff/POS state must not be backfilled; no reliable historic stock balance can be inferred from wastage/order records. The configured bootstrap admin need not become an invite-managed staff row.
- Debugging dead-ends and what actually worked: `Get-Date -AsUTC` is unavailable in this PowerShell version; `[DateTime]::UtcNow` returned the required UTC time.
- Techniques/patterns worth reusing for future tasks: Add new tables idempotently in `schema.sql`; use metadata-checked additive ALTERs only for existing columns; keep balance mutation, append-only movement, related business data, and audit event in one transaction; record actor snapshots rather than relying on deletable accounts.
- Learnings consumed: [architect/spring-jdbc-feature-boundaries]

## [t4] Implementing inventory, staff, and audit persistence schema
- Codebase/domain discoveries: The baseline initializer can be applied repeatedly in H2 MySQL mode; checkout verification fields need a separate metadata-guarded additive migration for databases whose old checkout table predates them.
- Wrong assumptions and corrections: A test using `DriverManagerDataSource` closed H2's schema-creation connection before string-valued CHECK constraints were evaluated; `SingleConnectionDataSource` kept the test's in-memory schema session stable and allowed the constraint tests to validate correctly.
- Debugging dead-ends and what actually worked: The standalone `java` command selected 21.0.12, while `mvnw.cmd -version` resolved the wrapper's Java 25.0.5 runtime; the full test suite was run through the wrapper.
- Techniques/patterns worth reusing for future tasks: Seed stable lookup rows with idempotent `INSERT ... SELECT ... WHERE NOT EXISTS`; keep normalized identifiers and actor snapshots in schema; validate both fresh/repeated DDL and missing legacy-column migration paths in H2 MySQL mode.
- Learnings consumed: [dba/inventory-ledger-schema]
