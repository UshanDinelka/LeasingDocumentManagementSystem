package com.leasingdocument.app.network

object WorkflowContract {

    object DocumentTypeCode {

        const val NIC =
            "NIC"

        const val NIC_COPY =
            "NIC_COPY"

        const val DRIVING_LICENCE =
            "DRIVING_LICENCE"

        const val SALARY_SLIP =
            "SALARY_SLIP"

        const val BANK_STATEMENT =
            "BANK_STATEMENT"

        const val BUSINESS_REGISTRATION =
            "BUSINESS_REGISTRATION"

        const val VEHICLE_CR_BOOK =
            "VEHICLE_CR_BOOK"

        const val UTILITY_BILL =
            "UTILITY_BILL"
    }


    object CaptureSource {

        const val LIVE_CAMERA =
            "LIVE_CAMERA"

        const val LEGACY_UPLOAD =
            "LEGACY_UPLOAD"
    }


    object QualityStatus {

        const val PENDING =
            "PENDING"

        const val PASSED =
            "PASSED"

        const val FAILED =
            "FAILED"
    }


    object ProcessingStatus {

        const val CAPTURED =
            "CAPTURED"

        const val QUALITY_VALIDATED =
            "QUALITY_VALIDATED"

        const val INTEGRITY_PENDING =
            "INTEGRITY_PENDING"

        const val INTEGRITY_VERIFIED =
            "INTEGRITY_VERIFIED"

        const val ENCRYPTED =
            "ENCRYPTED"

        const val ANALYSIS_PENDING =
            "ANALYSIS_PENDING"

        const val ANALYSIS_COMPLETED =
            "ANALYSIS_COMPLETED"

        const val REVIEW_REQUIRED =
            "REVIEW_REQUIRED"

        const val COMPLETED =
            "COMPLETED"

        const val FAILED =
            "FAILED"
    }


    object VerificationStatus {

        const val PENDING =
            "PENDING"

        const val VERIFIED =
            "VERIFIED"

        const val REVIEW_REQUIRED =
            "REVIEW_REQUIRED"

        const val FAILED =
            "FAILED"
    }


    object IntegrityStatus {

        const val PENDING =
            "PENDING"

        const val VERIFIED =
            "VERIFIED"

        const val HASH_MISMATCH =
            "HASH_MISMATCH"

        const val FAILED =
            "FAILED"
    }


    object StorageStatus {

        const val PENDING =
            "PENDING"

        const val ENCRYPTED =
            "ENCRYPTED"

        const val VERIFIED =
            "VERIFIED"

        const val FAILED =
            "FAILED"
    }


    object AnalysisStatus {

        const val PENDING =
            "PENDING"

        const val PROCESSING =
            "PROCESSING"

        const val COMPLETED =
            "COMPLETED"

        const val FAILED =
            "FAILED"
    }


    object RiskLevel {

        const val LOW =
            "LOW"

        const val MEDIUM =
            "MEDIUM"

        const val HIGH =
            "HIGH"
    }


    object ReviewStatus {

        const val NOT_REVIEWED =
            "NOT_REVIEWED"

        const val PENDING_REVIEW =
            "PENDING_REVIEW"

        const val UNDER_REVIEW =
            "UNDER_REVIEW"

        const val REVIEWED =
            "REVIEWED"
    }


    object FinalDecision {

        const val APPROVED =
            "APPROVED"

        const val REJECTED =
            "REJECTED"

        const val REFERRED_FOR_INVESTIGATION =
            "REFERRED_FOR_INVESTIGATION"
    }
}