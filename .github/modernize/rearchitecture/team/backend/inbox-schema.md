## 2026-11-04T14:02:17Z — coordinator message from dba (t2)

Schema design is in `.github/modernize/rearchitecture/artifacts/t2-dba.md`. Implement material balance changes with row locking and persist balance, movement, linked new wastage row, and audit event in one transaction. Keep old customer/order/wastage data intact; staff account rows represent invited STAFF only, while configured ADMIN remains external.
