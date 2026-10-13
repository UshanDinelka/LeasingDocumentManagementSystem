package com.leasingdocument.app.screens

import android.provider.OpenableColumns

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.leasingdocument.app.network.RetrofitClient

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

import org.json.JSONObject

import java.util.Locale


// =====================================================================
// MAXIMUM UPLOAD SIZE = 20 MB
// =====================================================================

private const val MAX_FILE_SIZE_BYTES =
    20L * 1024L * 1024L


@Composable
fun UploadDocumentScreen() {

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()


    // =================================================================
    // STATE
    // =================================================================

    var selectedFileName by remember {
        mutableStateOf("")
    }

    var selectedFileBytes by remember {
        mutableStateOf<ByteArray?>(null)
    }

    var selectedFileSize by remember {
        mutableStateOf(0L)
    }

    var customerId by remember {
        mutableStateOf("")
    }

    var documentTypeId by remember {
        mutableStateOf("")
    }

    var message by remember {
        mutableStateOf("")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }


    // =================================================================
    // FILE PICKER
    // =================================================================

    val filePicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.GetContent()
        ) { uri ->

            if (
                uri != null
            ) {

                message =
                    ""

                selectedFileBytes =
                    null

                selectedFileName =
                    ""

                selectedFileSize =
                    0L


                try {

                    val cursor =
                        context
                            .contentResolver
                            .query(
                                uri,
                                null,
                                null,
                                null,
                                null
                            )


                    cursor?.use {

                        val nameIndex =
                            it.getColumnIndex(
                                OpenableColumns.DISPLAY_NAME
                            )

                        val sizeIndex =
                            it.getColumnIndex(
                                OpenableColumns.SIZE
                            )


                        if (
                            it.moveToFirst()
                        ) {

                            if (
                                nameIndex >= 0
                            ) {

                                selectedFileName =
                                    it.getString(
                                        nameIndex
                                    )
                                        ?: ""
                            }


                            if (
                                sizeIndex >= 0 &&
                                !it.isNull(
                                    sizeIndex
                                )
                            ) {

                                selectedFileSize =
                                    it.getLong(
                                        sizeIndex
                                    )
                            }
                        }
                    }


                    if (
                        selectedFileSize >
                        MAX_FILE_SIZE_BYTES
                    ) {

                        selectedFileBytes =
                            null

                        message =
                            "Selected file is too large. Maximum file size is 20 MB."

                    } else {

                        val bytes =
                            context
                                .contentResolver
                                .openInputStream(
                                    uri
                                )
                                ?.use {
                                        inputStream ->

                                    inputStream.readBytes()
                                }


                        if (
                            bytes == null
                        ) {

                            selectedFileBytes =
                                null

                            message =
                                "Unable to read selected file"

                        } else if (
                            bytes.size.toLong() >
                            MAX_FILE_SIZE_BYTES
                        ) {

                            selectedFileBytes =
                                null

                            selectedFileSize =
                                bytes.size.toLong()

                            message =
                                "Selected file is too large. Maximum file size is 20 MB."

                        } else {

                            selectedFileBytes =
                                bytes

                            selectedFileSize =
                                bytes.size.toLong()
                        }
                    }


                } catch (
                    e: Exception
                ) {

                    selectedFileBytes =
                        null

                    selectedFileName =
                        ""

                    selectedFileSize =
                        0L

                    message =
                        "Unable to read selected file"
                }
            }
        }


    // =================================================================
    // ROOT
    // =================================================================

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


        // =============================================================
        // BACKGROUND DECORATION
        // =============================================================

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

                        w * 0.36f,
                        h * 0.050f,

                        0f,
                        h * 0.14f
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
                                Color(0xFFFFD2B5)
                                    .copy(
                                        alpha =
                                            0.48f
                                    ),

                                Color(0xFFFFE8DF)
                                    .copy(
                                        alpha =
                                            0.20f
                                    ),

                                Color.Transparent
                            )
                    )
            )


            val accent =
                Path().apply {

                    moveTo(
                        0f,
                        h * 0.082f
                    )

                    cubicTo(
                        w * 0.20f,
                        h * 0.054f,

                        w * 0.38f,
                        h * 0.075f,

                        w * 0.60f,
                        h * 0.020f
                    )
                }


            drawPath(
                path =
                    accent,

                color =
                    Color(0xFFFF8B42)
                        .copy(
                            alpha =
                                0.68f
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
                        colors =
                            listOf(
                                Color(0xFF9F47DD)
                                    .copy(
                                        alpha =
                                            0.08f
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


        // =============================================================
        // PAGE CONTENT
        // =============================================================

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(
                    rememberScrollState()
                )
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


            Text(
                text =
                    "Upload Document",

                fontSize =
                    27.sp,

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
                    "Upload an existing leasing document securely.",

                fontSize =
                    12.sp,

                color =
                    Color(0xFF817A8B)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        24.dp
                    )
            )


            // =========================================================
            // HERO
            // =========================================================

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
                                    Color(0xFF6750B6),
                                    Color(0xFF9650C2),
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
                        modifier =
                            Modifier
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
                                            0.16f
                                    )
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text =
                                "UP",

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
                                14.dp
                            )
                    )


                    Column {

                        Text(
                            text =
                                "Secure Upload",

                            color =
                                Color.White,

                            fontSize =
                                18.sp,

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
                                "Maximum file size 20 MB",

                            color =
                                Color.White.copy(
                                    alpha =
                                        0.80f
                                ),

                            fontSize =
                                11.sp
                        )
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            // =========================================================
            // SELECT FILE
            // =========================================================

            Text(
                text =
                    "Document File",

                fontSize =
                    13.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF2C2738)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        9.dp
                    )
            )


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            19.dp
                        )
                    )
                    .background(
                        Color.White.copy(
                            alpha =
                                0.96f
                        )
                    )
                    .clickable(
                        enabled =
                            !isLoading
                    ) {

                        filePicker.launch(
                            "*/*"
                        )
                    }
                    .padding(
                        17.dp
                    )
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Box(
                        modifier =
                            Modifier
                                .size(
                                    45.dp
                                )
                                .clip(
                                    RoundedCornerShape(
                                        14.dp
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
                                "FILE",

                            fontSize =
                                9.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color(0xFF7650BD)
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
                                if (
                                    selectedFileName.isBlank()
                                ) {

                                    "Select Document"

                                } else {

                                    selectedFileName
                                },

                            fontSize =
                                13.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color(0xFF221E31),

                            maxLines =
                                2,

                            overflow =
                                TextOverflow.Ellipsis
                        )


                        Spacer(
                            modifier =
                                Modifier.height(
                                    3.dp
                                )
                        )


                        Text(
                            text =
                                if (
                                    selectedFileName.isBlank()
                                ) {

                                    "Tap to browse files"

                                } else {

                                    val sizeInMb =
                                        selectedFileSize.toDouble() /
                                                (
                                                        1024.0 *
                                                                1024.0
                                                        )

                                    String.format(
                                        Locale.US,
                                        "%.2f MB",
                                        sizeInMb
                                    )
                                },

                            fontSize =
                                10.sp,

                            color =
                                Color(0xFF817A8B)
                        )
                    }


                    Text(
                        text =
                            "›",

                        fontSize =
                            28.sp,

                        color =
                            Color(0xFF8C8494)
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            // =========================================================
            // CUSTOMER ID
            // =========================================================

            OutlinedTextField(
                value =
                    customerId,

                onValueChange = {

                    if (
                        it.all {
                                character ->
                            character.isDigit()
                        }
                    ) {

                        customerId =
                            it
                    }
                },

                label = {
                    Text(
                        "Customer ID"
                    )
                },

                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Number
                    ),

                singleLine =
                    true,

                enabled =
                    !isLoading,

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
                        14.dp
                    )
            )


            // =========================================================
            // DOCUMENT TYPE ID
            // =========================================================

            OutlinedTextField(
                value =
                    documentTypeId,

                onValueChange = {

                    if (
                        it.all {
                                character ->
                            character.isDigit()
                        }
                    ) {

                        documentTypeId =
                            it
                    }
                },

                label = {
                    Text(
                        "Document Type ID"
                    )
                },

                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Number
                    ),

                singleLine =
                    true,

                enabled =
                    !isLoading,

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
                        20.dp
                    )
            )


            // =========================================================
            // UPLOAD BUTTON
            // =========================================================

            val canUpload =
                !isLoading &&
                        selectedFileBytes != null &&
                        selectedFileName.isNotBlank() &&
                        customerId.isNotBlank() &&
                        documentTypeId.isNotBlank()


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        56.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            18.dp
                        )
                    )
                    .background(
                        if (
                            canUpload
                        ) {

                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color(0xFF6E50B8),
                                        Color(0xFF9650C3),
                                        Color(0xFFD04E93)
                                    )
                            )

                        } else {

                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color(0xFFD7D0DE),
                                        Color(0xFFD7D0DE)
                                    )
                            )
                        }
                    )
                    .clickable(
                        enabled =
                            canUpload
                    ) {


                        // =================================================
                        // FILE SIZE VALIDATION
                        // =================================================

                        if (
                            selectedFileSize >
                            MAX_FILE_SIZE_BYTES
                        ) {

                            message =
                                "Upload failed: File size must not exceed 20 MB"

                        } else if (
                            selectedFileBytes == null ||
                            selectedFileName.isBlank() ||
                            customerId.isBlank() ||
                            documentTypeId.isBlank()
                        ) {

                            message =
                                "Please select a document and enter all details"

                        } else if (
                            customerId.toLongOrNull() == null ||
                            documentTypeId.toLongOrNull() == null
                        ) {

                            message =
                                "Customer ID and Document Type ID must be numbers"

                        } else {


                            coroutineScope.launch {

                                isLoading =
                                    true

                                message =
                                    ""


                                try {

                                    val fileRequestBody =
                                        selectedFileBytes!!
                                            .toRequestBody(
                                                "application/octet-stream"
                                                    .toMediaTypeOrNull()
                                            )


                                    val filePart =
                                        MultipartBody.Part
                                            .createFormData(
                                                "file",
                                                selectedFileName,
                                                fileRequestBody
                                            )


                                    val customerIdBody =
                                        customerId
                                            .toRequestBody(
                                                "text/plain"
                                                    .toMediaTypeOrNull()
                                            )


                                    val documentTypeIdBody =
                                        documentTypeId
                                            .toRequestBody(
                                                "text/plain"
                                                    .toMediaTypeOrNull()
                                            )


                                    val response =
                                        RetrofitClient
                                            .apiService
                                            .uploadDocument(
                                                file =
                                                    filePart,

                                                customerId =
                                                    customerIdBody,

                                                documentTypeId =
                                                    documentTypeIdBody
                                            )


                                    if (
                                        response.isSuccessful
                                    ) {

                                        message =
                                            "Document uploaded successfully"

                                        selectedFileName =
                                            ""

                                        selectedFileBytes =
                                            null

                                        selectedFileSize =
                                            0L

                                        customerId =
                                            ""

                                        documentTypeId =
                                            ""

                                    } else if (
                                        response.code() ==
                                        413
                                    ) {

                                        message =
                                            "Upload failed: File size must not exceed 20 MB"

                                    } else {

                                        val errorBody =
                                            response
                                                .errorBody()
                                                ?.string()


                                        val backendMessage =
                                            getBackendErrorMessage(
                                                errorBody =
                                                    errorBody,

                                                statusCode =
                                                    response.code()
                                            )


                                        message =
                                            "Upload failed: $backendMessage"
                                    }


                                } catch (
                                    e: CancellationException
                                ) {

                                    throw e

                                } catch (
                                    e: Exception
                                ) {

                                    message =
                                        "Cannot connect to server"

                                } finally {

                                    isLoading =
                                        false
                                }
                            }
                        }
                    },

                contentAlignment =
                    Alignment.Center
            ) {

                if (
                    isLoading
                ) {

                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(
                                22.dp
                            ),

                        strokeWidth =
                            2.dp,

                        color =
                            Color.White
                    )

                } else {

                    Text(
                        text =
                            "UPLOAD DOCUMENT",

                        color =
                            Color.White,

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.Bold,

                        letterSpacing =
                            0.5.sp
                    )
                }
            }


            // =========================================================
            // MESSAGE
            // =========================================================

            if (
                message.isNotBlank()
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            15.dp
                        )
                )


                UploadMessageCard(
                    message =
                        message
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        30.dp
                    )
            )
        }
    }
}


