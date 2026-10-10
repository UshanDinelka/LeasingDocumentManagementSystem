-- ============================================================
-- LEASING DOCUMENT CAPTURE AND FRAUD DETECTION APPLICATION
-- Purpose:
-- Upgrade the original project database to the shared
-- integration schema required by the final application.
--
-- IMPORTANT:
-- This script is intended to be executed ONCE against the
-- original pre-Step-3 database.
--
-- Existing application data is preserved.
-- ============================================================


USE leasing_document_db;


-- ============================================================
-- 1. DEVICE
--
-- Adds stronger device identification and agent assignment.
-- ============================================================

ALTER TABLE device
    MODIFY COLUMN imei_number VARCHAR(50) NULL,

    ADD COLUMN device_identifier VARCHAR(128) NULL
        AFTER imei_number,

    ADD COLUMN identifier_type VARCHAR(30) NULL
        AFTER device_identifier,

    ADD COLUMN assigned_agent_id INT NULL
        AFTER identifier_type,

    ADD COLUMN last_seen_at TIMESTAMP NULL
        AFTER assigned_date;


ALTER TABLE device
    ADD UNIQUE KEY uq_device_identifier (
        device_identifier
    );


ALTER TABLE device
    ADD CONSTRAINT fk_device_assigned_agent
        FOREIGN KEY (assigned_agent_id)
        REFERENCES agent(agent_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE;



-- ============================================================
-- 2. DOCUMENT TYPE
--
-- type_code is the stable identifier shared between:
--
-- Android
-- Spring Boot
-- MySQL
-- FastAPI
-- ============================================================

ALTER TABLE document_type
    ADD COLUMN type_code VARCHAR(50) NULL
        AFTER document_type_id,

    ADD COLUMN analysis_enabled TINYINT(1)
        NOT NULL DEFAULT 0
        AFTER required_flag,

    ADD COLUMN status VARCHAR(20)
        NOT NULL DEFAULT 'ACTIVE'
        AFTER analysis_enabled;


ALTER TABLE document_type
    ADD UNIQUE KEY uq_document_type_code (
        type_code
    );


-- ------------------------------------------------------------
-- Existing document types
-- ------------------------------------------------------------

UPDATE document_type
SET
    type_code = 'NIC',
    analysis_enabled = 1,
    status = 'ACTIVE'
WHERE document_type_id = 1;


UPDATE document_type
SET
    type_code = 'NIC_COPY',
    analysis_enabled = 0,
    status = 'ACTIVE'
WHERE document_type_id = 2;



-- ------------------------------------------------------------
-- Additional document types
-- ------------------------------------------------------------

INSERT IGNORE INTO document_type
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
    'DRIVING_LICENCE',
    'Driving Licence',
    'Customer driving licence',
    0,
    1,
    'ACTIVE'
);


INSERT IGNORE INTO document_type
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
    'SALARY_SLIP',
    'Salary Slip',
    'Customer salary slip',
    0,
    0,
    'ACTIVE'
);


INSERT IGNORE INTO document_type
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
    'BANK_STATEMENT',
    'Bank Statement',
    'Customer bank statement',
    0,
    0,
    'ACTIVE'
);


INSERT IGNORE INTO document_type
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
    'BUSINESS_REGISTRATION',
    'Business Registration',
    'Customer business registration document',
    0,
    0,
    'ACTIVE'
);


INSERT IGNORE INTO document_type
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
    'VEHICLE_CR_BOOK',
    'Vehicle CR Book',
    'Vehicle certificate of registration',
    0,
    0,
    'ACTIVE'
);


INSERT IGNORE INTO document_type
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
    'UTILITY_BILL',
    'Utility Bill',
    'Customer utility bill',
    0,
    0,
    'ACTIVE'
);



-- ============================================================
-- 3. DOCUMENT
--
-- The document record becomes the main integration record.
--
-- It connects:
--
-- Customer
-- Agent
-- Device
-- Document Type
-- GPS
-- Image Quality
-- Integrity Verification
-- Alteration Detection
-- ============================================================

