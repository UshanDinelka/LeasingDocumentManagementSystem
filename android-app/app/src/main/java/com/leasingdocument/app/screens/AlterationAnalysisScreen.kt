package com.leasingdocument.app.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.leasingdocument.app.network.Agent
import com.leasingdocument.app.network.AlterationResult
import com.leasingdocument.app.network.AnalysisRequest
import com.leasingdocument.app.network.AnalysisReviewRequest
import com.leasingdocument.app.network.AuthSession
import com.leasingdocument.app.network.Customer
import com.leasingdocument.app.network.Document
import com.leasingdocument.app.network.DocumentType
import com.leasingdocument.app.network.RetrofitClient
import com.leasingdocument.app.ocr.DocumentOcrProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import java.security.MessageDigest


private enum class AnalysisAdminPage {
    AGENTS,
    CLIENTS,
    DOCUMENTS,
    DETAILS
}

private const val CURRENT_ANALYSIS_VERSION = "document-analysis-6"


@Composable
fun AlterationAnalysisScreen() {

    if (AuthSession.role != "ADMIN") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF8FB)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Administrator access required.",
                color = Color(0xFFA13A4B),
                fontWeight = FontWeight.Bold
            )
        }
        return
    }

    val scope = rememberCoroutineScope()

    var agents by remember {
        mutableStateOf<List<Agent>>(emptyList())
    }

    var customers by remember {
        mutableStateOf<List<Customer>>(emptyList())
    }

    var documents by remember {
        mutableStateOf<List<Document>>(emptyList())
    }

    var types by remember {
        mutableStateOf<List<DocumentType>>(emptyList())
    }

    var results by remember {
        mutableStateOf<List<AlterationResult>>(emptyList())
    }

    var page by remember {
        mutableStateOf(AnalysisAdminPage.AGENTS)
    }

    var selectedAgent by remember {
        mutableStateOf<Agent?>(null)
    }

    var selectedCustomer by remember {
        mutableStateOf<Customer?>(null)
    }

    var selectedDocument by remember {
        mutableStateOf<Document?>(null)
    }

    var selectedResult by remember {
        mutableStateOf<AlterationResult?>(null)
    }

    var report by remember {
        mutableStateOf<JSONObject?>(null)
    }

    var sourceImage by remember {
        mutableStateOf<Bitmap?>(null)
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

    var referenceId by remember {
        mutableStateOf<Long?>(null)
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

    suspend fun refresh() {
        val api = RetrofitClient.apiService

        agents = requireAnalysisResponse(api.getAgents())
        customers = requireAnalysisResponse(api.getCustomers())
        documents = requireAnalysisResponse(api.getDocuments())
        types = requireAnalysisResponse(api.getDocumentTypes())
        results = requireAnalysisResponse(api.getAlterationResults())
    }

    suspend fun openResult(row: AlterationResult) {
        selectedResult = row
        report = null
        normalized = null
        cropped = null
        highlighted = null
        notes = ""
        checked = false

        if (
            row.analysisStatus !in listOf("COMPLETED", "INCOMPLETE") ||
            row.highlightedImagePath == null
        ) {
            return
        }

        val body = requireAnalysisResponse(
            RetrofitClient.apiService.getAnalysisReport(
                requireNotNull(row.alterationResultId)
            )
        )

        val parsed = withContext(Dispatchers.IO) {
            body.use { JSONObject(it.string()) }
        }

        val decoded = withContext(Dispatchers.Default) {
            val normalizedText = parsed
                .optString("normalizedImageBase64")
                .takeIf { it.isNotBlank() }
                ?: parsed
                    .optString("originalImageBase64")
                    .takeIf { it.isNotBlank() }

            val normalizedBitmap = normalizedText?.let(::decodeAnalysisImage)

            val croppedBitmap = parsed
                .optString("croppedImageBase64")
                .takeIf { it.isNotBlank() }
                ?.let(::decodeAnalysisImage)

            val highlightedBitmap = parsed
                .optString("highlightedImageBase64")
                .takeIf { it.isNotBlank() }
                ?.let(::decodeAnalysisImage)

            Triple(normalizedBitmap, croppedBitmap, highlightedBitmap)
        }

        report = parsed
        normalized = decoded.first
        cropped = decoded.second
        highlighted = decoded.third
    }

    suspend fun openDocument(doc: Document) {
        selectedDocument = doc
        selectedResult = results
            .filter { it.documentId == doc.documentId }
            .maxByOrNull { it.alterationResultId ?: 0L }

        report = null
        sourceImage = null
        normalized = null
        cropped = null
        highlighted = null
        referenceId = null
        notes = ""
        checked = false
        page = AnalysisAdminPage.DETAILS

        val fileName = requireNotNull(doc.fileName) {
            "This document has no stored image filename."
        }

        val body = requireAnalysisResponse(
            RetrofitClient.apiService.getDocumentImage(fileName)
        )

        val bytes = withContext(Dispatchers.IO) {
            body.use { it.bytes() }
        }

        try {
            sourceImage = withContext(Dispatchers.Default) {
                decodeAnalysisImage(bytes)
            }
        } finally {
            bytes.fill(0)
        }

        selectedResult?.let { latest ->
            if (latest.analysisStatus in listOf("COMPLETED", "INCOMPLETE")) {
                openResult(latest)
            }
        }
    }

    suspend fun runAnalysis(doc: Document) {
        val fileName = requireNotNull(doc.fileName) {
            "Missing document filename."
        }

        val body = requireAnalysisResponse(
            RetrofitClient.apiService.getDocumentImage(fileName)
        )

        val bytes = withContext(Dispatchers.IO) {
            body.use { it.bytes() }
        }

        try {
            val ocrJson = DocumentOcrProcessor.extract(bytes)

            val digest = withContext(Dispatchers.Default) {
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") {
                        "%02x".format(it.toInt() and 255)
                    }
            }

            val newResult = requireAnalysisResponse(
                RetrofitClient.apiService.analyzeDocument(
                    requireNotNull(doc.documentId),
                    AnalysisRequest(
                        digest,
                        ocrJson,
                        referenceId
                    )
                )
            )

            refresh()
            openResult(newResult)

        } finally {
            bytes.fill(0)
        }
    }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return

        scope.launch {
            busy = true
            message = ""

            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message
                    ?: "Operation failed. Check the connection and retry."
            } finally {
                busy = false
            }
        }
    }

    fun goBack() {
        when (page) {
            AnalysisAdminPage.DETAILS -> {
                page = AnalysisAdminPage.DOCUMENTS
                selectedDocument = null
                selectedResult = null
                report = null
                sourceImage = null
                normalized = null
                cropped = null
                highlighted = null
                referenceId = null
                notes = ""
                checked = false
            }

            AnalysisAdminPage.DOCUMENTS -> {
                page = AnalysisAdminPage.CLIENTS
                selectedCustomer = null
            }

            AnalysisAdminPage.CLIENTS -> {
                page = AnalysisAdminPage.AGENTS
                selectedAgent = null
            }

            AnalysisAdminPage.AGENTS -> Unit
        }

        message = ""
    }

    BackHandler(
        enabled = page != AnalysisAdminPage.AGENTS && !busy
    ) {
        goBack()
    }

    LaunchedEffect(Unit) {
        busy = true
        try {
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "Cannot load alteration analysis data."
        } finally {
            busy = false
        }
    }

    val agentDocuments = documents.filter {
        it.agentId != null && it.agentId == selectedAgent?.agentId
    }

    val clientDocuments = agentDocuments.filter {
        it.customerId != null && it.customerId == selectedCustomer?.customerId
    }

    val uniqueCustomers = agentDocuments
        .mapNotNull { it.customerId }
        .toSet()
        .mapNotNull { id -> customers.firstOrNull { it.customerId == id } }
        .sortedBy { it.fullName.orEmpty() }

    val selectedType = selectedDocument?.let { doc ->
        types.firstOrNull {
            it.documentTypeId == doc.documentTypeId
        }
    }

    val referenceCandidates = selectedDocument?.let { doc ->
        val typeCode = selectedType?.typeCode

        if (typeCode in setOf("SALARY_SLIP", "UTILITY_BILL")) {
            emptyList()
        } else {
            documents
                .filter { other ->
                    other.documentId != null &&
                            doc.documentId != null &&
                            other.documentId < doc.documentId &&
                            other.customerId == doc.customerId &&
                            other.documentTypeId == doc.documentTypeId &&
                            other.captureSource == "LIVE_CAMERA" &&
                            other.qualityStatus == "PASSED" &&
                            other.blurPassed == true &&
                            other.brightnessPassed == true &&
                            other.resolutionPassed == true &&
                            results
                                .filter { it.documentId == other.documentId }
                                .maxByOrNull { it.alterationResultId ?: 0L }
                                ?.let {
                                    it.analysisStatus == "COMPLETED" &&
                                            it.finalDecision == "APPROVED" &&
                                            it.reviewStatus == "REVIEWED" &&
                                            it.algorithmVersion in setOf(
                                        "document-analysis-4",
                                        "document-analysis-5",
                                        CURRENT_ANALYSIS_VERSION
                                    )
                                } == true
                }
                .sortedByDescending { it.documentId ?: 0L }
        }
    }.orEmpty()

    val locationText = when (page) {
        AnalysisAdminPage.AGENTS -> "Agents"
        AnalysisAdminPage.CLIENTS ->
            "${selectedAgent?.fullName.orEmpty()}  •  Clients"
        AnalysisAdminPage.DOCUMENTS ->
            "${selectedCustomer?.fullName.orEmpty()}  •  Documents"
        AnalysisAdminPage.DETAILS ->
            "Document #${selectedDocument?.documentId ?: "-"}  •  Details"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFCFB),
                        Color(0xFFFFF8FB),
                        Color(0xFFF8F2FF)
                    )
                )
            )
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height

            val topShape = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.72f, 0f)
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
                path = topShape,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFD1AF).copy(alpha = 0.58f),
                        Color(0xFFFFE8DE).copy(alpha = 0.24f),
                        Color.Transparent
                    )
                )
            )

            val ribbon = Path().apply {
                moveTo(0f, h * 0.075f)
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
                path = ribbon,
                color = Color(0xFFFF8B42).copy(alpha = 0.70f),
                style = Stroke(width = 1.1.dp.toPx())
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF9F47DD).copy(alpha = 0.08f),
                        Color.Transparent
                    )
                ),
                radius = w * 0.75f,
                center = Offset(
                    x = w * 0.55f,
                    y = h
                )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Alteration Analysis",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF171A2C)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Review submissions by agent and client, compare registered data with OCR, and inspect Component 4 v6 alteration evidence.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF817A8B)
                )
            }

            item {
                AnalysisOverviewCard(
                    documentCount = documents.size,
                    resultCount = results.size,
                    highRiskCount = results.count {
                        it.riskLevel?.equals("HIGH", ignoreCase = true) == true
                    }
                )
            }

            item {
                AnalysisLocationCard(
                    location = locationText,
                    busy = busy,
                    onRefresh = {
                        runAction { refresh() }
                    }
                )
            }

            if (busy) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF4EFFA))
                            .padding(14.dp)
                    ) {
                        Column {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Working… analysis may take several minutes.",
                                fontSize = 11.sp,
                                color = Color(0xFF6C5B7A)
                            )
                        }
                    }
                }
            }

            if (message.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFEEF1))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = message,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Color(0xFFA13A4B)
                        )
                    }
                }
            }

            when (page) {
                AnalysisAdminPage.AGENTS -> {
                    item {
                        AnalysisSectionHeading(
                            title = "Select an Agent",
                            subtitle = "Open an agent to see the clients represented in their submitted documents."
                        )
                    }

                    val visibleAgents = agents
                        .filter { agent ->
                            documents.any { it.agentId == agent.agentId }
                        }
                        .sortedBy { it.fullName.orEmpty() }

                    items(
                        items = visibleAgents,
                        key = { it.agentId ?: it.hashCode().toLong() }
                    ) { agent ->
                        val count = documents.count {
                            it.agentId == agent.agentId
                        }

                        AnalysisNavigationCard(
                            eyebrow = "AGENT",
                            title = agent.fullName ?: "Agent #${agent.agentId ?: "-"}",
                            subtitle = agent.email.orEmpty().ifBlank {
                                "No email recorded"
                            },
                            meta = "$count submitted document${if (count == 1) "" else "s"}",
                            enabled = !busy,
                            onClick = {
                                selectedAgent = agent
                                selectedCustomer = null
                                selectedDocument = null
                                page = AnalysisAdminPage.CLIENTS
                                message = ""
                            }
                        )
                    }

                    if (visibleAgents.isEmpty()) {
                        item {
                            AnalysisInfoNotice(
                                text = "No agent submissions are available for alteration analysis."
                            )
                        }
                    }
                }

                AnalysisAdminPage.CLIENTS -> {
                    item {
                        AnalysisSectionHeading(
                            title = "Clients Submitted by This Agent",
                            subtitle = selectedAgent?.fullName
                                ?: "Selected agent"
                        )
                    }

                    items(
                        items = uniqueCustomers,
                        key = { it.customerId ?: it.hashCode().toLong() }
                    ) { customer ->
                        val count = agentDocuments.count {
                            it.customerId == customer.customerId
                        }

                        AnalysisNavigationCard(
                            eyebrow = "CLIENT",
                            title = customer.fullName
                                ?: "Client #${customer.customerId ?: "-"}",
                            subtitle = "NIC: ${customer.nic ?: "Not entered"}",
                            meta = "$count document${if (count == 1) "" else "s"}",
                            enabled = !busy,
                            onClick = {
                                selectedCustomer = customer
                                selectedDocument = null
                                page = AnalysisAdminPage.DOCUMENTS
                                message = ""
                            }
                        )
                    }

                    if (uniqueCustomers.isEmpty()) {
                        item {
                            AnalysisInfoNotice(
                                text = "No registered clients were found for this agent's documents."
                            )
                        }
                    }
                }

                AnalysisAdminPage.DOCUMENTS -> {
                    item {
                        AnalysisSectionHeading(
                            title = "Client Documents",
                            subtitle = selectedCustomer?.fullName
                                ?: "Selected client"
                        )
                    }

                    items(
                        items = clientDocuments.sortedByDescending {
                            it.documentId ?: 0L
                        },
                        key = { it.documentId ?: it.hashCode().toLong() }
                    ) { doc ->
                        val type = types.firstOrNull {
                            it.documentTypeId == doc.documentTypeId
                        }

                        val latest = results
                            .filter { it.documentId == doc.documentId }
                            .maxByOrNull { it.alterationResultId ?: 0L }

                        val latestText = when {
                            latest?.riskLevel != null ->
                                "${latest.riskLevel} risk"
                            latest?.analysisStatus != null ->
                                latest.analysisStatus
                            else ->
                                "Not analysed"
                        }

                        AnalysisNavigationCard(
                            eyebrow = type?.typeCode ?: "DOCUMENT",
                            title = type?.typeName
                                ?: "Document #${doc.documentId ?: "-"}",
                            subtitle = "Document #${doc.documentId ?: "-"}  •  ${doc.qualityStatus ?: "Quality unknown"}",
                            meta = latestText,
                            enabled = !busy,
                            onClick = {
                                runAction {
                                    openDocument(doc)
                                }
                            }
                        )
                    }

                    if (clientDocuments.isEmpty()) {
                        item {
                            AnalysisInfoNotice(
                                text = "This agent has no documents for the selected client."
                            )
                        }
                    }
                }

                AnalysisAdminPage.DETAILS -> {
                    val doc = selectedDocument

                    if (doc == null) {
                        item {
                            AnalysisInfoNotice(
                                text = "The selected document is no longer available. Go back and refresh."
                            )
                        }
                    } else {
                        item {
                            AnalysisSectionHeading(
                                title = "Document Review Workspace",
                                subtitle = "Document #${doc.documentId ?: "-"}"
                            )
                        }

                        item {
                            AnalysisDetailCard(
                                title = "Registered Client",
                                rows = listOf(
                                    "Name" to (selectedCustomer?.fullName ?: "-"),
                                    "NIC" to (selectedCustomer?.nic ?: "-"),
                                    "Phone" to (selectedCustomer?.phone ?: "-"),
                                    "Email" to (selectedCustomer?.email ?: "-"),
                                    "Submitted By" to (selectedAgent?.fullName ?: "-")
                                )
                            )
                        }

                        item {
                            AnalysisDetailCard(
                                title = "Document Details",
                                rows = listOf(
                                    "Document ID" to (doc.documentId?.toString() ?: "-"),
                                    "Type" to (selectedType?.typeName ?: "Unknown"),
                                    "Type Code" to (selectedType?.typeCode ?: "Unknown"),
                                    "Captured" to (doc.captureDateTime ?: "Unknown"),
                                    "Capture Source" to (doc.captureSource ?: "Unknown"),
                                    "Quality" to (doc.qualityStatus ?: "Unknown"),
                                    "Processing" to (doc.processingStatus ?: "Unknown"),
                                    "C4 Analysis" to if (selectedType?.analysisEnabled == true) "Enabled" else "Disabled"
                                )
                            )
                        }

                        item {
                            AnalysisEvidenceCard(
                                title = "Submitted Original",
                                subtitle = "Securely retrieved document image used for OCR and alteration analysis."
                            ) {
                                sourceImage?.let { bitmap ->
                                    AnalysisPreview(
                                        bitmap = bitmap,
                                        description = "Submitted document"
                                    )
                                } ?: Text(
                                    text = "Image is not available.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF817A8B)
                                )
                            }
                        }

                        selectedResult?.let { row ->
                            item {
                                AnalysisResultCard(row = row)
                            }
                        }

                        item {
                            AnalysisReferenceSelector(
                                typeCode = selectedType?.typeCode,
                                candidates = referenceCandidates,
                                selectedId = referenceId,
                                busy = busy,
                                onSelect = { selected ->
                                    referenceId = selected
                                }
                            )
                        }

                        item {
                            val canAnalyze =
                                !busy &&
                                        selectedType?.analysisEnabled == true &&
                                        doc.captureSource == "LIVE_CAMERA" &&
                                        doc.qualityStatus == "PASSED" &&
                                        doc.blurPassed == true &&
                                        doc.brightnessPassed == true &&
                                        doc.resolutionPassed == true

                            AnalysisPrimaryAction(
                                text = if (selectedResult == null) {
                                    "RUN OCR & COMPONENT 4 V6 ANALYSIS"
                                } else {
                                    "RUN NEW COMPONENT 4 V6 ANALYSIS"
                                },
                                enabled = canAnalyze,
                                onClick = {
                                    runAction {
                                        runAnalysis(doc)
                                    }
                                }
                            )

                            if (selectedType?.analysisEnabled != true) {
                                Spacer(modifier = Modifier.height(8.dp))
                                AnalysisInfoNotice(
                                    text = "Alteration analysis is disabled for this document type."
                                )
                            }
                        }

                        report?.let { details ->
                            item {
                                Component4ReferenceCard(details = details)
                            }

                            item {
                                Component4CustomerComparisonCard(
                                    customer = selectedCustomer,
                                    details = details
                                )
                            }

                            cropped?.let { bitmap ->
                                item {
                                    AnalysisEvidenceCard(
                                        title = "Cropped Document",
                                        subtitle = "Detected document boundary used by the analysis pipeline."
                                    ) {
                                        AnalysisPreview(
                                            bitmap = bitmap,
                                            description = "Cropped document"
                                        )
                                    }
                                }
                            }

                            normalized?.let { bitmap ->
                                item {
                                    AnalysisEvidenceCard(
                                        title = if (
                                            selectedResult?.analysisStatus == "INCOMPLETE"
                                        ) {
                                            "Original Document"
                                        } else {
                                            "Template-Aligned Document"
                                        },
                                        subtitle = if (
                                            selectedResult?.analysisStatus == "INCOMPLETE"
                                        ) {
                                            "Analysis is incomplete; this preview is not a verification result."
                                        } else {
                                            "Image used by the Component 4 analysis pipeline."
                                        }
                                    ) {
                                        AnalysisPreview(
                                            bitmap = bitmap,
                                            description = "Analysis document image"
                                        )
                                    }
                                }
                            }

                            highlighted?.let { bitmap ->
                                item {
                                    AnalysisEvidenceCard(
                                        title = "Highlighted Candidate Areas",
                                        subtitle = "Highlighted regions are review indicators and require human checking."
                                    ) {
                                        AnalysisPreview(
                                            bitmap = bitmap,
                                            description = "Highlighted candidate areas"
                                        )
                                    }
                                }
                            }

                            if (selectedResult?.analysisStatus == "INCOMPLETE") {
                                item {
                                    AnalysisInfoNotice(
                                        text = "No complete alteration conclusion is available. Recapture or refer the document for investigation."
                                    )
                                }
                            }

                            item {
                                AnalysisTextSection(
                                    title = "OCR Text",
                                    value = details
                                        .optJSONObject("ocr")
                                        ?.optString("fullText")
                                        .orEmpty()
                                        .ifBlank { "No text recognized." }
                                )
                            }

                            item {
                                Component4ExtractedFieldsCard(details)
                            }

                            item {
                                Component4OcrValidationCard(details)
                            }

                            item {
                                Component4CoverageCard(details)
                            }

                            item {
                                Component4FindingsCard(details)
                            }

                            item {
                                Component4ScoresCard(details)
                            }

                            selectedResult?.let { row ->
                                if (row.finalDecision == null) {
                                    item {
                                        val onlyReferral =
                                            row.riskLevel == "HIGH" ||
                                                    row.analysisStatus == "INCOMPLETE" ||
                                                    row.algorithmVersion != CURRENT_ANALYSIS_VERSION ||
                                                    details.optString("verificationStatus") != "REFERENCE_COMPARED"

                                        ManualReviewCard(
                                            notes = notes,
                                            checked = checked,
                                            busy = busy,
                                            onNotesChange = { value ->
                                                if (value.length <= 1000) {
                                                    notes = value
                                                }
                                            },
                                            onCheckedChange = { value ->
                                                checked = value
                                            },
                                            decisions = if (onlyReferral) {
                                                listOf("REFERRED_FOR_INVESTIGATION")
                                            } else {
                                                listOf(
                                                    "APPROVED",
                                                    "REJECTED",
                                                    "REFERRED_FOR_INVESTIGATION"
                                                )
                                            },
                                            onDecision = { decision ->
                                                runAction {
                                                    selectedResult = requireAnalysisResponse(
                                                        RetrofitClient.apiService.reviewAnalysis(
                                                            requireNotNull(row.alterationResultId),
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
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}


@Composable
private fun AnalysisLocationCard(
    location: String,
    busy: Boolean,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(Color.White.copy(alpha = 0.94f))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = "Workspace",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9A849E),
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = location.ifBlank { "Alteration Analysis" },
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF262237)
            )

            Spacer(modifier = Modifier.height(10.dp))

            AnalysisMiniAction(
                text = "REFRESH",
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = onRefresh
            )
        }
    }
}


@Composable
private fun AnalysisMiniAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF6B50B8),
                            Color(0xFFD34D92)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFE6E0E9),
                            Color(0xFFE6E0E9)
                        )
                    )
                }
            )
            .clickable(enabled = enabled) {
                onClick()
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color(0xFF9A939F)
        )
    }
}