// =====================================================================
// MESSAGE CARD
// =====================================================================

@Composable
private fun UploadMessageCard(
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

                    Color(0xFFFFEEF1)
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

            lineHeight =
                17.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                if (
                    success
                ) {

                    Color(0xFF247148)

                } else {

                    Color(0xFFA13A4B)
                }
        )
    }
}


// =====================================================================
// EXTRACT SAFE BACKEND ERROR MESSAGE
// =====================================================================

private fun getBackendErrorMessage(
    errorBody: String?,
    statusCode: Int
): String {


    if (
        statusCode ==
        413
    ) {

        return "File size must not exceed 20 MB"
    }


    if (
        errorBody.isNullOrBlank()
    ) {

        return when (
            statusCode
        ) {

            400 ->
                "Invalid upload request"

            401 ->
                "Authentication required"

            403 ->
                "You are not authorized to perform this action"

            404 ->
                "Requested resource was not found"

            else ->
                "Server rejected the upload"
        }
    }


    return try {

        val json =
            JSONObject(
                errorBody
            )


        var serverMessage =
            json.optString(
                "message",
                ""
            )


        if (
            serverMessage.isBlank()
        ) {

            serverMessage =
                json.optString(
                    "error",
                    ""
                )
        }


        if (
            serverMessage.isBlank()
        ) {

            return when (
                statusCode
            ) {

                400 ->
                    "Invalid upload request"

                401 ->
                    "Authentication required"

                403 ->
                    "You are not authorized to perform this action"

                404 ->
                    "Requested resource was not found"

                else ->
                    "Server rejected the upload"
            }
        }


        val firstQuote =
            serverMessage.indexOf(
                '"'
            )

        val lastQuote =
            serverMessage.lastIndexOf(
                '"'
            )


        if (
            firstQuote >= 0 &&
            lastQuote >
            firstQuote
        ) {

            serverMessage.substring(
                firstQuote + 1,
                lastQuote
            )

        } else {

            serverMessage
        }


    } catch (
        e: Exception
    ) {

        when (
            statusCode
        ) {

            400 ->
                "Invalid upload request"

            401 ->
                "Authentication required"

            403 ->
                "You are not authorized to perform this action"

            413 ->
                "File size must not exceed 20 MB"

            else ->
                "Server rejected the upload"
        }
    }
}
