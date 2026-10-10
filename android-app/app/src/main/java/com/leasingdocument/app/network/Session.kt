package com.leasingdocument.app.network

data class Session(
    val sessionId: Long?,
    val adminId: Long?,
    val agentId: Long?,
    val deviceId: Long?,
    val loginTime: String?,
    val logoutTime: String?,
    val status: String?
)