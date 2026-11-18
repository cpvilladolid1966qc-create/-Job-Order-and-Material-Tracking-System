## 2026-11-04T14:02:17Z — coordinator message from dba (t2)

Migration risk from DBA: production MySQL version and table engine were not discoverable from source. Confirm supported `CHECK` enforcement and InnoDB before relying on DB-level invariants; DDL auto-commits, so startup migration steps must remain independently idempotent.
