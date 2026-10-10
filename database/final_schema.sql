-- ============================================================
-- LEASING DOCUMENT CAPTURE AND FRAUD DETECTION APPLICATION
-- FINAL CONSOLIDATED DATABASE SCHEMA
--
-- Database: leasing_document_db
-- Database Engine: MySQL 8.x
--
-- Purpose:
-- Recreate the complete integrated database structure from
-- scratch for the Leasing Document Capture and Fraud Detection
-- Application.
--
-- IMPORTANT:
-- This script removes existing tables in leasing_document_db.
-- Do NOT run this against the current working development
-- database unless a complete recreation is intended.
-- ============================================================


-- ============================================================
-- 1. CREATE DATABASE
-- ============================================================

CREATE DATABASE IF NOT EXISTS leasing_document_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE leasing_document_db;


-- ============================================================
-- 2. DROP EXISTING TABLES
--
-- Allows this schema to be recreated cleanly.
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS template_region;
DROP TABLE IF EXISTS document_template;
DROP TABLE IF EXISTS ocr_extracted_field;
DROP TABLE IF EXISTS alteration_finding;
DROP TABLE IF EXISTS alteration_result;
DROP TABLE IF EXISTS integrity_result;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS session;
DROP TABLE IF EXISTS document;
DROP TABLE IF EXISTS document_type;
DROP TABLE IF EXISTS device;
DROP TABLE IF EXISTS customer;
DROP TABLE IF EXISTS agent;
DROP TABLE IF EXISTS admin;

SET FOREIGN_KEY_CHECKS = 1;


-- ============================================================
-- 3. ADMIN
--
-- Stores system administrator accounts.
-- Passwords are stored as BCrypt hashes by the backend.
-- ============================================================

