package com.leasingdocument.app.network

data class AnalysisRequest(val imageSha256: String, val ocrJson: String)
data class AnalysisReviewRequest(val decision: String, val notes: String)
