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
import com.leasingdocument.app.network.Customer
import com.leasingdocument.app.network.Device
import com.leasingdocument.app.network.DocumentType
import com.leasingdocument.app.network.RetrofitClient
import com.leasingdocument.app.network.Session

@Composable
fun SystemDataScreen() {

    var customers by remember {
        mutableStateOf<List<Customer>>(emptyList())
    }

    var documentTypes by remember {
        mutableStateOf<List<DocumentType>>(emptyList())
    }

    var devices by remember {
        mutableStateOf<List<Device>>(emptyList())
    }

    var sessions by remember {
        mutableStateOf<List<Session>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {

        try {

            val customerResponse =
                RetrofitClient.apiService.getCustomers()

            val documentTypeResponse =
                RetrofitClient.apiService.getDocumentTypes()

            val deviceResponse =
                RetrofitClient.apiService.getDevices()

            val sessionResponse =
                RetrofitClient.apiService.getSessions()

            if (
                customerResponse.isSuccessful &&
                documentTypeResponse.isSuccessful &&
                deviceResponse.isSuccessful &&
                sessionResponse.isSuccessful
            ) {

                customers =
                    customerResponse.body() ?: emptyList()

                documentTypes =
                    documentTypeResponse.body() ?: emptyList()

                devices =
                    deviceResponse.body() ?: emptyList()

                sessions =
                    sessionResponse.body() ?: emptyList()

            } else {

                errorMessage =
                    "Failed to load system data"
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
            text = "System Data",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        if (isLoading) {

            CircularProgressIndicator()

        } else if (errorMessage.isNotEmpty()) {

            Text(
                text = errorMessage
            )

        } else {

            LazyColumn {

                // -------------------------
                // CUSTOMERS
                // -------------------------

                item {

                    Text(
                        text = "Customers",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }

                if (customers.isEmpty()) {

                    item {
                        Text("No customers found")
                    }

                } else {

                    items(customers) { customer ->

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
                                        customer.fullName
                                            ?: "Unknown Customer",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                Text(
                                    text =
                                        "Customer ID: ${customer.customerId ?: "-"}"
                                )

                                Text(
                                    text =
                                        "NIC: ${customer.nic ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Phone: ${customer.phone ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Email: ${customer.email ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Address: ${customer.address ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Created At: ${customer.createdAt ?: "-"}"
                                )
                            }
                        }
                    }
                }

                // -------------------------
                // DOCUMENT TYPES
                // -------------------------

                item {

                    Spacer(
                        modifier = Modifier.height(28.dp)
                    )

                    Text(
                        text = "Document Types",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }

                if (documentTypes.isEmpty()) {

                    item {
                        Text("No document types found")
                    }

                } else {

                    items(documentTypes) { documentType ->

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
                                        documentType.typeName
                                            ?: "Unknown Document Type",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                Text(
                                    text =
                                        "Document Type ID: ${documentType.documentTypeId ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Description: ${documentType.description ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Required: ${
                                            when (documentType.requiredFlag) {
                                                true -> "Yes"
                                                false -> "No"
                                                null -> "-"
                                            }
                                        }"
                                )
                            }
                        }
                    }
                }

                // -------------------------
                // DEVICES
                // -------------------------

                item {

                    Spacer(
                        modifier = Modifier.height(28.dp)
                    )

                    Text(
                        text = "Devices",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }

                if (devices.isEmpty()) {

                    item {
                        Text("No devices found")
                    }

                } else {

                    items(devices) { device ->

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
                                        device.deviceType
                                            ?: "Unknown Device",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                Text(
                                    text =
                                        "Device ID: ${device.deviceId ?: "-"}"
                                )

                                Text(
                                    text =
                                        "IMEI: ${device.imeiNumber ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Device Number: ${device.deviceNumber ?: "-"}"
                                )

                                Text(
                                    text =
                                        "OS Version: ${device.osVersion ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Status: ${device.status ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Assigned Date: ${device.assignedDate ?: "-"}"
                                )
                            }
                        }
                    }
                }

                // -------------------------
                // SESSIONS
                // -------------------------

                item {

                    Spacer(
                        modifier = Modifier.height(28.dp)
                    )

                    Text(
                        text = "Sessions",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }

                if (sessions.isEmpty()) {

                    item {
                        Text("No sessions found")
                    }

                } else {

                    items(sessions) { session ->

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
                                        "Session ${session.sessionId ?: "-"}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                Text(
                                    text =
                                        "Admin ID: ${session.adminId ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Agent ID: ${session.agentId ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Device ID: ${session.deviceId ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Login Time: ${session.loginTime ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Logout Time: ${session.logoutTime ?: "-"}"
                                )

                                Text(
                                    text =
                                        "Status: ${session.status ?: "-"}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}