# Existing Data Model

Persistence is implemented with SQL and `JdbcTemplate`, not ORM entity classes. The schema definitions below are from `src/main/resources/schema.sql`; the two Java `ApplicationRunner` schema helpers add legacy compatibility columns to existing tables.

## Tables

### `wastage_logs`

- `id BIGINT` primary key, auto-increment.
- Required order/material/context: `order_id VARCHAR(100)`, `material VARCHAR(100)`, `description VARCHAR(1000)`, `reason VARCHAR(500)`, `action_taken VARCHAR(500)`.
- Required quantity/actor/time: `quantity DECIMAL(12,3)`, `unit VARCHAR(50)`, `logged_by VARCHAR(100)`, `logged_at TIMESTAMP(6)`.
- Required production context: `order_stage VARCHAR(100)`, `delivery_date DATE`.
- Required cost impact: `material_cost DECIMAL(12,2)`, `labour_cost DECIMAL(12,2)`, `total_impact DECIMAL(12,2)`.
- Required flags/status: `recoverable BOOLEAN`, `status VARCHAR(50)`.
- No declared foreign key from `order_id` to an order table.

### `custom_orders`

- `id BIGINT` primary key, auto-increment; `order_id VARCHAR(32)` unique.
- Request dimensions/configuration: `length DECIMAL(10,2)`, `width DECIMAL(10,2)`, `unit VARCHAR(5)`, `frame VARCHAR(100)`, `custom_frame_request VARCHAR(200)`, `message VARCHAR(500)`.
- Optional reference image fields: `reference_image_name VARCHAR(255)`, `reference_image LONGBLOB`.
- Request time and totals: `submitted_at TIMESTAMP(6)`, `base_price`, `size_adjustment`, `frame_adjustment`, `led_lighting`, `wall_mounting_hardware`, `subtotal`, `delivery_fee`, `total`, `downpayment`, and `remaining_balance` as `DECIMAL(12,2)`.
- Admin workflow: `admin_status VARCHAR(30)` default `NEW_REQUEST`, `admin_notes VARCHAR(2000)` default empty, `status_updated_at TIMESTAMP(6)`.
- Requester snapshot: `requester_name VARCHAR(100)`, `requester_email VARCHAR(254)`, `requester_phone VARCHAR(40)`, `requester_address VARCHAR(500)`, nullable `requester_customer_id BIGINT`.
- Quote fields: nullable `quote_base_amount`, `quote_frame_amount`, `quote_labor_amount`, `quote_delivery_amount`, `quote_total` (`DECIMAL(12,2)`), and `quote_sent_at TIMESTAMP(6)`.
- `requester_customer_id` is not declared as a foreign key in the current schema.

### `custom_order_checkouts`

- `order_id VARCHAR(32)` primary key and foreign key to `custom_orders.order_id`.
- Required snapshot/payment: `customer_name VARCHAR(100)`, `address VARCHAR(500)`, `contact_number VARCHAR(40)`, `payment_method VARCHAR(30)`, `payment_reference VARCHAR(100)`.
- Optional receipt: `receipt_name VARCHAR(255)`, `receipt LONGBLOB`.
- Nullable `payment_amount DECIMAL(12,2)`, `payment_status VARCHAR(30)` default `SUBMITTED`, and required `submitted_at TIMESTAMP(6)`.
- One checkout row per custom order is enforced by primary key.

### `customer_accounts`

- `id BIGINT` primary key, auto-increment; `email VARCHAR(254)` unique.
- Required `full_name VARCHAR(100)`, `email`, `address VARCHAR(500)` default empty, `phone VARCHAR(40)` default empty.
- Optional `profile_photo MEDIUMBLOB`, `profile_photo_content_type VARCHAR(40)`.
- Credential fields `password_salt VARCHAR(64)`, `password_hash VARCHAR(128)`; `created_at TIMESTAMP(6)`.

### `password_reset_tokens`

- `id BIGINT` primary key, auto-increment; required `customer_id BIGINT`, `token_hash CHAR(64)` unique, `expires_at TIMESTAMP(6)`, `created_at TIMESTAMP(6)`, nullable `used_at TIMESTAMP(6)`.
- Foreign key `customer_id → customer_accounts.id`; index on `customer_id`.

## Relationships and Persistence Boundaries

- `custom_order_checkouts.order_id → custom_orders.order_id` is the only declared order relationship.
- `password_reset_tokens.customer_id → customer_accounts.id`.
- Customer ownership is enforced in controller SQL using `requester_customer_id`; it is not an explicit FK.
- Wastage logs are independent records; stock quantities/material catalogs/stock-movement ledger/staff accounts/invites/audit events are not present in the existing SQL schema.

## Observed Transaction Boundaries

- `DemoAuthController.updateCustomerProfile`, password reset, customer custom-order request creation, custom-order checkout submission, combined demo checkout, and wastage log creation carry `@Transactional`.
- Admin custom-order status update, quote-draft save, and quote publication currently execute individual `JdbcTemplate` updates without an annotated transaction or audit row.
- Existing password-reset token creation/consumption and schema-upgrade behavior are implemented in controller/helper code and `ApplicationRunner`s, not separate services.

## Key Existing Entities

1. `customer_accounts` — customer identity, password hash/salt, and profile fields.
2. `password_reset_tokens` — single-use/expiry metadata for customer reset links.
3. `custom_orders` — customer-submitted custom mirror requests, quote, and admin status.
4. `custom_order_checkouts` — one payment submission and receipt snapshot per custom order.
5. `wastage_logs` — operational wastage record and calculated cost impact.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/project-profile.yaml` — confirmed current DB ownership and that existing DB customer/custom-order data must be retained.

## Evidence Mapping

- `schema.sql#CREATE TABLE wastage_logs` → wastage table description.
- `schema.sql#CREATE TABLE custom_orders` and `custom_order_checkouts` → custom-order and checkout descriptions/relationship.
- `schema.sql#CREATE TABLE customer_accounts` and `password_reset_tokens` → account/token descriptions/relationship.
- `StylemariaApplication.java#@Transactional` methods and migration runners → observed transaction and compatibility notes.
