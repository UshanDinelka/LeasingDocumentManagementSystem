package com.leasingdocument.app.screens

import android.content.Intent

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.leasingdocument.app.capture.CaptureDraftsActivity


@Composable
fun AgentDashboard(
    onLogout: () -> Unit = {}
) {

    val context =
        LocalContext.current

    var currentScreen by rememberSaveable {
        mutableStateOf("DASHBOARD")
    }


    // =========================================================
    // SYSTEM BACK
    // =========================================================

    BackHandler(
        enabled =
            currentScreen != "DASHBOARD" &&
                    currentScreen != "MY_DOCUMENTS"
    ) {

        currentScreen =
            "DASHBOARD"
    }


    // =========================================================
    // NAVIGATION
    // =========================================================

    when (currentScreen) {

        // =====================================================
        // CAPTURE
        // =====================================================

        "CAPTURE" -> {

            AgentSubScreenContainer(
                title = "Capture Document",
                onBack = {
                    currentScreen =
                        "DASHBOARD"
                }
            ) {

                CapturePreparationScreen()
            }
        }


        // =====================================================
        // MY DOCUMENTS
        // =====================================================

        "MY_DOCUMENTS" -> {

            MyDocumentsScreen(
                onBack = {
                    currentScreen =
                        "DASHBOARD"
                }
            )
        }


        // =====================================================
        // VERIFICATION RESULTS
        // =====================================================

        "VERIFICATION_RESULTS" -> {

            AgentSubScreenContainer(
                title = "Verification Results",
                onBack = {
                    currentScreen =
                        "DASHBOARD"
                }
            ) {

                VerificationResultsScreen()
            }
        }


        // =====================================================
        // DASHBOARD
        // =====================================================

        else -> {

            PremiumAgentDashboard(
                onCaptureClick = {
                    currentScreen =
                        "CAPTURE"
                },

                onDocumentsClick = {
                    currentScreen =
                        "MY_DOCUMENTS"
                },

                onVerificationClick = {
                    currentScreen =
                        "VERIFICATION_RESULTS"
                },

                onDraftsClick = {

                    context.startActivity(
                        Intent(
                            context,
                            CaptureDraftsActivity::class.java
                        )
                    )
                },

                onLogout = {
                    onLogout()
                }
            )
        }
    }
}


// =====================================================================
// PREMIUM AGENT DASHBOARD
// =====================================================================

