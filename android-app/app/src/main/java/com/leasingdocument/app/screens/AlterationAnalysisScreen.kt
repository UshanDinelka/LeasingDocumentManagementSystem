package com.leasingdocument.app.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.AlterationResult
import com.leasingdocument.app.network.AnalysisRequest
import com.leasingdocument.app.network.AnalysisReviewRequest
import com.leasingdocument.app.network.AuthSession
import com.leasingdocument.app.network.Document
import com.leasingdocument.app.network.RetrofitClient
import com.leasingdocument.app.ocr.DocumentOcrProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import java.security.MessageDigest


@Composable
fun AlterationAnalysisScreen() {

    val scope =
        rememberCoroutineScope()

    var documents by remember {
        mutableStateOf<List<Document>>(emptyList())
    }

    var results by remember {
        mutableStateOf<List<AlterationResult>>(emptyList())
    }

    var selected by remember {
        mutableStateOf<AlterationResult?>(null)
    }

    var report by remember {
        mutableStateOf<JSONObject?>(null)
    }

    var normalized by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var cropped by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var highlighted by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var busy by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("")
    }

    var notes by remember {
        mutableStateOf("")
    }

    var checked by remember {
        mutableStateOf(false)
    }


    // =========================================================
    // DATA HELPERS
    // =========================================================

    suspend fun refresh() {

        documents =
            requireAnalysisResponse(
                RetrofitClient
                    .apiService
                    .getDocuments()
            )

        results =
            requireAnalysisResponse(
                RetrofitClient
                    .apiService
                    .getAlterationResults()
            )
    }


    suspend fun openResult(
        row: AlterationResult
    ) {

        selected = row
        report = null
        normalized = null
        cropped = null
        highlighted = null
        notes = ""
        checked = false


        if (
            row.analysisStatus !in
            listOf(
                "COMPLETED",
                "INCOMPLETE"
            )
            ||
            row.highlightedImagePath == null
        ) {
            return
        }


        val body =
            requireAnalysisResponse(
                RetrofitClient
                    .apiService
                    .getAnalysisReport(
                        requireNotNull(
                            row.alterationResultId
                        )
                    )
            )


        val parsed =
            withContext(
                Dispatchers.IO
            ) {

                body.use {
                    JSONObject(
                        it.string()
                    )
                }
            }


        val images =
            withContext(
                Dispatchers.Default
            ) {

                decodeAnalysisImage(
                    parsed
                        .optString(
                            "normalizedImageBase64"
                        )
                        .ifBlank {
                            parsed.getString(
                                "originalImageBase64"
                            )
                        }
                ) to
                        parsed
                            .optString(
                                "highlightedImageBase64"
                            )
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                decodeAnalysisImage(it)
                            }
            }


        cropped =
            withContext(
                Dispatchers.Default
            ) {

                parsed
                    .optString(
                        "croppedImageBase64"
                    )
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        decodeAnalysisImage(it)
                    }
            }


        report = parsed
        normalized = images.first
        highlighted = images.second
    }


    fun runAction(
        action: suspend () -> Unit
    ) {

        if (busy) {
            return
        }


        scope.launch {

            busy = true
            message = ""

            try {

                action()

            } catch (
                e: CancellationException
            ) {

                throw e

            } catch (
                e: Exception
            ) {

                message =
                    e.message
                        ?: "Operation failed. Check the connection and retry."

            } finally {

                busy = false
            }
        }
    }


    // =========================================================
    // INITIAL LOAD
    // =========================================================

    LaunchedEffect(Unit) {

        busy = true

        try {

            refresh()

        } catch (
            e: CancellationException
        ) {

            throw e

        } catch (
            e: Exception
        ) {

            message =
                e.message
                    ?: "Cannot load documents"

        } finally {

            busy = false
        }
    }


    // =========================================================
    // AUTHORIZATION
    // =========================================================

    if (
        AuthSession.role !=
        "ADMIN"
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color(0xFFFFF8FB)
                ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    "Administrator access required.",

                color =
                    Color(0xFFA13A4B),

                fontWeight =
                    FontWeight.Bold
            )
        }

        return
    }


    // =========================================================
    // ROOT
    // =========================================================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors =
                        listOf(
                            Color(0xFFFFFCFB),
                            Color(0xFFFFF8FB),
                            Color(0xFFF8F2FF)
                        )
                )
            )
    ) {


        // =====================================================
        // BACKGROUND DECORATION
        // =====================================================

        Canvas(
            modifier =
                Modifier.fillMaxSize()
        ) {

            val w =
                size.width

            val h =
                size.height


            val topShape =
                Path().apply {

                    moveTo(
                        0f,
                        0f
                    )

                    lineTo(
                        w * 0.72f,
                        0f
                    )

                    cubicTo(
                        w * 0.58f,
                        h * 0.028f,

                        w * 0.40f,
                        h * 0.060f,

                        0f,
                        h * 0.16f
                    )

                    close()
                }


            drawPath(
                path =
                    topShape,

                brush =
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0xFFFFD1AF)
                                    .copy(
                                        alpha =
                                            0.58f
                                    ),

                                Color(0xFFFFE8DE)
                                    .copy(
                                        alpha =
                                            0.24f
                                    ),

                                Color.Transparent
                            )
                    )
            )


            val ribbon =
                Path().apply {

                    moveTo(
                        0f,
                        h * 0.075f
                    )

                    cubicTo(
                        w * 0.19f,
                        h * 0.052f,

                        w * 0.35f,
                        h * 0.080f,

                        w * 0.58f,
                        h * 0.022f
                    )
                }


            drawPath(
                path =
                    ribbon,

                color =
                    Color(0xFFFF8B42)
                        .copy(
                            alpha =
                                0.70f
                        ),

                style =
                    Stroke(
                        width =
                            1.1.dp.toPx()
                    )
            )


            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(0xFF9F47DD)
                                    .copy(
                                        alpha =
                                            0.08f
                                    ),

                                Color.Transparent
                            )
                    ),

                radius =
                    w * 0.75f,

                center =
                    Offset(
                        x =
                            w * 0.55f,

                        y =
                            h
                    )
            )
        }


        // =====================================================
        // MAIN CONTENT
        // =====================================================

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(
                        horizontal =
                            22.dp
                    ),

            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {


            item {

                Spacer(
                    modifier =
                        Modifier.height(
                            18.dp
                        )
                )


                Text(
                    text =
                        "Alteration Analysis",

                    fontSize =
                        27.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF171A2C)
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            4.dp
                        )
                )


                Text(
                    text =
                        "Screen submitted documents for possible alterations and review supporting evidence.",

                    fontSize =
                        12.sp,

                    lineHeight =
                        17.sp,

                    color =
                        Color(0xFF817A8B)
                )
            }


            item {

                AnalysisOverviewCard(
                    documentCount =
                        documents.size,

                    resultCount =
                        results.size,

                    highRiskCount =
                        results.count {
                            it.riskLevel
                                ?.equals(
                                    "HIGH",
                                    ignoreCase = true
                                ) == true
                        }
                )
            }


            if (busy) {

                item {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        16.dp
                                    )
                                )
                                .background(
                                    Color(0xFFF4EFFA)
                                )
                                .padding(
                                    14.dp
                                )
                    ) {

                        Column {

                            LinearProgressIndicator(
                                modifier =
                                    Modifier.fillMaxWidth()
                            )


                            Spacer(
                                modifier =
                                    Modifier.height(
                                        8.dp
                                    )
                            )


                            Text(
                                text =
                                    "Working… analysis may take several minutes.",

                                fontSize =
                                    11.sp,

                                color =
                                    Color(0xFF6C5B7A)
                            )
                        }
                    }
                }
            }


            if (
                message.isNotBlank()
            ) {

                item {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        16.dp
                                    )
                                )
                                .background(
                                    Color(0xFFFFEEF1)
                                )
                                .padding(
                                    14.dp
                                )
                    ) {

                        Text(
                            text =
                                message,

                            fontSize =
                                12.sp,

                            color =
                                Color(0xFFA13A4B)
                        )
                    }
                }
            }


            item {

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    16.dp
                                )
                            )
                            .background(
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF6950B7),
                                            Color(0xFF9E4FC4),
                                            Color(0xFFD34D92)
                                        )
                                )
                            )
                            .clickable(
                                enabled =
                                    !busy
                            ) {

                                runAction {
                                    refresh()
                                }
                            }
                            .padding(
                                vertical =
                                    14.dp
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "REFRESH DOCUMENTS & RESULTS",

                        color =
                            Color.White,

                        fontSize =
                            11.sp,

                        fontWeight =
                            FontWeight.Bold,

                        letterSpacing =
                            0.5.sp
                    )
                }
            }


            // =================================================
            // SELECTED RESULT
            // =================================================

            selected?.let {
                    row ->

                item {

                    AnalysisResultCard(
                        row =
                            row
                    )
                }


                report?.let {
                        details ->

                    item {

                        Text(
                            text =
                                "Analysis Evidence",

                            fontSize =
                                18.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color(0xFF1B1D30)
                        )
                    }


                    cropped?.let {
                            bitmap ->

                        item {

                            AnalysisEvidenceCard(
                                title =
                                    "Cropped Document",

                                subtitle =
                                    "Check the detected document boundary."
                            ) {

                                AnalysisPreview(
                                    bitmap =
                                        bitmap,

                                    description =
                                        "Cropped document"
                                )
                            }
                        }
                    }


                    item {

                        AnalysisEvidenceCard(
                            title =
                                if (
                                    row.analysisStatus ==
                                    "INCOMPLETE"
                                ) {

                                    "Original Document"

                                } else {

                                    "Template-Aligned Document"
                                },

                            subtitle =
                                if (
                                    row.analysisStatus ==
                                    "INCOMPLETE"
                                ) {

                                    "Analysis is incomplete."

                                } else {

                                    "Image used by the analysis pipeline."
                                }
                        ) {

                            normalized?.let {
                                    bitmap ->

                                AnalysisPreview(
                                    bitmap =
                                        bitmap,

                                    description =
                                        "Document image"
                                )
                            }
                        }
                    }


                    highlighted?.let {
                            bitmap ->

                        item {

                            AnalysisEvidenceCard(
                                title =
                                    "Highlighted Candidate Areas",

                                subtitle =
                                    "Highlighted regions require manual checking."
                            ) {

                                AnalysisPreview(
                                    bitmap =
                                        bitmap,

                                    description =
                                        "Candidate areas"
                                )
                            }
                        }
                    }


                    if (
                        row.analysisStatus ==
                        "INCOMPLETE"
                    ) {

                        item {

                            AnalysisInfoNotice(
                                text =
                                    "No alteration score or highlights are available. Recapture or refer for manual investigation."
                            )
                        }
                    }


                    item {

                        AnalysisTextSection(
                            title =
                                "Alignment",

                            value =
                                details
                                    .optJSONObject(
                                        "alignment"
                                    )
                                    ?.toString(
                                        2
                                    )
                                    .orEmpty()
                                    .ifBlank {
                                        "No alignment details available."
                                    }
                        )
                    }


                    item {

                        AnalysisTextSection(
                            title =
                                "Reason Code",

                            value =
                                details
                                    .optString(
                                        "reasonCode"
                                    )
                                    .ifBlank {
                                        "No reason code available."
                                    }
                        )
                    }


                    item {

                        AnalysisTextSection(
                            title =
                                "OCR Text",

                            value =
                                details
                                    .optJSONObject(
                                        "ocr"
                                    )
                                    ?.optString(
                                        "fullText"
                                    )
                                    .orEmpty()
                                    .ifBlank {
                                        "No text recognized."
                                    }
                        )
                    }


                    item {

                        AnalysisTextSection(
                            title =
                                "Extracted Candidates",

                            value =
                                details
                                    .optJSONObject(
                                        "ocr"
                                    )
                                    ?.optJSONObject(
                                        "fields"
                                    )
                                    ?.toString(
                                        2
                                    )
                                    .orEmpty()
                                    .ifBlank {
                                        "No extracted candidates."
                                    }
                        )
                    }


                    item {

                        AnalysisTextSection(
                            title =
                                "OCR Issues",

                            value =
                                details
                                    .optJSONObject(
                                        "ocr_validation"
                                    )
                                    ?.optJSONArray(
                                        "issues"
                                    )
                                    ?.toString(
                                        2
                                    )
                                    .orEmpty()
                                    .ifBlank {
                                        "No OCR issues reported."
                                    }
                        )
                    }


                    item {

                        AnalysisTextSection(
                            title =
                                "Analysis Coverage",

                            value =
                                details
                                    .optJSONObject(
                                        "altered_text_localization"
                                    )
                                    ?.optString(
                                        "coverage_note"
                                    )
                                    .orEmpty()
                                    .ifBlank {
                                        "No coverage note available."
                                    }
                        )
                    }


                    item {

                        AnalysisTextSection(
                            title =
                                "Detector Coverage",

                            value =
                                details
                                    .optJSONObject(
                                        "risk_details"
                                    )
                                    ?.optJSONObject(
                                        "detector_coverage"
                                    )
                                    ?.toString(
                                        2
                                    )
                                    .orEmpty()
                                    .ifBlank {
                                        "No detector coverage details."
                                    }
                        )
                    }


                    item {

                        AnalysisTextSection(
                            title =
                                "Detector Findings",

                            value =
                                details
                                    .optJSONObject(
                                        "risk_details"
                                    )
                                    ?.optJSONArray(
                                        "findings"
                                    )
                                    ?.toString(
                                        2
                                    )
                                    .orEmpty()
                                    .ifBlank {
                                        "No detector findings."
                                    }
                        )
                    }


                    item {

                        AnalysisTextSection(
                            title =
                                "Detector Scores",

                            subtitle =
                                "Uncalibrated screening indicators",

                            value =
                                details
                                    .optJSONObject(
                                        "risk_details"
                                    )
                                    ?.optJSONObject(
                                        "component_scores"
                                    )
                                    ?.toString(
                                        2
                                    )
                                    .orEmpty()
                                    .ifBlank {
                                        "No component scores available."
                                    }
                        )
                    }


                    if (
                        row.finalDecision ==
                        null
                    ) {

                        item {

                            ManualReviewCard(
                                notes =
                                    notes,

                                checked =
                                    checked,

                                busy =
                                    busy,

                                onNotesChange = {
                                        value ->

                                    if (
                                        value.length <=
                                        1000
                                    ) {

                                        notes =
                                            value
                                    }
                                },

                                onCheckedChange = {
                                        value ->

                                    checked =
                                        value
                                },

                                decisions =
                                    if (
                                        row.riskLevel ==
                                        "HIGH"
                                        ||
                                        row.analysisStatus ==
                                        "INCOMPLETE"
                                        ||
                                        row.algorithmVersion !=
                                        "document-analysis-4"
                                    ) {

                                        listOf(
                                            "REFERRED_FOR_INVESTIGATION"
                                        )

                                    } else {

                                        listOf(
                                            "APPROVED",
                                            "REJECTED",
                                            "REFERRED_FOR_INVESTIGATION"
                                        )
                                    },

                                onDecision = {
                                        decision ->

                                    runAction {

                                        selected =
                                            requireAnalysisResponse(
                                                RetrofitClient
                                                    .apiService
                                                    .reviewAnalysis(
                                                        requireNotNull(
                                                            row.alterationResultId
                                                        ),

                                                        AnalysisReviewRequest(
                                                            decision,
                                                            notes
                                                        )
                                                    )
                                            )

                                        refresh()
                                    }
                                }
                            )
                        }
                    }
                }
            }


            item {

                Spacer(
                    modifier =
                        Modifier.height(
                            4.dp
                        )
                )


                Text(
                    text =
                        "Submitted Documents",

                    fontSize =
                        18.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF1B1D30)
                )
            }


            // =================================================
            // DOCUMENT LIST
            // =================================================

            items(
                items =
                    documents,

                key = {
                    it.documentId
                        ?: it.hashCode()
                }
            ) {
                    doc ->

                val latest =
                    results
                        .filter {
                            it.documentId ==
                                    doc.documentId
                        }
                        .maxByOrNull {
                            it.alterationResultId
                                ?: 0
                        }


                SubmittedDocumentCard(
                    doc =
                        doc,

                    latest =
                        latest,

                    busy =
                        busy,

                    onViewResult = {

                        if (
                            latest !=
                            null
                        ) {

                            runAction {
                                openResult(
                                    latest
                                )
                            }
                        }
                    },

                    onRunAnalysis = {

                        runAction {

                            val imageBody =
                                requireAnalysisResponse(
                                    RetrofitClient
                                        .apiService
                                        .getDocumentImage(
                                            requireNotNull(
                                                doc.fileName
                                            )
                                        )
                                )


                            val bytes =
                                withContext(
                                    Dispatchers.IO
                                ) {

                                    imageBody.use {
                                        it.bytes()
                                    }
                                }


                            try {

                                val ocr =
                                    DocumentOcrProcessor
                                        .extract(
                                            bytes
                                        )


                                val hash =
                                    withContext(
                                        Dispatchers.Default
                                    ) {

                                        MessageDigest
                                            .getInstance(
                                                "SHA-256"
                                            )
                                            .digest(
                                                bytes
                                            )
                                            .joinToString(
                                                ""
                                            ) {
                                                "%02x".format(
                                                    it.toInt()
                                                            and 255
                                                )
                                            }
                                    }


                                val row =
                                    requireAnalysisResponse(
                                        RetrofitClient
                                            .apiService
                                            .analyzeDocument(
                                                requireNotNull(
                                                    doc.documentId
                                                ),

                                                AnalysisRequest(
                                                    hash,
                                                    ocr
                                                )
                                            )
                                    )


                                refresh()

                                openResult(
                                    row
                                )

                            } finally {

                                bytes.fill(
                                    0
                                )
                            }
                        }
                    }
                )
            }


            item {

                Spacer(
                    modifier =
                        Modifier.height(
                            24.dp
                        )
                )
            }
        }
    }
}


