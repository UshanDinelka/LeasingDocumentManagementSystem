-- ============================================================
-- LEASING DOCUMENT CAPTURE AND FRAUD DETECTION APPLICATION
-- SEED DATA
--
-- Database: leasing_document_db
-- MySQL: 8.x
--
-- PURPOSE:
-- Insert the minimum development/demo data required to run
-- the Spring Boot backend and Android application after
-- final_schema.sql has recreated the database.
--
-- RUN ORDER:
--
-- 1. final_schema.sql
-- 2. seed_data.sql
--
-- ============================================================


USE leasing_document_db;


START TRANSACTION;


-- ============================================================
-- 1. ADMIN ACCOUNT
--
-- Development / demonstration account
--
-- Email:
-- admin@gmail.com
--
-- Password:
-- admin123
--
-- Password is stored as a BCrypt hash.
-- ============================================================

INSERT INTO admin
(
    admin_id,
    full_name,
    email,
    password_hash,
    status
)
VALUES
(
    1,
    'System Admin',
    'admin@gmail.com',
    '$2a$10$28rlv7v7kPvjXttW8Ss1lebANzTp7Q5w.8fKfWwTXmNwdCu7QCxMG',
    'ACTIVE'
)
ON DUPLICATE KEY UPDATE

    full_name = VALUES(full_name),
    password_hash = VALUES(password_hash),
    status = VALUES(status);



-- ============================================================
-- 2. AGENT ACCOUNTS
--
-- Agent 1
--
-- Email:
-- agent@test.com
--
-- Password:
-- agent123
--
--
-- Agent 2
--
-- Email:
-- nimal@gmail.com
--
-- Password:
-- agent123
-- ============================================================

INSERT INTO agent
(
    agent_id,
    full_name,
    email,
    password_hash,
    nic,
    phone,
    status
)
VALUES
(
    1,
    'Test Agent',
    'agent@test.com',
    '$2a$10$hYk/sHcy11jbeWHiNLspquszAui3ck7BLJn7c.pAkN3FGc/AZqvOG',
    'AGENT123456',
    '0771234567',
    'ACTIVE'
),
(
    2,
    'Nimal Perera',
    'nimal@gmail.com',
    '$2a$10$/t1/r.HPLJjZtxcV/g0YIuBL0LlfbEIlQWT/Xo7hUqydSiuZyFMA2',
    '901234567V',
    '0712345678',
    'ACTIVE'
)
ON DUPLICATE KEY UPDATE

    full_name = VALUES(full_name),
    password_hash = VALUES(password_hash),
    phone = VALUES(phone),
    status = VALUES(status);



-- ============================================================
-- 3. CUSTOMER DATA
--
-- Demo customers used while testing document capture.
-- ============================================================

INSERT INTO customer
(
    customer_id,
    full_name,
    nic,
    phone,
    email,
    address
)
VALUES
(
    1,
    'Test Customer',
    'TEST123456',
    '0712345678',
    'test@gmail.com',
    'Colombo'
),
(
    2,
    'Kamal Perera',
    '901234567V',
    '0771234567',
    'kamal@gmail.com',
    'Colombo'
)
ON DUPLICATE KEY UPDATE

    full_name = VALUES(full_name),
    phone = VALUES(phone),
    email = VALUES(email),
    address = VALUES(address);



-- ============================================================
-- 4. DEVICE
--
-- IMPORTANT:
--
-- device_id = 1 is intentionally maintained because the
-- current Android authentication flow uses device ID 1.
--
-- Later, the final device-registration workflow will replace
-- this fixed development device logic.
-- ============================================================

INSERT INTO device
(
    device_id,
    imei_number,
    device_identifier,
    identifier_type,
    assigned_agent_id,
    device_number,
    device_type,
    os_version,
    status,
    assigned_date,
    last_seen_at
)
VALUES
(
    1,
    '123456789012345',
    'DEVICE-001',
    'DEVELOPMENT_DEVICE_ID',
    1,
    'DEVICE-001',
    'Android Mobile',
    'Android 15',
    'ACTIVE',
    CURRENT_DATE,
    NULL
)
ON DUPLICATE KEY UPDATE

    device_identifier = VALUES(device_identifier),
    identifier_type = VALUES(identifier_type),
    assigned_agent_id = VALUES(assigned_agent_id),
    device_number = VALUES(device_number),
    device_type = VALUES(device_type),
    os_version = VALUES(os_version),
    status = VALUES(status);



