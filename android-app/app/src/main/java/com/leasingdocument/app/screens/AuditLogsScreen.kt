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
import com.leasingdocument.app.network.AuditLog
import com.leasingdocument.app.network.RetrofitClient


@Composable
fun AuditLogsScreen() {

    var logs by remember {
        mutableStateOf<List<AuditLog>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }


    // =========================================================
    // LOAD AUDIT LOGS
    // =========================================================

    LaunchedEffect(Unit) {

        isLoading = true
        errorMessage = ""

        try {

            val response =
                RetrofitClient
                    .apiService
                    .getAuditLogs()

            if (response.isSuccessful) {

                logs =
                    response.body()
                        ?: emptyList()

            } else {

                errorMessage =
                    "Failed to load audit logs (${response.code()})"
            }

        } catch (e: Exception) {

            errorMessage =
                "Cannot connect to server"

        } finally {

            isLoading = false
        }
    }


    // =========================================================
    // COUNTS
    // =========================================================

    val adminLinkedCount =
        logs.count {
            it.adminId != null
        }

    val agentLinkedCount =
        logs.count {
            it.agentId != null
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
                path =
                    topShape,

                brush =
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0xFFFFD1AF)
                                    .copy(alpha = 0.58f),

                                Color(0xFFFFE8DE)
                                    .copy(alpha = 0.24f),

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
                        .copy(alpha = 0.70f),

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
                                    .copy(alpha = 0.08f),

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
        // CONTENT
        // =====================================================

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal =
                        22.dp
                )
        ) {


            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )


            Text(
                text =
                    "Audit Logs",

                fontSize =
                    27.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )


            Text(
                text =
                    "Review important administrative and agent activity records.",

                fontSize =
                    12.sp,

                color =
                    Color(0xFF817A8B)
            )


            Spacer(
                modifier =
                    Modifier.height(22.dp)
            )


            // =================================================
            // OVERVIEW
            // =================================================

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(24.dp)
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color(0xFF5F55BF),
                                    Color(0xFF8D50C9),
                                    Color(0xFFC94F99)
                                )
                        )
                    )
                    .padding(20.dp)
            ) {

                Column {

                    Text(
                        text =
                            "Activity Overview",

                        color =
                            Color.White.copy(alpha = 0.84f),

                        fontSize =
                            12.sp
                    )


                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )


                    Text(
                        text =
                            "${logs.size} Audit Event${if (logs.size == 1) "" else "s"}",

                        color =
                            Color.White,

                        fontSize =
                            22.sp,

                        fontWeight =
                            FontWeight.Bold
                    )


                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )


                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {

                        AuditOverviewPill(
                            label =
                                "ADMIN",

                            value =
                                adminLinkedCount.toString()
                        )


                        AuditOverviewPill(
                            label =
                                "AGENT",

                            value =
                                agentLinkedCount.toString()
                        )
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )


            Text(
                text =
                    "Activity Records",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF1B1D30)
            )


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            // =================================================
            // STATES
            // =================================================

            when {

                isLoading -> {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(30.dp),

                                strokeWidth =
                                    3.dp,

                                color =
                                    Color(0xFF7650BD)
                            )


                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )


                            Text(
                                text =
                                    "Loading audit logs...",

                                fontSize =
                                    12.sp,

                                color =
                                    Color(0xFF817A8B)
                            )
                        }
                    }
                }


                errorMessage.isNotBlank() -> {

                    AuditStatusCard(
                        title =
                            "Unable to load audit logs",

                        message =
                            errorMessage,

                        background =
                            Color(0xFFFFEEF1),

                        titleColor =
                            Color(0xFFA13A4B)
                    )
                }


                logs.isEmpty() -> {

                    AuditStatusCard(
                        title =
                            "No audit activity found",

                        message =
                            "Recorded administrative and agent activities will appear here.",

                        background =
                            Color.White,

                        titleColor =
                            Color(0xFF171A2C)
                    )
                }


                else -> {

                    LazyColumn(
                        modifier =
                            Modifier.fillMaxSize()
                    ) {

                        items(
                            items =
                                logs
                        ) { log ->

                            AuditLogCard(
                                log =
                                    log
                            )


                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )
                        }


                        item {

                            Spacer(
                                modifier =
                                    Modifier.height(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


// =====================================================================
// OVERVIEW PILL
// =====================================================================

@Composable
private fun AuditOverviewPill(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(14.dp)
                )
                .background(
                    Color.White.copy(alpha = 0.15f)
                )
                .padding(
                    horizontal =
                        12.dp,

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
                14.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                Color.White
        )


        Spacer(
            modifier =
                Modifier.width(6.dp)
        )


        Text(
            text =
                label,

            fontSize =
                9.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color.White.copy(alpha = 0.82f)
        )
    }
}


// =====================================================================
// AUDIT LOG CARD
// =====================================================================

@Composable
private fun AuditLogCard(
    log: AuditLog
) {

    val action =
        log.action
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Unknown Action"


    val description =
        log.description
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "No description available"


    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(21.dp)
                )
                .background(
                    Color.White.copy(alpha = 0.96f)
                )
                .padding(16.dp)
    ) {

        Column(
            modifier =
                Modifier.fillMaxWidth()
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
                            .size(48.dp)
                            .clip(
                                RoundedCornerShape(15.dp)
                            )
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF6557C8),
                                            Color(0xFF9C4FD0)
                                        )
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "LOG",

                        color =
                            Color.White,

                        fontSize =
                            11.sp,

                        fontWeight =
                            FontWeight.Bold
                    )
                }


                Spacer(
                    modifier =
                        Modifier.width(13.dp)
                )


                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            action,

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF171A2C),

                        maxLines =
                            1,

                        overflow =
                            TextOverflow.Ellipsis
                    )


                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )


                    Text(
                        text =
                            log.createdAt
                                ?: "-",

                        fontSize =
                            10.sp,

                        color =
                            Color(0xFF8D8695),

                        maxLines =
                            1,

                        overflow =
                            TextOverflow.Ellipsis
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(13.dp)
            )


            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(14.dp)
                        )
                        .background(
                            Color(0xFFF8F5FB)
                        )
                        .padding(12.dp)
            ) {

                Text(
                    text =
                        description,

                    fontSize =
                        12.sp,

                    lineHeight =
                        17.sp,

                    color =
                        Color(0xFF5F5968)
                )
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                AuditInfoBlock(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "ADMIN",

                    value =
                        log.adminId
                            ?.toString()
                            ?: "-"
                )


                AuditInfoBlock(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "AGENT",

                    value =
                        log.agentId
                            ?.toString()
                            ?: "-"
                )
            }


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            AuditInfoBlock(
                modifier =
                    Modifier.fillMaxWidth(),

                label =
                    "IP ADDRESS",

                value =
                    log.ipAddress
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "-"
            )
        }
    }
}


