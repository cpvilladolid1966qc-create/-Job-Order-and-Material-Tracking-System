# Wastage Catalog Unit Selection

When wastage consumes inventory, bind the form to catalog IDs and use the selected material's canonical unit code.

## What Happened

For Style Maria task t10.1, the existing wastage form's fixed options diverged from the database catalog and server unit validation. Loading active inventory materials fixed newly-created material support; an informational unit selector that is derived from the chosen material prevents mismatches, and fractional quantity controls must be disabled for counted `EA` stock.

## Takeaway

For server-backed stock mutations, populate options from the authenticated catalog API, post the selected ID plus canonical unit code, keep loading/empty/error states visible, and exercise the catalog-to-mutation flow in integration tests. Disable fractional controls for discrete count units.

## History
- 2026-11-04 (stylemaria/t10.1): initial
