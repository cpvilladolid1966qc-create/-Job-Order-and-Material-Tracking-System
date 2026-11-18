## 2026-11-04T16:14:00Z — tester notification (t10)

`store.js` still uses a fixed `legacyMaterialUnitCosts` map for financial-impact estimates. Catalog materials absent from that map may show a zero or stale estimate; this map is not used for material selection, units, or stock.
