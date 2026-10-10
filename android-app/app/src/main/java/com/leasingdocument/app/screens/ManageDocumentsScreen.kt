package com.leasingdocument.app.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.Document
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.launch

@Composable
fun ManageDocumentsScreen() {

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

    var message by remember {
        mutableStateOf("")
    }

    var documentToDelete by remember {
        mutableStateOf<Document?>(null)
    }

    LaunchedEffect(Unit) {

        try {

            val response =
                RetrofitClient.apiService.getDocuments()

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

    // Delete confirmation
    if (documentToDelete != null) {

        AlertDialog(
            onDismissRequest = {
                documentToDelete = null
            },

            title = {
                Text("Delete Document")
            },

            text = {
                Text(
                    "Are you sure you want to delete ${documentToDelete?.fileName}?"
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        val documentId =
                            documentToDelete?.documentId

                        documentToDelete = null

                        if (documentId != null) {

                            coroutineScope.launch {

                                try {

                                    val response =
                                        RetrofitClient.apiService
                                            .deleteDocument(documentId)

                                    if (response.isSuccessful) {

                                        documents =
                                            documents.filter {
                                                it.documentId != documentId
                                            }

                                        message =
                                            "Document deleted successfully"

                                    } else {

                                        message =
                                            "Delete failed: ${response.code()}"
                                    }

                                } catch (e: Exception) {

                                    message =
                                        "Cannot connect to server"
                                }
                            }
                        }
                    }
                ) {
                    Text("DELETE")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        documentToDelete = null
                    }
                ) {
                    Text("CANCEL")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            text = "Manage Documents",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        if (message.isNotEmpty()) {

            Text(message)

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }

        if (isLoading) {

            CircularProgressIndicator()

        } else if (errorMessage.isNotEmpty()) {

            Text(errorMessage)

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

                            Text(
                                text =
                                    document.fileName
                                        ?: "Unknown Document",
                                fontWeight = FontWeight.Bold
                            )

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
                                    "Agent ID: ${document.agentId ?: "-"}"
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

                            Button(
                                onClick = {
                                    documentToDelete = document
                                },

                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {

                                Text("DELETE")
                            }
                        }
                    }
                }
            }
        }
    }
}