package com.leasingdocument.app.network

data class Device(
    val deviceId: Long?,
    val imeiNumber: String?,
    val deviceIdentifier: String?,
    val identifierType: String?,
    val assignedAgentId: Long?,
    val deviceNumber: String?,
    val deviceType: String?,
    val osVersion: String?,
    val status: String?,
    val assignedDate: String?,
    val lastSeenAt: String?
)