@Composable
private fun PremiumAgentDashboard(
    onCaptureClick: () -> Unit,
    onDocumentsClick: () -> Unit,
    onVerificationClick: () -> Unit,
    onDraftsClick: () -> Unit,
    onLogout: () -> Unit
) {

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


        // =========================================================
        // BACKGROUND
        // =========================================================

        Canvas(
            modifier =
                Modifier.fillMaxSize()
        ) {

            val w =
                size.width

            val h =
                size.height


            // =====================================================
            // TOP PEACH
            // =====================================================

            val topPeach =
                Path().apply {

                    moveTo(
                        0f,
                        0f
                    )

                    lineTo(
                        w * 0.70f,
                        0f
                    )

                    cubicTo(
                        w * 0.58f,
                        h * 0.03f,

                        w * 0.40f,
                        h * 0.055f,

                        w * 0.23f,
                        h * 0.105f
                    )

                    cubicTo(
                        w * 0.12f,
                        h * 0.14f,

                        w * 0.05f,
                        h * 0.17f,

                        0f,
                        h * 0.20f
                    )

                    close()
                }


            drawPath(
                path =
                    topPeach,

                brush =
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFD1AF)
                                .copy(alpha = 0.62f),

                            Color(0xFFFFE8DE)
                                .copy(alpha = 0.30f),

                            Color.Transparent
                        )
                    )
            )


            // =====================================================
            // PINK RIBBON
            // =====================================================

            val pinkRibbon =
                Path().apply {

                    moveTo(
                        0f,
                        h * 0.055f
                    )

                    cubicTo(
                        w * 0.12f,
                        h * 0.05f,

                        w * 0.26f,
                        h * 0.06f,

                        w * 0.38f,
                        h * 0.035f
                    )

                    cubicTo(
                        w * 0.26f,
                        h * 0.105f,

                        w * 0.12f,
                        h * 0.175f,

                        0f,
                        h * 0.235f
                    )

                    close()
                }


            drawPath(
                path =
                    pinkRibbon,

                brush =
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFF47AAC)
                                .copy(alpha = 0.14f),

                            Color(0xFFF75FA7)
                                .copy(alpha = 0.35f),

                            Color.Transparent
                        )
                    )
            )


            // =====================================================
            // ORANGE LINE
            // =====================================================

            val orangeLine =
                Path().apply {

                    moveTo(
                        0f,
                        h * 0.115f
                    )

                    cubicTo(
                        w * 0.20f,
                        h * 0.080f,

                        w * 0.38f,
                        h * 0.095f,

                        w * 0.56f,
                        h * 0.030f
                    )
                }


            drawPath(
                path =
                    orangeLine,

                color =
                    Color(0xFFFF923F)
                        .copy(alpha = 0.78f),

                style =
                    Stroke(
                        width =
                            1.2.dp.toPx()
                    )
            )


            // =====================================================
            // BOTTOM GLOW
            // =====================================================

            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFAA44E6)
                                .copy(alpha = 0.10f),

                            Color.Transparent
                        )
                    ),

                radius =
                    w * 0.75f,

                center =
                    Offset(
                        x =
                            w * 0.50f,

                        y =
                            h
                    )
            )
        }


        // =========================================================
        // CONTENT
        // =========================================================

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal =
                        22.dp
                )
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        22.dp
                    )
            )


            // =====================================================
            // HEADER
            // =====================================================

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

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            text =
                                "DOC",

                            fontSize =
                                25.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color(0xFF131A30)
                        )


                        Spacer(
                            modifier =
                                Modifier.width(
                                    6.dp
                                )
                        )


                        Text(
                            text =
                                "SECURE",

                            style =
                                TextStyle(
                                    brush =
                                        Brush.horizontalGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFFFF7A3C),
                                                    Color(0xFFF04F89),
                                                    Color(0xFFB72AC5)
                                                )
                                        ),

                                    fontSize =
                                        25.sp,

                                    fontWeight =
                                        FontWeight.Bold
                                )
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.height(
                                3.dp
                            )
                    )


                    Text(
                        text =
                            "AGENT WORKSPACE",

                        fontSize =
                            8.sp,

                        letterSpacing =
                            1.4.sp,

                        color =
                            Color(0xFF77718B)
                    )
                }


                Box(
                    modifier = Modifier
                        .size(
                            48.dp
                        )
                        .clip(
                            CircleShape
                        )
                        .background(
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        Color(0xFFFF8A45),
                                        Color(0xFFF25796),
                                        Color(0xFF9A42D5)
                                    )
                            )
                        ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "AG",

                        color =
                            Color.White,

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        32.dp
                    )
            )


            // =====================================================
            // WELCOME CARD
            // =====================================================

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            26.dp
                        )
                    )
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color(0xFF5F51B4),
                                    Color(0xFF924FC2),
                                    Color(0xFFD14D92)
                                )
                        )
                    )
                    .padding(
                        horizontal =
                            22.dp,

                        vertical =
                            24.dp
                    )
            ) {

                Column {

                    Text(
                        text =
                            "Welcome Back",

                        color =
                            Color.White.copy(
                                alpha =
                                    0.84f
                            ),

                        fontSize =
                            13.sp
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                5.dp
                            )
                    )


                    Text(
                        text =
                            "Leasing Agent",

                        color =
                            Color.White,

                        fontSize =
                            27.sp,

                        fontWeight =
                            FontWeight.Bold
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )


                    Text(
                        text =
                            "Capture, submit and monitor secure leasing documents from your workspace.",

                        color =
                            Color.White.copy(
                                alpha =
                                    0.80f
                            ),

                        fontSize =
                            13.sp,

                        lineHeight =
                            19.sp
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )


            // =====================================================
            // QUICK ACTIONS TITLE
            // =====================================================

            Text(
                text =
                    "Quick Actions",

                fontSize =
                    20.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF181B2E)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )


            Text(
                text =
                    "Choose what you want to do",

                fontSize =
                    13.sp,

                color =
                    Color(0xFF817B8D)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            // =====================================================
            // CAPTURE DOCUMENT
            // =====================================================

            AgentFeatureCard(
                shortName =
                    "CP",

                title =
                    "Capture Document",

                description =
                    "Start a secure camera capture workflow for a customer document.",

                accentColors =
                    listOf(
                        Color(0xFFFF7D42),
                        Color(0xFFF04F89)
                    ),

                onClick =
                    onCaptureClick
            )


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            // =====================================================
            // MY DOCUMENTS
            // =====================================================

            AgentFeatureCard(
                shortName =
                    "MD",

                title =
                    "My Documents",

                description =
                    "View documents you have captured and submitted.",

                accentColors =
                    listOf(
                        Color(0xFF8248DC),
                        Color(0xFFB74DDA)
                    ),

                onClick =
                    onDocumentsClick
            )


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            // =====================================================
            // VERIFICATION RESULTS
            // =====================================================

            AgentFeatureCard(
                shortName =
                    "VR",

                title =
                    "Verification Results",

                description =
                    "Review document verification and integrity results.",

                accentColors =
                    listOf(
                        Color(0xFF5D57C8),
                        Color(0xFF8D53D2)
                    ),

                onClick =
                    onVerificationClick
            )


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            // =====================================================
            // LOCAL DRAFTS
            // =====================================================

            AgentFeatureCard(
                shortName =
                    "DR",

                title =
                    "Local Capture Drafts",

                description =
                    "Continue or review locally saved capture drafts.",

                accentColors =
                    listOf(
                        Color(0xFFF07B45),
                        Color(0xFFF3A64A)
                    ),

                onClick =
                    onDraftsClick
            )


            Spacer(
                modifier =
                    Modifier.height(
                        30.dp
                    )
            )


            // =====================================================
            // LOGOUT
            // =====================================================

            Button(
                onClick = {
                    onLogout()
                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            54.dp
                        ),

                shape =
                    RoundedCornerShape(
                        16.dp
                    ),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFF171B2D),

                        contentColor =
                            Color.White
                    )
            ) {

                Text(
                    text =
                        "LOG OUT",

                    fontSize =
                        14.sp,

                    fontWeight =
                        FontWeight.Bold,

                    letterSpacing =
                        1.sp
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )


            Text(
                text =
                    "AUTHORIZED AGENT ACCESS ONLY",

                modifier =
                    Modifier.fillMaxWidth(),

                textAlign =
                    TextAlign.Center,

                fontSize =
                    9.sp,

                letterSpacing =
                    1.2.sp,

                color =
                    Color(0xFF8B8496)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )
        }
    }
}


