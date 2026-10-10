package com.leasingdocument.app.network

data class AlterationResult(

    val alterationResultId: Long?,

    val documentId: Long?,

    val riskScore: Double?,

    val riskLevel: String?,

    val recommendedAction: String?,

    val highlightedImagePath: String?,

    val ocrStatus: String?,

    val ocrEngine: String?,

    val ocrFullText: String?,

    val analysisStatus: String?,

    val analysisMessage: String?,

    val suspiciousRegionCount: Int?,

    val algorithmVersion: String?,

    val reviewStatus: String?,

    val reviewedByAdminId: Long?,

    val reviewedAt: String?,

    val reviewNotes: String?,

    val finalDecision: String?,

    val processedAt: String?
)