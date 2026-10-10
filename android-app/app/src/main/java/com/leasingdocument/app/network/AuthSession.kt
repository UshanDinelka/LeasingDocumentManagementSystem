package com.leasingdocument.app.network

object AuthSession {

    var token: String? = null
    var userId: Long? = null
    var role: String? = null

    fun clear() {
        token = null
        userId = null
        role = null
    }
}