package com.leasingdocument.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.leasingdocument.app.network.AuthSession
import com.leasingdocument.app.network.LoginRequest
import com.leasingdocument.app.network.LogoutRequest
import com.leasingdocument.app.network.RetrofitClient
import com.leasingdocument.app.screens.AdminDashboard
import com.leasingdocument.app.screens.AgentDashboard
import com.leasingdocument.app.ui.theme.LeasingDocumentAppTheme

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            LeasingDocumentAppTheme {

                var currentScreen by remember {
                    mutableStateOf("SPLASH")
                }

                var isLoggingOut by remember {
                    mutableStateOf(false)
                }

                val appScope = rememberCoroutineScope()


                // =========================================================
                // LOGOUT
                // =========================================================

                val performLogout: () -> Unit = {

                    if (!isLoggingOut) {

                        isLoggingOut = true

                        appScope.launch {

                            try {

                                RetrofitClient
                                    .apiService
                                    .logout(
                                        LogoutRequest(
                                            deviceId = 1
                                        )
                                    )

                            } catch (e: Exception) {
                                // local session still cleared
                            }

                            AuthSession.clear()

                            currentScreen = "LOGIN"

                            isLoggingOut = false
                        }
                    }
                }


                // =========================================================
                // SYSTEM BACK
                // =========================================================

                BackHandler(
                    enabled =
                        (
                                currentScreen == "AGENT" ||
                                        currentScreen == "ADMIN"
                                ) &&
                                !isLoggingOut
                ) {

                    performLogout()
                }


                // =========================================================
                // NAVIGATION
                // =========================================================

                when (currentScreen) {

                    "SPLASH" -> {

                        LaunchedEffect(Unit) {

                            delay(1500)

                            currentScreen = "LOGIN"
                        }

                        SplashScreen()
                    }


                    "AGENT" -> {

                        AgentDashboard(
                            onLogout = {
                                performLogout()
                            }
                        )
                    }


                    "ADMIN" -> {

                        AdminDashboard(
                            onLogout = {
                                performLogout()
                            }
                        )
                    }


                    else -> {

                        Scaffold(
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->

                            LoginScreen(
                                modifier =
                                    Modifier.padding(innerPadding),

                                onAgentLoginSuccess = {
                                    currentScreen = "AGENT"
                                },

                                onAdminLoginSuccess = {
                                    currentScreen = "ADMIN"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


// =====================================================================
// SPLASH SCREEN
// =====================================================================

@Composable
fun SplashScreen() {

    val transition =
        rememberInfiniteTransition(
            label = "splash_loader"
        )

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,

        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = 1000,
                        easing = LinearEasing
                    ),

                repeatMode =
                    RepeatMode.Restart
            ),

        label = "loader_rotation"
    )


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFFFFFEFD)
            )
    ) {

        // =========================================================
        // BACKGROUND
        // =========================================================

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val w = size.width
            val h = size.height


            // -----------------------------------------------------
            // TOP PEACH
            // -----------------------------------------------------

            val topPeach = Path().apply {

                moveTo(0f, 0f)

                lineTo(
                    w * 0.68f,
                    0f
                )

                cubicTo(
                    w * 0.56f,
                    h * 0.03f,

                    w * 0.37f,
                    h * 0.065f,

                    w * 0.24f,
                    h * 0.12f
                )

                cubicTo(
                    w * 0.14f,
                    h * 0.17f,

                    w * 0.07f,
                    h * 0.23f,

                    0f,
                    h * 0.31f
                )

                close()
            }


            drawPath(
                path = topPeach,

                brush =
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFD1B2)
                                .copy(alpha = 0.88f),

                            Color(0xFFFFE8DD)
                                .copy(alpha = 0.54f),

                            Color(0xFFFFFAF7)
                                .copy(alpha = 0.10f)
                        ),

                        start = Offset.Zero,

                        end = Offset(
                            w * 0.70f,
                            h * 0.25f
                        )
                    )
            )


            // -----------------------------------------------------
            // TOP PINK
            // -----------------------------------------------------

            val topPinkRibbon = Path().apply {

                moveTo(
                    0f,
                    h * 0.10f
                )

                cubicTo(
                    w * 0.11f,
                    h * 0.09f,

                    w * 0.26f,
                    h * 0.08f,

                    w * 0.36f,
                    h * 0.055f
                )

                cubicTo(
                    w * 0.25f,
                    h * 0.12f,

                    w * 0.13f,
                    h * 0.22f,

                    0f,
                    h * 0.34f
                )

                close()
            }


            drawPath(
                path = topPinkRibbon,

                brush =
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFBA6C9)
                                .copy(alpha = 0.15f),

                            Color(0xFFF775B4)
                                .copy(alpha = 0.55f),

                            Color(0xFFFFC1D2)
                                .copy(alpha = 0.22f)
                        ),

                        start = Offset(
                            0f,
                            h * 0.08f
                        ),

                        end = Offset(
                            w * 0.35f,
                            h * 0.28f
                        )
                    )
            )


            // -----------------------------------------------------
            // TOP RIGHT
            // -----------------------------------------------------

            val topRightWash = Path().apply {

                moveTo(
                    w * 0.55f,
                    0f
                )

                lineTo(w, 0f)

                lineTo(
                    w,
                    h * 0.08f
                )

                cubicTo(
                    w * 0.84f,
                    h * 0.055f,

                    w * 0.70f,
                    h * 0.025f,

                    w * 0.55f,
                    0f
                )

                close()
            }


            drawPath(
                path = topRightWash,

                brush =
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFDE9F1)
                                .copy(alpha = 0.80f),

                            Color(0xFFF7EAF9)
                                .copy(alpha = 0.40f)
                        )
                    )
            )


            // -----------------------------------------------------
            // TOP ORANGE CURVE
            // -----------------------------------------------------

            val topOrangeCurve = Path().apply {

                moveTo(
                    0f,
                    h * 0.145f
                )

                cubicTo(
                    w * 0.18f,
                    h * 0.09f,

                    w * 0.35f,
                    h * 0.10f,

                    w * 0.48f,
                    h * 0.06f
                )

                cubicTo(
                    w * 0.57f,
                    h * 0.03f,

                    w * 0.61f,
                    h * 0.01f,

                    w * 0.64f,
                    0f
                )
            }


            drawPath(
                path = topOrangeCurve,

                color =
                    Color(0xFFFF8E31)
                        .copy(alpha = 0.92f),

                style =
                    Stroke(
                        width = 1.25.dp.toPx()
                    )
            )


            // =====================================================
            // BOTTOM SOFT VEIL
            // =====================================================

            val backVeil = Path().apply {

                moveTo(
                    0f,
                    h * 0.73f
                )

                cubicTo(
                    w * 0.18f,
                    h * 0.76f,

                    w * 0.36f,
                    h * 0.84f,

                    w * 0.56f,
                    h * 0.86f
                )

                cubicTo(
                    w * 0.73f,
                    h * 0.88f,

                    w * 0.87f,
                    h * 0.80f,

                    w,
                    h * 0.76f
                )

                lineTo(w, h)
                lineTo(0f, h)

                close()
            }


            drawPath(
                path = backVeil,

                brush =
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFEFF3)
                                .copy(alpha = 0.48f),

                            Color(0xFFFFF9F7)
                                .copy(alpha = 0.22f),

                            Color(0xFFFFEBD9)
                                .copy(alpha = 0.40f)
                        ),

                        start =
                            Offset(
                                0f,
                                h * 0.71f
                            ),

                        end =
                            Offset(
                                w,
                                h
                            )
                    )
            )


            // -----------------------------------------------------
            // PEACH WAVE
            // -----------------------------------------------------

            val peachWave = Path().apply {

                moveTo(
                    0f,
                    h * 0.80f
                )

                cubicTo(
                    w * 0.17f,
                    h * 0.79f,

                    w * 0.34f,
                    h * 0.89f,

                    w * 0.53f,
                    h * 0.90f
                )

                cubicTo(
                    w * 0.70f,
                    h * 0.91f,

                    w * 0.84f,
                    h * 0.84f,

                    w,
                    h * 0.79f
                )

                lineTo(w, h)
                lineTo(0f, h)

                close()
            }


            drawPath(
                path = peachWave,

                brush =
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFFD8CF)
                                .copy(alpha = 0.52f),

                            Color(0xFFFFEBDD)
                                .copy(alpha = 0.58f),

                            Color(0xFFFFC174)
                                .copy(alpha = 0.60f)
                        )
                    )
            )


            // -----------------------------------------------------
            // ORANGE FLOW LINE
            // -----------------------------------------------------

            val orangeFlowLine = Path().apply {

                moveTo(
                    0f,
                    h * 0.79f
                )

                cubicTo(
                    w * 0.19f,
                    h * 0.77f,

                    w * 0.37f,
                    h * 0.91f,

                    w * 0.55f,
                    h * 0.91f
                )

                cubicTo(
                    w * 0.72f,
                    h * 0.91f,

                    w * 0.84f,
                    h * 0.80f,

                    w,
                    h * 0.77f
                )
            }


            drawPath(
                path = orangeFlowLine,

                color =
                    Color(0xFFFF9851)
                        .copy(alpha = 0.75f),

                style =
                    Stroke(
                        width = 1.15.dp.toPx()
                    )
            )


            // -----------------------------------------------------
            // PINK WAVE
            // -----------------------------------------------------

            val pinkWave = Path().apply {

                moveTo(
                    0f,
                    h * 0.84f
                )

                cubicTo(
                    w * 0.16f,
                    h * 0.83f,

                    w * 0.34f,
                    h * 0.92f,

                    w * 0.54f,
                    h * 0.94f
                )

                cubicTo(
                    w * 0.71f,
                    h * 0.96f,

                    w * 0.86f,
                    h * 0.89f,

                    w,
                    h * 0.85f
                )

                lineTo(w, h)
                lineTo(0f, h)

                close()
            }


            drawPath(
                path = pinkWave,

                brush =
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFF742A8)
                                .copy(alpha = 0.70f),

                            Color(0xFFF67EBE)
                                .copy(alpha = 0.58f),

                            Color(0xFFFF9A78)
                                .copy(alpha = 0.48f)
                        )
                    )
            )


            // -----------------------------------------------------
            // CORAL WAVE
            // -----------------------------------------------------

            val coralRibbon = Path().apply {

                moveTo(
                    0f,
                    h * 0.89f
                )

                cubicTo(
                    w * 0.20f,
                    h * 0.88f,

                    w * 0.39f,
                    h * 0.96f,

                    w * 0.58f,
                    h * 0.965f
                )

                cubicTo(
                    w * 0.74f,
                    h * 0.97f,

                    w * 0.88f,
                    h * 0.91f,

                    w,
                    h * 0.88f
                )

                lineTo(w, h)
                lineTo(0f, h)

                close()
            }


            drawPath(
                path = coralRibbon,

                brush =
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFF6FA0)
                                .copy(alpha = 0.36f),

                            Color(0xFFFF9EA0)
                                .copy(alpha = 0.42f),

                            Color(0xFFFFB669)
                                .copy(alpha = 0.36f)
                        )
                    )
            )


            // -----------------------------------------------------
            // PURPLE WAVE
            // -----------------------------------------------------

            val purpleWave = Path().apply {

                moveTo(
                    0f,
                    h * 0.92f
                )

                cubicTo(
                    w * 0.16f,
                    h * 0.91f,

                    w * 0.35f,
                    h * 0.97f,

                    w * 0.54f,
                    h * 0.985f
                )

                cubicTo(
                    w * 0.72f,
                    h * 1.00f,

                    w * 0.87f,
                    h * 0.95f,

                    w,
                    h * 0.91f
                )

                lineTo(w, h)
                lineTo(0f, h)

                close()
            }


            drawPath(
                path = purpleWave,

                brush =
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFB22EEA)
                                .copy(alpha = 0.88f),

                            Color(0xFFD34CCE)
                                .copy(alpha = 0.72f),

                            Color(0xFFC47CE6)
                                .copy(alpha = 0.68f)
                        )
                    )
            )


            // -----------------------------------------------------
            // PURPLE DETAIL LINE
            // -----------------------------------------------------

            val purpleLine = Path().apply {

                moveTo(
                    0f,
                    h * 0.925f
                )

                cubicTo(
                    w * 0.20f,
                    h * 0.95f,

                    w * 0.42f,
                    h * 0.985f,

                    w * 0.64f,
                    h * 0.95f
                )

                cubicTo(
                    w * 0.80f,
                    h * 0.925f,

                    w * 0.92f,
                    h * 0.90f,

                    w,
                    h * 0.91f
                )
            }


            drawPath(
                path = purpleLine,

                color =
                    Color(0xFF9D2CDC)
                        .copy(alpha = 0.46f),

                style =
                    Stroke(
                        width = 1.15.dp.toPx()
                    )
            )
        }


        // =========================================================
        // CENTER CONTENT
        // =========================================================

        Column(
            modifier = Modifier
                .align(
                    Alignment.Center
                )
                .offset(
                    y = (-30).dp
                ),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {


            Image(
                painter =
                    painterResource(
                        id = R.drawable.splash_logo
                    ),

                contentDescription =
                    "Leasing Document Logo",

                modifier =
                    Modifier.size(
                        175.dp
                    )
            )


            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            Row(
                horizontalArrangement =
                    Arrangement.Center,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = "Leasing",

                    fontSize =
                        30.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF10182E)
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            7.dp
                        )
                )


                Text(
                    text = "Document",

                    style =
                        TextStyle(
                            brush =
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            Color(0xFFFF783B),
                                            Color(0xFFF04F89),
                                            Color(0xFFC22ABF)
                                        )
                                ),

                            fontSize =
                                30.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        7.dp
                    )
            )


            Text(
                text =
                    "SECURE DOCUMENT MANAGEMENT SYSTEM",

                fontSize =
                    9.sp,

                letterSpacing =
                    1.6.sp,

                color =
                    Color(0xFF6E6794),

                textAlign =
                    TextAlign.Center,

                maxLines =
                    1
            )


            Spacer(
                modifier =
                    Modifier.height(
                        15.dp
                    )
            )


            Box(
                modifier = Modifier
                    .width(
                        74.dp
                    )
                    .height(
                        3.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            50
                        )
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFFA23C),
                                Color(0xFFF04F89),
                                Color(0xFF9036E7)
                            )
                        )
                    )
            )


            Spacer(
                modifier =
                    Modifier.height(
                        42.dp
                    )
            )


            Canvas(
                modifier =
                    Modifier.size(
                        40.dp
                    )
            ) {

                rotate(
                    degrees = rotation
                ) {

                    drawArc(
                        color =
                            Color(0xFFFFA23C),

                        startAngle =
                            -90f,

                        sweepAngle =
                            105f,

                        useCenter =
                            false,

                        style =
                            Stroke(
                                width =
                                    4.dp.toPx(),

                                cap =
                                    StrokeCap.Round
                            )
                    )


                    drawArc(
                        color =
                            Color(0xFF9036E7),

                        startAngle =
                            32f,

                        sweepAngle =
                            105f,

                        useCenter =
                            false,

                        style =
                            Stroke(
                                width =
                                    4.dp.toPx(),

                                cap =
                                    StrokeCap.Round
                            )
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )


            Text(
                text =
                    "Loading...",

                fontSize =
                    12.sp,

                color =
                    Color(0xFF827A95)
            )
        }
    }
}


