package com.leasingdocument.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.Customer
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// Presentation layer only. This file does not alter OCR, risk scoring, request bodies or Component 4.
private val Ink = Color(0xFF171A2C)
private val Muted = Color(0xFF817A8B)
private val Purple = Color(0xFF6950B7)
private val Magenta = Color(0xFFD34D92)
private val PalePurple = Color(0xFFF4EFFA)
private val Alert = Color(0xFFB13C55)
private val AlertBg = Color(0xFFFFEDF1)
private val Good = Color(0xFF277451)
private val GoodBg = Color(0xFFEAF7EF)
private val Review = Color(0xFF8B641F)
private val ReviewBg = Color(0xFFFFF5E7)

private fun labelize(value: String?): String = value.orEmpty()
    .replace('_', ' ')
    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
    .lowercase(Locale.ROOT)
    .replaceFirstChar { it.uppercaseChar().toString() }
    .ifBlank { "Not recorded" }

private fun friendly(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "Not recorded"

private fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)
private fun JSONObject.array(key: String): JSONArray? = optJSONArray(key)

private fun candidates(values: JSONArray?): String {
    if (values == null || values.length() == 0) return "Not extracted"
    val text = (0 until values.length())
        .mapNotNull { index ->
            values.opt(index)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim()
        }
        .filter { it.isNotBlank() }
        .distinct()
    return text.take(4).joinToString(" / ").ifBlank { "Not extracted" } +
            if (text.size > 4) " (+${text.size - 4} more)" else ""
}

private fun simpleValue(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "Not extracted"
    is JSONArray -> candidates(value)
    is JSONObject -> when {
        value.has("value") -> simpleValue(value.opt("value"))
        value.has("candidates") -> simpleValue(value.opt("candidates"))
        else -> "Structured details available in source report"
    }
    else -> value.toString().ifBlank { "Not extracted" }
}

private fun statusColors(status: String): Pair<Color, Color> {
    val normalized = status.uppercase(Locale.ROOT)
    return when {
        normalized == "MATCH" || normalized == "ASSESSED" || normalized == "VERIFIED" ||
                normalized == "COMPLETE" -> Good to GoodBg
        normalized == "MISMATCH" || normalized == "POSSIBLE_MISMATCH" ||
                normalized == "DISCREPANCY_FOUND" || normalized.contains("FAIL") ||
                normalized == "HIGH" -> Alert to AlertBg
        else -> Review to ReviewBg
    }
}

@Composable
private fun ReportPanel(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(Color.White.copy(alpha = .96f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
        }
        content()
    }
}

@Composable
private fun ReportStatus(status: String) {
    val (foreground, background) = statusColors(status)
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(background)
        .padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(labelize(status), color = foreground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(value.ifBlank { "Not recorded" }, color = Ink, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun ReportNote(text: String, warning: Boolean = false) {
    if (text.isBlank()) return
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (warning) AlertBg else PalePurple).padding(11.dp)) {
        Text(text, fontSize = 11.sp, lineHeight = 16.sp,
            color = if (warning) Alert else Purple)
    }
}

private data class ComparisonRow(
    val label: String, val registered: String, val extracted: String, val status: String
)

@Composable
private fun ComparisonHeaderCell(value: String, width: Int) {
    Text(value, modifier = Modifier.width(width.dp), color = Purple,
        fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
private fun ComparisonValueCell(value: String, width: Int) {
    Text(value, modifier = Modifier.width(width.dp), color = Ink,
        fontSize = 11.sp, lineHeight = 16.sp)
}

@Composable
fun Component4CustomerComparisonCard(customer: Customer?, details: JSONObject) {
    val check = details.obj("customerCrossCheck")
    val fieldChecks = check?.obj("fields")
    val ocrFields = details.obj("ocr")?.obj("fields")

    val nic = fieldChecks?.obj("nic")
    val name = fieldChecks?.obj("fullName")
    val rows = mutableListOf<ComparisonRow>()
    rows += ComparisonRow(
        "NIC number",
        friendly(nic?.optString("expected")?.takeIf { it.isNotBlank() } ?: customer?.nic),
        candidates(nic?.array("extractedCandidates")),
        nic?.optString("status")?.ifBlank { "NOT_CHECKED" } ?: "NOT_CHECKED"
    )
    rows += ComparisonRow(
        "Full name",
        friendly(name?.optString("expected")?.takeIf { it.isNotBlank() } ?: customer?.fullName),
        candidates(name?.array("extractedCandidates")),
        name?.optString("status")?.ifBlank { "NOT_CHECKED" } ?: "NOT_CHECKED"
    )
    val dates = ocrFields?.array("dates")
    if (dates != null && dates.length() > 0) {
        rows += ComparisonRow("Date candidates", "Not stored", candidates(dates), "NOT_CROSS_CHECKED")
    }

    ReportPanel("Registered Information vs OCR",
        "Four-column comparison • expected values come from the server's customer record when provided.") {
        if (check == null) {
            ReportNote("No customer comparison was returned. Run a new analysis to check NIC/name consistency.", true)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Overall", fontSize = 11.sp, color = Muted)
                ReportStatus(check.optString("status", "NOT_ASSESSED"))
            }
        }
        // Horizontal scrolling prevents text/numbers from being clipped on small phones.
        Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                .background(PalePurple).padding(horizontal = 10.dp, vertical = 10.dp)) {
                ComparisonHeaderCell("Field", 115)
                ComparisonHeaderCell("Registered", 145)
                ComparisonHeaderCell("OCR extracted", 170)
                ComparisonHeaderCell("Result", 132)
            }
            rows.forEach { row ->
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top) {
                    ComparisonValueCell(row.label, 115)
                    ComparisonValueCell(row.registered, 145)
                    ComparisonValueCell(row.extracted, 170)
                    Box(modifier = Modifier.width(132.dp)) { ReportStatus(row.status) }
                }
                HorizontalDivider(color = Color(0xFFF0ECF4))
            }
        }
        check?.array("discrepancies")?.let { discrepancies ->
            if (discrepancies.length() > 0) {
                val notes = (0 until discrepancies.length()).mapNotNull { i ->
                    discrepancies.optJSONObject(i)?.optString("reason")?.takeIf { it.isNotBlank() }
                }
                notes.forEach { ReportNote(it, true) }
            }
        }
        ReportNote("A missing or uncertain OCR read is not proof of alteration. A matching name/NIC does not establish authenticity.")
    }
}

