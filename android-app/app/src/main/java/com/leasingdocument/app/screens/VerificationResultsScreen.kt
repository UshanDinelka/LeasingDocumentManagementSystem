package com.leasingdocument.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.leasingdocument.app.network.AlterationResult
import com.leasingdocument.app.network.IntegrityResult
import com.leasingdocument.app.network.RetrofitClient

import kotlinx.coroutines.CancellationException


@Composable
fun VerificationResultsScreen() {

    // =========================================================
    // STATE
    // =========================================================

    var alterationResults by remember {
        mutableStateOf<List<AlterationResult>>(
            emptyList()
        )
    }

    var integrityResults by remember {
        mutableStateOf<List<IntegrityResult>>(
            emptyList()
        )
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    var reload by remember {
        mutableStateOf(0)
    }


    // =========================================================
    // LOAD RESULTS
    // =========================================================

    LaunchedEffect(reload) {

        isLoading = true
        errorMessage = ""

        try {

            val alterationResponse =
                RetrofitClient
                    .apiService
                    .getAlterationResults()

            val integrityResponse =
                RetrofitClient
                    .apiService
                    .getIntegrityResults()


            if (
                alterationResponse.isSuccessful &&
                integrityResponse.isSuccessful
            ) {

                alterationResults =
                    alterationResponse.body()
                        ?: emptyList()

                integrityResults =
                    integrityResponse.body()
                        ?: emptyList()

            } else {

                errorMessage =
                    when {

                        alterationResponse.code() == 401 ||
                                integrityResponse.code() == 401 ->
                            "Your session has expired. Please sign in again."

                        alterationResponse.code() == 403 ||
                                integrityResponse.code() == 403 ->
                            "You do not have permission to view verification results."

                        else ->
                            "Failed to load verification results."
                    }
            }

        } catch (
            e: CancellationException
        ) {

            throw e

        } catch (
            e: Exception
        ) {

            errorMessage =
                "Cannot connect to server."

        } finally {

            isLoading = false
        }
    }


    // =========================================================
    // ROOT
    // =========================================================

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
                        w * 0.74f,
                        0f
                    )

                    cubicTo(
                        w * 0.56f,
                        h * 0.025f,

                        w * 0.35f,
                        h * 0.050f,

                        0f,
                        h * 0.135f
                    )

                    close()
                }


            drawPath(
                path =
                    topShape,

                brush =
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFD5B7)
                                .copy(
                                    alpha = 0.48f
                                ),

                            Color(0xFFFFE9E0)
                                .copy(
                                    alpha = 0.20f
                                ),

                            Color.Transparent
                        )
                    )
            )


            val accentLine =
                Path().apply {

                    moveTo(
                        0f,
                        h * 0.082f
                    )

                    cubicTo(
                        w * 0.21f,
                        h * 0.055f,

                        w * 0.38f,
                        h * 0.075f,

                        w * 0.60f,
                        h * 0.020f
                    )
                }


            drawPath(
                path =
                    accentLine,

                color =
                    Color(0xFFFF8B42)
                        .copy(
                            alpha = 0.70f
                        ),

                style =
                    Stroke(
                        width =
                            1.dp.toPx()
                    )
            )


            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9D45DB)
                                .copy(
                                    alpha = 0.08f
                                ),

                            Color.Transparent
                        )
                    ),

                radius =
                    w * 0.82f,

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal =
                        20.dp
                )
        ) {


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            // =================================================
            // HEADER
            // =================================================

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
                            "Verification Results",

                        fontSize =
                            26.sp,

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
                            "Document integrity and alteration analysis",

                        fontSize =
                            12.sp,

                        color =
                            Color(0xFF81798B)
                    )
                }


                Box(
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
                                        0.92f
                                )
                            )
                            .padding(
                                horizontal =
                                    14.dp,

                                vertical =
                                    10.dp
                            )
                ) {

                    Text(
                        text =
                            "DOC SECURE",

                        fontSize =
                            8.sp,

                        letterSpacing =
                            1.1.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF7650BD)
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        24.dp
                    )
            )


            // =================================================
            // SUMMARY CARD
            // =================================================

            VerificationSummaryCard(
                alterationCount =
                    alterationResults.size,

                integrityCount =
                    integrityResults.size
            )


            Spacer(
                modifier =
                    Modifier.height(
                        22.dp
                    )
            )


            // =================================================
            // BODY
            // =================================================

            when {


                isLoading -> {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(
                                    1f
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            CircularProgressIndicator(
                                color =
                                    Color(0xFF7650BD)
                            )


                            Spacer(
                                modifier =
                                    Modifier.height(
                                        14.dp
                                    )
                            )


                            Text(
                                text =
                                    "Loading verification results...",

                                fontSize =
                                    12.sp,

                                color =
                                    Color(0xFF817A8B)
                            )
                        }
                    }
                }


                errorMessage.isNotBlank() -> {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(
                                    1f
                                ),

                        contentAlignment =
                            Alignment.TopCenter
                    ) {

                        VerificationErrorCard(
                            message =
                                errorMessage
                        )
                    }
                }


                alterationResults.isEmpty() &&
                        integrityResults.isEmpty() -> {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(
                                    1f
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        EmptyVerificationCard()
                    }
                }


                else -> {

                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(
                                    1f
                                ),

                        verticalArrangement =
                            Arrangement.spacedBy(
                                14.dp
                            )
                    ) {


                        // =========================================
                        // ALTERATION SECTION
                        // =========================================

                        item {

                            VerificationSectionHeader(
                                badge =
                                    "AI",

                                title =
                                    "Alteration Detection",

                                subtitle =
                                    "${alterationResults.size} analysis result(s)",

                                colors =
                                    listOf(
                                        Color(0xFFFF8247),
                                        Color(0xFFF05291)
                                    )
                            )
                        }


                        if (
                            alterationResults.isEmpty()
                        ) {

                            item {

                                SmallEmptyCard(
                                    text =
                                        "No alteration analysis results available."
                                )
                            }

                        } else {

                            items(
                                items =
                                    alterationResults
                            ) { result ->

                                AlterationResultCard(
                                    result =
                                        result
                                )
                            }
                        }


                        // =========================================
                        // INTEGRITY SECTION
                        // =========================================

                        item {

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        8.dp
                                    )
                            )


                            VerificationSectionHeader(
                                badge =
                                    "SH",

                                title =
                                    "Integrity Verification",

                                subtitle =
                                    "${integrityResults.size} result(s)",

                                colors =
                                    listOf(
                                        Color(0xFF7151BB),
                                        Color(0xFFAA4DCE)
                                    )
                            )
                        }


                        if (
                            integrityResults.isEmpty()
                        ) {

                            item {

                                SmallEmptyCard(
                                    text =
                                        "No integrity verification results available."
                                )
                            }

                        } else {

                            items(
                                items =
                                    integrityResults
                            ) { result ->

                                IntegrityResultCard(
                                    result =
                                        result
                                )
                            }
                        }


                        item {

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        28.dp
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}


