package com.leasingdocument.app.network

data class AddAgentRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val nic: String,
    val phone: String
)