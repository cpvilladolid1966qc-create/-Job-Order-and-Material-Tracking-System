# Session Actors and Transactional Audit

## Context

In the existing Spring MVC/JDBC application, new admin/staff APIs coexist with customer custom-order workflows. Actor identity must not be trusted from request payloads, and audit evidence must not outlive a rolled-back business mutation (or vice versa).

## Conventions

- Resolve the actor's email and role from the server-side `HttpSession`; do not accept actor/role fields from clients.
- Enforce role checks at the server endpoint boundary, and re-check persisted staff activation for protected requests so deactivation takes effect without waiting for session expiry.
- Perform stock balance changes, append-only movement inserts, and audit inserts in one transaction. Lock the material row before computing the resulting balance.
- Treat audit as part of the mutation's success contract: if the audit insert fails, the transaction must roll back the business row and ledger change.
- Keep demo/unquoted checkout submissions distinct from quote-based submissions; only the latter may enter official payment verification.

## Evidence

These conventions are implemented by `ApiSupport.java`, `InventoryApi.java`, `BusinessOrderApi.java`, and `StylemariaApplication.java`; the H2 rollback probe in `BackendApiRuntimeTests.java` confirms audit failure rolls back stock and movement writes.