ALTER TABLE document
    MODIFY COLUMN file_path VARCHAR(500) NULL,

    ADD COLUMN device_id INT NULL
        AFTER agent_id,

    ADD COLUMN capture_latitude DECIMAL(10,7) NULL
        AFTER capture_location,

    ADD COLUMN capture_longitude DECIMAL(10,7) NULL
        AFTER capture_latitude,

    ADD COLUMN image_width INT NULL
        AFTER image_quality,

    ADD COLUMN image_height INT NULL
        AFTER image_width,

    ADD COLUMN blur_score DECIMAL(12,4) NULL
        AFTER image_height,

    ADD COLUMN brightness_score DECIMAL(8,4) NULL
        AFTER blur_score,

    ADD COLUMN blur_passed TINYINT(1) NULL
        AFTER brightness_score,

    ADD COLUMN brightness_passed TINYINT(1) NULL
        AFTER blur_passed,

    ADD COLUMN resolution_passed TINYINT(1) NULL
        AFTER brightness_passed,

    ADD COLUMN quality_status VARCHAR(30) NULL
        AFTER resolution_passed,

    ADD COLUMN capture_source VARCHAR(30)
        NOT NULL DEFAULT 'LEGACY_UPLOAD'
        AFTER quality_status,

    ADD COLUMN mime_type VARCHAR(100) NULL
        AFTER capture_source,

    ADD COLUMN file_size_bytes BIGINT NULL
        AFTER mime_type,

    ADD COLUMN processing_status VARCHAR(50)
        NOT NULL DEFAULT 'CAPTURED'
        AFTER capture_status,

    ADD COLUMN uploaded_at TIMESTAMP NULL
        AFTER processing_status;


ALTER TABLE document
    ADD INDEX idx_document_device (
        device_id
    ),

    ADD INDEX idx_document_processing_status (
        processing_status
    ),

    ADD INDEX idx_document_verification_status (
        verification_status
    );


