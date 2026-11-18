# Route Authorization Defaults

Review route matching separately from endpoint behavior: explicit protection for known sensitive routes does not provide default-deny coverage for future routes.

## What Happened
In Stylemaria task t8, the reviewed interceptor imposed role requirements on known sensitive route patterns but left unmatched paths without a required role. No currently mapped sensitive route was identified as falling through. This is a forward-looking route-registration risk rather than a confirmed exposed endpoint.

## Takeaway
Prefer default-deny for `/api/**` with an explicit allowlist for public endpoints. Add a test that verifies all mapped sensitive API routes resolve to an authorization policy.

## History
- 2026-11-04 (stylemaria/t8): initial
