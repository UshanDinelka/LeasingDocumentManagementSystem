package com.leasingdocument.app.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.leasingdocument.app.network.*
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
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf<List<Document>>(emptyList()) }
    var results by remember { mutableStateOf<List<AlterationResult>>(emptyList()) }
    var selected by remember { mutableStateOf<AlterationResult?>(null) }
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var normalized by remember { mutableStateOf<Bitmap?>(null) }
    var cropped by remember { mutableStateOf<Bitmap?>(null) }
    var highlighted by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(false) }
    var showTechnical by remember { mutableStateOf(false) }
    // Per-document, admin-selected comparison baseline. Null means screening only.
    var referenceSelections by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }

    suspend fun refresh() {
        documents = requireAnalysisResponse(RetrofitClient.apiService.getDocuments())
        results = requireAnalysisResponse(RetrofitClient.apiService.getAlterationResults())
    }
    suspend fun openResult(row: AlterationResult) {
        selected = row; report = null; normalized = null; cropped = null; highlighted = null
        notes = ""; checked = false; showTechnical = false
        if (row.analysisStatus !in listOf("COMPLETED", "INCOMPLETE")) return
        val body = requireAnalysisResponse(RetrofitClient.apiService.getAnalysisReport(requireNotNull(row.alterationResultId)))
        val parsed = withContext(Dispatchers.IO) { body.use { JSONObject(it.string()) } }
        val images = withContext(Dispatchers.Default) {
            decodeAnalysisImage(parsed.optString("normalizedImageBase64").ifBlank { parsed.getString("originalImageBase64") }) to
                parsed.optString("highlightedImageBase64").takeIf { it.isNotBlank() }?.let { decodeAnalysisImage(it) }
        }
        cropped = withContext(Dispatchers.Default) {
            parsed.optString("croppedImageBase64").takeIf { it.isNotBlank() }?.let { decodeAnalysisImage(it) }
        }
        report = parsed; normalized = images.first; highlighted = images.second
    }
    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; message = ""
            try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message ?: "Operation failed. Check the connection and retry." } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        busy = true
        try { refresh() } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message ?: "Cannot load documents" } finally { busy = false }
    }
    if (AuthSession.role != "ADMIN") { Text("Administrator access required."); return }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Document Alteration Analysis", style = MaterialTheme.typography.headlineSmall)
            Text("Screening highlights possible alterations. A person makes the final decision.")
            if (busy) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()); Text("Working… analysis may take several minutes.") }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            Button(enabled = !busy, onClick = { runAction { refresh() } }) { Text("Refresh documents and results") }
        }
        selected?.let { row ->
            item {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Document ${row.documentId} · Result ${row.alterationResultId}", style = MaterialTheme.typography.titleMedium)
                        Text("Analysis: ${row.analysisStatus ?: "Unknown"}")
                        if (row.algorithmVersion != "document-analysis-6") Text("Earlier analysis: rerun with the corrected pipeline before making a decision.", color = MaterialTheme.colorScheme.error)
                        Text("Risk: ${row.riskLevel ?: "Not available"} · Score: ${row.riskScore ?: "Not available"}")
                        if (report != null && report?.optString("detectorBuild") != "proposal-4.4.3-all-eight-v2") {
                            Text("This saved result predates the latest detector. Run a NEW analysis.", color = MaterialTheme.colorScheme.error)
                        }
                        Text("HIGH means inspect the highlighted difference. LOW does not establish authenticity; without an approved original, field contents are not verified.")
                        val comparisonStatus = report?.optString("verificationStatus") ?: "UNKNOWN"
                        if (comparisonStatus == "NOT_VERIFIED_NO_SAME_DOCUMENT_REFERENCE") {
                            Text("IDENTITY NOT VERIFIED — no approved original was compared. Name, NIC number, dates, photograph and signature could have been changed.",
                                color = MaterialTheme.colorScheme.error)
                        } else if (comparisonStatus == "REFERENCE_COMPARED") {
                            Text("Compared against prior same-document capture. Differences need human review; authenticity is NOT established.")
                        } else if (comparisonStatus == "REFERENCE_UNRELIABLE") {
                            Text("Reference comparison failed. Recapture and investigate.", color = MaterialTheme.colorScheme.error)
                        }
                        if (report?.optString("captureContextWarning") == "POSSIBLE_DOCUMENT_EDITOR_SCREEN_IN_CAPTURE") {
                            Text("This image appears to include a document-editing screen. Do not use it as proof of an original physical document.",
                                color = MaterialTheme.colorScheme.error)
                        }
                        Text(row.analysisMessage ?: "")
                        Text("Recommendation: ${row.recommendedAction ?: "Pending"}")
                        Text("OCR: ${row.ocrStatus ?: "Unknown"}")
                        Text("Manual decision: ${row.finalDecision ?: "Not recorded"}")
                        if (row.reviewNotes != null) Text("Review notes: ${row.reviewNotes}")
                    }
                }
            }
            report?.let { details ->
                item {
                    cropped?.let {
                        Text("Cropped document — check the boundary")
                        AnalysisPreview(it, "Cropped document")
                    }
                    Text(if (row.analysisStatus == "INCOMPLETE") "Original document — analysis incomplete" else "Template-aligned document — used for analysis")
                    normalized?.let { AnalysisPreview(it, "Document image") }
                    if (highlighted != null) Text("Highlighted candidate areas — require manual checking")
                    highlighted?.let { AnalysisPreview(it, "Candidate areas") }
                    if (row.analysisStatus == "INCOMPLETE") Text("No alteration score or highlights are available. Recapture or refer for manual investigation.")
                    details.optString("reasonCode").takeIf { it.isNotBlank() }?.let {
                        Text("Reason: $it", color = MaterialTheme.colorScheme.error)
                    }
                    val referenceId = details.optLong("referenceDocumentId", -1L)
                    Text(if (referenceId > 0) "Compared with prior approved document #$referenceId"
                         else "Template screening only — no same-document reference supplied")
                    Text(details.optString("referenceProvenanceNotice"))
                    Text("Same-document verification", style = MaterialTheme.typography.titleMedium)
                    Text(details.optJSONObject("same_document_verification")?.optString("status")
                        ?: "No comparison was performed")
                }
                item {
                    Text("Proposal §4.4.3 — analysis coverage", style = MaterialTheme.typography.titleMedium)
                    val proposal = details.optJSONObject("proposal_4_4_3")
                    val checks = proposal?.optJSONObject("checks")
                    val requirements = listOf(
                        "ocr_analysis" to "1. OCR fields and format",
                        "text_analysis" to "2. Text size / alignment",
                        "altered_text_detection" to "3. Replaced / covered text",
                        "background_analysis" to "4. Security background",
                        "symbol_analysis" to "5. Signatures and logos",
                        "photo_analysis" to "6. Photograph",
                        "edge_analysis" to "7. Edge and pasted regions"
                    )
                    if (checks == null) {
                        Text("Detailed proposal checks not available in this saved result. Run a new analysis.",
                            color = MaterialTheme.colorScheme.error)
                    } else {
                        for ((key, label) in requirements) {
                            val section = checks.optJSONObject(key)
                            val status = section?.optString("status") ?: "NOT_ASSESSED"
                            val count = section?.optInt("finding_count", 0) ?: 0
                            Text("$label: $status · $count finding(s)")
                            if (status != "ASSESSED") {
                                Text(section?.optJSONArray("limitations")?.optString(0).orEmpty(),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        val physical = checks.optJSONObject("physical_obstruction")
                        Text("Physical object / finger screening: ${physical?.optString("status") ?: "NOT_ASSESSED"} · ${physical?.optInt("finding_count") ?: 0} finding(s)")
                        Text("8. Combined risk score: ${row.riskLevel ?: "Unavailable"} · ${row.riskScore ?: "Unavailable"}")
                        Text("Scoring prioritizes suspicious independent regions; it is not a probability of forgery.")
                    }
                    Text("Registered customer vs document OCR", style = MaterialTheme.typography.titleMedium)
                    val crossCheck = details.optJSONObject("customerCrossCheck")
                    if (crossCheck == null || crossCheck.optString("status") == "NOT_PROVIDED") {
                        Text("No registered customer comparison was supplied by Spring Boot. This must be connected on the backend.",
                            color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Comparison: ${crossCheck.optString("status")}")
                        val extractedFields = crossCheck.optJSONObject("fields")
                        for ((key, label) in listOf("nic" to "NIC number", "fullName" to "Customer name")) {
                            val field = extractedFields?.optJSONObject(key) ?: continue
                            Text("$label: ${field.optString("status")}")
                            val candidates = field.optJSONArray("extractedCandidates")
                            Text("OCR candidates: ${candidates?.join(", ") ?: "none"}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (crossCheck.optString("status") == "DISCREPANCY_FOUND") {
                            Text("Document details do not agree with the registered customer. Verify the OCR and original records.",
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text("Localized alteration indicators", style = MaterialTheme.typography.titleMedium)
                    val visualFindings = details.optJSONObject("risk_details")?.optJSONArray("findings")
                    if (visualFindings == null || visualFindings.length() == 0) {
                        Text("No localized discrepancies detected; this does not prove authenticity.")
                    } else {
                        for (i in 0 until minOf(25, visualFindings.length())) {
                            val finding = visualFindings.optJSONObject(i) ?: continue
                            Text("${i + 1}. ${finding.optString("code")} · ${finding.optString("region")} · score ${finding.optDouble("score")}")
                            Text(finding.optString("reason"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("OCR text", style = MaterialTheme.typography.titleMedium)
                    Text(details.optJSONObject("ocr")?.optString("fullText").orEmpty().ifBlank { "No text recognized" })
                    Text("Extracted candidates", style = MaterialTheme.typography.titleMedium)
                    Text(details.optJSONObject("ocr")?.optJSONObject("fields")?.toString(2).orEmpty())
                    val suspicious = details.optJSONObject("altered_text_localization")?.optJSONArray("suspicious_areas")
                    Text("Highlighted findings: ${suspicious?.length() ?: 0}",
                        style = MaterialTheme.typography.titleMedium)
                    if (suspicious != null) {
                        for (i in 0 until minOf(12, suspicious.length())) {
                            val finding = suspicious.optJSONObject(i) ?: continue
                            Text("${i + 1}. ${finding.optString("finding_type")}: ${finding.optString("reason")}")
                        }
                    }
                    OutlinedButton(onClick = { showTechnical = !showTechnical }) {
                        Text(if (showTechnical) "Hide technical diagnostics" else "Show technical diagnostics")
                    }
                    if (showTechnical) {
                        Text("Alignment", style = MaterialTheme.typography.titleMedium)
                        Text(details.optJSONObject("alignment")?.toString(2).orEmpty())
                        Text("OCR issues", style = MaterialTheme.typography.titleMedium)
                        Text(details.optJSONObject("ocr_validation")?.optJSONArray("issues")?.toString(2).orEmpty())
                        Text("Coverage and uncalibrated scores", style = MaterialTheme.typography.titleMedium)
                        Text(details.optJSONObject("risk_details")?.optJSONObject("detector_coverage")?.toString(2).orEmpty())
                        Text(details.optJSONObject("risk_details")?.optJSONObject("component_scores")?.toString(2).orEmpty())
                    }
                }
                if (row.finalDecision == null) item {
                    OutlinedTextField(value = notes, onValueChange = { if (it.length <= 1000) notes = it },
                        label = { Text("Manual review notes") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    Row {
                        Checkbox(checked = checked, onCheckedChange = { checked = it }, enabled = !busy)
                        Text("I checked the available document image, OCR, and analysis evidence.")
                    }
                    val referenceVerified = report?.optString("verificationStatus") == "REFERENCE_COMPARED"
                    val decisions = if (!referenceVerified || row.riskLevel == "HIGH" || row.analysisStatus == "INCOMPLETE" || row.algorithmVersion != "document-analysis-6") listOf("REFERRED_FOR_INVESTIGATION")
                        else listOf("APPROVED", "REJECTED", "REFERRED_FOR_INVESTIGATION")
                    for (decision in decisions) {
                        Button(enabled = !busy && checked && notes.isNotBlank(), onClick = {
                            runAction {
                                selected = requireAnalysisResponse(RetrofitClient.apiService.reviewAnalysis(
                                    requireNotNull(row.alterationResultId), AnalysisReviewRequest(decision, notes)))
                                refresh()
                            }
                        }) { Text(if (decision == "REFERRED_FOR_INVESTIGATION") "Record referral for investigation" else decision) }
                    }
                    Text("A referral is recorded here for authorized follow-up; this does not send a notification.")
                }
            }
        }
        item { Text("Submitted documents", style = MaterialTheme.typography.titleLarge) }
        items(documents, key = { it.documentId ?: it.hashCode() }) { doc ->
            val latest = results.filter { it.documentId == doc.documentId }.maxByOrNull { it.alterationResultId ?: 0 }
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Document ${doc.documentId} · Customer ${doc.customerId}")
                    Text("Latest analysis: ${latest?.analysisStatus ?: "Not analysed"}")
                    val candidateReferences = documents.filter { candidate ->
                        val candidateId = candidate.documentId
                        candidateId != null && doc.documentId != null && candidateId < doc.documentId &&
                            candidate.customerId != null && candidate.customerId == doc.customerId &&
                            candidate.documentTypeId == doc.documentTypeId &&
                            candidate.captureSource == "LIVE_CAMERA" && candidate.qualityStatus == "PASSED" &&
                            candidate.blurPassed == true && candidate.brightnessPassed == true &&
                            candidate.resolutionPassed == true &&
                            results.filter { it.documentId == candidateId }
                                .maxByOrNull { it.alterationResultId ?: 0L }
                                ?.let { it.analysisStatus == "COMPLETED" && it.finalDecision == "APPROVED" &&
                                    it.reviewStatus == "REVIEWED" &&
                                    it.algorithmVersion == "document-analysis-6" } == true
                    }
                    if (candidateReferences.isEmpty()) {
                        Text("No prior approved reference. Analysis will screen the template, not confirm personal fields.")
                    } else {
                        var referencesOpen by remember(doc.documentId) { mutableStateOf(false) }
                        Box {
                            OutlinedButton(enabled = !busy, onClick = { referencesOpen = true }) {
                                Text(referenceSelections[doc.documentId]?.let { "Reference: document #$it" }
                                    ?: "No reference — template screening")
                            }
                            DropdownMenu(expanded = referencesOpen, onDismissRequest = { referencesOpen = false }) {
                                DropdownMenuItem(text = { Text("No reference — template only") }, onClick = {
                                    referenceSelections = referenceSelections - requireNotNull(doc.documentId)
                                    referencesOpen = false
                                })
                                candidateReferences.forEach { candidate ->
                                    DropdownMenuItem(text = { Text("Compare to approved #${candidate.documentId}") }, onClick = {
                                        referenceSelections = referenceSelections + (requireNotNull(doc.documentId) to requireNotNull(candidate.documentId))
                                        referencesOpen = false
                                    })
                                }
                            }
                        }
                        Text("References are earlier approved captures, not independently authenticated identity records.")
                    }
                    if (latest != null) Button(enabled = !busy, onClick = { runAction { openResult(latest) } }) { Text("View latest result") }
                    Button(enabled = !busy && doc.captureSource == "LIVE_CAMERA" && doc.qualityStatus == "PASSED",
                        onClick = { runAction {
                            val imageBody = requireAnalysisResponse(RetrofitClient.apiService.getDocumentImage(requireNotNull(doc.fileName)))
                            val bytes = withContext(Dispatchers.IO) { imageBody.use { it.bytes() } }
                            try {
                                val ocr = DocumentOcrProcessor.extract(bytes)
                                val hash = withContext(Dispatchers.Default) {
                                    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                                }
                                val row = requireAnalysisResponse(RetrofitClient.apiService.analyzeDocument(
                                    requireNotNull(doc.documentId), AnalysisRequest(hash, ocr, referenceSelections[doc.documentId])))
                                refresh(); openResult(row)
                            } finally { bytes.fill(0) }
                        } }) { Text(if (latest == null) "Run OCR and analysis" else "Run a new analysis") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun <T> requireAnalysisResponse(response: Response<T>): T {
    if (!response.isSuccessful) {
        val raw = response.errorBody()?.use { it.string() }.orEmpty()
        val detail = runCatching {
            val obj = JSONObject(raw)
            obj.optString("detail").ifBlank { obj.optString("message") }
        }.getOrDefault("")
        error(detail.ifBlank { when (response.code()) {
            401 -> "Session expired. Sign in again."
            403 -> "Administrator access is required."
            409 -> "Analysis is busy or the document state changed. Refresh and retry."
            422 -> "This document type is not supported by the configured templates. Manual review is required."
            502, 503 -> "Analysis service unavailable. Check the Python service and shared token."
            else -> "Request failed (HTTP ${response.code()})."
        } })
    }
    return requireNotNull(response.body()) { "The server returned an empty response." }
}

private fun decodeAnalysisImage(encoded: String): Bitmap {
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 12_000_000L)
    return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
}

@Composable
private fun AnalysisPreview(bitmap: Bitmap, description: String) {
    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
    Image(bitmap.asImageBitmap(), description,
        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).aspectRatio(ratio, matchHeightConstraintsFirst = true),
        contentScale = ContentScale.Fit)
}
