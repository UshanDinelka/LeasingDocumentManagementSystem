package com.leasingdocument.app.screens

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

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

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.leasingdocument.app.network.Document
import com.leasingdocument.app.network.RetrofitClient

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

import java.io.File
import java.io.FileOutputStream


@Composable
fun MyDocumentsScreen(
    onBack: () -> Unit
) {

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()


    var documents by remember {
        mutableStateOf<List<Document>>(
            emptyList()
        )
    }


    var isLoading by remember {
        mutableStateOf(true)
    }


    var errorMessage by remember {
        mutableStateOf("")
    }


    var selectedFileName by remember {
        mutableStateOf<String?>(
            null
        )
    }


    var downloadMessage by remember {
        mutableStateOf("")
    }


    var reload by remember {
        mutableStateOf(0)
    }


    // =========================================================
    // SYSTEM BACK
    // =========================================================

    BackHandler {

        if (
            selectedFileName != null
        ) {

            selectedFileName =
                null

        } else {

            onBack()
        }
    }


    // =========================================================
    // DOCUMENT VIEW
    // =========================================================

    if (
        selectedFileName != null
    ) {

        DocumentViewerScreen(
            fileName =
                selectedFileName!!,

            onBack = {
                selectedFileName =
                    null
            }
        )

        return
    }


    // =========================================================
    // LOAD DOCUMENTS
    // =========================================================

    LaunchedEffect(
        reload
    ) {

        isLoading =
            true

        errorMessage =
            ""

        try {

            val response =
                RetrofitClient
                    .apiService
                    .getMyDocuments()


            if (
                response.isSuccessful
            ) {

                documents =
                    response.body()
                        ?: emptyList()

            } else {

                errorMessage =
                    when (
                        response.code()
                    ) {

                        401 ->
                            "Your session has expired. Please sign in again."

                        403 ->
                            "You do not have permission to view these documents."

                        else ->
                            "Failed to load documents (HTTP ${response.code()})."
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

            isLoading =
                false
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
                        w * 0.72f,
                        0f
                    )

                    cubicTo(
                        w * 0.56f,
                        h * 0.025f,

                        w * 0.34f,
                        h * 0.05f,

                        0f,
                        h * 0.13f
                    )

                    close()
                }


            drawPath(
                path =
                    topShape,

                brush =
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFD4B8)
                                .copy(
                                    alpha = 0.48f
                                ),

                            Color(0xFFFFE8E0)
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
                        h * 0.075f
                    )

                    cubicTo(
                        w * 0.20f,
                        h * 0.05f,

                        w * 0.36f,
                        h * 0.075f,

                        w * 0.58f,
                        h * 0.02f
                    )
                }


            drawPath(
                path =
                    accentLine,

                color =
                    Color(0xFFFF8B42)
                        .copy(
                            alpha = 0.65f
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
                            Color(0xFF9E46DB)
                                .copy(
                                    alpha = 0.08f
                                ),

                            Color.Transparent
                        )
                    ),

                radius =
                    w * 0.80f,

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
            // TOP BAR
            // =================================================

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {


                Box(
                    modifier = Modifier
                        .size(
                            38.dp
                        )
                        .clip(
                            CircleShape
                        )
                        .background(
                            Color(0xFFF3EDFA)
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
                            29.sp,

                        color =
                            Color(0xFF7650BD),

                        modifier =
                            Modifier.padding(
                                bottom =
                                    3.dp
                            )
                    )
                }


                Spacer(
                    modifier =
                        Modifier.width(
                            14.dp
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
                            "My Documents",

                        fontSize =
                            24.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF171A2C)
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                2.dp
                            )
                    )


                    Text(
                        text =
                            "DOC SECURE",

                        fontSize =
                            8.sp,

                        letterSpacing =
                            1.4.sp,

                        color =
                            Color(0xFF8A8396)
                    )
                }


                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                14.dp
                            )
                        )
                        .background(
                            Color.White.copy(
                                alpha =
                                    0.90f
                            )
                        )
                        .clickable {

                            reload++
                        }
                        .padding(
                            horizontal =
                                14.dp,

                            vertical =
                                10.dp
                        )
                ) {

                    Text(
                        text =
                            "REFRESH",

                        fontSize =
                            10.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF7650BD),

                        letterSpacing =
                            0.4.sp
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )


            // =================================================
            // HERO CARD
            // =================================================

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
                            colors = listOf(
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

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(
                                54.dp
                            )
                            .clip(
                                RoundedCornerShape(
                                    17.dp
                                )
                            )
                            .background(
                                Color.White.copy(
                                    alpha =
                                        0.17f
                                )
                            ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text =
                                "DOC",

                            color =
                                Color.White,

                            fontSize =
                                13.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.width(
                                15.dp
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
                                "${documents.size} Documents",

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
                                    4.dp
                                )
                        )


                        Text(
                            text =
                                "Your captured and submitted leasing documents",

                            color =
                                Color.White.copy(
                                    alpha =
                                        0.78f
                                ),

                            fontSize =
                                12.sp,

                            lineHeight =
                                17.sp
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


            // =================================================
            // DOWNLOAD MESSAGE
            // =================================================

            if (
                downloadMessage.isNotBlank()
            ) {

                DownloadStatusCard(
                    message =
                        downloadMessage
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            14.dp
                        )
                )
            }


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
                                    "Loading documents...",

                                fontSize =
                                    12.sp,

                                color =
                                    Color(0xFF81798C)
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

                        ErrorStateCard(
                            message =
                                errorMessage,

                            onRetry = {

                                reload++
                            }
                        )
                    }
                }


                documents.isEmpty() -> {

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

                        EmptyDocumentsCard()
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

                        items(
                            items =
                                documents,

                            key = { document ->

                                document.documentId
                                    ?: document.fileName
                                    ?: document.hashCode()
                            }
                        ) { document ->


                            DocumentCard(
                                document =
                                    document,

                                onView = {

                                    val fileName =
                                        document.fileName

                                    if (
                                        !fileName.isNullOrBlank()
                                    ) {

                                        selectedFileName =
                                            fileName
                                    }
                                },

                                onDownload = {

                                    val fileName =
                                        document.fileName


                                    if (
                                        !fileName.isNullOrBlank()
                                    ) {

                                        coroutineScope.launch {

                                            downloadMessage =
                                                "Downloading document..."

                                            try {

                                                val response =
                                                    RetrofitClient
                                                        .apiService
                                                        .getDocumentImage(
                                                            fileName
                                                        )


                                                if (
                                                    response.isSuccessful
                                                ) {

                                                    val bytes =
                                                        response
                                                            .body()
                                                            ?.bytes()


                                                    if (
                                                        bytes != null
                                                    ) {


                                                        // =================================
                                                        // ANDROID 10+
                                                        // =================================

                                                        if (
                                                            Build.VERSION.SDK_INT >=
                                                            Build.VERSION_CODES.Q
                                                        ) {

                                                            val values =
                                                                ContentValues()
                                                                    .apply {

                                                                        put(
                                                                            MediaStore.Downloads.DISPLAY_NAME,
                                                                            fileName
                                                                        )

                                                                        put(
                                                                            MediaStore.Downloads.MIME_TYPE,
                                                                            "application/octet-stream"
                                                                        )

                                                                        put(
                                                                            MediaStore.Downloads.RELATIVE_PATH,
                                                                            Environment.DIRECTORY_DOWNLOADS
                                                                        )
                                                                    }


                                                            val uri =
                                                                context
                                                                    .contentResolver
                                                                    .insert(
                                                                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                                                        values
                                                                    )


                                                            if (
                                                                uri != null
                                                            ) {

                                                                context
                                                                    .contentResolver
                                                                    .openOutputStream(
                                                                        uri
                                                                    )
                                                                    ?.use {

                                                                        it.write(
                                                                            bytes
                                                                        )
                                                                    }


                                                                downloadMessage =
                                                                    "Downloaded successfully"

                                                            } else {

                                                                downloadMessage =
                                                                    "Download failed"
                                                            }


                                                        } else {


                                                            // =================================
                                                            // OLDER ANDROID
                                                            // =================================

                                                            val directory =
                                                                context
                                                                    .getExternalFilesDir(
                                                                        Environment.DIRECTORY_DOWNLOADS
                                                                    )


                                                            if (
                                                                directory != null
                                                            ) {

                                                                val file =
                                                                    File(
                                                                        directory,
                                                                        fileName
                                                                    )


                                                                FileOutputStream(
                                                                    file
                                                                ).use {

                                                                    it.write(
                                                                        bytes
                                                                    )
                                                                }


                                                                downloadMessage =
                                                                    "Downloaded successfully"

                                                            } else {

                                                                downloadMessage =
                                                                    "Download folder unavailable"
                                                            }
                                                        }


                                                    } else {

                                                        downloadMessage =
                                                            "Download failed"
                                                    }


                                                } else {

                                                    downloadMessage =
                                                        "Download failed: HTTP ${response.code()}"
                                                }


                                            } catch (
                                                e: CancellationException
                                            ) {

                                                throw e

                                            } catch (
                                                e: Exception
                                            ) {

                                                downloadMessage =
                                                    "Download failed"
                                            }
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
        }
    }
}


// =====================================================================
// DOCUMENT CARD
// =====================================================================

@Composable
private fun DocumentCard(
    document: Document,
    onView: () -> Unit,
    onDownload: () -> Unit
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
                        0.96f
                )
            )
            .padding(
                17.dp
            )
    ) {

        Column {


            // =====================================================
            // TOP
            // =====================================================

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {


                Box(
                    modifier = Modifier
                        .size(
                            50.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                16.dp
                            )
                        )
                        .background(
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        Color(0xFFFF8247),
                                        Color(0xFFF05291),
                                        Color(0xFF9A4ED1)
                                    )
                            )
                        ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "DOC",

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
                        Modifier.width(
                            14.dp
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
                            document.fileName
                                ?: "Unknown Document",

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF171A2C),

                        maxLines =
                            2
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                4.dp
                            )
                    )


                    Text(
                        text =
                            "Document #${document.documentId ?: "-"}",

                        fontSize =
                            11.sp,

                        color =
                            Color(0xFF817A8B)
                    )
                }


                DocumentStatusBadge(
                    status =
                        document.status
                            ?: "-"
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        17.dp
                    )
            )


            // =====================================================
            // INFO
            // =====================================================

            DocumentInfoRow(
                label =
                    "Customer ID",

                value =
                    document.customerId
                        ?.toString()
                        ?: "-"
            )


            DocumentInfoRow(
                label =
                    "Capture",

                value =
                    document.captureStatus
                        ?: "-"
            )


            DocumentInfoRow(
                label =
                    "Verification",

                value =
                    document.verificationStatus
                        ?: "-"
            )


            Spacer(
                modifier =
                    Modifier.height(
                        15.dp
                    )
            )


            // =====================================================
            // ACTIONS
            // =====================================================

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {


                Box(
                    modifier = Modifier
                        .weight(
                            1f
                        )
                        .height(
                            48.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                15.dp
                            )
                        )
                        .background(
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color(0xFF6E50B8),
                                        Color(0xFF9650C3)
                                    )
                            )
                        )
                        .clickable(
                            enabled =
                                !document.fileName.isNullOrBlank()
                        ) {

                            onView()
                        },

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "VIEW",

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


                Box(
                    modifier = Modifier
                        .weight(
                            1f
                        )
                        .height(
                            48.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                15.dp
                            )
                        )
                        .background(
                            Color(0xFFF2ECFA)
                        )
                        .clickable(
                            enabled =
                                !document.fileName.isNullOrBlank()
                        ) {

                            onDownload()
                        },

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "DOWNLOAD",

                        color =
                            Color(0xFF7650BD),

                        fontSize =
                            11.sp,

                        fontWeight =
                            FontWeight.Bold,

                        letterSpacing =
                            0.3.sp
                    )
                }
            }
        }
    }
}


