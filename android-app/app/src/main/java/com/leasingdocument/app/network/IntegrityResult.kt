package com.leasingdocument.app.network

data class IntegrityResult(
    val integrityResultId: Long?,
    val documentId: Long?,
    val sha256Hash: String?,
    val backendSha256Hash: String?,
    val finalSha256Hash: String?,
    val initialVerificationResult: String?,
    val finalVerificationResult: String?,
    val verificationResult: String?,
    val encryptionAlgorithm: String?,
    val encryptionIv: String?,
    val encryptedFilePath: String?,
    val encryptionKeyVersion: String?,
    val storageStatus: String?,
    val encryptedAt: String?,
    val finalVerifiedAt: String?,
    val processedAt: String?
)