// =====================================================================
// SUMMARY CARD
// =====================================================================

@Composable
private fun VerificationSummaryCard(
    alterationCount: Int,
    integrityCount: Int
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
                            Color(0xFF654FB5),
                            Color(0xFF9450C1),
                            Color(0xFFD14E94)
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
                    "Security Overview",

                color =
                    Color.White,

                fontSize =
                    19.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )


            Text(
                text =
                    "Review document authenticity and integrity checks.",

                color =
                    Color.White.copy(
                        alpha =
                            0.78f
                    ),

                fontSize =
                    12.sp
            )


            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {

                VerificationCountBox(
                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    count =
                        alterationCount,

                    label =
                        "Alteration"
                )


                VerificationCountBox(
                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    count =
                        integrityCount,

                    label =
                        "Integrity"
                )
            }
        }
    }
}


// =====================================================================
// COUNT BOX
// =====================================================================

@Composable
private fun VerificationCountBox(
    modifier: Modifier,
    count: Int,
    label: String
) {

    Box(
        modifier =
            modifier
                .clip(
                    RoundedCornerShape(
                        17.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.15f
                    )
                )
                .padding(
                    14.dp
                )
    ) {

        Column {

            Text(
                text =
                    count.toString(),

                color =
                    Color.White,

                fontSize =
                    24.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier =
                    Modifier.height(
                        2.dp
                    )
            )


            Text(
                text =
                    label,

                color =
                    Color.White.copy(
                        alpha =
                            0.78f
                    ),

                fontSize =
                    10.sp
            )
        }
    }
}


// =====================================================================
// SECTION HEADER
// =====================================================================

@Composable
private fun VerificationSectionHeader(
    badge: String,
    title: String,
    subtitle: String,
    colors: List<Color>
) {

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
                        46.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            15.dp
                        )
                    )
                    .background(
                        Brush.linearGradient(
                            colors =
                                colors
                        )
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    badge,

                color =
                    Color.White,

                fontSize =
                    12.sp,

                fontWeight =
                    FontWeight.Bold
            )
        }


        Spacer(
            modifier =
                Modifier.width(
                    13.dp
                )
        )


        Column {

            Text(
                text =
                    title,

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF1A1B2D)
            )


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
                    11.sp,

                color =
                    Color(0xFF817A8B)
            )
        }
    }
}


