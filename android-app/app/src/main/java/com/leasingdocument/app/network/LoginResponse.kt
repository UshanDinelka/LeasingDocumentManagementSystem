package com.leasingdocument.app.network

data class LoginResponse(
    val message: String,
    val userId: Long?,
    val role: String?,
    val token: String?
)