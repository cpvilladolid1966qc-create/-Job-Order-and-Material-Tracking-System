# Server API modules for static pages

When integrating a static page with authenticated APIs, keep the page-specific workflow in a focused deferred JavaScript module and route JSON requests through one same-origin helper. Preserve HTTP status and API error details in thrown errors so UI feedback can explain authorization and validation failures.

Treat returned server data as authoritative: use server IDs and statuses for subsequent mutations, then reload after writes rather than maintaining a second browser-side copy. Never use localStorage for business or staff records. The browser remains a presentation layer; role checks and actor identity belong on the server.

For static HTML checks, strip query strings from script `src` values before resolving local paths; asset cache-busting parameters are not part of the filename.
