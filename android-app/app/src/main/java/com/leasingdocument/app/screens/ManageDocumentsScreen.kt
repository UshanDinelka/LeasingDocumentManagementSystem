package com.leasingdocument.app.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.Document
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.launch


@Composable
fun ManageDocumentsScreen() {

    val coroutineScope =
        rememberCoroutineScope()

    var documents by remember {
        mutableStateOf<List<Document>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    var message by remember {
        mutableStateOf("")
    }

    var documentToDelete by remember {
        mutableStateOf<Document?>(null)
    }

    var deleteInProgress by remember {
        mutableStateOf(false)
    }


    // =========================================================
    // LOAD DOCUMENTS
    // =========================================================

    LaunchedEffect(Unit) {

        isLoading = true
        errorMessage = ""

        try {

            val response =
                RetrofitClient
                    .apiService
                    .getDocuments()

            if (response.isSuccessful) {

                documents =
                    response.body()
                        ?: emptyList()

            } else {

                errorMessage =
                    "Failed to load documents (${response.code()})"
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

    val verifiedCount =
        documents.count {
            it.verificationStatus
                ?.equals(
                    "VERIFIED",
                    ignoreCase = true
                ) == true
        }

    val pendingCount =
        documents.count {
            it.verificationStatus
                ?.equals(
                    "PENDING",
                    ignoreCase = true
                ) == true
        }


    // =========================================================
    // DELETE CONFIRMATION
    // =========================================================

    val selectedDocument =
        documentToDelete

    if (selectedDocument != null) {

        AlertDialog(
            onDismissRequest = {
                if (!deleteInProgress) {
                    documentToDelete = null
                }
            },

            title = {

                Text(
                    text =
                        "Remove Document?",

                    fontWeight =
                        FontWeight.Bold
                )
            },

            text = {

                Text(
                    text =
                        "Remove ${selectedDocument.fileName ?: "this document"} from active document records? Existing backend evidence/history rules remain enforced."
                )
            },

            confirmButton = {

                TextButton(
                    enabled =
                        !deleteInProgress,

                    onClick = {

                        val documentId =
                            selectedDocument.documentId
                                ?: return@TextButton

                        deleteInProgress = true
                        message = ""

                        coroutineScope.launch {

                            try {

                                val response =
                                    RetrofitClient
                                        .apiService
                                        .deleteDocument(documentId)

                                if (response.isSuccessful) {

                                    documents =
                                        documents.filter {
                                            it.documentId != documentId
                                        }

                                    message =
                                        "Document removed successfully."

                                    documentToDelete = null

                                } else {

                                    message =
                                        when (response.code()) {

                                            401 ->
                                                "Your session is no longer valid."

                                            403 ->
                                                "Only an administrator can remove documents."

                                            404 ->
                                                "Document not found."

                                            else ->
                                                "Remove failed (${response.code()})."
                                        }
                                }

                            } catch (e: Exception) {

                                message =
                                    "Cannot connect to server"

                            } finally {

                                deleteInProgress = false
                            }
                        }
                    }
                ) {

                    if (deleteInProgress) {

                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(18.dp),

                            strokeWidth =
                                2.dp
                        )

                    } else {

                        Text(
                            text =
                                "REMOVE",

                            color =
                                Color(0xFFC34757),

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            },

            dismissButton = {

                TextButton(
                    enabled =
                        !deleteInProgress,

                    onClick = {
                        documentToDelete = null
                    }
                ) {

                    Text(
                        text =
                            "CANCEL",

                        color =
                            Color(0xFF777184)
                    )
                }
            }
        )
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
        // SCREEN CONTENT
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
                    "Manage Documents",

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
                    "Review submitted documents, verification state and administrative removal.",

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
            // OVERVIEW CARD
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
                                    Color(0xFF6950B7),
                                    Color(0xFF9950BE),
                                    Color(0xFFD54E91)
                                )
                        )
                    )
                    .padding(20.dp)
            ) {

                Column {

                    Text(
                        text =
                            "Document Overview",

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
                            "${documents.size} Document${if (documents.size == 1) "" else "s"}",

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

                        DocumentOverviewPill(
                            label =
                                "VERIFIED",

                            value =
                                verifiedCount.toString()
                        )


                        DocumentOverviewPill(
                            label =
                                "PENDING",

                            value =
                                pendingCount.toString()
                        )
                    }
                }
            }


            if (message.isNotBlank()) {

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )


                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(16.dp)
                            )
                            .background(
                                Color(0xFFF3EEF9)
                            )
                            .padding(13.dp)
                ) {

                    Text(
                        text =
                            message,

                        fontSize =
                            12.sp,

                        color =
                            Color(0xFF6F4BB8)
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )


            Text(
                text =
                    "Document Records",

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
            // LOADING / ERROR / EMPTY / LIST
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
                                    "Loading documents...",

                                fontSize =
                                    12.sp,

                                color =
                                    Color(0xFF817A8B)
                            )
                        }
                    }
                }


                errorMessage.isNotBlank() -> {

                    DocumentStatusCard(
                        title =
                            "Unable to load documents",

                        message =
                            errorMessage,

                        background =
                            Color(0xFFFFEEF1),

                        titleColor =
                            Color(0xFFA13A4B)
                    )
                }


                documents.isEmpty() -> {

                    DocumentStatusCard(
                        title =
                            "No documents found",

                        message =
                            "Submitted documents will appear here.",

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
                                documents,

                            key = {
                                it.documentId
                                    ?: it.hashCode()
                            }
                        ) { document ->

                            AdminDocumentCard(
                                document =
                                    document,

                                actionEnabled =
                                    !deleteInProgress,

                                onDelete = {
                                    documentToDelete =
                                        document
                                }
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
private fun DocumentOverviewPill(
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
// DOCUMENT CARD
// =====================================================================

@Composable
private fun AdminDocumentCard(
    document: Document,
    actionEnabled: Boolean,
    onDelete: () -> Unit
) {

    val fileName =
        document.fileName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Unknown Document"


    val captureStatus =
        document.captureStatus
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "UNKNOWN"


    val verificationStatus =
        document.verificationStatus
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "UNKNOWN"


    val documentStatus =
        document.status
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "UNKNOWN"


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
                                            Color(0xFFEE7656),
                                            Color(0xFFF0528D)
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
                        Modifier.width(13.dp)
                )


                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            fileName,

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
                            "Document ID: ${document.documentId ?: "-"}",

                        fontSize =
                            11.sp,

                        color =
                            Color(0xFF817A8B)
                    )
                }


                Spacer(
                    modifier =
                        Modifier.width(10.dp)
                )


                VerificationBadge(
                    status =
                        verificationStatus
                )
            }


            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )


            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                InfoBlock(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "CUSTOMER",

                    value =
                        document.customerId
                            ?.toString()
                            ?: "-"
                )


                InfoBlock(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "AGENT",

                    value =
                        document.agentId
                            ?.toString()
                            ?: "-"
                )
            }


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                InfoBlock(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "CAPTURE",

                    value =
                        captureStatus
                )


                InfoBlock(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "STATUS",

                    value =
                        documentStatus
                )
            }


            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )


            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(14.dp)
                        )
                        .background(
                            Color(0xFFFFEEF1)
                        )
                        .clickable(
                            enabled =
                                actionEnabled &&
                                        document.documentId != null
                        ) {
                            onDelete()
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
                        "REMOVE DOCUMENT",

                    fontSize =
                        11.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFFC34757),

                    letterSpacing =
                        0.5.sp
                )
            }
        }
    }
}


// =====================================================================
// INFO BLOCK
// =====================================================================

@Composable
private fun InfoBlock(
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
// VERIFICATION BADGE
// =====================================================================

@Composable
private fun VerificationBadge(
    status: String
) {

    val normalized =
        status.uppercase()


    val background =
        when (normalized) {

            "VERIFIED",
            "PASS",
            "PASSED" ->
                Color(0xFFEAF7EF)

            "FAILED",
            "FAIL",
            "REJECTED" ->
                Color(0xFFFFEDEF)

            else ->
                Color(0xFFF3EEF8)
        }


    val foreground =
        when (normalized) {

            "VERIFIED",
            "PASS",
            "PASSED" ->
                Color(0xFF247148)

            "FAILED",
            "FAIL",
            "REJECTED" ->
                Color(0xFFB54050)

            else ->
                Color(0xFF7650BD)
        }


    Box(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(50.dp)
                )
                .background(background)
                .padding(
                    horizontal =
                        10.dp,

                    vertical =
                        7.dp
                )
    ) {

        Text(
            text =
                normalized,

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
// STATUS CARD
// =====================================================================

@Composable
private fun DocumentStatusCard(
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
