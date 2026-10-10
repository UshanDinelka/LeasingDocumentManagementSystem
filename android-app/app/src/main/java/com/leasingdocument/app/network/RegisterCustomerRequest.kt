package com.leasingdocument.app.network

data class RegisterCustomerRequest(
    val fullName: String,
    val nic: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null
)
