package com.leasingdocument.app.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.AlterationResult
import com.leasingdocument.app.network.IntegrityResult
import com.leasingdocument.app.network.RetrofitClient

@Composable
fun VerificationResultsScreen() {

    var alterationResults by remember {
        mutableStateOf<List<AlterationResult>>(emptyList())
    }

    var integrityResults by remember {
        mutableStateOf<List<IntegrityResult>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {

        try {

            val alterationResponse =
                RetrofitClient.apiService.getAlterationResults()

            val integrityResponse =
                RetrofitClient.apiService.getIntegrityResults()

            if (
                alterationResponse.isSuccessful &&
                integrityResponse.isSuccessful
            ) {

                alterationResults =
                    alterationResponse.body() ?: emptyList()

                integrityResults =
                    integrityResponse.body() ?: emptyList()

            } else {

                errorMessage =
                    "Failed to load verification results"
            }

        } catch (e: Exception) {

            errorMessage =
                "Cannot connect to server"
        }

        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            text = "Verification Results",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        if (isLoading) {

            CircularProgressIndicator()

        } else if (errorMessage.isNotEmpty()) {

            Text(errorMessage)

        } else {

            LazyColumn {

                item {

                    Text(
                        text = "Alteration Detection",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }

                items(alterationResults) { result ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text(
                                text = "Document ID: ${result.documentId ?: "-"}",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Risk Score: ${result.riskScore ?: "-"}"
                            )

                            Text(
                                text = "Risk Level: ${result.riskLevel ?: "-"}"
                            )

                            Text(
                                text = "Analysis Status: ${result.analysisStatus ?: "-"}"
                            )

                            Text(
                                text = "Suspicious Regions: ${result.suspiciousRegionCount ?: "-"}"
                            )

                            Text(
                                text = "Algorithm: ${result.algorithmVersion ?: "-"}"
                            )

                            Text(
                                text = "Processed At: ${result.processedAt ?: "-"}"
                            )
                        }
                    }
                }

                item {

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    Text(
                        text = "Integrity Verification",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }

                items(integrityResults) { result ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text(
                                text = "Document ID: ${result.documentId ?: "-"}",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Verification: ${result.verificationResult ?: "-"}"
                            )

                            Text(
                                text = "SHA-256: ${result.sha256Hash ?: "-"}"
                            )

                            Text(
                                text = "Processed At: ${result.processedAt ?: "-"}"
                            )
                        }
                    }
                }
            }
        }
    }
}