// =====================================================================
// DOCUMENT INFO ROW
// =====================================================================

@Composable
private fun DocumentInfoRow(
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
                    100.dp
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
                Color(0xFF2A2637)
        )
    }
}


// =====================================================================
// STATUS BADGE
// =====================================================================

@Composable
private fun DocumentStatusBadge(
    status: String
) {

    val statusUpper =
        status.uppercase()


    val background =
        when {

            statusUpper.contains(
                "ACTIVE"
            ) ||
                    statusUpper.contains(
                        "VERIFIED"
                    ) ||
                    statusUpper.contains(
                        "SUCCESS"
                    ) -> Color(
                0xFFE7F7EE
            )

            statusUpper.contains(
                "FAIL"
            ) ||
                    statusUpper.contains(
                        "REJECT"
                    ) -> Color(
                0xFFFFE9EC
            )

            else ->
                Color(
                    0xFFFFF2E7
                )
        }


    val foreground =
        when {

            statusUpper.contains(
                "ACTIVE"
            ) ||
                    statusUpper.contains(
                        "VERIFIED"
                    ) ||
                    statusUpper.contains(
                        "SUCCESS"
                    ) -> Color(
                0xFF247148
            )

            statusUpper.contains(
                "FAIL"
            ) ||
                    statusUpper.contains(
                        "REJECT"
                    ) -> Color(
                0xFFB33A4A
            )

            else ->
                Color(
                    0xFF9A6233
                )
        }


    Box(
        modifier = Modifier
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
                    9.dp,

                vertical =
                    5.dp
            )
    ) {

        Text(
            text =
                statusUpper,

            fontSize =
                8.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                foreground,

            maxLines =
                1
        )
    }
}


