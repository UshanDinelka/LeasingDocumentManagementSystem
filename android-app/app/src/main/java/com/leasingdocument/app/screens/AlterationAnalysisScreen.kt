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

    suspend fun refresh() {
        documents = requireAnalysisResponse(RetrofitClient.apiService.getDocuments())
        results = requireAnalysisResponse(RetrofitClient.apiService.getAlterationResults())
    }
    suspend fun openResult(row: AlterationResult) {
        selected = row; report = null; normalized = null; cropped = null; highlighted = null
        notes = ""; checked = false
        if (row.analysisStatus !in listOf("COMPLETED", "INCOMPLETE") || row.highlightedImagePath == null) return
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
                        if (row.algorithmVersion != "document-analysis-4") Text("Earlier analysis: rerun with the corrected pipeline before making a decision.", color = MaterialTheme.colorScheme.error)
                        Text("Risk: ${row.riskLevel ?: "Not available"} · Score: ${row.riskScore ?: "Not available"}")
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
                    Text(details.optJSONObject("alignment")?.toString(2).orEmpty())
                    Text(details.optString("reasonCode"))
                }
                item {
                    Text("OCR text", style = MaterialTheme.typography.titleMedium)
                    Text(details.optJSONObject("ocr")?.optString("fullText").orEmpty().ifBlank { "No text recognized" })
                    Text("Extracted candidates", style = MaterialTheme.typography.titleMedium)
                    Text(details.optJSONObject("ocr")?.optJSONObject("fields")?.toString(2).orEmpty())
                    Text("OCR issues", style = MaterialTheme.typography.titleMedium)
                    Text(details.optJSONObject("ocr_validation")?.optJSONArray("issues")?.toString(2).orEmpty())
                    Text("Analysis coverage", style = MaterialTheme.typography.titleMedium)
                    Text(details.optJSONObject("altered_text_localization")?.optString("coverage_note").orEmpty())
                    Text(details.optJSONObject("risk_details")?.optJSONObject("detector_coverage")?.toString(2).orEmpty())
                    Text("Detector findings", style = MaterialTheme.typography.titleMedium)
                    Text(details.optJSONObject("risk_details")?.optJSONArray("findings")?.toString(2).orEmpty())
                    Text("Detector scores — uncalibrated screening indicators", style = MaterialTheme.typography.titleMedium)
                    Text(details.optJSONObject("risk_details")?.optJSONObject("component_scores")?.toString(2).orEmpty())
                }
                if (row.finalDecision == null) item {
                    OutlinedTextField(value = notes, onValueChange = { if (it.length <= 1000) notes = it },
                        label = { Text("Manual review notes") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    Row {
                        Checkbox(checked = checked, onCheckedChange = { checked = it }, enabled = !busy)
                        Text("I checked the available document image, OCR, and analysis evidence.")
                    }
                    val decisions = if (row.riskLevel == "HIGH" || row.analysisStatus == "INCOMPLETE" || row.algorithmVersion != "document-analysis-4") listOf("REFERRED_FOR_INVESTIGATION")
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
                                    requireNotNull(doc.documentId), AnalysisRequest(hash, ocr)))
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
