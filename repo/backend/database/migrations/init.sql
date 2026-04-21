CREATE DATABASE IF NOT EXISTS `anju_sys`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `anju_sys`;

CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `secondary_password` VARCHAR(255) NULL,
  `display_name` VARCHAR(100) NOT NULL DEFAULT '',
  `email` VARCHAR(128) NULL,
  `phone` VARCHAR(255) NULL,
  `role` VARCHAR(32) NOT NULL DEFAULT 'STAFF',
  `status` TINYINT NOT NULL DEFAULT 1,
  `last_login_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `users` (`username`, `password`, `display_name`, `role`, `status`)
VALUES
  ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMye1J8YvQqZqjmp6eUXEEfZ.HlLJQfP5H2', 'System Admin', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE
  `display_name` = VALUES(`display_name`),
  `role` = VALUES(`role`),
  `status` = VALUES(`status`);

CREATE TABLE IF NOT EXISTS `property` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
  `rent` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `deposit` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `start_date` DATETIME NOT NULL,
  `end_date` DATETIME NULL,
  `compliance_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `media_refs` VARCHAR(2000) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_property_code` (`code`),
  KEY `idx_property_status_time` (`status`, `start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `appointment` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `number` VARCHAR(64) NOT NULL,
  `staff_id` BIGINT UNSIGNED NOT NULL,
  `resource_id` BIGINT UNSIGNED NOT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `penalty` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `reschedule_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `idempotency_key` VARCHAR(128) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appointment_number` (`number`),
  UNIQUE KEY `uk_appointment_idempotency_key` (`idempotency_key`),
  KEY `idx_appointment_status_time` (`status`, `start_time`, `end_time`),
  KEY `idx_appointment_staff_time` (`staff_id`, `start_time`, `end_time`),
  CONSTRAINT `fk_appointment_staff` FOREIGN KEY (`staff_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_appointment_resource` FOREIGN KEY (`resource_id`) REFERENCES `property` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `transaction` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `transaction_no` VARCHAR(64) NOT NULL,
  `appointment_id` BIGINT UNSIGNED NULL,
  `payer_id` BIGINT UNSIGNED NULL,
  `type` VARCHAR(32) NOT NULL DEFAULT 'PAYMENT',
  `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `currency` VARCHAR(8) NOT NULL DEFAULT 'USD',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `channel` VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  `refundable` TINYINT(1) NOT NULL DEFAULT 1,
  `exception_flag` TINYINT(1) NOT NULL DEFAULT 0,
  `exception_reason` VARCHAR(255) NULL,
  `idempotency_key` VARCHAR(128) NULL,
  `occurred_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `related_transaction_no` VARCHAR(64) NULL,
  `invoice_no` VARCHAR(64) NULL,
  `invoice_status` VARCHAR(32) NOT NULL DEFAULT 'NONE',
  `settled_at` DATETIME NULL,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transaction_no` (`transaction_no`),
  UNIQUE KEY `uk_transaction_idempotency_key` (`idempotency_key`),
  KEY `idx_transaction_status_time` (`status`, `occurred_at`),
  CONSTRAINT `fk_transaction_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`),
  CONSTRAINT `fk_transaction_payer` FOREIGN KEY (`payer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_file` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `file_name` VARCHAR(255) NOT NULL,
  `content_type` VARCHAR(100) NULL,
  `size_bytes` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `hash` CHAR(64) NOT NULL,
  `chunks` INT UNSIGNED NOT NULL DEFAULT 1,
  `version` INT UNSIGNED NOT NULL DEFAULT 1,
  `storage_path` VARCHAR(512) NULL,
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `uploaded_by` BIGINT UNSIGNED NULL,
  `expires_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_file_hash_version` (`hash`, `version`),
  KEY `idx_sys_file_hash` (`hash`),
  CONSTRAINT `fk_sys_file_uploader` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `revoked_token` (
  `token_hash` VARCHAR(64) NOT NULL,
  `expires_at` DATETIME NOT NULL,
  PRIMARY KEY (`token_hash`),
  KEY `idx_revoked_token_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_audit_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `trace_id` VARCHAR(64) NULL,
  `user_id` BIGINT UNSIGNED NULL,
  `username` VARCHAR(64) NULL,
  `module` VARCHAR(64) NULL,
  `action` VARCHAR(128) NOT NULL,
  `http_method` VARCHAR(16) NULL,
  `endpoint` VARCHAR(255) NULL,
  `request_payload` LONGTEXT NULL,
  `response_payload` LONGTEXT NULL,
  `ip_address` VARCHAR(45) NULL,
  `user_agent` VARCHAR(255) NULL,
  `success` TINYINT(1) NOT NULL DEFAULT 1,
  `error_message` VARCHAR(1024) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sys_audit_log_user_time` (`user_id`, `created_at`),
  KEY `idx_sys_audit_log_module_time` (`module`, `created_at`),
  CONSTRAINT `fk_sys_audit_log_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