// =====================================================================
// OVERVIEW CARD
// =====================================================================

@Composable
private fun AnalysisOverviewCard(
    documentCount: Int,
    resultCount: Int,
    highRiskCount: Int
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    24.dp
                )
            )
            .background(
                Brush.horizontalGradient(
                    colors =
                        listOf(
                            Color(0xFF6650B8),
                            Color(0xFF9750C5),
                            Color(0xFFD34D92)
                        )
                )
            )
            .padding(
                20.dp
            )
    ) {

        Column {

            Text(
                text =
                    "Analysis Overview",

                color =
                    Color.White.copy(
                        alpha =
                            0.84f
                    ),

                fontSize =
                    12.sp
            )


            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )


            Text(
                text =
                    "$documentCount Submitted Document${if (documentCount == 1) "" else "s"}",

                color =
                    Color.White,

                fontSize =
                    22.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {

                AnalysisPill(
                    label =
                        "RESULTS",

                    value =
                        resultCount.toString()
                )


                AnalysisPill(
                    label =
                        "HIGH RISK",

                    value =
                        highRiskCount.toString()
                )
            }
        }
    }
}


// =====================================================================
// PILL
// =====================================================================

@Composable
private fun AnalysisPill(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.15f
                    )
                )
                .padding(
                    horizontal =
                        11.dp,

                    vertical =
                        9.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text =
                value,

            fontSize =
                13.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                Color.White
        )


        Spacer(
            modifier =
                Modifier.width(
                    5.dp
                )
        )


        Text(
            text =
                label,

            fontSize =
                8.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color.White.copy(
                    alpha =
                        0.82f
                )
        )
    }
}