-- ============================================================
-- 5. DOCUMENT TYPES
--
-- final_schema.sql already inserts these records.
--
-- These statements ensure that the expected document types
-- are still available if seed_data.sql is executed separately
-- after document-type data has been modified.
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
)
ON DUPLICATE KEY UPDATE

    type_name = VALUES(type_name),
    description = VALUES(description),
    required_flag = VALUES(required_flag),
    analysis_enabled = VALUES(analysis_enabled),
    status = VALUES(status);



-- ============================================================
-- 6. DOCUMENT TEMPLATE
--
-- Initial NIC template metadata.
--
-- The actual template image will later be provided by the
-- alteration-detection component.
-- ============================================================

INSERT INTO document_template
(
    template_id,
    document_type_id,
    template_name,
    template_version,
    template_file_path,
    file_path
)
SELECT
    1,
    dt.document_type_id,
    'NIC Front Template',
    'v1.0',
    'templates/nic_front.png',
    'templates/nic_front.png'
FROM document_type dt
WHERE dt.type_code = 'NIC'
ON DUPLICATE KEY UPDATE

    template_name = VALUES(template_name),
    template_version = VALUES(template_version),
    template_file_path = VALUES(template_file_path),
    file_path = VALUES(file_path);



-- ============================================================
-- 7. INITIAL NIC TEMPLATE REGION
--
-- This preserves the initial database/template concept.
-- Exact production regions will later be synchronized with
-- the alteration-detection module.
-- ============================================================

INSERT INTO template_region
(
    template_region_id,
    template_id,
    region_name,
    region_type,
    x,
    y,
    width,
    height,
    required_flag
)
VALUES
(
    1,
    1,
    'NIC Number Area',
    'TEXT',
    120,
    80,
    200,
    100,
    1
)
ON DUPLICATE KEY UPDATE

    region_name = VALUES(region_name),
    region_type = VALUES(region_type),
    x = VALUES(x),
    y = VALUES(y),
    width = VALUES(width),
    height = VALUES(height),
    required_flag = VALUES(required_flag);



-- ============================================================
-- 8. RESET AUTO-INCREMENT STARTING POINTS
--
-- Ensures newly created records receive IDs after the seeded
-- demonstration records.
-- ============================================================

ALTER TABLE admin
    AUTO_INCREMENT = 2;

ALTER TABLE agent
    AUTO_INCREMENT = 3;

ALTER TABLE customer
    AUTO_INCREMENT = 3;

ALTER TABLE device
    AUTO_INCREMENT = 2;

ALTER TABLE document_template
    AUTO_INCREMENT = 2;

ALTER TABLE template_region
    AUTO_INCREMENT = 2;



COMMIT;



-- ============================================================
-- 9. SEED VERIFICATION
-- ============================================================


-- ------------------------------------------------------------
-- ADMIN
-- ------------------------------------------------------------

SELECT
    admin_id,
    full_name,
    email,
    status
FROM admin
ORDER BY admin_id;



-- ------------------------------------------------------------
-- AGENTS
-- ------------------------------------------------------------

SELECT
    agent_id,
    full_name,
    email,
    nic,
    phone,
    status
FROM agent
ORDER BY agent_id;



-- ------------------------------------------------------------
-- CUSTOMERS
-- ------------------------------------------------------------

SELECT
    customer_id,
    full_name,
    nic,
    phone,
    email,
    address
FROM customer
ORDER BY customer_id;



-- ------------------------------------------------------------
-- DEVICES
-- ------------------------------------------------------------

SELECT
    device_id,
    device_identifier,
    identifier_type,
    assigned_agent_id,
    device_number,
    device_type,
    os_version,
    status
FROM device
ORDER BY device_id;



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
-- TEMPLATE
-- ------------------------------------------------------------

SELECT
    dt.template_id,
    dtype.type_code,
    dt.template_name,
    dt.template_version,
    dt.template_file_path
FROM document_template dt

INNER JOIN document_type dtype
    ON dtype.document_type_id =
       dt.document_type_id

ORDER BY dt.template_id;



-- ============================================================
-- END OF SEED DATA
-- ============================================================