package com.leasingdocument.backend.contract;

public final class WorkflowConstants {

    private WorkflowConstants() {
    }


    // =========================================================
    // DOCUMENT TYPE CODES
    //
    // These values are shared between:
    // Android
    // Spring Boot
    // MySQL
    // FastAPI
    // =========================================================

    public static final class DocumentTypeCode {

        public static final String NIC =
                "NIC";

        public static final String NIC_COPY =
                "NIC_COPY";

        public static final String DRIVING_LICENCE =
                "DRIVING_LICENCE";

        public static final String SALARY_SLIP =
                "SALARY_SLIP";

        public static final String BANK_STATEMENT =
                "BANK_STATEMENT";

        public static final String BUSINESS_REGISTRATION =
                "BUSINESS_REGISTRATION";

        public static final String VEHICLE_CR_BOOK =
                "VEHICLE_CR_BOOK";

        public static final String UTILITY_BILL =
                "UTILITY_BILL";

        private DocumentTypeCode() {
        }
    }


    // =========================================================
    // CAPTURE SOURCE
    // =========================================================

    public static final class CaptureSource {

        // Final application workflow.
        public static final String LIVE_CAMERA =
                "LIVE_CAMERA";

        // Existing test / historical records only.
        public static final String LEGACY_UPLOAD =
                "LEGACY_UPLOAD";

        private CaptureSource() {
        }
    }


    // =========================================================
    // IMAGE QUALITY STATUS
    // =========================================================

    public static final class QualityStatus {

        public static final String PENDING =
                "PENDING";

        public static final String PASSED =
                "PASSED";

        public static final String FAILED =
                "FAILED";

        private QualityStatus() {
        }
    }


    // =========================================================
    // DOCUMENT PROCESSING STATUS
    // =========================================================

    public static final class ProcessingStatus {

        public static final String CAPTURED =
                "CAPTURED";

        public static final String QUALITY_VALIDATED =
                "QUALITY_VALIDATED";

        public static final String INTEGRITY_PENDING =
                "INTEGRITY_PENDING";

        public static final String INTEGRITY_VERIFIED =
                "INTEGRITY_VERIFIED";

        public static final String ENCRYPTED =
                "ENCRYPTED";

        public static final String ANALYSIS_PENDING =
                "ANALYSIS_PENDING";

        public static final String ANALYSIS_COMPLETED =
                "ANALYSIS_COMPLETED";

        public static final String REVIEW_REQUIRED =
                "REVIEW_REQUIRED";

        public static final String COMPLETED =
                "COMPLETED";

        public static final String FAILED =
                "FAILED";

        private ProcessingStatus() {
        }
    }


    // =========================================================
    // OVERALL DOCUMENT VERIFICATION STATUS
    // =========================================================

    public static final class VerificationStatus {

        public static final String PENDING =
                "PENDING";

        public static final String VERIFIED =
                "VERIFIED";

        public static final String REVIEW_REQUIRED =
                "REVIEW_REQUIRED";

        public static final String FAILED =
                "FAILED";

        private VerificationStatus() {
        }
    }


    // =========================================================
    // INTEGRITY STATUS
    // =========================================================

    public static final class IntegrityStatus {

        public static final String PENDING =
                "PENDING";

        public static final String VERIFIED =
                "VERIFIED";

        public static final String HASH_MISMATCH =
                "HASH_MISMATCH";

        public static final String FAILED =
                "FAILED";

        private IntegrityStatus() {
        }
    }


    // =========================================================
    // ENCRYPTED STORAGE STATUS
    // =========================================================

    public static final class StorageStatus {

        public static final String PENDING =
                "PENDING";

        public static final String ENCRYPTED =
                "ENCRYPTED";

        public static final String VERIFIED =
                "VERIFIED";

        public static final String FAILED =
                "FAILED";

        private StorageStatus() {
        }
    }


    // =========================================================
    // ALTERATION ANALYSIS STATUS
    // =========================================================

    public static final class AnalysisStatus {

        public static final String PENDING =
                "PENDING";

        public static final String PROCESSING =
                "PROCESSING";

        public static final String COMPLETED =
                "COMPLETED";

        public static final String FAILED =
                "FAILED";

        private AnalysisStatus() {
        }
    }


    // =========================================================
    // FRAUD RISK LEVEL
    // =========================================================

    public static final class RiskLevel {

        public static final String LOW =
                "LOW";

        public static final String MEDIUM =
                "MEDIUM";

        public static final String HIGH =
                "HIGH";

        private RiskLevel() {
        }
    }


    // =========================================================
    // MANUAL REVIEW STATUS
    // =========================================================

    public static final class ReviewStatus {

        public static final String NOT_REVIEWED =
                "NOT_REVIEWED";

        public static final String PENDING_REVIEW =
                "PENDING_REVIEW";

        public static final String UNDER_REVIEW =
                "UNDER_REVIEW";

        public static final String REVIEWED =
                "REVIEWED";

        private ReviewStatus() {
        }
    }


    // =========================================================
    // FINAL HUMAN DECISION
    //
    // Fraud detection does NOT automatically decide fraud.
    // =========================================================

    public static final class FinalDecision {

        public static final String APPROVED =
                "APPROVED";

        public static final String REJECTED =
                "REJECTED";

        public static final String REFERRED_FOR_INVESTIGATION =
                "REFERRED_FOR_INVESTIGATION";

        private FinalDecision() {
        }
    }


    // =========================================================
    // AUDIT EVENT STATUS
    // =========================================================

    public static final class AuditEventStatus {

        public static final String SUCCESS =
                "SUCCESS";

        public static final String FAILED =
                "FAILED";

        public static final String WARNING =
                "WARNING";

        private AuditEventStatus() {
        }
    }


    // =========================================================
    // AUDIT ACTIONS
    // =========================================================

    public static final class AuditAction {

        public static final String LOGIN =
                "LOGIN";

        public static final String LOGOUT =
                "LOGOUT";

        public static final String DOCUMENT_CAPTURE =
                "DOCUMENT_CAPTURE";

        public static final String QUALITY_VALIDATION =
                "QUALITY_VALIDATION";

        public static final String DOCUMENT_UPLOAD =
                "DOCUMENT_UPLOAD";

        public static final String INTEGRITY_CHECK =
                "INTEGRITY_CHECK";

        public static final String DOCUMENT_ENCRYPTED =
                "DOCUMENT_ENCRYPTED";

        public static final String ALTERATION_ANALYSIS =
                "ALTERATION_ANALYSIS";

        public static final String DOCUMENT_REVIEW =
                "DOCUMENT_REVIEW";

        public static final String FINAL_DECISION =
                "FINAL_DECISION";

        private AuditAction() {
        }
    }
}