// =====================================================================
// RESULT CARD
// =====================================================================

@Composable
private fun AnalysisResultCard(
    row: AlterationResult
) {

    val risk =
        row.riskLevel
            ?: "UNKNOWN"


    val riskBackground =
        when (
            risk.uppercase()
        ) {

            "HIGH" ->
                Color(0xFFFFEDEF)

            "MEDIUM" ->
                Color(0xFFFFF3E7)

            "LOW" ->
                Color(0xFFEAF7EF)

            else ->
                Color(0xFFF3EEF8)
        }


    val riskText =
        when (
            risk.uppercase()
        ) {

            "HIGH" ->
                Color(0xFFB54050)

            "MEDIUM" ->
                Color(0xFFA86428)

            "LOW" ->
                Color(0xFF247148)

            else ->
                Color(0xFF7650BD)
        }


    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        21.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.96f
                    )
                )
                .padding(
                    16.dp
                )
    ) {

        Column {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    Text(
                        text =
                            "Document ${row.documentId ?: "-"}",

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF171A2C)
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                3.dp
                            )
                    )


                    Text(
                        text =
                            "Result ${row.alterationResultId ?: "-"}",

                        fontSize =
                            10.sp,

                        color =
                            Color(0xFF8D8695)
                    )
                }


                Box(
                    modifier =
                        Modifier
                            .clip(
                                RoundedCornerShape(
                                    50.dp
                                )
                            )
                            .background(
                                riskBackground
                            )
                            .padding(
                                horizontal =
                                    10.dp,

                                vertical =
                                    7.dp
                            )
                ) {

                    Text(
                        text =
                            risk.uppercase(),

                        fontSize =
                            9.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            riskText
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )


            AnalysisInfoRow(
                label =
                    "Analysis",

                value =
                    row.analysisStatus
                        ?: "Unknown"
            )


            AnalysisInfoRow(
                label =
                    "Score",

                value =
                    row.riskScore
                        ?.toString()
                        ?: "Not available"
            )


            AnalysisInfoRow(
                label =
                    "Recommendation",

                value =
                    row.recommendedAction
                        ?: "Pending"
            )


            AnalysisInfoRow(
                label =
                    "OCR",

                value =
                    row.ocrStatus
                        ?: "Unknown"
            )


            AnalysisInfoRow(
                label =
                    "Manual Decision",

                value =
                    row.finalDecision
                        ?: "Not recorded"
            )


            if (
                row.analysisMessage
                    ?.isNotBlank() ==
                true
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )


                Text(
                    text =
                        row.analysisMessage.orEmpty(),

                    fontSize =
                        11.sp,

                    lineHeight =
                        16.sp,

                    color =
                        Color(0xFF5F5968)
                )
            }


            if (
                row.reviewNotes !=
                null
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )


                Text(
                    text =
                        "Review notes: ${row.reviewNotes}",

                    fontSize =
                        11.sp,

                    lineHeight =
                        16.sp,

                    color =
                        Color(0xFF5F5968)
                )
            }


            if (
                row.algorithmVersion !=
                "document-analysis-4"
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            10.dp
                        )
                )


                AnalysisInfoNotice(
                    text =
                        "Earlier analysis detected. Rerun with the corrected pipeline before making a decision."
                )
            }
        }
    }
}