@Composable
private fun AnalysisSectionHeading(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B1D30)
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = subtitle,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = Color(0xFF817A8B)
        )
    }
}


@Composable
private fun AnalysisNavigationCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    meta: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .clickable(enabled = enabled) {
                onClick()
            }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFFF8149),
                                Color(0xFFF04F8A)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = eyebrow
                        .take(3)
                        .uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(13.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF171A2C),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = Color(0xFF8D8695),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = meta,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6F4BB8),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "OPEN",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    Color(0xFFD34D92)
                } else {
                    Color(0xFFA9A2AD)
                }
            )
        }
    }
}


@Composable
private fun AnalysisDetailCard(
    title: String,
    rows: List<Pair<String, String>>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A2C)
            )

            Spacer(modifier = Modifier.height(8.dp))

            rows.forEach { (label, value) ->
                AnalysisInfoRow(
                    label = label,
                    value = value.ifBlank { "-" }
                )
            }
        }
    }
}


@Composable
private fun AnalysisReferenceSelector(
    typeCode: String?,
    candidates: List<Document>,
    selectedId: Long?,
    busy: Boolean,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember(typeCode, candidates.size) {
        mutableStateOf(false)
    }

    val disabledForType =
        typeCode in setOf("SALARY_SLIP", "UTILITY_BILL")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Trusted Same-Document Reference",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A2C)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Only an earlier secure live capture for the same client and document type, with an approved reviewed result, can be used.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Color(0xFF817A8B)
            )

            Spacer(modifier = Modifier.height(12.dp))

            when {
                disabledForType -> {
                    AnalysisInfoNotice(
                        text = "Trusted-reference comparison is not available for this document type. Component 4 will run single-image screening only."
                    )
                }

                candidates.isEmpty() -> {
                    AnalysisInfoNotice(
                        text = "No eligible earlier approved reference is available. You can still run screening, but approval or rejection requires a trusted same-document comparison."
                    )
                }

                else -> {
                    Box {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF1ECFA))
                                .clickable(enabled = !busy) {
                                    expanded = true
                                }
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 12.dp
                                )
                        ) {
                            Text(
                                text = selectedId?.let {
                                    "Reference: approved document #$it"
                                } ?: "No reference selected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF6F4BB8)
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = {
                                expanded = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text("No reference")
                                },
                                onClick = {
                                    onSelect(null)
                                    expanded = false
                                }
                            )

                            candidates.forEach { document ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Approved document #${document.documentId ?: "-"}"
                                        )
                                    },
                                    onClick = {
                                        onSelect(document.documentId)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun AnalysisPrimaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF6950B7),
                            Color(0xFF9E4FC4),
                            Color(0xFFD34D92)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFE4DEE9),
                            Color(0xFFE4DEE9)
                        )
                    )
                }
            )
            .clickable(enabled = enabled) {
                onClick()
            }
            .padding(
                horizontal = 14.dp,
                vertical = 14.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) {
                Color.White
            } else {
                Color(0xFF9B949F)
            }
        )
    }
}


