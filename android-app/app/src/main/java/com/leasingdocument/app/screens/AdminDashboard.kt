package com.leasingdocument.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdminDashboard(
    onLogout: () -> Unit = {}
) {

    var currentScreen by rememberSaveable {
        mutableStateOf("DASHBOARD")
    }


    // =========================================================
    // PHONE / EMULATOR SYSTEM BACK BUTTON
    // =========================================================

    BackHandler(
        enabled = currentScreen != "DASHBOARD"
    ) {
        currentScreen = "DASHBOARD"
    }


    // =========================================================
    // SCREEN NAVIGATION
    // =========================================================

    when (currentScreen) {
        "ANALYSIS" -> {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Button(onClick = { currentScreen = "DASHBOARD" }, modifier = Modifier.padding(16.dp)) {
                    Text("BACK")
                }
                AlterationAnalysisScreen()
            }
        }



        // =====================================================
        // MANAGE AGENTS
        // =====================================================

        "AGENTS" -> {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {

                Button(
                    onClick = {
                        currentScreen = "DASHBOARD"
                    },
                    modifier = Modifier.padding(
                        start = 24.dp,
                        top = 8.dp,
                        bottom = 4.dp
                    )
                ) {
                    Text("BACK")
                }

                ManageAgentsScreen()
            }
        }


        // =====================================================
        // MANAGE DOCUMENTS
        // =====================================================

        "DOCUMENTS" -> {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {

                Button(
                    onClick = {
                        currentScreen = "DASHBOARD"
                    },
                    modifier = Modifier.padding(
                        start = 24.dp,
                        top = 8.dp,
                        bottom = 4.dp
                    )
                ) {
                    Text("BACK")
                }

                ManageDocumentsScreen()
            }
        }


        // =====================================================
        // AUDIT LOGS
        // =====================================================

        "AUDIT_LOGS" -> {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {

                Button(
                    onClick = {
                        currentScreen = "DASHBOARD"
                    },
                    modifier = Modifier.padding(
                        start = 24.dp,
                        top = 8.dp,
                        bottom = 4.dp
                    )
                ) {
                    Text("BACK")
                }

                AuditLogsScreen()
            }
        }


        // =====================================================
        // SYSTEM DATA
        // =====================================================

        "SYSTEM_DATA" -> {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {

                Button(
                    onClick = {
                        currentScreen = "DASHBOARD"
                    },
                    modifier = Modifier.padding(
                        start = 24.dp,
                        top = 8.dp,
                        bottom = 4.dp
                    )
                ) {
                    Text("BACK")
                }

                SystemDataScreen()
            }
        }


        // =====================================================
        // ADMIN DASHBOARD
        // =====================================================

        else -> {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(28.dp),

                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Admin Dashboard",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "System Administration",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.height(40.dp)
                )


                Button(onClick = { currentScreen = "ANALYSIS" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Document Alteration Analysis")
                }
                Spacer(modifier = Modifier.height(16.dp))

                // =============================================
                // MANAGE AGENTS BUTTON
                // =============================================

                Button(
                    onClick = {
                        currentScreen = "AGENTS"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Agents")
                }


                Spacer(
                    modifier = Modifier.height(16.dp)
                )


                // =============================================
                // MANAGE DOCUMENTS BUTTON
                // =============================================

                Button(
                    onClick = {
                        currentScreen = "DOCUMENTS"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Documents")
                }


                Spacer(
                    modifier = Modifier.height(16.dp)
                )


                // =============================================
                // AUDIT LOGS BUTTON
                // =============================================

                Button(
                    onClick = {
                        currentScreen = "AUDIT_LOGS"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Audit Logs")
                }


                Spacer(
                    modifier = Modifier.height(16.dp)
                )


                // =============================================
                // SYSTEM DATA BUTTON
                // =============================================

                Button(
                    onClick = {
                        currentScreen = "SYSTEM_DATA"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("System Data")
                }


                Spacer(
                    modifier = Modifier.height(32.dp)
                )


                // =============================================
                // LOGOUT BUTTON
                // =============================================

                Button(
                    onClick = {
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("LOGOUT")
                }
            }
        }
    }
}