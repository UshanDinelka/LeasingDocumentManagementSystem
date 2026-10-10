package com.leasingdocument.app.network

data class SecureSubmissionResponse(
    val documentId: Long?,
    val originalHash: String?,
    val backendHash: String?,
    val finalHash: String?,
    val integrityVerified: Boolean,
    val encrypted: Boolean,
    val decryptionVerified: Boolean,
    val stored: Boolean,
    val message: String?
)