@Composable
private fun AnalysisCustomerComparisonCard(
    customer: Customer?,
    details: JSONObject
) {
    val check = details.optJSONObject("customerCrossCheck")
    val fields = check?.optJSONObject("fields")
    val nic = fields?.optJSONObject("nic")
    val name = fields?.optJSONObject("fullName")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Registered Information vs OCR",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A2C)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Registered values come from the server database. OCR differences are review signals, not proof of identity fraud.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Color(0xFF817A8B)
            )

            Spacer(modifier = Modifier.height(10.dp))

            AnalysisInfoRow(
                label = "Cross-check",
                value = check?.optString("status")
                    ?.ifBlank { "Not assessed" }
                    ?: "Not assessed"
            )

            AnalysisInfoRow(
                label = "Registered NIC",
                value = customer?.nic ?: "Not registered"
            )

            AnalysisInfoRow(
                label = "NIC OCR",
                value = analysisComparisonText(nic)
            )

            AnalysisInfoRow(
                label = "Registered Name",
                value = customer?.fullName ?: "Not registered"
            )

            AnalysisInfoRow(
                label = "Name OCR",
                value = analysisComparisonText(name)
            )

            if (check == null) {
                Spacer(modifier = Modifier.height(8.dp))
                AnalysisInfoNotice(
                    text = "This report has no registered-customer cross-check. Run a new Component 4 v6 analysis."
                )
            }
        }
    }
}


