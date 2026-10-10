package com.leasingdocument.app.network

data class DocumentType(

    val documentTypeId: Long?,

    val typeCode: String?,

    val typeName: String?,

    val description: String?,

    val requiredFlag: Boolean?,

    val analysisEnabled: Boolean?,

    val status: String?
)