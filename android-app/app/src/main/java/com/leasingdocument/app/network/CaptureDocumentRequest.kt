package com.leasingdocument.app.network

data class CaptureDocumentRequest(

    val customerId: Long,

    val documentTypeCode: String,

    val deviceId: Long,

    val capturedAt: String,

    val latitude: Double?,

    val longitude: Double?,

    val imageWidth: Int,

    val imageHeight: Int,

    val blurScore: Double,

    val brightnessScore: Double,

    val blurPassed: Boolean,

    val brightnessPassed: Boolean,

    val resolutionPassed: Boolean,

    val mobileSha256: String,

    val ocrJson: String? = null
)