// =====================================================================
// EVIDENCE CARD
// =====================================================================

@Composable
private fun AnalysisEvidenceCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        21.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.96f
                    )
                )
                .padding(
                    16.dp
                )
    ) {

        Column {

            Text(
                text =
                    title,

                fontSize =
                    15.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        4.dp
                    )
            )


            Text(
                text =
                    subtitle,

                fontSize =
                    11.sp,

                color =
                    Color(0xFF817A8B)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )


            content()
        }
    }
}


// =====================================================================
// TEXT SECTION
// =====================================================================

@Composable
private fun AnalysisTextSection(
    title: String,
    value: String,
    subtitle: String? = null
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        18.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.95f
                    )
                )
                .padding(
                    15.dp
                )
    ) {

        Column {

            Text(
                text =
                    title,

                fontSize =
                    14.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            if (
                subtitle !=
                null
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            2.dp
                        )
                )


                Text(
                    text =
                        subtitle,

                    fontSize =
                        10.sp,

                    color =
                        Color(0xFF8D8695)
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            Text(
                text =
                    value,

                fontSize =
                    10.sp,

                lineHeight =
                    15.sp,

                color =
                    Color(0xFF524B5D)
            )
        }
    }
}


// =====================================================================
// MANUAL REVIEW
// =====================================================================

