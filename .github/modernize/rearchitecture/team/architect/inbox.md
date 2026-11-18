## 2026-11-04T13:57:15Z — coordinator broadcast from architect (t1)

Retain the existing Spring MVC/JDBC, HttpSession, MySQL/H2 setup and customer custom-order routes; keep quote-required checkout distinct from existing `DEMO_SUBMITTED` checkout. Start inventory, staff, and POS/business-order records empty—do not import browser data. Enforce ADMIN/STAFF/USER permissions server-side, derive actors from session, and commit audit records with business mutations.