@Composable
fun Component4ExtractedFieldsCard(details: JSONObject) {
    val fields = details.obj("ocr")?.obj("fields")
    val excluded = setOf("documentFieldPolicy", "candidateNote")
    val entries = mutableListOf<Pair<String, String>>()
    fields?.keys()?.forEach { key ->
        if (key !in excluded) {
            val value = simpleValue(fields.opt(key))
            if (value != "Not extracted") entries += labelize(key) to value
        }
    }
    ReportPanel("Extracted Document Fields", "Other OCR candidates. These are not verified customer details.") {
        if (entries.isEmpty()) {
            ReportNote("No additional structured fields were extracted.")
        } else {
            entries.forEachIndexed { index, (label, value) ->
                ReportLine(label, value)
                if (index < entries.lastIndex) HorizontalDivider(color = Color(0xFFF0ECF4))
            }
        }
        fields?.optString("candidateNote")?.takeIf { it.isNotBlank() }?.let { ReportNote(it) }
    }
}

@Composable
fun Component4OcrValidationCard(details: JSONObject) {
    val validation = details.obj("ocr_validation")
    val issues = validation?.array("issues")
    ReportPanel("OCR Validation", "Shows text quality issues rather than declaring document forgery.") {
        if (validation == null) ReportNote("No OCR validation information available.", true)
        else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Validation", fontSize = 11.sp, color = Muted)
                ReportStatus(validation.optString("status", "NOT_ASSESSED"))
            }
            if (issues == null || issues.length() == 0) {
                ReportNote("No specific OCR validation issues were reported.")
            } else {
                (0 until issues.length()).forEach { i ->
                    val issue = issues.optJSONObject(i) ?: return@forEach
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(labelize(issue.optString("code", "OCR review")), color = Ink,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        val field = issue.optString("field")
                        if (field.isNotBlank()) Text("Field: ${labelize(field)}", fontSize = 10.sp, color = Muted)
                        val reason = issue.optString("message")
                            .ifBlank { issue.optString("reason") }
                        if (reason.isNotBlank()) Text(reason, fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
                    }
                    if (i < issues.length() - 1) HorizontalDivider(color = Color(0xFFF0ECF4))
                }
            }
        }
    }
}

@Composable
fun Component4ReferenceCard(details: JSONObject) {
    val reference = details.obj("same_document_verification")
    val status = reference?.optString("status")?.ifBlank {
        details.optString("referenceComparisonStatus")
    }?.ifBlank { "NOT_PROVIDED" } ?: "NOT_PROVIDED"
    val compared = details.optString("verificationStatus") == "REFERENCE_COMPARED"
    ReportPanel("Same-Document Verification", "Layout-template checks and same-document comparisons are different tests.") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reference comparison", fontSize = 11.sp, color = Muted)
            ReportStatus(status)
        }
        ReportLine("Reference supplied", if (details.optBoolean("referenceSupplied", false)) "Yes" else "No")
        ReportLine("Verification context", labelize(details.optString("verificationStatus")))
        val note = details.optString("referenceProvenanceNotice")
            .ifBlank { reference?.optString("note").orEmpty() }
        if (note.isNotBlank()) ReportNote(note, !compared)
        if (!compared) ReportNote("No verified same-document comparison: a changed name, photo or signature cannot be confirmed using a generic template alone.", true)
    }
}