@Composable
private fun ManualReviewCard(
    notes: String,
    checked: Boolean,
    busy: Boolean,
    onNotesChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    decisions: List<String>,
    onDecision: (String) -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        21.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.96f
                    )
                )
                .padding(
                    16.dp
                )
    ) {

        Column {

            Text(
                text =
                    "Manual Review",

                fontSize =
                    16.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        6.dp
                    )
            )


            Text(
                text =
                    "Record the human review decision after checking the available evidence.",

                fontSize =
                    11.sp,

                lineHeight =
                    16.sp,

                color =
                    Color(0xFF817A8B)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )


            OutlinedTextField(
                value =
                    notes,

                onValueChange =
                    onNotesChange,

                label = {
                    Text(
                        "Manual review notes"
                    )
                },

                modifier =
                    Modifier.fillMaxWidth(),

                enabled =
                    !busy
            )


            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )


            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Checkbox(
                    checked =
                        checked,

                    onCheckedChange =
                        onCheckedChange,

                    enabled =
                        !busy
                )


                Text(
                    text =
                        "I checked the document image, OCR and analysis evidence.",

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    fontSize =
                        11.sp,

                    lineHeight =
                        16.sp,

                    color =
                        Color(0xFF5F5968)
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )


            for (
            decision in
            decisions
            ) {

                Button(
                    enabled =
                        !busy &&
                                checked &&
                                notes.isNotBlank(),

                    onClick = {
                        onDecision(
                            decision
                        )
                    },

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                bottom =
                                    8.dp
                            ),

                    shape =
                        RoundedCornerShape(
                            14.dp
                        ),

                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    when (
                                        decision
                                    ) {

                                        "APPROVED" ->
                                            Color(0xFF357A57)

                                        "REJECTED" ->
                                            Color(0xFFB54050)

                                        else ->
                                            Color(0xFF7650BD)
                                    }
                            )
                ) {

                    Text(
                        text =
                            when (
                                decision
                            ) {

                                "REFERRED_FOR_INVESTIGATION" ->
                                    "REFER FOR INVESTIGATION"

                                else ->
                                    decision
                            },

                        fontSize =
                            11.sp,

                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }


            Text(
                text =
                    "A referral is recorded for authorized follow-up; this does not send a notification.",

                fontSize =
                    10.sp,

                lineHeight =
                    15.sp,

                color =
                    Color(0xFF8D8695)
            )
        }
    }
}