CREATE TABLE admin (
    admin_id INT NOT NULL AUTO_INCREMENT,

    full_name VARCHAR(100) NOT NULL,

    email VARCHAR(100) NOT NULL,

    password_hash VARCHAR(255) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (admin_id),

    UNIQUE KEY uq_admin_email (
        email
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 4. AGENT
--
-- Stores authorized leasing field agents.
-- ============================================================

CREATE TABLE agent (
    agent_id INT NOT NULL AUTO_INCREMENT,

    full_name VARCHAR(100) NOT NULL,

    email VARCHAR(100) NOT NULL,

    password_hash VARCHAR(255),

    nic VARCHAR(20) NOT NULL,

    phone VARCHAR(20),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (agent_id),

    UNIQUE KEY uq_agent_email (
        email
    ),

    UNIQUE KEY uq_agent_nic (
        nic
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 5. CUSTOMER
--
-- Stores leasing customer information.
-- ============================================================

CREATE TABLE customer (
    customer_id INT NOT NULL AUTO_INCREMENT,

    full_name VARCHAR(100) NOT NULL,

    nic VARCHAR(20) NOT NULL,

    phone VARCHAR(20),

    email VARCHAR(100),

    address VARCHAR(255),

    created_at TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (customer_id),

    UNIQUE KEY uq_customer_nic (
        nic
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 6. DEVICE
--
-- Stores organization-managed device information.
--
-- device_identifier:
-- Stable identifier used by the application.
--
-- identifier_type:
-- Identifies the identifier source/type.
--
-- assigned_agent_id:
-- Agent currently assigned to the device.
-- ============================================================

CREATE TABLE device (
    device_id INT NOT NULL AUTO_INCREMENT,

    imei_number VARCHAR(50),

    device_identifier VARCHAR(128),

    identifier_type VARCHAR(30),

    assigned_agent_id INT,

    device_number VARCHAR(50),

    device_type VARCHAR(50),

    os_version VARCHAR(50),

    status VARCHAR(20)
        NOT NULL DEFAULT 'ACTIVE',

    assigned_date DATE,

    last_seen_at TIMESTAMP NULL,

    PRIMARY KEY (device_id),

    UNIQUE KEY uq_device_imei (
        imei_number
    ),

    UNIQUE KEY uq_device_identifier (
        device_identifier
    ),

    KEY idx_device_assigned_agent (
        assigned_agent_id
    ),

    CONSTRAINT fk_device_assigned_agent
        FOREIGN KEY (assigned_agent_id)
        REFERENCES agent(agent_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 7. DOCUMENT TYPE
--
-- type_code is the stable identifier shared between:
--
-- Android
-- Spring Boot
-- MySQL
-- FastAPI alteration service
--
-- analysis_enabled controls whether automated alteration
-- analysis is available for the document type.
-- ============================================================

CREATE TABLE document_type (
    document_type_id INT NOT NULL AUTO_INCREMENT,

    type_code VARCHAR(50) NOT NULL,

    type_name VARCHAR(100) NOT NULL,

    description VARCHAR(255),

    required_flag TINYINT(1)
        NOT NULL DEFAULT 1,

    analysis_enabled TINYINT(1)
        NOT NULL DEFAULT 0,

    status VARCHAR(20)
        NOT NULL DEFAULT 'ACTIVE',

    PRIMARY KEY (document_type_id),

    UNIQUE KEY uq_document_type_code (
        type_code
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 8. DOCUMENT
--
-- Central integration table.
--
-- A document links:
--
-- Customer
-- Agent
-- Device
-- Document Type
-- Capture Evidence
-- GPS
-- Image Quality
-- Integrity Verification
-- Alteration Detection
-- Admin Review
-- Audit Events
-- ============================================================

CREATE TABLE document (
    document_id INT NOT NULL AUTO_INCREMENT,

    customer_id INT NOT NULL,

    agent_id INT NOT NULL,

    device_id INT,

    document_type_id INT NOT NULL,

    file_name VARCHAR(255),

    file_path VARCHAR(500),

    capture_date_time TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    capture_location VARCHAR(255),

    capture_latitude DECIMAL(10,7),

    capture_longitude DECIMAL(10,7),

    image_quality VARCHAR(50),

    image_width INT,

    image_height INT,

    blur_score DECIMAL(12,4),

    brightness_score DECIMAL(8,4),

    blur_passed TINYINT(1),

    brightness_passed TINYINT(1),

    resolution_passed TINYINT(1),

    quality_status VARCHAR(30),

    capture_source VARCHAR(30)
        NOT NULL DEFAULT 'LEGACY_UPLOAD',

    mime_type VARCHAR(100),

    file_size_bytes BIGINT,

    capture_status VARCHAR(50),

    processing_status VARCHAR(50)
        NOT NULL DEFAULT 'CAPTURED',

    uploaded_at TIMESTAMP NULL,

    verification_status VARCHAR(50),

    status VARCHAR(20)
        NOT NULL DEFAULT 'ACTIVE',

    PRIMARY KEY (document_id),

    KEY idx_document_customer (
        customer_id
    ),

    KEY idx_document_agent (
        agent_id
    ),

    KEY idx_document_device (
        device_id
    ),

    KEY idx_document_type (
        document_type_id
    ),

    KEY idx_document_processing_status (
        processing_status
    ),

    KEY idx_document_verification_status (
        verification_status
    ),

    CONSTRAINT fk_document_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id),

    CONSTRAINT fk_document_agent
        FOREIGN KEY (agent_id)
        REFERENCES agent(agent_id),

    CONSTRAINT fk_document_device
        FOREIGN KEY (device_id)
        REFERENCES device(device_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT fk_document_type
        FOREIGN KEY (document_type_id)
        REFERENCES document_type(document_type_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 9. INTEGRITY RESULT
--
-- Stores document integrity and secure-storage information.
--
-- sha256_hash:
-- Original SHA-256 generated from the accepted capture.
--
-- backend_sha256_hash:
-- SHA-256 independently calculated by the backend.
--
-- final_sha256_hash:
-- SHA-256 generated during final/retrieval verification.
--
-- AES encryption metadata is also stored here.
-- The encryption key itself must NOT be stored in this table.
-- ============================================================

CREATE TABLE integrity_result (
    integrity_result_id INT NOT NULL AUTO_INCREMENT,

    document_id INT NOT NULL,

    sha256_hash VARCHAR(64) NOT NULL,

    backend_sha256_hash VARCHAR(64),

    final_sha256_hash VARCHAR(64),

    initial_verification_result VARCHAR(50),

    final_verification_result VARCHAR(50),

    verification_result VARCHAR(50),

    encryption_algorithm VARCHAR(50),

    encryption_iv VARCHAR(255),

    encrypted_file_path VARCHAR(500),

    encryption_key_version VARCHAR(50),

    storage_status VARCHAR(50),

    encrypted_at TIMESTAMP NULL,

    final_verified_at TIMESTAMP NULL,

    processed_at TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (integrity_result_id),

    KEY idx_integrity_document (
        document_id
    ),

    KEY idx_integrity_verification (
        verification_result
    ),

    CONSTRAINT fk_integrity_document
        FOREIGN KEY (document_id)
        REFERENCES document(document_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 10. ALTERATION RESULT
--
-- Stores the overall result produced by the physical document
-- alteration detection service.
--
-- The automated system provides a risk assessment.
-- The final fraud decision remains a manual/admin decision.
-- ============================================================

CREATE TABLE alteration_result (
    alteration_result_id INT NOT NULL AUTO_INCREMENT,

    document_id INT NOT NULL,

    risk_score DECIMAL(5,2),

    risk_level VARCHAR(20),

    recommended_action VARCHAR(100),

    highlighted_image_path VARCHAR(500),

    ocr_status VARCHAR(50),

    ocr_engine VARCHAR(50),

    ocr_full_text MEDIUMTEXT,

    analysis_status VARCHAR(50),

    analysis_message VARCHAR(1000),

    suspicious_region_count INT,

    algorithm_version VARCHAR(50),

    review_status VARCHAR(50)
        NOT NULL DEFAULT 'NOT_REVIEWED',

    reviewed_by_admin_id INT,

    reviewed_at TIMESTAMP NULL,

    review_notes VARCHAR(1000),

    final_decision VARCHAR(50),

    processed_at TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (alteration_result_id),

    KEY idx_alteration_document (
        document_id
    ),

    KEY idx_alteration_risk_level (
        risk_level
    ),

    KEY idx_alteration_review_status (
        review_status
    ),

    KEY idx_alteration_review_admin (
        reviewed_by_admin_id
    ),

    CONSTRAINT fk_alteration_document
        FOREIGN KEY (document_id)
        REFERENCES document(document_id),

    CONSTRAINT fk_alteration_review_admin
        FOREIGN KEY (reviewed_by_admin_id)
        REFERENCES admin(admin_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 11. ALTERATION FINDING
--
-- Stores individual suspicious findings detected within a
-- document.
--
-- Coordinates identify the suspicious image region.
-- ============================================================

CREATE TABLE alteration_finding (
    finding_id INT NOT NULL AUTO_INCREMENT,

    alteration_result_id INT NOT NULL,

    finding_type VARCHAR(100),

    description VARCHAR(255),

    confidence DECIMAL(5,2),

    severity VARCHAR(50),

    x INT,

    y INT,

    width INT,

    height INT,

    created_at TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (finding_id),

    KEY idx_finding_alteration_result (
        alteration_result_id
    ),

    CONSTRAINT fk_finding_alteration_result
        FOREIGN KEY (alteration_result_id)
        REFERENCES alteration_result(alteration_result_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 12. OCR EXTRACTED FIELD
--
-- Stores structured OCR data and the location of each
-- extracted field in the document.
-- ============================================================

CREATE TABLE ocr_extracted_field (
    ocr_field_id INT NOT NULL AUTO_INCREMENT,

    alteration_result_id INT NOT NULL,

    field_name VARCHAR(100),

    extracted_value VARCHAR(255),

    confidence DECIMAL(5,2),

    is_suspicious TINYINT(1)
        NOT NULL DEFAULT 0,

    x INT,

    y INT,

    width INT,

    height INT,

    created_at TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (ocr_field_id),

    KEY idx_ocr_alteration_result (
        alteration_result_id
    ),

    CONSTRAINT fk_ocr_alteration_result
        FOREIGN KEY (alteration_result_id)
        REFERENCES alteration_result(alteration_result_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 13. DOCUMENT TEMPLATE
--
-- Stores approved/reference document templates used by the
-- physical alteration-detection module.
-- ============================================================

CREATE TABLE document_template (
    template_id INT NOT NULL AUTO_INCREMENT,

    document_type_id INT NOT NULL,

    template_name VARCHAR(100) NOT NULL,

    template_version VARCHAR(50),

    template_file_path VARCHAR(255),

    file_path VARCHAR(255),

    created_at TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (template_id),

    KEY idx_template_document_type (
        document_type_id
    ),

    CONSTRAINT fk_template_document_type
        FOREIGN KEY (document_type_id)
        REFERENCES document_type(document_type_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 14. TEMPLATE REGION
--
-- Defines important regions of approved document templates.
--
-- Examples:
--
-- NIC number
-- Photograph
-- Signature
-- Name
-- Date
-- Security/logo area
-- ============================================================

CREATE TABLE template_region (
    template_region_id INT NOT NULL AUTO_INCREMENT,

    template_id INT NOT NULL,

    region_name VARCHAR(100) NOT NULL,

    region_type VARCHAR(50),

    x INT,

    y INT,

    width INT,

    height INT,

    required_flag TINYINT(1)
        NOT NULL DEFAULT 1,

    PRIMARY KEY (template_region_id),

    KEY idx_template_region_template (
        template_id
    ),

    CONSTRAINT fk_template_region_template
        FOREIGN KEY (template_id)
        REFERENCES document_template(template_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 15. SESSION
--
-- Records authenticated ADMIN/AGENT login sessions and the
-- device used for the session.
-- ============================================================

CREATE TABLE session (
    session_id INT NOT NULL AUTO_INCREMENT,

    admin_id INT,

    agent_id INT,

    device_id INT NOT NULL,

    login_time TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    logout_time TIMESTAMP NULL,

    status VARCHAR(20)
        NOT NULL DEFAULT 'ACTIVE',

    PRIMARY KEY (session_id),

    KEY idx_session_admin (
        admin_id
    ),

    KEY idx_session_agent (
        agent_id
    ),

    KEY idx_session_device (
        device_id
    ),

    CONSTRAINT fk_session_admin
        FOREIGN KEY (admin_id)
        REFERENCES admin(admin_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT fk_session_agent
        FOREIGN KEY (agent_id)
        REFERENCES agent(agent_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT fk_session_device
        FOREIGN KEY (device_id)
        REFERENCES device(device_id)
        ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 16. AUDIT LOG
--
-- Records important security and system events.
--
-- Examples:
--
-- LOGIN
-- LOGOUT
-- DOCUMENT_CAPTURED
-- QUALITY_CHECK_FAILED
-- DOCUMENT_UPLOADED
-- INTEGRITY_VERIFIED
-- INTEGRITY_FAILED
-- DOCUMENT_ENCRYPTED
-- ALTERATION_ANALYSIS_STARTED
-- ALTERATION_ANALYSIS_COMPLETED
-- DOCUMENT_FLAGGED
-- DOCUMENT_REVIEWED
-- ============================================================

CREATE TABLE audit_log (
    log_id INT NOT NULL AUTO_INCREMENT,

    admin_id INT,

    agent_id INT,

    document_id INT,

    device_id INT,

    action VARCHAR(100),

    event_status VARCHAR(50),

    description VARCHAR(1000),

    ip_address VARCHAR(50),

    created_at TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (log_id),

    KEY idx_audit_admin (
        admin_id
    ),

    KEY idx_audit_agent (
        agent_id
    ),

    KEY idx_audit_document (
        document_id
    ),

    KEY idx_audit_device (
        device_id
    ),

    KEY idx_audit_action (
        action
    ),

    KEY idx_audit_created_at (
        created_at
    ),

    CONSTRAINT fk_audit_admin
        FOREIGN KEY (admin_id)
        REFERENCES admin(admin_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT fk_audit_agent
        FOREIGN KEY (agent_id)
        REFERENCES agent(agent_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT fk_audit_document
        FOREIGN KEY (document_id)
        REFERENCES document(document_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT fk_audit_device
        FOREIGN KEY (device_id)
        REFERENCES device(device_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- 17. DEFAULT DOCUMENT TYPES
--
-- Only document types with working alteration-analysis
-- templates should have analysis_enabled = 1.
--
-- NIC and Driving Licence are enabled first.
-- ============================================================

INSERT INTO document_type
(
    type_code,
    type_name,
    description,
    required_flag,
    analysis_enabled,
    status
)
VALUES
(
    'NIC',
    'NIC',
    'National Identity Card',
    1,
    1,
    'ACTIVE'
),
(
    'NIC_COPY',
    'NIC Copy',
    'Customer identification document',
    1,
    0,
    'ACTIVE'
),
(
    'DRIVING_LICENCE',
    'Driving Licence',
    'Customer driving licence',
    0,
    1,
    'ACTIVE'
),
(
    'SALARY_SLIP',
    'Salary Slip',
    'Customer salary slip',
    0,
    0,
    'ACTIVE'
),
(
    'BANK_STATEMENT',
    'Bank Statement',
    'Customer bank statement',
    0,
    0,
    'ACTIVE'
),
(
    'BUSINESS_REGISTRATION',
    'Business Registration',
    'Customer business registration document',
    0,
    0,
    'ACTIVE'
),
(
    'VEHICLE_CR_BOOK',
    'Vehicle CR Book',
    'Vehicle certificate of registration',
    0,
    0,
    'ACTIVE'
),
(
    'UTILITY_BILL',
    'Utility Bill',
    'Customer utility bill',
    0,
    0,
    'ACTIVE'
);


-- ============================================================
-- 18. SCHEMA VERIFICATION
-- ============================================================

SHOW TABLES;


SELECT
    document_type_id,
    type_code,
    type_name,
    required_flag,
    analysis_enabled,
    status
FROM document_type
ORDER BY document_type_id;


-- ============================================================
-- END OF FINAL CONSOLIDATED DATABASE SCHEMA
-- ============================================================