// =====================================================================
// DOWNLOAD STATUS
// =====================================================================

@Composable
private fun DownloadStatusCard(
    message: String
) {

    val success =
        message.contains(
            "success",
            ignoreCase =
                true
        )


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    16.dp
                )
            )
            .background(
                if (
                    success
                ) {

                    Color(0xFFEAF7EF)

                } else {

                    Color(0xFFF3EDFA)
                }
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

            fontWeight =
                FontWeight.SemiBold,

            color =
                if (
                    success
                ) {

                    Color(0xFF247148)

                } else {

                    Color(0xFF684C92)
                }
        )
    }
}


// =====================================================================
// ERROR CARD
// =====================================================================

@Composable
private fun ErrorStateCard(
    message: String,
    onRetry: () -> Unit
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
            .padding(
                22.dp
            )
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                text =
                    "Unable to load documents",

                fontSize =
                    16.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF282234)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            Text(
                text =
                    message,

                textAlign =
                    TextAlign.Center,

                fontSize =
                    12.sp,

                lineHeight =
                    17.sp,

                color =
                    Color(0xFF817A89)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        48.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            15.dp
                        )
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color(0xFF7050B8),
                                    Color(0xFF9A4FC4)
                                )
                        )
                    )
                    .clickable {

                        onRetry()
                    },

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        "TRY AGAIN",

                    color =
                        Color.White,

                    fontSize =
                        11.sp,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}