ALTER TABLE document
    ADD CONSTRAINT fk_document_device
        FOREIGN KEY (device_id)
        REFERENCES device(device_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE;


-- Existing documents were created through the old
-- upload workflow.

UPDATE document
SET capture_source = 'LEGACY_UPLOAD'
WHERE document_id > 0
  AND (
        capture_source IS NULL
        OR capture_source = ''
      );



-- ============================================================
-- 4. INTEGRITY RESULT
--
-- sha256_hash
--     Original hash generated from the accepted capture.
--
-- backend_sha256_hash
--     Independently generated backend hash.
--
-- final_sha256_hash
--     Final/retrieval integrity verification hash.
-- ============================================================

ALTER TABLE integrity_result
    MODIFY COLUMN sha256_hash VARCHAR(64) NOT NULL,

    ADD COLUMN backend_sha256_hash VARCHAR(64) NULL
        AFTER sha256_hash,

    ADD COLUMN final_sha256_hash VARCHAR(64) NULL
        AFTER backend_sha256_hash,

    ADD COLUMN initial_verification_result VARCHAR(50) NULL
        AFTER final_sha256_hash,

    ADD COLUMN final_verification_result VARCHAR(50) NULL
        AFTER initial_verification_result,

    ADD COLUMN encryption_algorithm VARCHAR(50) NULL
        AFTER verification_result,

    ADD COLUMN encryption_iv VARCHAR(255) NULL
        AFTER encryption_algorithm,

    ADD COLUMN encrypted_file_path VARCHAR(500) NULL
        AFTER encryption_iv,

    ADD COLUMN encryption_key_version VARCHAR(50) NULL
        AFTER encrypted_file_path,

    ADD COLUMN storage_status VARCHAR(50) NULL
        AFTER encryption_key_version,

    ADD COLUMN encrypted_at TIMESTAMP NULL
        AFTER storage_status,

    ADD COLUMN final_verified_at TIMESTAMP NULL
        AFTER encrypted_at;


-- Existing integrity records represent the original
-- verification result.

UPDATE integrity_result
SET initial_verification_result =
        verification_result
WHERE integrity_result_id > 0
  AND initial_verification_result IS NULL;


-- The original schema already contains an index on document_id,
-- therefore only the new verification index is required.

CREATE INDEX idx_integrity_verification
ON integrity_result (
    verification_result
);



-- ============================================================
-- 5. ALTERATION RESULT
--
-- Stores the overall result produced by the Python/FastAPI
-- alteration detection module.
-- ============================================================

ALTER TABLE alteration_result
    ADD COLUMN recommended_action VARCHAR(100) NULL
        AFTER risk_level,

    ADD COLUMN highlighted_image_path VARCHAR(500) NULL
        AFTER recommended_action,

    ADD COLUMN ocr_status VARCHAR(50) NULL
        AFTER highlighted_image_path,

    ADD COLUMN ocr_engine VARCHAR(50) NULL
        AFTER ocr_status,

    ADD COLUMN ocr_full_text MEDIUMTEXT NULL
        AFTER ocr_engine,

    ADD COLUMN analysis_message VARCHAR(1000) NULL
        AFTER analysis_status,

    ADD COLUMN review_status VARCHAR(50)
        NOT NULL DEFAULT 'NOT_REVIEWED'
        AFTER algorithm_version,

    ADD COLUMN reviewed_by_admin_id INT NULL
        AFTER review_status,

    ADD COLUMN reviewed_at TIMESTAMP NULL
        AFTER reviewed_by_admin_id,

    ADD COLUMN review_notes VARCHAR(1000) NULL
        AFTER reviewed_at,

    ADD COLUMN final_decision VARCHAR(50) NULL
        AFTER review_notes;


CREATE INDEX idx_alteration_risk_level
ON alteration_result (
    risk_level
);


CREATE INDEX idx_alteration_review_status
ON alteration_result (
    review_status
);


CREATE INDEX idx_alteration_review_admin
ON alteration_result (
    reviewed_by_admin_id
);


ALTER TABLE alteration_result
    ADD CONSTRAINT fk_alteration_review_admin
        FOREIGN KEY (reviewed_by_admin_id)
        REFERENCES admin(admin_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE;



-- ============================================================
-- 6. AUDIT LOG
--
-- Allows security events to reference the affected
-- document and device directly.
-- ============================================================

ALTER TABLE audit_log
    MODIFY COLUMN description VARCHAR(1000) NULL,

    ADD COLUMN document_id INT NULL
        AFTER agent_id,

    ADD COLUMN device_id INT NULL
        AFTER document_id,

    ADD COLUMN event_status VARCHAR(50) NULL
        AFTER action;


CREATE INDEX idx_audit_document
ON audit_log (
    document_id
);


CREATE INDEX idx_audit_device
ON audit_log (
    device_id
);


CREATE INDEX idx_audit_action
ON audit_log (
    action
);


CREATE INDEX idx_audit_created_at
ON audit_log (
    created_at
);


ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_document
        FOREIGN KEY (document_id)
        REFERENCES document(document_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE;


ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_device
        FOREIGN KEY (device_id)
        REFERENCES device(device_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE;


ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_admin
        FOREIGN KEY (admin_id)
        REFERENCES admin(admin_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE;



-- ============================================================
-- 7. MIGRATION VERIFICATION
-- ============================================================


-- ------------------------------------------------------------
-- DOCUMENT TYPES
-- ------------------------------------------------------------

SELECT
    document_type_id,
    type_code,
    type_name,
    required_flag,
    analysis_enabled,
    status
FROM document_type
ORDER BY document_type_id;



-- ------------------------------------------------------------
-- VERIFY IMPORTANT NEW COLUMNS
-- ------------------------------------------------------------

SHOW COLUMNS FROM device
LIKE 'device_identifier';


SHOW COLUMNS FROM document_type
LIKE 'type_code';


SHOW COLUMNS FROM document
LIKE 'device_id';


SHOW COLUMNS FROM document
LIKE 'capture_latitude';


SHOW COLUMNS FROM document
LIKE 'processing_status';


SHOW COLUMNS FROM integrity_result
LIKE 'backend_sha256_hash';


SHOW COLUMNS FROM integrity_result
LIKE 'final_sha256_hash';


SHOW COLUMNS FROM alteration_result
LIKE 'recommended_action';


SHOW COLUMNS FROM alteration_result
LIKE 'review_status';


SHOW COLUMNS FROM audit_log
LIKE 'document_id';


SHOW COLUMNS FROM audit_log
LIKE 'device_id';



-- ------------------------------------------------------------
-- VERIFY EXISTING DOCUMENT DATA
-- ------------------------------------------------------------

SELECT
    d.document_id,
    d.customer_id,
    d.agent_id,
    d.device_id,
    dt.type_code,
    dt.type_name,
    d.capture_source,
    d.processing_status,
    d.verification_status
FROM document d

INNER JOIN document_type dt
    ON dt.document_type_id =
       d.document_type_id

ORDER BY d.document_id;



-- ------------------------------------------------------------
-- VERIFY EXISTING INTEGRITY DATA
-- ------------------------------------------------------------

SELECT
    integrity_result_id,
    document_id,
    sha256_hash,
    backend_sha256_hash,
    final_sha256_hash,
    initial_verification_result,
    verification_result,
    storage_status
FROM integrity_result

ORDER BY integrity_result_id;



