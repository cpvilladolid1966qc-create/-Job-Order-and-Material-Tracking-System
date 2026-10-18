-- Style Maria Workshop — MySQL schema (reference)
-- Spring Boot auto-generates these via Hibernate (ddl-auto=update),
-- but you can run this manually if you prefer.

CREATE DATABASE IF NOT EXISTS style_maria
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE style_maria;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(190) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(190) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER',
  token VARCHAR(128),
  created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS cart_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  length DOUBLE NOT NULL,
  width DOUBLE NOT NULL,
  frame_material VARCHAR(64) NOT NULL,
  reference_image VARCHAR(500),
  quantity INT NOT NULL DEFAULT 1,
  unit_price DECIMAL(12,2) NOT NULL,
  created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  reference VARCHAR(32) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  subtotal DECIMAL(12,2) NOT NULL,
  delivery_fee DECIMAL(12,2) NOT NULL,
  total DECIMAL(12,2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  receipt_image VARCHAR(500),
  recipient_name VARCHAR(255),
  phone VARCHAR(255),
  street VARCHAR(500),
  city VARCHAR(255),
  province VARCHAR(255),
  delivery_area VARCHAR(255),
  created_at DATETIME NOT NULL,
  updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  length DOUBLE NOT NULL,
  width DOUBLE NOT NULL,
  frame_material VARCHAR(64) NOT NULL,
  reference_image VARCHAR(500),
  quantity INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS settings (
  setting_key VARCHAR(64) PRIMARY KEY,
  setting_value VARCHAR(1000)
);

-- Delivery address columns on orders (added for delivery fees by area)
-- ALTER TABLE orders ADD recipient_name VARCHAR(255), ADD phone VARCHAR(255), ADD street VARCHAR(500),
--   ADD city VARCHAR(255), ADD province VARCHAR(255), ADD delivery_area VARCHAR(255);
