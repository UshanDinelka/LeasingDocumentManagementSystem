package com.leasingdocument.app.screens

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.Document
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun MyDocumentsScreen(
    onBack: () -> Unit
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var documents by remember {
        mutableStateOf<List<Document>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    var selectedFileName by remember {
        mutableStateOf<String?>(null)
    }

    var downloadMessage by remember {
        mutableStateOf("")
    }


    // =========================================================
    // PHONE / EMULATOR BACK BUTTON
    // =========================================================

    BackHandler {

        if (selectedFileName != null) {

            // Document View -> My Documents
            selectedFileName = null

        } else {

            // My Documents -> Agent Dashboard
            onBack()
        }
    }


    // =========================================================
    // DOCUMENT VIEW SCREEN
    // =========================================================

    if (selectedFileName != null) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {

            Button(
                onClick = {
                    selectedFileName = null
                },
                modifier = Modifier.padding(
                    start = 24.dp,
                    top = 8.dp,
                    bottom = 8.dp
                )
            ) {
                Text("BACK")
            }

            DocumentImageScreen(
                fileName = selectedFileName!!
            )
        }

        return
    }


    // =========================================================
    // LOAD DOCUMENTS
    // =========================================================

    LaunchedEffect(Unit) {

        try {

            val response =
                RetrofitClient
                    .apiService
                    .getMyDocuments()

            if (response.isSuccessful) {

                documents =
                    response.body() ?: emptyList()

            } else {

                errorMessage =
                    "Failed to load documents"
            }

        } catch (e: Exception) {

            errorMessage =
                "Cannot connect to server"
        }

        isLoading = false
    }


    // =========================================================
    // MY DOCUMENTS SCREEN
    // =========================================================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {

        // =====================================================
        // TOP BACK BUTTON
        // =====================================================

        Button(
            onClick = {
                onBack()
            },
            modifier = Modifier.padding(
                start = 24.dp,
                top = 8.dp,
                bottom = 4.dp
            )
        ) {
            Text("BACK")
        }


        // =====================================================
        // PAGE CONTENT
        // =====================================================

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 8.dp,
                    bottom = 24.dp
                )
        ) {

            Text(
                text = "My Documents",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )


            // =================================================
            // DOWNLOAD MESSAGE
            // =================================================

            if (downloadMessage.isNotEmpty()) {

                Text(
                    text = downloadMessage
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )
            }


            // =================================================
            // LOADING / ERROR / DOCUMENT LIST
            // =================================================

            if (isLoading) {

                CircularProgressIndicator()

            } else if (errorMessage.isNotEmpty()) {

                Text(
                    text = errorMessage
                )

            } else if (documents.isEmpty()) {

                Text(
                    text = "No documents found"
                )

            } else {

                LazyColumn {

                    items(documents) { document ->

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {

                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {

                                // =========================================
                                // DOCUMENT NAME
                                // =========================================

                                Text(
                                    text =
                                        document.fileName
                                            ?: "Unknown Document",
                                    fontWeight = FontWeight.Bold
                                )


                                // =========================================
                                // DOCUMENT INFORMATION
                                // =========================================

                                Text(
                                    text =
                                        "Document ID: ${document.documentId ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Customer ID: ${document.customerId ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Capture Status: ${document.captureStatus ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Verification Status: ${document.verificationStatus ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Status: ${document.status ?: "-"}"
                                )

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )


                                // =========================================
                                // VIEW + DOWNLOAD BUTTONS
                                // =========================================

                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {

                                    // =====================================
                                    // VIEW BUTTON
                                    // =====================================

                                    Button(
                                        onClick = {

                                            if (
                                                !document
                                                    .fileName
                                                    .isNullOrBlank()
                                            ) {

                                                selectedFileName =
                                                    document.fileName
                                            }
                                        },

                                        modifier =
                                            Modifier.weight(1f)
                                    ) {

                                        Text("VIEW")
                                    }


                                    Spacer(
                                        modifier =
                                            Modifier.weight(0.05f)
                                    )


                                    // =====================================
                                    // DOWNLOAD BUTTON
                                    // =====================================

                                    Button(
                                        onClick = {

                                            val fileName =
                                                document.fileName

                                            if (
                                                !fileName.isNullOrBlank()
                                            ) {

                                                coroutineScope.launch {

                                                    downloadMessage =
                                                        "Downloading..."

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
                                                                }

                                                            } else {

                                                                downloadMessage =
                                                                    "Download failed"
                                                            }

                                                        } else {

                                                            downloadMessage =
                                                                "Download failed: ${response.code()}"
                                                        }

                                                    } catch (
                                                        e: Exception
                                                    ) {

                                                        downloadMessage =
                                                            "Download failed"
                                                    }
                                                }
                                            }
                                        },

                                        modifier =
                                            Modifier.weight(1f)
                                    ) {

                                        Text("DOWNLOAD")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}