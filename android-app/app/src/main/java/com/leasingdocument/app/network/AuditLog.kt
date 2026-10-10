package com.leasingdocument.app.network

data class AuditLog(
    val logId: Long?,
    val adminId: Long?,
    val agentId: Long?,
    val documentId: Long?,
    val deviceId: Long?,
    val action: String?,
    val eventStatus: String?,
    val description: String?,
    val ipAddress: String?,
    val createdAt: String?
)
