CREATE TABLE IF NOT EXISTS wastage_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL,
    material VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    action_taken VARCHAR(500) NOT NULL,
    quantity DECIMAL(12, 3) NOT NULL, 
    unit VARCHAR(50) NOT NULL,
    logged_by VARCHAR(100) NOT NULL,
    logged_at TIMESTAMP(6) NOT NULL,
    order_stage VARCHAR(100) NOT NULL,
    delivery_date DATE NOT NULL,
    material_cost DECIMAL(12, 2) NOT NULL,
    labour_cost DECIMAL(12, 2) NOT NULL,
    total_impact DECIMAL(12, 2) NOT NULL,
    recoverable BOOLEAN NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory_categories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory_units (
    code VARCHAR(20) NOT NULL PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Stable unit codes; these are lookup values, not imported inventory.
INSERT INTO inventory_units (code, display_name)
SELECT 'EA', 'Each'
WHERE NOT EXISTS (SELECT 1 FROM inventory_units WHERE code = 'EA');
INSERT INTO inventory_units (code, display_name)
SELECT 'M', 'Meter'
WHERE NOT EXISTS (SELECT 1 FROM inventory_units WHERE code = 'M');
INSERT INTO inventory_units (code, display_name)
SELECT 'CM', 'Centimeter'
WHERE NOT EXISTS (SELECT 1 FROM inventory_units WHERE code = 'CM');
INSERT INTO inventory_units (code, display_name)
SELECT 'MM', 'Millimeter'
WHERE NOT EXISTS (SELECT 1 FROM inventory_units WHERE code = 'MM');
INSERT INTO inventory_units (code, display_name)
SELECT 'KG', 'Kilogram'
WHERE NOT EXISTS (SELECT 1 FROM inventory_units WHERE code = 'KG');
INSERT INTO inventory_units (code, display_name)
SELECT 'G', 'Gram'
WHERE NOT EXISTS (SELECT 1 FROM inventory_units WHERE code = 'G');

CREATE TABLE IF NOT EXISTS inventory_materials (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    sku VARCHAR(40) UNIQUE,
    category_id BIGINT NOT NULL,
    unit_code VARCHAR(20) NOT NULL,
    quantity_on_hand DECIMAL(14, 3) NOT NULL DEFAULT 0,
    low_stock_threshold DECIMAL(14, 3) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_inventory_material_category
        FOREIGN KEY (category_id) REFERENCES inventory_categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_material_unit
        FOREIGN KEY (unit_code) REFERENCES inventory_units (code) ON DELETE RESTRICT,
    CONSTRAINT chk_inventory_material_quantity CHECK (quantity_on_hand >= 0),
    CONSTRAINT chk_inventory_material_threshold CHECK (low_stock_threshold >= 0),
    INDEX idx_inventory_material_active_category (active, category_id),
    INDEX idx_inventory_material_category_name (category_id, name)
);

CREATE TABLE IF NOT EXISTS inventory_movements (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    movement_type VARCHAR(16) NOT NULL,
    quantity_delta DECIMAL(14, 3) NOT NULL,
    balance_after DECIMAL(14, 3) NOT NULL,
    actor_email VARCHAR(254) NOT NULL,
    actor_role VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    reference_id VARCHAR(100),
    wastage_log_id BIGINT UNIQUE,
    reason VARCHAR(500) NOT NULL,
    CONSTRAINT fk_inventory_movement_material
        FOREIGN KEY (material_id) REFERENCES inventory_materials (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movement_wastage
        FOREIGN KEY (wastage_log_id) REFERENCES wastage_logs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_inventory_movement_type CHECK (
        CASE movement_type
            WHEN 'RECEIPT' THEN 1
            WHEN 'USE' THEN 1
            WHEN 'RETURN' THEN 1
            WHEN 'ADJUSTMENT' THEN 1
            WHEN 'WASTAGE' THEN 1
            ELSE 0
        END = 1),
    CONSTRAINT chk_inventory_movement_delta CHECK (
        CASE movement_type
            WHEN 'RECEIPT' THEN CASE WHEN quantity_delta > 0 THEN 1 ELSE 0 END
            WHEN 'RETURN' THEN CASE WHEN quantity_delta > 0 THEN 1 ELSE 0 END
            WHEN 'USE' THEN CASE WHEN quantity_delta < 0 THEN 1 ELSE 0 END
            WHEN 'WASTAGE' THEN CASE WHEN quantity_delta < 0 THEN 1 ELSE 0 END
            WHEN 'ADJUSTMENT' THEN CASE WHEN quantity_delta <> 0 THEN 1 ELSE 0 END
            ELSE 0
        END = 1),
    CONSTRAINT chk_inventory_movement_balance CHECK (balance_after >= 0),
    INDEX idx_inventory_movement_material_time (material_id, occurred_at, id),
    INDEX idx_inventory_movement_time (occurred_at)
);

CREATE TABLE IF NOT EXISTS staff_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_salt VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    created_by_email VARCHAR(254) NOT NULL
);

CREATE TABLE IF NOT EXISTS staff_invites (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(100) NOT NULL DEFAULT '',
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    created_by_email VARCHAR(254) NOT NULL,
    accepted_at TIMESTAMP(6),
    revoked_at TIMESTAMP(6),
    INDEX idx_staff_invite_email_expiry (email, expires_at)
);

CREATE TABLE IF NOT EXISTS audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    actor_email VARCHAR(254) NOT NULL,
    actor_role VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    details_json TEXT NOT NULL,
    INDEX idx_audit_entity_time (entity_type, entity_id, occurred_at, id),
    INDEX idx_audit_actor_time (actor_email, occurred_at)
);