// =====================================================================
// LOGIN
// =====================================================================

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onAgentLoginSuccess: () -> Unit,
    onAdminLoginSuccess: () -> Unit
) {

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var message by remember {
        mutableStateOf("")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    val coroutineScope =
        rememberCoroutineScope()


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFEFD),
                        Color(0xFFFFFAFC),
                        Color(0xFFF8F1FF)
                    )
                )
            )
    ) {


        // =========================================================
        // LOGIN BACKGROUND
        // =========================================================

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val w = size.width
            val h = size.height


            val topPeach = Path().apply {

                moveTo(0f, 0f)

                lineTo(
                    w * 0.58f,
                    0f
                )

                cubicTo(
                    w * 0.46f,
                    h * 0.020f,

                    w * 0.28f,
                    h * 0.040f,

                    w * 0.14f,
                    h * 0.075f
                )

                cubicTo(
                    w * 0.07f,
                    h * 0.095f,

                    w * 0.03f,
                    h * 0.115f,

                    0f,
                    h * 0.135f
                )

                close()
            }


            drawPath(
                path = topPeach,

                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFD0AC)
                            .copy(alpha = 0.55f),

                        Color(0xFFFFE8DD)
                            .copy(alpha = 0.28f),

                        Color.Transparent
                    ),

                    start = Offset.Zero,

                    end = Offset(
                        w * 0.62f,
                        h * 0.14f
                    )
                )
            )


            val topPink = Path().apply {

                moveTo(
                    0f,
                    h * 0.055f
                )

                cubicTo(
                    w * 0.10f,
                    h * 0.055f,

                    w * 0.20f,
                    h * 0.060f,

                    w * 0.31f,
                    h * 0.040f
                )

                cubicTo(
                    w * 0.24f,
                    h * 0.075f,

                    w * 0.13f,
                    h * 0.115f,

                    0f,
                    h * 0.165f
                )

                close()
            }


            drawPath(
                path = topPink,

                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF58AC8)
                            .copy(alpha = 0.10f),

                        Color(0xFFF26FAC)
                            .copy(alpha = 0.34f),

                        Color.Transparent
                    ),

                    start =
                        Offset(
                            0f,
                            h * 0.04f
                        ),

                    end =
                        Offset(
                            w * 0.32f,
                            h * 0.15f
                        )
                )
            )


            val orangeCurve = Path().apply {

                moveTo(
                    0f,
                    h * 0.105f
                )

                cubicTo(
                    w * 0.18f,
                    h * 0.070f,

                    w * 0.33f,
                    h * 0.080f,

                    w * 0.47f,
                    h * 0.050f
                )

                cubicTo(
                    w * 0.55f,
                    h * 0.032f,

                    w * 0.60f,
                    h * 0.012f,

                    w * 0.64f,
                    0f
                )
            }


            drawPath(
                path = orangeCurve,

                color =
                    Color(0xFFFF963E)
                        .copy(alpha = 0.78f),

                style =
                    Stroke(
                        width = 1.dp.toPx()
                    )
            )
        }


        // =========================================================
        // LOGIN CONTENT
        // =========================================================

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 28.dp
                ),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        68.dp
                    )
            )


            Image(
                painter =
                    painterResource(
                        id = R.drawable.splash_logo
                    ),

                contentDescription =
                    "Leasing Document Logo",

                modifier =
                    Modifier.size(
                        120.dp
                    )
            )


            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            Row(
                verticalAlignment =
                    Alignment.CenterVertically,

                horizontalArrangement =
                    Arrangement.Center
            ) {

                Text(
                    text =
                        "Leasing",

                    fontSize =
                        28.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF10182E)
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            6.dp
                        )
                )


                Text(
                    text =
                        "Document",

                    style =
                        TextStyle(
                            brush =
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            Color(0xFFFF783B),
                                            Color(0xFFF04F89),
                                            Color(0xFFC22ABF)
                                        )
                                ),

                            fontSize =
                                28.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )


            Text(
                text =
                    "SECURE DOCUMENT MANAGEMENT SYSTEM",

                fontSize =
                    8.sp,

                letterSpacing =
                    1.5.sp,

                color =
                    Color(0xFF6E6794),

                textAlign =
                    TextAlign.Center
            )


            Spacer(
                modifier =
                    Modifier.height(
                        30.dp
                    )
            )


            // =====================================================
            // LOGIN CARD
            // =====================================================

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            28.dp
                        )
                    )
                    .background(
                        Color.White.copy(
                            alpha = 0.95f
                        )
                    )
                    .padding(
                        horizontal = 22.dp,
                        vertical = 26.dp
                    )
            ) {

                Column(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text =
                            "Welcome Back",

                        fontSize =
                            24.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF15192B)
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                6.dp
                            )
                    )


                    Text(
                        text =
                            "Sign in to continue",

                        fontSize =
                            14.sp,

                        color =
                            Color(0xFF817A8A)
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                26.dp
                            )
                    )


                    OutlinedTextField(
                        value = email,

                        onValueChange = {
                            email = it
                        },

                        label = {
                            Text(
                                "Email Address"
                            )
                        },

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Email
                            ),

                        singleLine =
                            true,

                        shape =
                            RoundedCornerShape(
                                16.dp
                            ),

                        modifier =
                            Modifier.fillMaxWidth()
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                16.dp
                            )
                    )


                    OutlinedTextField(
                        value = password,

                        onValueChange = {
                            password = it
                        },

                        label = {
                            Text(
                                "Password"
                            )
                        },

                        visualTransformation =
                            PasswordVisualTransformation(),

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Password
                            ),

                        singleLine =
                            true,

                        shape =
                            RoundedCornerShape(
                                16.dp
                            ),

                        modifier =
                            Modifier.fillMaxWidth()
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                24.dp
                            )
                    )


                    Button(
                        onClick = {

                            if (
                                email.isBlank() ||
                                password.isBlank()
                            ) {

                                message =
                                    "Please enter email and password"

                            } else {

                                coroutineScope.launch {

                                    isLoading =
                                        true

                                    message =
                                        ""

                                    try {

                                        val request =
                                            LoginRequest(
                                                email =
                                                    email,

                                                password =
                                                    password,

                                                deviceId =
                                                    1
                                            )


                                        val response =
                                            RetrofitClient
                                                .apiService
                                                .login(
                                                    request
                                                )


                                        if (
                                            response.isSuccessful
                                        ) {

                                            val loginResponse =
                                                response.body()


                                            if (
                                                loginResponse?.token != null
                                            ) {

                                                AuthSession.token =
                                                    loginResponse.token

                                                AuthSession.userId =
                                                    loginResponse.userId

                                                AuthSession.role =
                                                    loginResponse.role


                                                when (
                                                    loginResponse.role
                                                ) {

                                                    "AGENT" -> {

                                                        onAgentLoginSuccess()
                                                    }


                                                    "ADMIN" -> {

                                                        onAdminLoginSuccess()
                                                    }


                                                    else -> {

                                                        message =
                                                            "Unknown user role"
                                                    }
                                                }

                                            } else {

                                                message =
                                                    loginResponse
                                                        ?.message
                                                        ?: "Login failed"
                                            }

                                        } else {

                                            message =
                                                "Login failed"
                                        }

                                    } catch (
                                        e: Exception
                                    ) {

                                        message =
                                            "Cannot connect to server"
                                    }


                                    isLoading =
                                        false
                                }
                            }
                        },

                        enabled =
                            !isLoading,

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    54.dp
                                ),

                        shape =
                            RoundedCornerShape(
                                16.dp
                            )
                    ) {


                        if (
                            isLoading
                        ) {

                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(
                                        24.dp
                                    ),

                                strokeWidth =
                                    2.dp
                            )

                        } else {

                            Text(
                                text =
                                    "SIGN IN",

                                fontSize =
                                    15.sp,

                                fontWeight =
                                    FontWeight.Bold,

                                letterSpacing =
                                    1.sp
                            )
                        }
                    }


                    if (
                        message.isNotEmpty()
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    16.dp
                                )
                        )

                        Text(
                            text =
                                message,

                            fontSize =
                                13.sp,

                            color =
                                Color(0xFFB3261E),

                            textAlign =
                                TextAlign.Center
                        )
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        24.dp
                    )
            )


            Text(
                text =
                    "AUTHORIZED USERS ONLY",

                fontSize =
                    10.sp,

                letterSpacing =
                    1.4.sp,

                color =
                    Color(0xFF8B8493)
            )


            Spacer(
                modifier =
                    Modifier.weight(
                        1f
                    )
            )


            Box(
                modifier =
                    Modifier
                        .width(
                            58.dp
                        )
                        .height(
                            3.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                100
                            )
                        )
                        .background(
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color(0xFFFFA23C),
                                        Color(0xFFF04F89),
                                        Color(0xFF9036E7)
                                    )
                            )
                        )
            )


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