private val coverageLabels = listOf(
    "ocr_analysis" to "OCR analysis",
    "text_analysis" to "Text structure",
    "altered_text_detection" to "Altered / covered text",
    "background_analysis" to "Background anomalies",
    "symbol_analysis" to "Signatures and logos",
    "photo_analysis" to "Photograph screening",
    "edge_analysis" to "Edge analysis",
    "physical_obstruction" to "Physical obstruction"
)

@Composable
fun Component4CoverageCard(details: JSONObject) {
    val coverage = details.obj("risk_details")?.obj("detector_coverage")
    val proposal = details.obj("proposal_4_4_3")?.obj("checks")
    ReportPanel("Detection Coverage", "What Component 4 assessed for this particular image.") {
        if (proposal != null) {
            coverageLabels.forEachIndexed { index, (key, title) ->
                val check = proposal.obj(key)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), fontSize = 11.sp, color = Ink)
                    ReportStatus(check?.optString("status", "NOT_ASSESSED") ?: "NOT_ASSESSED")
                }
                val count = check?.optInt("finding_count", 0) ?: 0
                Text("$count reported finding${if (count == 1) "" else "s"}", fontSize = 10.sp, color = Muted)
                val limits = check?.array("limitations")
                if (limits != null && limits.length() > 0) {
                    Text(limits.optString(0), fontSize = 10.sp, color = Review)
                }
                if (index < coverageLabels.lastIndex) HorizontalDivider(color = Color(0xFFF0ECF4))
            }
        } else if (coverage != null) {
            ReportLine("Active checks", candidates(coverage.array("active")))
        } else {
            ReportNote("No coverage details were recorded for this result.", true)
        }
        val notAssessed = coverage?.array("not_assessed")
        if (notAssessed != null && notAssessed.length() > 0) {
            ReportNote("Not verified: ${candidates(notAssessed)}")
        }
    }
}

@Composable
fun Component4FindingsCard(details: JSONObject) {
    val risk = details.obj("risk_details")
    val findings = risk?.array("findings")
    val count = findings?.length() ?: 0
    ReportPanel("Alteration Findings", "$count localized candidate finding${if (count == 1) "" else "s"}. These require human review.") {
        if (count == 0) ReportNote("No localized finding was reported. This does not confirm the image is genuine.")
        else (0 until count).forEach { index ->
            val finding = findings?.optJSONObject(index) ?: return@forEach
            val category = labelize(finding.optString("component"))
            val region = labelize(finding.optString("region"))
            val score = finding.optDouble("score", Double.NaN)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}. ${labelize(finding.optString("code", "Finding"))}",
                        color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    if (score.isFinite()) ReportStatus(if (score >= 65) "HIGH" else if (score >= 35) "REVIEW" else "LOW")
                }
                Text(finding.optString("reason").ifBlank { finding.optString("message") },
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Region: $region", fontSize = 10.sp, color = Purple, modifier = Modifier.weight(1f))
                    Text("Detector: $category", fontSize = 10.sp, color = Purple, modifier = Modifier.weight(1f))
                }
                if (score.isFinite()) Text("Indicator score: ${String.format(Locale.US, "%.1f", score)}/100", fontSize = 10.sp, color = Muted)
                val mode = finding.optString("comparisonMode")
                if (mode.isNotBlank()) Text("Method: ${labelize(mode)}", fontSize = 10.sp, color = Muted)
                val x1 = finding.optInt("x1", -1)
                val y1 = finding.optInt("y1", -1)
                val x2 = finding.optInt("x2", -1)
                val y2 = finding.optInt("y2", -1)
                if (x1 >= 0 && y1 >= 0 && x2 > x1 && y2 > y1) {
                    Text("Image area: ($x1, $y1) to ($x2, $y2)", fontSize = 10.sp, color = Muted)
                }
            }
            if (index < count - 1) HorizontalDivider(color = Color(0xFFF0ECF4))
        }
    }
}

@Composable
fun Component4ScoresCard(details: JSONObject) {
    val values = details.obj("risk_details")?.obj("component_scores")
    val scoreKeys = listOf(
        "altered_text" to "Altered text", "photo" to "Photograph",
        "signature_logo" to "Signature / logo", "background" to "Background",
        "ocr" to "OCR", "fixed_artwork" to "Fixed artwork",
        "physical_obstruction" to "Physical obstruction", "customer_crosscheck" to "Customer comparison"
    )
    ReportPanel("Detector Scores", "Uncalibrated anomaly indicators (0–100), not forgery probabilities.") {
        if (values == null) ReportNote("Detector scores are not available for this result.", true)
        else scoreKeys.forEach { (key, label) ->
            val score = values.optDouble(key, Double.NaN)
            if (!score.isFinite()) return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f), fontSize = 11.sp, color = Ink)
                    Text(String.format(Locale.US, "%.1f", score), fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, color = if (score >= 65) Alert else Purple)
                }
                LinearProgressIndicator(
                    progress = { (score / 100.0).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (score >= 65) Magenta else Purple,
                    trackColor = PalePurple
                )
            }
        }
        ReportNote("A zero means the detector did not report an anomaly; it is not a guarantee that the document is authentic.")
    }
}