// =====================================================================
// EMPTY DOCUMENTS
// =====================================================================

@Composable
private fun EmptyDocumentsCard() {

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
                        0.94f
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
                modifier = Modifier
                    .size(
                        56.dp
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
                        "DOC",

                    fontSize =
                        11.sp,

                    fontWeight =
                        FontWeight.Bold,

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
                    "No documents yet",

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
                    "Captured and submitted documents will appear here.",

                fontSize =
                    12.sp,

                lineHeight =
                    17.sp,

                textAlign =
                    TextAlign.Center,

                color =
                    Color(0xFF817A89)
            )
        }
    }
}


// =====================================================================
// DOCUMENT VIEWER
// =====================================================================

@Composable
private fun DocumentViewerScreen(
    fileName: String,
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFFFCF9FF)
            )
            .statusBarsPadding()
    ) {


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
                        38.dp
                    )
                    .clip(
                        CircleShape
                    )
                    .background(
                        Color(0xFFF3EDFA)
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
                        29.sp,

                    color =
                        Color(0xFF7650BD),

                    modifier =
                        Modifier.padding(
                            bottom =
                                3.dp
                        )
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        14.dp
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
                        "Document Preview",

                    fontSize =
                        18.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF171A2C)
                )


                Text(
                    text =
                        fileName,

                    fontSize =
                        9.sp,

                    color =
                        Color(0xFF89818F),

                    maxLines =
                        1
                )
            }
        }


        Box(
            modifier =
                Modifier.fillMaxSize()
        ) {

            DocumentImageScreen(
                fileName =
                    fileName
            )
        }
    }
}