// =====================================================================
// ALTERATION CARD
// =====================================================================

@Composable
private fun AlterationResultCard(
    result: AlterationResult
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        22.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.96f
                    )
                )
                .padding(
                    17.dp
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
                            "Document #${result.documentId ?: "-"}",

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF181A2D)
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                3.dp
                            )
                    )


                    Text(
                        text =
                            "Alteration Analysis",

                        fontSize =
                            11.sp,

                        color =
                            Color(0xFF817A8B)
                    )
                }


                RiskBadge(
                    riskLevel =
                        result.riskLevel
                            ?: "-"
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        15.dp
                    )
            )


            VerificationInfoRow(
                label =
                    "Risk Score",

                value =
                    result.riskScore
                        ?.toString()
                        ?: "-"
            )


            VerificationInfoRow(
                label =
                    "Status",

                value =
                    result.analysisStatus
                        ?: "-"
            )


            VerificationInfoRow(
                label =
                    "Suspicious",

                value =
                    result.suspiciousRegionCount
                        ?.toString()
                        ?: "-"
            )


            VerificationInfoRow(
                label =
                    "Algorithm",

                value =
                    result.algorithmVersion
                        ?: "-"
            )


            VerificationInfoRow(
                label =
                    "Processed",

                value =
                    result.processedAt
                        ?: "-"
            )
        }
    }
}


// =====================================================================
// INTEGRITY CARD - COMPONENT 3
// =====================================================================

@Composable
private fun IntegrityResultCard(
    result: IntegrityResult
) {

    val verification =
        result.verificationResult
            ?: "UNKNOWN"

    val upper =
        verification.uppercase()

    val passed =
        upper.contains(
            "PASS"
        ) ||
                upper.contains(
                    "VALID"
                ) ||
                upper.contains(
                    "VERIFIED"
                ) ||
                upper.contains(
                    "MATCH"
                )


    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        22.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.96f
                    )
                )
                .padding(
                    17.dp
                )
    ) {

        Column {

            // =================================================
            // DOCUMENT + FINAL INTEGRITY STATUS
            // =================================================

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
                            "Document #${result.documentId ?: "-"}",

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF181A2D)
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                3.dp
                            )
                    )


                    Text(
                        text =
                            "SHA-256 Integrity Check",

                        fontSize =
                            11.sp,

                        color =
                            Color(0xFF817A8B)
                    )
                }


                IntegrityBadge(
                    result =
                        verification
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            // =================================================
            // SUCCESS / FAILURE OUTCOME
            // =================================================

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
                            if (
                                passed
                            ) {

                                Color(0xFFEAF8F0)

                            } else {

                                Color(0xFFFFECEF)
                            }
                        )
                        .padding(
                            14.dp
                        )
            ) {

                Text(
                    text =
                        if (
                            passed
                        ) {

                            "INTEGRITY VERIFIED"

                        } else {

                            "INTEGRITY FAILED"
                        },

                    fontSize =
                        12.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        if (
                            passed
                        ) {

                            Color(0xFF247148)

                        } else {

                            Color(0xFFB33449)
                        }
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            // =================================================
            // COMPONENT 3 SECURITY CONTROLS
            // =================================================

            Text(
                text =
                    "Security Details",

                fontSize =
                    12.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF41364F)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        7.dp
                    )
            )


            VerificationInfoRow(
                label =
                    "Document ID",

                value =
                    result.documentId
                        ?.toString()
                        ?: "-"
            )


            VerificationInfoRow(
                label =
                    "Integrity",

                value =
                    "SHA-256"
            )


            VerificationInfoRow(
                label =
                    "Encryption",

                value =
                    "AES-256-GCM"
            )


            VerificationInfoRow(
                label =
                    "Storage",

                value =
                    "Encrypted"
            )


            VerificationInfoRow(
                label =
                    "Final Check",

                value =
                    verification
            )


            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            // =================================================
            // HASH EVIDENCE
            // =================================================

            Text(
                text =
                    "SHA-256 Hash",

                fontSize =
                    10.sp,

                color =
                    Color(0xFF8A8290)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )


            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                13.dp
                            )
                        )
                        .background(
                            Color(0xFFF5F0FA)
                        )
                        .padding(
                            11.dp
                        )
            ) {

                Text(
                    text =
                        result.sha256Hash
                            ?: "-",

                    fontSize =
                        10.sp,

                    lineHeight =
                        14.sp,

                    color =
                        Color(0xFF604C78),

                    maxLines =
                        3,

                    overflow =
                        TextOverflow.Ellipsis
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            VerificationInfoRow(
                label =
                    "Processed",

                value =
                    result.processedAt
                        ?: "-"
            )


        }
    }
}