// =====================================================================
// SUBMITTED DOCUMENT
// =====================================================================

@Composable
private fun SubmittedDocumentCard(
    doc: Document,
    latest: AlterationResult?,
    busy: Boolean,
    onViewResult: () -> Unit,
    onRunAnalysis: () -> Unit
) {

    val canAnalyze =
        !busy &&
                doc.captureSource ==
                "LIVE_CAMERA" &&
                doc.qualityStatus ==
                "PASSED"


    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        21.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.96f
                    )
                )
                .padding(
                    16.dp
                )
    ) {

        Column {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier
                            .size(
                                48.dp
                            )
                            .clip(
                                RoundedCornerShape(
                                    15.dp
                                )
                            )
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(0xFFFF8149),
                                            Color(0xFFF04F8A)
                                        )
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "AI",

                        color =
                            Color.White,

                        fontWeight =
                            FontWeight.Bold,

                        fontSize =
                            12.sp
                    )
                }


                Spacer(
                    modifier =
                        Modifier.width(
                            13.dp
                        )
                )


                Column(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    Text(
                        text =
                            "Document ${doc.documentId ?: "-"}",

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF171A2C)
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                3.dp
                            )
                    )


                    Text(
                        text =
                            "Customer ${doc.customerId ?: "-"}",

                        fontSize =
                            10.sp,

                        color =
                            Color(0xFF8D8695)
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )


            AnalysisInfoRow(
                label =
                    "Latest Analysis",

                value =
                    latest
                        ?.analysisStatus
                        ?: "Not analysed"
            )


            AnalysisInfoRow(
                label =
                    "Capture Source",

                value =
                    doc.captureSource
                        ?: "-"
            )


            AnalysisInfoRow(
                label =
                    "Quality",

                value =
                    doc.qualityStatus
                        ?: "-"
            )


            if (
                latest !=
                null
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )


                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    14.dp
                                )
                            )
                            .background(
                                Color(0xFFF1ECFA)
                            )
                            .clickable(
                                enabled =
                                    !busy
                            ) {
                                onViewResult()
                            }
                            .padding(
                                vertical =
                                    11.dp
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "VIEW LATEST RESULT",

                        fontSize =
                            11.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF6F4BB8)
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        9.dp
                    )
            )


            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                14.dp
                            )
                        )
                        .background(
                            if (
                                canAnalyze
                            ) {

                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF6950B7),
                                            Color(0xFFD34D92)
                                        )
                                )

                            } else {

                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            Color(0xFFE4DEE9),
                                            Color(0xFFE4DEE9)
                                        )
                                )
                            }
                        )
                        .clickable(
                            enabled =
                                canAnalyze
                        ) {
                            onRunAnalysis()
                        }
                        .padding(
                            vertical =
                                11.dp
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        if (
                            latest ==
                            null
                        ) {

                            "RUN OCR & ANALYSIS"

                        } else {

                            "RUN NEW ANALYSIS"
                        },

                    fontSize =
                        11.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        if (
                            canAnalyze
                        ) {

                            Color.White

                        } else {

                            Color(0xFF9B949F)
                        }
                )
            }
        }
    }
}


