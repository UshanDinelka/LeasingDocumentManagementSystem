package com.leasingdocument.app.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


// =============================================================
// MAXIMUM UPLOAD SIZE = 20 MB
// =============================================================

private const val MAX_FILE_SIZE_BYTES =
    20L * 1024L * 1024L


@Composable
fun UploadDocumentScreen() {

    val context = LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()


    // =========================================================
    // STATE
    // =========================================================

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


    // =========================================================
    // FILE PICKER
    // =========================================================

    val filePicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri != null) {

                message = ""

                selectedFileBytes = null

                selectedFileName = ""

                selectedFileSize = 0L


                try {

                    // =================================================
                    // GET FILE NAME AND FILE SIZE
                    // =================================================

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


                        if (it.moveToFirst()) {

                            if (nameIndex >= 0) {

                                selectedFileName =
                                    it.getString(nameIndex)
                                        ?: ""
                            }


                            if (
                                sizeIndex >= 0 &&
                                !it.isNull(sizeIndex)
                            ) {

                                selectedFileSize =
                                    it.getLong(sizeIndex)
                            }
                        }
                    }


                    // =================================================
                    // CLIENT SIDE 20 MB SIZE CHECK
                    // =================================================

                    if (
                        selectedFileSize >
                        MAX_FILE_SIZE_BYTES
                    ) {

                        selectedFileBytes = null

                        message =
                            "Selected file is too large. Maximum file size is 20 MB."

                    } else {

                        // =============================================
                        // READ FILE ONLY IF SIZE IS ACCEPTABLE
                        // =============================================

                        val bytes =
                            context
                                .contentResolver
                                .openInputStream(uri)
                                ?.use { inputStream ->

                                    inputStream.readBytes()
                                }


                        if (bytes == null) {

                            selectedFileBytes = null

                            message =
                                "Unable to read selected file"

                        } else {

                            // =========================================
                            // SECOND SIZE CHECK
                            //
                            // Some providers may not return file size
                            // through OpenableColumns.SIZE.
                            // =========================================

                            if (
                                bytes.size.toLong() >
                                MAX_FILE_SIZE_BYTES
                            ) {

                                selectedFileBytes = null

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
                    }

                } catch (e: Exception) {

                    selectedFileBytes = null

                    selectedFileName = ""

                    selectedFileSize = 0L

                    message =
                        "Unable to read selected file"
                }
            }
        }


    // =========================================================
    // SCREEN
    // =========================================================

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp)
    ) {


        Text(
            text = "Upload Document",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )


        Spacer(
            modifier =
                Modifier.height(24.dp)
        )


        // =====================================================
        // SELECT DOCUMENT
        // =====================================================

        Button(
            onClick = {

                filePicker.launch("*/*")
            },

            enabled = !isLoading,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text("Select Document")
        }


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        // =====================================================
        // SELECTED FILE NAME
        // =====================================================

        if (
            selectedFileName.isNotEmpty()
        ) {

            Text(
                text =
                    "Selected: $selectedFileName"
            )


            if (
                selectedFileSize > 0L
            ) {

                val sizeInMb =
                    selectedFileSize.toDouble() /
                            (1024.0 * 1024.0)

                Text(
                    text =
                        "Size: %.2f MB".format(
                            sizeInMb
                        )
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        // =====================================================
        // CUSTOMER ID
        // =====================================================

        OutlinedTextField(
            value = customerId,

            onValueChange = {
                customerId = it
            },

            label = {
                Text("Customer ID")
            },

            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Number
                ),

            singleLine = true,

            enabled = !isLoading,

            modifier =
                Modifier.fillMaxWidth()
        )


        Spacer(
            modifier =
                Modifier.height(16.dp)
        )


        // =====================================================
        // DOCUMENT TYPE ID
        // =====================================================

        OutlinedTextField(
            value = documentTypeId,

            onValueChange = {
                documentTypeId = it
            },

            label = {
                Text("Document Type ID")
            },

            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Number
                ),

            singleLine = true,

            enabled = !isLoading,

            modifier =
                Modifier.fillMaxWidth()
        )


        Spacer(
            modifier =
                Modifier.height(24.dp)
        )


        // =====================================================
        // UPLOAD BUTTON
        // =====================================================

        Button(
            onClick = {


                // =================================================
                // FILE SIZE VALIDATION
                // =================================================

                if (
                    selectedFileSize >
                    MAX_FILE_SIZE_BYTES
                ) {

                    message =
                        "Upload failed: File size must not exceed 20 MB"


                    // =================================================
                    // REQUIRED FIELDS
                    // =================================================

                } else if (
                    selectedFileBytes == null ||
                    selectedFileName.isBlank() ||
                    customerId.isBlank() ||
                    documentTypeId.isBlank()
                ) {

                    message =
                        "Please select a document and enter all details"


                    // =================================================
                    // NUMERIC ID VALIDATION
                    // =================================================

                } else if (
                    customerId.toLongOrNull() == null ||
                    documentTypeId.toLongOrNull() == null
                ) {

                    message =
                        "Customer ID and Document Type ID must be numbers"


                } else {


                    coroutineScope.launch {

                        isLoading = true

                        message = ""


                        try {

                            // =====================================
                            // FILE REQUEST BODY
                            // =====================================

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


                            // =====================================
                            // CUSTOMER ID
                            // =====================================

                            val customerIdBody =
                                customerId
                                    .toRequestBody(
                                        "text/plain"
                                            .toMediaTypeOrNull()
                                    )


                            // =====================================
                            // DOCUMENT TYPE ID
                            // =====================================

                            val documentTypeIdBody =
                                documentTypeId
                                    .toRequestBody(
                                        "text/plain"
                                            .toMediaTypeOrNull()
                                    )


                            // =====================================
                            // BACKEND API CALL
                            // =====================================

                            val response =
                                RetrofitClient
                                    .apiService
                                    .uploadDocument(
                                        file = filePart,
                                        customerId =
                                            customerIdBody,
                                        documentTypeId =
                                            documentTypeIdBody
                                    )


                            // =====================================
                            // SUCCESS
                            // =====================================

                            if (
                                response.isSuccessful
                            ) {

                                message =
                                    "Document uploaded successfully"


                                selectedFileName = ""

                                selectedFileBytes = null

                                selectedFileSize = 0L

                                customerId = ""

                                documentTypeId = ""


                            } else {


                                // =================================
                                // FILE TOO LARGE
                                // =================================

                                if (
                                    response.code() == 413
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
                            }


                        } catch (
                            e: Exception
                        ) {

                            message =
                                "Cannot connect to server"
                        }


                        isLoading = false
                    }
                }
            },

            enabled = !isLoading,

            modifier =
                Modifier.fillMaxWidth()
        ) {


            if (isLoading) {

                CircularProgressIndicator()

            } else {

                Text("UPLOAD")
            }
        }


        Spacer(
            modifier =
                Modifier.height(16.dp)
        )


        // =====================================================
        // MESSAGE
        // =====================================================

        if (
            message.isNotEmpty()
        ) {

            Text(
                text = message
            )
        }
    }
}


// =================================================================
// EXTRACT SAFE BACKEND ERROR MESSAGE
// =================================================================

private fun getBackendErrorMessage(
    errorBody: String?,
    statusCode: Int
): String {


    // =============================================================
    // SPECIAL HTTP STATUS MESSAGES
    // =============================================================

    if (
        statusCode == 413
    ) {

        return "File size must not exceed 20 MB"
    }


    if (
        errorBody.isNullOrBlank()
    ) {

        return when (statusCode) {

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


    // =============================================================
    // PARSE SPRING BOOT JSON ERROR RESPONSE
    // =============================================================

    return try {

        val json =
            JSONObject(errorBody)


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

            return when (statusCode) {

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


        // =========================================================
        // ResponseStatusException may return:
        //
        // 400 BAD_REQUEST "Only JPG, JPEG, PNG and PDF..."
        //
        // Extract only text inside quotes.
        // =========================================================

        val firstQuote =
            serverMessage.indexOf('"')

        val lastQuote =
            serverMessage.lastIndexOf('"')


        if (
            firstQuote >= 0 &&
            lastQuote > firstQuote
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

        when (statusCode) {

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