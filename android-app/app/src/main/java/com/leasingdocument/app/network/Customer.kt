package com.leasingdocument.app.network

data class Customer(
    val customerId: Long?,
    val fullName: String?,
    val nic: String?,
    val phone: String?,
    val email: String?,
    val address: String?,
    val createdAt: String?
)