// =====================================================================
// INFO ROW
// =====================================================================

@Composable
private fun AnalysisInfoRow(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        4.dp
                ),

        verticalAlignment =
            Alignment.Top
    ) {

        Text(
            text =
                label,

            modifier =
                Modifier.weight(
                    0.40f
                ),

            fontSize =
                10.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color(0xFF8D8698)
        )


        Spacer(
            modifier =
                Modifier.width(
                    8.dp
                )
        )


        Text(
            text =
                value,

            modifier =
                Modifier.weight(
                    0.60f
                ),

            fontSize =
                10.sp,

            color =
                Color(0xFF3F394A),

            maxLines =
                3,

            overflow =
                TextOverflow.Ellipsis
        )
    }
}


// =====================================================================
// NOTICE
// =====================================================================

@Composable
private fun AnalysisInfoNotice(
    text: String
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        16.dp
                    )
                )
                .background(
                    Color(0xFFFFF3E9)
                )
                .padding(
                    13.dp
                )
    ) {

        Text(
            text =
                text,

            fontSize =
                11.sp,

            lineHeight =
                16.sp,

            color =
                Color(0xFF8A5A2E)
        )
    }
}


// =====================================================================
// HTTP HELPER
// =====================================================================

private fun <T> requireAnalysisResponse(
    response: Response<T>
): T {

    if (
        !response.isSuccessful
    ) {

        val raw =
            response
                .errorBody()
                ?.use {
                    it.string()
                }
                .orEmpty()


        val detail =
            runCatching {

                val obj =
                    JSONObject(
                        raw
                    )

                obj
                    .optString(
                        "detail"
                    )
                    .ifBlank {
                        obj.optString(
                            "message"
                        )
                    }

            }.getOrDefault(
                ""
            )


        error(
            detail.ifBlank {

                when (
                    response.code()
                ) {

                    401 ->
                        "Session expired. Sign in again."

                    403 ->
                        "Administrator access is required."

                    409 ->
                        "Analysis is busy or the document state changed. Refresh and retry."

                    422 ->
                        "This document type is not supported by the configured templates. Manual review is required."

                    502,
                    503 ->
                        "Analysis service unavailable. Check the Python service and shared token."

                    else ->
                        "Request failed (HTTP ${response.code()})."
                }
            }
        )
    }


    return requireNotNull(
        response.body()
    ) {
        "The server returned an empty response."
    }
}


// =====================================================================
// IMAGE DECODE
// =====================================================================

private fun decodeAnalysisImage(
    encoded: String
): Bitmap {

    val bytes =
        Base64.decode(
            encoded,
            Base64.DEFAULT
        )


    val options =
        BitmapFactory.Options()
            .apply {
                inJustDecodeBounds =
                    true
            }


    BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        options
    )


    require(
        options.outWidth >
                0 &&
                options.outHeight >
                0 &&
                options.outWidth
                    .toLong() *
                options.outHeight <=
                12_000_000L
    )


    return requireNotNull(
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size
        )
    )
}


// =====================================================================
// IMAGE PREVIEW
// =====================================================================

@Composable
private fun AnalysisPreview(
    bitmap: Bitmap,
    description: String
) {

    val ratio =
        bitmap.width
            .toFloat() /
                bitmap.height
                    .toFloat()


    Image(
        bitmap =
            bitmap.asImageBitmap(),

        contentDescription =
            description,

        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(
                    max =
                        400.dp
                )
                .aspectRatio(
                    ratio,
                    matchHeightConstraintsFirst =
                        true
                ),

        contentScale =
            ContentScale.Fit
    )
}