private fun analysisComparisonText(
    field: JSONObject?
): String {
    if (field == null) {
        return "Not assessed"
    }

    val candidates = field.optJSONArray("extractedCandidates")

    val extracted = if (
        candidates == null || candidates.length() == 0
    ) {
        "Not extracted"
    } else {
        (0 until candidates.length()).joinToString("; ") {
            candidates.optString(it)
        }
    }

    val status = field
        .optString("status", "NOT_CHECKED")
        .ifBlank { "NOT_CHECKED" }

    return "$extracted — $status"
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
                CURRENT_ANALYSIS_VERSION
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            10.dp
                        )
                )


                AnalysisInfoNotice(
                    text =
                        "Earlier analysis detected. Rerun with Component 4 v6 before making a new decision."
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
): Bitmap =
    decodeAnalysisImage(
        Base64.decode(
            encoded,
            Base64.DEFAULT
        )
    )


private fun decodeAnalysisImage(
    bytes: ByteArray
): Bitmap {

    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        options
    )

    require(
        options.outWidth > 0 &&
                options.outHeight > 0 &&
                options.outWidth.toLong() * options.outHeight <= 12_000_000L
    ) {
        "Invalid document image"
    }

    return requireNotNull(
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size
        )
    ) {
        "Unable to read document image"
    }
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