CREATE TABLE IF NOT EXISTS pos_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    customer_name VARCHAR(120) NOT NULL,
    contact VARCHAR(40) NOT NULL DEFAULT '',
    address VARCHAR(500) NOT NULL DEFAULT '',
    item_description VARCHAR(500) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    paid_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    payment_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    payment_method VARCHAR(30),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    created_by_email VARCHAR(254) NOT NULL,
    completed_at TIMESTAMP(6),
    CONSTRAINT chk_pos_order_amount CHECK (amount > 0),
    CONSTRAINT chk_pos_order_paid_amount CHECK (paid_amount >= 0),
    INDEX idx_pos_orders_status_created (status, created_at, id)
);

CREATE TABLE IF NOT EXISTS pos_order_payments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    tendered_amount DECIMAL(12, 2) NOT NULL,
    change_due DECIMAL(12, 2) NOT NULL DEFAULT 0,
    payment_method VARCHAR(30) NOT NULL,
    reference VARCHAR(100),
    submitted_by_email VARCHAR(254) NOT NULL,
    submitted_by_role VARCHAR(16) NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_pos_payment_order
        FOREIGN KEY (order_id) REFERENCES pos_orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT chk_pos_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_pos_payment_tendered CHECK (tendered_amount >= amount),
    CONSTRAINT chk_pos_payment_change CHECK (change_due >= 0),
    INDEX idx_pos_payment_order_time (order_id, submitted_at, id)
);

CREATE TABLE IF NOT EXISTS custom_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(32) UNIQUE,
    length DECIMAL(10, 2) NOT NULL,
    width DECIMAL(10, 2) NOT NULL,
    unit VARCHAR(5) NOT NULL,
    frame VARCHAR(100) NOT NULL,
    custom_frame_request VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    reference_image_name VARCHAR(255),
    reference_image LONGBLOB,
    submitted_at TIMESTAMP(6) NOT NULL,
    base_price DECIMAL(12, 2) NOT NULL,
    size_adjustment DECIMAL(12, 2) NOT NULL,
    frame_adjustment DECIMAL(12, 2) NOT NULL,
    led_lighting DECIMAL(12, 2) NOT NULL,
    wall_mounting_hardware DECIMAL(12, 2) NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    delivery_fee DECIMAL(12, 2) NOT NULL,
    total DECIMAL(12, 2) NOT NULL,
    downpayment DECIMAL(12, 2) NOT NULL,
    remaining_balance DECIMAL(12, 2) NOT NULL,
    admin_status VARCHAR(30) NOT NULL DEFAULT 'NEW_REQUEST',
    admin_notes VARCHAR(2000) NOT NULL DEFAULT '',
    status_updated_at TIMESTAMP(6),
    requester_name VARCHAR(100) NOT NULL DEFAULT '',
    requester_email VARCHAR(254) NOT NULL DEFAULT '',
    requester_phone VARCHAR(40) NOT NULL DEFAULT '',
    requester_address VARCHAR(500) NOT NULL DEFAULT '',
    requester_customer_id BIGINT,
    quote_base_amount DECIMAL(12, 2),
    quote_frame_amount DECIMAL(12, 2),
    quote_labor_amount DECIMAL(12, 2),
    quote_delivery_amount DECIMAL(12, 2),
    quote_total DECIMAL(12, 2),
    quote_sent_at TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS custom_order_checkouts (
    order_id VARCHAR(32) NOT NULL PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    address VARCHAR(500) NOT NULL,
    contact_number VARCHAR(40) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    payment_reference VARCHAR(100) NOT NULL,
    receipt_name VARCHAR(255),
    receipt LONGBLOB,
    payment_amount DECIMAL(12, 2),
    payment_status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    submission_type VARCHAR(24) NOT NULL DEFAULT 'UNCLASSIFIED',
    submitted_at TIMESTAMP(6) NOT NULL,
    payment_verified_at TIMESTAMP(6),
    payment_verified_by_email VARCHAR(254),
    CONSTRAINT fk_custom_checkout_order
        FOREIGN KEY (order_id) REFERENCES custom_orders (order_id)
);

CREATE TABLE IF NOT EXISTS customer_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    address VARCHAR(500) NOT NULL DEFAULT '',
    phone VARCHAR(40) NOT NULL DEFAULT '',
    profile_photo MEDIUMBLOB,
    profile_photo_content_type VARCHAR(40),
    password_salt VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS customer_email_verification_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_customer_email_verification_customer
        FOREIGN KEY (customer_id) REFERENCES customer_accounts (id) ON DELETE CASCADE,
    INDEX idx_customer_email_verification_customer (customer_id)
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_password_reset_customer
        FOREIGN KEY (customer_id) REFERENCES customer_accounts (id),
    INDEX idx_password_reset_customer (customer_id)
);

CREATE TABLE IF NOT EXISTS staff_password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    staff_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_staff_password_reset_staff
        FOREIGN KEY (staff_id) REFERENCES staff_accounts (id) ON DELETE CASCADE,
    INDEX idx_staff_password_reset_staff (staff_id)
);