// =====================================================================
// INFO BLOCK
// =====================================================================

@Composable
private fun AuditInfoBlock(
    modifier: Modifier,
    label: String,
    value: String
) {

    Box(
        modifier =
            modifier
                .clip(
                    RoundedCornerShape(14.dp)
                )
                .background(
                    Color(0xFFF8F5FB)
                )
                .padding(
                    horizontal =
                        12.dp,

                    vertical =
                        10.dp
                )
    ) {

        Column {

            Text(
                text =
                    label,

                fontSize =
                    8.sp,

                fontWeight =
                    FontWeight.Bold,

                letterSpacing =
                    0.7.sp,

                color =
                    Color(0xFF9A92A3)
            )


            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )


            Text(
                text =
                    value,

                fontSize =
                    11.sp,

                fontWeight =
                    FontWeight.SemiBold,

                color =
                    Color(0xFF353146),

                maxLines =
                    1,

                overflow =
                    TextOverflow.Ellipsis
            )
        }
    }
}


// =====================================================================
// STATUS CARD
// =====================================================================

@Composable
private fun AuditStatusCard(
    title: String,
    message: String,
    background: Color,
    titleColor: Color
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(20.dp)
                )
                .background(background)
                .padding(18.dp)
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
                    titleColor
            )


            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )


            Text(
                text =
                    message,

                fontSize =
                    12.sp,

                lineHeight =
                    17.sp,

                color =
                    Color(0xFF817A8B)
            )
        }
    }
}
