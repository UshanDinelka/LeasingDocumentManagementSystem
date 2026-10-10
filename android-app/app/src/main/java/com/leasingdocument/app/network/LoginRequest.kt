package com.leasingdocument.app.network

data class LoginRequest(
    val email: String,
    val password: String,
    val deviceId: Long
)