// =====================================================================
// AGENT FEATURE CARD
// =====================================================================

@Composable
private fun AgentFeatureCard(
    shortName: String,
    title: String,
    description: String,
    accentColors: List<Color>,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
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
            .clickable {
                onClick()
            }
            .padding(
                16.dp
            )
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(
                        55.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            17.dp
                        )
                    )
                    .background(
                        Brush.linearGradient(
                            colors =
                                accentColors
                        )
                    ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        shortName,

                    color =
                        Color.White,

                    fontSize =
                        15.sp,

                    fontWeight =
                        FontWeight.Bold
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        16.dp
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
                        title,

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
                            5.dp
                        )
                )


                Text(
                    text =
                        description,

                    fontSize =
                        12.sp,

                    lineHeight =
                        17.sp,

                    color =
                        Color(0xFF817B8B)
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        10.dp
                    )
            )


            Text(
                text =
                    "›",

                fontSize =
                    30.sp,

                fontWeight =
                    FontWeight.Light,

                color =
                    Color(0xFF8D8598)
            )
        }
    }
}


// =====================================================================
// AGENT SUB SCREEN CONTAINER
// =====================================================================

@Composable
private fun AgentSubScreenContainer(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFFFCF9FF)
            )
            .statusBarsPadding()
    ) {

        // =========================================================
        // HEADER
        // =========================================================

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color.White
                )
                .padding(
                    horizontal =
                        18.dp,

                    vertical =
                        14.dp
                ),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(
                        42.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            13.dp
                        )
                    )
                    .background(
                        Color(0xFFF1EBFA)
                    )
                    .clickable {
                        onBack()
                    },

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        "‹",

                    fontSize =
                        32.sp,

                    color =
                        Color(0xFF6049A3)
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        14.dp
                    )
            )


            Column {

                Text(
                    text =
                        title,

                    fontSize =
                        19.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF16192B)
                )


                Text(
                    text =
                        "DOC SECURE",

                    fontSize =
                        9.sp,

                    letterSpacing =
                        1.2.sp,

                    color =
                        Color(0xFF8A8396)
                )
            }
        }


        // =========================================================
        // EXISTING SCREEN
        // =========================================================

        Box(
            modifier =
                Modifier.fillMaxSize()
        ) {

            content()
        }
    }
}