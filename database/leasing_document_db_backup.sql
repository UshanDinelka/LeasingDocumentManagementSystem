-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: leasing_document_db
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `admin`
--

DROP TABLE IF EXISTS `admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin` (
  `admin_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `status` varchar(20) DEFAULT 'ACTIVE',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`admin_id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin`
--

LOCK TABLES `admin` WRITE;
/*!40000 ALTER TABLE `admin` DISABLE KEYS */;
INSERT INTO `admin` VALUES (1,'System Admin','admin@gmail.com','$2a$10$28rlv7v7kPvjXttW8Ss1lebANzTp7Q5w.8fKfWwTXmNwdCu7QCxMG','ACTIVE','2026-09-19 10:00:00');
/*!40000 ALTER TABLE `admin` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `agent`
--

DROP TABLE IF EXISTS `agent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent` (
  `agent_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `nic` varchar(20) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` varchar(20) DEFAULT 'ACTIVE',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`agent_id`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `nic` (`nic`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `agent`
--

LOCK TABLES `agent` WRITE;
/*!40000 ALTER TABLE `agent` DISABLE KEYS */;
INSERT INTO `agent` VALUES (1,'Test Agent','agent@test.com','$2a$10$hYk/sHcy11jbeWHiNLspquszAui3ck7BLJn7c.pAkN3FGc/AZqvOG','AGENT123456','0771234567','ACTIVE','2026-09-19 10:47:58'),(2,'Nimal Perera','nimal@gmail.com','$2a$10$/t1/r.HPLJjZtxcV/g0YIuBL0LlfbEIlQWT/Xo7hUqydSiuZyFMA2','901234567V','0712345678','ACTIVE','2026-09-19 09:30:00');
/*!40000 ALTER TABLE `agent` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `alteration_finding`
--

DROP TABLE IF EXISTS `alteration_finding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alteration_finding` (
  `finding_id` int NOT NULL AUTO_INCREMENT,
  `alteration_result_id` int NOT NULL,
  `finding_type` varchar(100) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `confidence` decimal(5,2) DEFAULT NULL,
  `severity` varchar(50) DEFAULT NULL,
  `x` int DEFAULT NULL,
  `y` int DEFAULT NULL,
  `width` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`finding_id`),
  KEY `alteration_result_id` (`alteration_result_id`),
  CONSTRAINT `alteration_finding_ibfk_1` FOREIGN KEY (`alteration_result_id`) REFERENCES `alteration_result` (`alteration_result_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `alteration_finding`
--

LOCK TABLES `alteration_finding` WRITE;
/*!40000 ALTER TABLE `alteration_finding` DISABLE KEYS */;
INSERT INTO `alteration_finding` VALUES (1,1,'TEXT_MODIFICATION','Signature area has possible modification',0.95,'HIGH',120,80,200,100,'2026-09-19 08:00:00');
/*!40000 ALTER TABLE `alteration_finding` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `alteration_result`
--

DROP TABLE IF EXISTS `alteration_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alteration_result` (
  `alteration_result_id` int NOT NULL AUTO_INCREMENT,
  `document_id` int NOT NULL,
  `risk_score` decimal(5,2) DEFAULT NULL,
  `risk_level` varchar(20) DEFAULT NULL,
  `analysis_status` varchar(50) DEFAULT NULL,
  `suspicious_region_count` int DEFAULT NULL,
  `algorithm_version` varchar(50) DEFAULT NULL,
  `processed_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`alteration_result_id`),
  KEY `document_id` (`document_id`),
  CONSTRAINT `alteration_result_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `document` (`document_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `alteration_result`
--

LOCK TABLES `alteration_result` WRITE;
/*!40000 ALTER TABLE `alteration_result` DISABLE KEYS */;
INSERT INTO `alteration_result` VALUES (1,1,20.00,'LOW','COMPLETED',0,'v1.0','2026-09-19 07:30:00'),(2,2,25.00,'LOW','COMPLETED',1,'v1.0','2026-10-01 18:30:06'),(3,5,80.00,'HIGH','COMPLETED',4,'v1.0','2026-10-01 18:30:06');
/*!40000 ALTER TABLE `alteration_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `admin_id` int DEFAULT NULL,
  `agent_id` int DEFAULT NULL,
  `action` varchar(100) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `ip_address` varchar(50) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`),
  KEY `agent_id` (`agent_id`),
  CONSTRAINT `audit_log_ibfk_1` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`agent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
INSERT INTO `audit_log` VALUES (1,NULL,1,'DOCUMENT_UPLOAD','BMW.jpg document uploaded successfully','127.0.0.1','2026-09-19 07:00:00'),(2,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 13:15:58'),(3,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 13:40:35'),(4,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 14:13:04'),(5,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 14:17:29'),(6,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 14:34:20'),(7,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 14:34:38'),(8,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 14:35:32'),(9,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 17:58:39'),(10,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 18:03:38'),(11,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 18:07:30'),(12,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 18:17:17'),(13,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 18:23:55'),(14,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 18:28:57'),(15,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 18:34:34'),(16,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 18:39:40'),(17,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 18:41:16'),(18,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 18:49:49'),(19,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 18:55:43'),(20,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 19:02:36'),(21,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 19:03:12'),(22,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 19:10:34'),(23,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 19:16:45'),(24,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 19:54:37'),(25,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-09-30 20:05:52'),(26,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 20:13:58'),(27,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 20:15:40'),(28,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 20:29:54'),(29,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 20:49:51'),(30,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 21:45:36'),(31,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-09-30 21:56:14'),(32,1,NULL,'LOGOUT','ADMIN logged out successfully','LOCAL','2026-09-30 21:56:17'),(33,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 11:05:00'),(34,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 11:26:07'),(35,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 12:09:27'),(36,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 12:14:50'),(37,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 12:18:03'),(38,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 12:18:19'),(39,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 12:22:27'),(40,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 12:22:45'),(41,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 12:27:55'),(42,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 12:28:19'),(43,NULL,2,'LOGOUT','AGENT logged out successfully','LOCAL','2026-10-01 12:29:44'),(44,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 12:35:34'),(45,NULL,2,'LOGOUT','AGENT logged out successfully','LOCAL','2026-10-01 12:35:49'),(46,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-10-01 12:38:07'),(47,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-10-01 12:44:38'),(48,1,NULL,'LOGOUT','ADMIN logged out successfully','LOCAL','2026-10-01 12:44:44'),(49,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 13:01:24'),(50,NULL,1,'LOGOUT','AGENT logged out successfully','LOCAL','2026-10-01 13:02:40'),(51,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 13:02:50'),(52,NULL,2,'LOGOUT','AGENT logged out successfully','LOCAL','2026-10-01 13:04:41'),(53,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 13:09:05'),(54,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 13:12:00'),(55,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 13:23:12'),(56,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 13:33:08'),(57,NULL,2,'LOGOUT','AGENT logged out successfully','LOCAL','2026-10-01 13:35:17'),(58,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 13:42:37'),(59,NULL,2,'LOGOUT','AGENT logged out successfully','LOCAL','2026-10-01 13:43:26'),(60,NULL,2,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-01 13:46:32'),(61,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-10-03 21:34:41'),(62,1,NULL,'LOGOUT','ADMIN logged out successfully','LOCAL','2026-10-03 21:34:58'),(63,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-03 21:35:19'),(64,NULL,1,'LOGOUT','AGENT logged out successfully','LOCAL','2026-10-03 21:35:29'),(65,1,NULL,'LOGIN','ADMIN logged in successfully','LOCAL','2026-10-03 21:35:56'),(66,1,NULL,'LOGOUT','ADMIN logged out successfully','LOCAL','2026-10-03 21:37:00'),(67,NULL,1,'LOGIN','AGENT logged in successfully','LOCAL','2026-10-03 21:37:11'),(68,NULL,1,'LOGOUT','AGENT logged out successfully','LOCAL','2026-10-03 21:43:27');
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `customer`
--

DROP TABLE IF EXISTS `customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer` (
  `customer_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) NOT NULL,
  `nic` varchar(20) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `nic` (`nic`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `customer`
--

LOCK TABLES `customer` WRITE;
/*!40000 ALTER TABLE `customer` DISABLE KEYS */;
INSERT INTO `customer` VALUES (1,'Test Customer','TEST123456','0712345678','test@gmail.com','Colombo','2026-09-19 10:46:41'),(2,'Kamal Perera','901234567V','0771234567','kamal@gmail.com','Colombo','2026-09-19 09:00:00');
/*!40000 ALTER TABLE `customer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `device`
--

DROP TABLE IF EXISTS `device`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `device` (
  `device_id` int NOT NULL AUTO_INCREMENT,
  `imei_number` varchar(50) NOT NULL,
  `device_number` varchar(50) DEFAULT NULL,
  `device_type` varchar(50) DEFAULT NULL,
  `os_version` varchar(50) DEFAULT NULL,
  `status` varchar(20) DEFAULT 'ACTIVE',
  `assigned_date` date DEFAULT NULL,
  PRIMARY KEY (`device_id`),
  UNIQUE KEY `imei_number` (`imei_number`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `device`
--

LOCK TABLES `device` WRITE;
/*!40000 ALTER TABLE `device` DISABLE KEYS */;
INSERT INTO `device` VALUES (1,'123456789012345','DEVICE-001','Android Mobile','Android 15','ACTIVE','2026-09-19');
/*!40000 ALTER TABLE `device` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document`
--

DROP TABLE IF EXISTS `document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document` (
  `document_id` int NOT NULL AUTO_INCREMENT,
  `customer_id` int NOT NULL,
  `agent_id` int NOT NULL,
  `document_type_id` int NOT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_path` varchar(255) DEFAULT NULL,
  `capture_date_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `capture_location` varchar(255) DEFAULT NULL,
  `image_quality` varchar(50) DEFAULT NULL,
  `capture_status` varchar(50) DEFAULT NULL,
  `verification_status` varchar(50) DEFAULT NULL,
  `status` varchar(20) DEFAULT 'ACTIVE',
  PRIMARY KEY (`document_id`),
  KEY `customer_id` (`customer_id`),
  KEY `agent_id` (`agent_id`),
  KEY `document_type_id` (`document_type_id`),
  CONSTRAINT `document_ibfk_1` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`customer_id`),
  CONSTRAINT `document_ibfk_2` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`agent_id`),
  CONSTRAINT `document_ibfk_3` FOREIGN KEY (`document_type_id`) REFERENCES `document_type` (`document_type_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document`
--

LOCK TABLES `document` WRITE;
/*!40000 ALTER TABLE `document` DISABLE KEYS */;
INSERT INTO `document` VALUES (1,1,1,1,'test-document.jpg','test/test-document.jpg',NULL,'Colombo','GOOD','CAPTURED','PENDING','DELETED'),(2,1,1,1,'BMW.jpg','uploads\\BMW.jpg',NULL,NULL,NULL,'CAPTURED','PENDING','ACTIVE'),(3,1,1,1,'chrysanthemums_trees_sparkle.jpg','uploads\\chrysanthemums_trees_sparkle.jpg',NULL,NULL,NULL,'CAPTURED','PENDING','ACTIVE'),(4,2,1,2,'e2a92f7f-c525-4a57-9867-34d2e3c58545_images.jpeg','uploads\\e2a92f7f-c525-4a57-9867-34d2e3c58545_images.jpeg',NULL,NULL,NULL,'CAPTURED','PENDING','ACTIVE'),(5,2,2,2,'706d5b42-9137-4c8a-95ce-26431901a69f_images.jpeg','uploads\\706d5b42-9137-4c8a-95ce-26431901a69f_images.jpeg',NULL,NULL,NULL,'CAPTURED','PENDING','ACTIVE'),(7,2,1,2,'43719924-7b39-469a-937a-c1d6029c6ee6_sample__1_.pdf','uploads\\43719924-7b39-469a-937a-c1d6029c6ee6_sample__1_.pdf',NULL,NULL,NULL,'CAPTURED','PENDING','ACTIVE');
/*!40000 ALTER TABLE `document` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_template`
--

DROP TABLE IF EXISTS `document_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_template` (
  `template_id` int NOT NULL AUTO_INCREMENT,
  `document_type_id` int NOT NULL,
  `template_name` varchar(100) NOT NULL,
  `template_version` varchar(50) DEFAULT NULL,
  `template_file_path` varchar(255) DEFAULT NULL,
  `file_path` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`template_id`),
  KEY `document_type_id` (`document_type_id`),
  CONSTRAINT `document_template_ibfk_1` FOREIGN KEY (`document_type_id`) REFERENCES `document_type` (`document_type_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_template`
--

LOCK TABLES `document_template` WRITE;
/*!40000 ALTER TABLE `document_template` DISABLE KEYS */;
INSERT INTO `document_template` VALUES (1,2,'NIC Front Template','v1.0','/templates/nic_front.png','/uploads/templates/nic_front.png','2026-09-19 11:30:00');
/*!40000 ALTER TABLE `document_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_type`
--

DROP TABLE IF EXISTS `document_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_type` (
  `document_type_id` int NOT NULL AUTO_INCREMENT,
  `type_name` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `required_flag` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`document_type_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_type`
--

LOCK TABLES `document_type` WRITE;
/*!40000 ALTER TABLE `document_type` DISABLE KEYS */;
INSERT INTO `document_type` VALUES (1,'NIC','National Identity Card',1),(2,'NIC Copy','Customer identification document',1);
/*!40000 ALTER TABLE `document_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `integrity_result`
--

DROP TABLE IF EXISTS `integrity_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integrity_result` (
  `integrity_result_id` int NOT NULL AUTO_INCREMENT,
  `document_id` int NOT NULL,
  `sha256_hash` varchar(255) NOT NULL,
  `verification_result` varchar(50) DEFAULT NULL,
  `processed_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`integrity_result_id`),
  KEY `document_id` (`document_id`),
  CONSTRAINT `integrity_result_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `document` (`document_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `integrity_result`
--

LOCK TABLES `integrity_result` WRITE;
/*!40000 ALTER TABLE `integrity_result` DISABLE KEYS */;
INSERT INTO `integrity_result` VALUES (1,1,'abc123hashvalue','VERIFIED','2026-09-19 01:00:00'),(2,2,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','VERIFIED','2026-10-01 18:30:06'),(3,5,'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','VERIFIED','2026-10-01 18:30:06');
/*!40000 ALTER TABLE `integrity_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ocr_extracted_field`
--

DROP TABLE IF EXISTS `ocr_extracted_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ocr_extracted_field` (
  `ocr_field_id` int NOT NULL AUTO_INCREMENT,
  `alteration_result_id` int NOT NULL,
  `field_name` varchar(100) DEFAULT NULL,
  `extracted_value` varchar(255) DEFAULT NULL,
  `confidence` decimal(5,2) DEFAULT NULL,
  `is_suspicious` tinyint(1) DEFAULT '0',
  `x` int DEFAULT NULL,
  `y` int DEFAULT NULL,
  `width` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ocr_field_id`),
  KEY `alteration_result_id` (`alteration_result_id`),
  CONSTRAINT `ocr_extracted_field_ibfk_1` FOREIGN KEY (`alteration_result_id`) REFERENCES `alteration_result` (`alteration_result_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ocr_extracted_field`
--

LOCK TABLES `ocr_extracted_field` WRITE;
/*!40000 ALTER TABLE `ocr_extracted_field` DISABLE KEYS */;
INSERT INTO `ocr_extracted_field` VALUES (1,1,'NIC_Number','123456789V',0.98,0,50,100,150,40,'2026-09-19 08:30:00');
/*!40000 ALTER TABLE `ocr_extracted_field` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `session`
--

DROP TABLE IF EXISTS `session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `session` (
  `session_id` int NOT NULL AUTO_INCREMENT,
  `admin_id` int DEFAULT NULL,
  `agent_id` int DEFAULT NULL,
  `device_id` int NOT NULL,
  `login_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `logout_time` timestamp NULL DEFAULT NULL,
  `status` varchar(20) DEFAULT 'ACTIVE',
  PRIMARY KEY (`session_id`),
  KEY `agent_id` (`agent_id`),
  KEY `device_id` (`device_id`),
  CONSTRAINT `session_ibfk_1` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`agent_id`),
  CONSTRAINT `session_ibfk_2` FOREIGN KEY (`device_id`) REFERENCES `device` (`device_id`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `session`
--

LOCK TABLES `session` WRITE;
/*!40000 ALTER TABLE `session` DISABLE KEYS */;
INSERT INTO `session` VALUES (1,NULL,2,1,'2026-09-19 10:30:00','2026-09-19 11:00:00','ACTIVE'),(2,NULL,1,1,'2026-09-30 12:44:42',NULL,'ACTIVE'),(3,NULL,1,1,'2026-09-30 13:14:32',NULL,'ACTIVE'),(4,NULL,1,1,'2026-09-30 13:14:50',NULL,'ACTIVE'),(5,NULL,1,1,'2026-09-30 13:15:58',NULL,'ACTIVE'),(6,NULL,1,1,'2026-09-30 13:40:35',NULL,'ACTIVE'),(7,NULL,1,1,'2026-09-30 14:13:04',NULL,'ACTIVE'),(8,1,NULL,1,'2026-09-30 14:17:29',NULL,'ACTIVE'),(9,NULL,1,1,'2026-09-30 14:34:20',NULL,'ACTIVE'),(10,NULL,1,1,'2026-09-30 14:34:38',NULL,'ACTIVE'),(11,1,NULL,1,'2026-09-30 14:35:32',NULL,'ACTIVE'),(12,NULL,1,1,'2026-09-30 17:58:39',NULL,'ACTIVE'),(13,NULL,1,1,'2026-09-30 18:03:38',NULL,'ACTIVE'),(14,1,NULL,1,'2026-09-30 18:07:30',NULL,'ACTIVE'),(15,1,NULL,1,'2026-09-30 18:17:17',NULL,'ACTIVE'),(16,1,NULL,1,'2026-09-30 18:23:55',NULL,'ACTIVE'),(17,1,NULL,1,'2026-09-30 18:28:57',NULL,'ACTIVE'),(18,NULL,1,1,'2026-09-30 18:34:34',NULL,'ACTIVE'),(19,NULL,1,1,'2026-09-30 18:39:40',NULL,'ACTIVE'),(20,NULL,1,1,'2026-09-30 18:41:16',NULL,'ACTIVE'),(21,NULL,1,1,'2026-09-30 18:49:49',NULL,'ACTIVE'),(22,NULL,1,1,'2026-09-30 18:55:43',NULL,'ACTIVE'),(23,NULL,1,1,'2026-09-30 19:02:36',NULL,'ACTIVE'),(24,NULL,1,1,'2026-09-30 19:03:12',NULL,'ACTIVE'),(25,NULL,1,1,'2026-09-30 19:10:34',NULL,'ACTIVE'),(26,1,NULL,1,'2026-09-30 19:16:45',NULL,'ACTIVE'),(27,NULL,1,1,'2026-09-30 19:54:37',NULL,'ACTIVE'),(28,NULL,1,1,'2026-09-30 20:05:52',NULL,'ACTIVE'),(29,1,NULL,1,'2026-09-30 20:13:58',NULL,'ACTIVE'),(30,1,NULL,1,'2026-09-30 20:15:40',NULL,'ACTIVE'),(31,1,NULL,1,'2026-09-30 20:29:54',NULL,'ACTIVE'),(32,1,NULL,1,'2026-09-30 20:49:51',NULL,'ACTIVE'),(33,1,NULL,1,'2026-09-30 21:45:36',NULL,'ACTIVE'),(34,1,NULL,1,'2026-09-30 21:56:13','2026-09-30 21:56:17','LOGGED_OUT'),(35,NULL,1,1,'2026-10-01 11:05:00',NULL,'ACTIVE'),(36,NULL,1,1,'2026-10-01 11:26:07',NULL,'ACTIVE'),(37,NULL,1,1,'2026-10-01 12:09:27',NULL,'ACTIVE'),(38,NULL,2,1,'2026-10-01 12:14:50',NULL,'ACTIVE'),(39,NULL,2,1,'2026-10-01 12:18:03',NULL,'ACTIVE'),(40,NULL,2,1,'2026-10-01 12:18:19',NULL,'ACTIVE'),(41,NULL,2,1,'2026-10-01 12:22:27',NULL,'ACTIVE'),(42,NULL,2,1,'2026-10-01 12:22:45',NULL,'ACTIVE'),(43,NULL,2,1,'2026-10-01 12:27:55',NULL,'ACTIVE'),(44,NULL,2,1,'2026-10-01 12:28:19','2026-10-01 12:29:44','LOGGED_OUT'),(45,NULL,2,1,'2026-10-01 12:35:34','2026-10-01 12:35:49','LOGGED_OUT'),(46,1,NULL,1,'2026-10-01 12:38:07',NULL,'ACTIVE'),(47,1,NULL,1,'2026-10-01 12:44:38','2026-10-01 12:44:44','LOGGED_OUT'),(48,NULL,1,1,'2026-10-01 13:01:24','2026-10-01 13:02:40','LOGGED_OUT'),(49,NULL,2,1,'2026-10-01 13:02:50','2026-10-01 13:04:41','LOGGED_OUT'),(50,NULL,1,1,'2026-10-01 13:09:05',NULL,'ACTIVE'),(51,NULL,2,1,'2026-10-01 13:12:00','2026-10-01 13:35:17','LOGGED_OUT'),(52,NULL,1,1,'2026-10-01 13:23:12',NULL,'ACTIVE'),(53,NULL,1,1,'2026-10-01 13:33:08',NULL,'ACTIVE'),(54,NULL,2,1,'2026-10-01 13:42:37','2026-10-01 13:43:26','LOGGED_OUT'),(55,NULL,2,1,'2026-10-01 13:46:32',NULL,'ACTIVE'),(56,1,NULL,1,'2026-10-03 21:34:40','2026-10-03 21:34:58','LOGGED_OUT'),(57,NULL,1,1,'2026-10-03 21:35:19','2026-10-03 21:35:29','LOGGED_OUT'),(58,1,NULL,1,'2026-10-03 21:35:56','2026-10-03 21:37:00','LOGGED_OUT'),(59,NULL,1,1,'2026-10-03 21:37:11','2026-10-03 21:43:27','LOGGED_OUT');
/*!40000 ALTER TABLE `session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `template_region`
--

DROP TABLE IF EXISTS `template_region`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_region` (
  `template_region_id` int NOT NULL AUTO_INCREMENT,
  `template_id` int NOT NULL,
  `region_name` varchar(100) NOT NULL,
  `region_type` varchar(50) DEFAULT NULL,
  `x` int DEFAULT NULL,
  `y` int DEFAULT NULL,
  `width` int DEFAULT NULL,
  `required_flag` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`template_region_id`),
  KEY `template_id` (`template_id`),
  CONSTRAINT `template_region_ibfk_1` FOREIGN KEY (`template_id`) REFERENCES `document_template` (`template_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `template_region`
--

LOCK TABLES `template_region` WRITE;
/*!40000 ALTER TABLE `template_region` DISABLE KEYS */;
INSERT INTO `template_region` VALUES (1,1,'NIC Number Area','TEXT',120,80,200,1);
/*!40000 ALTER TABLE `template_region` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-04  9:26:09
