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
import com.leasingdocument.app.network.Agent
import com.leasingdocument.app.network.RetrofitClient

@Composable
fun ManageAgentsScreen() {

    var agents by remember {
        mutableStateOf<List<Agent>>(emptyList())
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
                RetrofitClient.apiService.getAgents()

            if (response.isSuccessful) {

                agents = response.body() ?: emptyList()

            } else {

                errorMessage =
                    "Failed to load agents"
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
            text = "Manage Agents",
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

                items(agents) { agent ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text(
                                text = agent.fullName ?: "Unknown",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = agent.email ?: ""
                            )

                            Text(
                                text = "Status: ${agent.status ?: ""}"
                            )
                        }
                    }
                }
            }
        }
    }
}