// =====================================================================
// INFO ROW
// =====================================================================

@Composable
private fun VerificationInfoRow(
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
                )
    ) {

        Text(
            text =
                label,

            modifier =
                Modifier.width(
                    94.dp
                ),

            fontSize =
                11.sp,

            color =
                Color(0xFF89818F)
        )


        Text(
            text =
                value,

            modifier =
                Modifier.weight(
                    1f
                ),

            fontSize =
                12.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color(0xFF292536)
        )
    }
}


// =====================================================================
// RISK BADGE
// =====================================================================

@Composable
private fun RiskBadge(
    riskLevel: String
) {

    val upper =
        riskLevel.uppercase()


    val background =
        when {

            upper.contains(
                "LOW"
            ) ->
                Color(0xFFE8F7EE)

            upper.contains(
                "HIGH"
            ) ||
                    upper.contains(
                        "CRITICAL"
                    ) ->
                Color(0xFFFFE8EC)

            else ->
                Color(0xFFFFF3E5)
        }


    val foreground =
        when {

            upper.contains(
                "LOW"
            ) ->
                Color(0xFF247148)

            upper.contains(
                "HIGH"
            ) ||
                    upper.contains(
                        "CRITICAL"
                    ) ->
                Color(0xFFB33449)

            else ->
                Color(0xFF996130)
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
                    background
                )
                .padding(
                    horizontal =
                        10.dp,

                    vertical =
                        6.dp
                )
    ) {

        Text(
            text =
                upper,

            fontSize =
                8.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                foreground
        )
    }
}


// =====================================================================
// INTEGRITY BADGE
// =====================================================================

@Composable
private fun IntegrityBadge(
    result: String
) {

    val upper =
        result.uppercase()


    val passed =
        upper.contains(
            "PASS"
        ) ||
                upper.contains(
                    "VALID"
                ) ||
                upper.contains(
                    "VERIFIED"
                ) ||
                upper.contains(
                    "MATCH"
                )


    Box(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(
                        50.dp
                    )
                )
                .background(
                    if (
                        passed
                    ) {

                        Color(0xFFE7F7EE)

                    } else {

                        Color(0xFFFFEBEE)
                    }
                )
                .padding(
                    horizontal =
                        10.dp,

                    vertical =
                        6.dp
                )
    ) {

        Text(
            text =
                upper,

            fontSize =
                8.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                if (
                    passed
                ) {

                    Color(0xFF247148)

                } else {

                    Color(0xFFB33449)
                }
        )
    }
}


// =====================================================================
// SMALL EMPTY CARD
// =====================================================================

@Composable
private fun SmallEmptyCard(
    text: String
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
                            0.94f
                    )
                )
                .padding(
                    18.dp
                )
    ) {

        Text(
            text =
                text,

            fontSize =
                12.sp,

            color =
                Color(0xFF817A89)
        )
    }
}


// =====================================================================
// EMPTY STATE
// =====================================================================

@Composable
private fun EmptyVerificationCard() {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        22.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.95f
                    )
                )
                .padding(
                    26.dp
                )
    ) {

        Column(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Box(
                modifier =
                    Modifier
                        .size(
                            58.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                18.dp
                            )
                        )
                        .background(
                            Color(0xFFF2ECFA)
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        "VR",

                    fontWeight =
                        FontWeight.Bold,

                    fontSize =
                        12.sp,

                    color =
                        Color(0xFF7650BD)
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            Text(
                text =
                    "No verification results",

                fontSize =
                    17.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF211D30)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        6.dp
                    )
            )


            Text(
                text =
                    "Document verification results will appear here after processing.",

                fontSize =
                    12.sp,

                lineHeight =
                    17.sp,

                color =
                    Color(0xFF817A89)
            )
        }
    }
}


// =====================================================================
// ERROR STATE
// =====================================================================

@Composable
private fun VerificationErrorCard(
    message: String
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        22.dp
                    )
                )
                .background(
                    Color(0xFFFFF0F2)
                )
                .padding(
                    20.dp
                )
    ) {

        Column {

            Text(
                text =
                    "Unable to load results",

                fontSize =
                    16.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF9D3343)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        7.dp
                    )
            )


            Text(
                text =
                    message,

                fontSize =
                    12.sp,

                lineHeight =
                    17.sp,

                color =
                    Color(0xFF7D5960)
            )
        }
    }
}