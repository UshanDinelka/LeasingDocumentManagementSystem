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

    LaunchedEffect(Unit) {

        try {

            val response =
                RetrofitClient.apiService.getAuditLogs()

            if (response.isSuccessful) {

                logs = response.body() ?: emptyList()

            } else {

                errorMessage = "Failed to load audit logs"
            }

        } catch (e: Exception) {

            errorMessage = "Cannot connect to server"
        }

        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            text = "Audit Logs",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        if (isLoading) {

            CircularProgressIndicator()

        } else if (errorMessage.isNotEmpty()) {

            Text(errorMessage)

        } else {

            LazyColumn {

                items(logs) { log ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text(
                                text = log.action ?: "Unknown Action",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = log.description ?: ""
                            )

                            Text(
                                text = "Admin ID: ${log.adminId ?: "-"}"
                            )

                            Text(
                                text = "Agent ID: ${log.agentId ?: "-"}"
                            )

                            Text(
                                text = "IP: ${log.ipAddress ?: "-"}"
                            )

                            Text(
                                text = "Time: ${log.createdAt ?: "-"}"
                            )
                        }
                    }
                }
            }
        }
    }
}