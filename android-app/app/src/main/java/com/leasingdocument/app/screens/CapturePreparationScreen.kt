package com.leasingdocument.app.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.leasingdocument.app.capture.LiveCaptureActivity
import com.leasingdocument.app.capture.CaptureDraftsActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leasingdocument.app.network.Customer
import com.leasingdocument.app.network.DocumentType
import com.leasingdocument.app.network.RegisterCustomerRequest
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/** Select capture context and open the internal live-camera activity. */
@Composable
fun CapturePreparationScreen() {
    var page by rememberSaveable { mutableStateOf("CUSTOMER") }
    var query by rememberSaveable { mutableStateOf("") }
    var customerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var typeCode by rememberSaveable { mutableStateOf<String?>(null) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var types by remember { mutableStateOf<List<DocumentType>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var fullName by rememberSaveable { mutableStateOf("") }
    var nic by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var draftSaved by rememberSaveable { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) draftSaved = true
    }
    val selectedCustomer = customers.firstOrNull { it.customerId == customerId }
    val selectedType = types.firstOrNull { it.typeCode == typeCode }

    LaunchedEffect(reload) {
        loading = true
        loaded = false
        message = ""
        try {
            val customerResponse = RetrofitClient.apiService.getCustomers()
            if (!customerResponse.isSuccessful) {
                message = preparationError(customerResponse.code())
                return@LaunchedEffect
            }
            val typeResponse = RetrofitClient.apiService.getDocumentTypes()
            if (!typeResponse.isSuccessful) {
                message = preparationError(typeResponse.code())
                return@LaunchedEffect
            }
            val customerBody = customerResponse.body()
            val typeBody = typeResponse.body()
            if (customerBody == null || typeBody == null) {
                message = "The server returned an empty response. Please retry."
                return@LaunchedEffect
            }
            customers = customerBody.filter { it.customerId != null && it.customerId > 0 }
                .sortedBy { it.fullName.orEmpty().lowercase(Locale.ROOT) }
            types = typeBody.filter {
                it.documentTypeId != null && it.documentTypeId > 0 &&
                    !it.typeCode.isNullOrBlank() &&
                    (it.status == null || it.status.equals("ACTIVE", ignoreCase = true))
            }.sortedBy { it.typeName.orEmpty().lowercase(Locale.ROOT) }
            loaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "Cannot load customers and document types. Check the connection and retry."
        } finally {
            loading = false
        }
    }

    // Takes priority over dashboard BackHandler for the internal preparation steps.
    BackHandler(enabled = page != "CUSTOMER" || saving) {
        if (!saving) {
            page = if (page == "SUMMARY") "TYPE" else "CUSTOMER"
            message = ""
        }
    }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Capture Document", style = MaterialTheme.typography.headlineMedium)
        }
        if (loading) {
            item { CircularProgressIndicator() }
        }
        if (message.isNotBlank()) {
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        if (!loading && !loaded) {
            item {
                Button(onClick = { reload++ }) { Text("Retry loading") }
            }
        }
        if (loaded && !loading) {
            when (page) {
                "CUSTOMER" -> {
                    item { Text("1. Select a customer", style = MaterialTheme.typography.titleLarge) }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search by name or NIC") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Button(onClick = { page = "CREATE"; message = "" }) {
                            Text("Create customer")
                        }
                        OutlinedButton(onClick = { reload++ }) { Text("Refresh customers") }
                    }
                    val filtered = customers.filter {
                        it.fullName.orEmpty().contains(query.trim(), ignoreCase = true) ||
                            it.nic.orEmpty().contains(query.trim(), ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        item { Text("No matching customers. Search again or create a customer.") }
                    }
                    items(filtered, key = { it.customerId!! }) { customer ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                customerId = customer.customerId
                                page = "TYPE"
                                message = ""
                            }
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(customer.fullName ?: "Unnamed customer")
                                Text("NIC: ${customer.nic ?: "Not provided"}")
                                if (customerId == customer.customerId) Text("Selected")
                            }
                        }
                    }
                }
                "CREATE" -> {
                    item { Text("Create customer", style = MaterialTheme.typography.titleLarge) }
                    item { CustomerInput("Full name *", fullName, !saving) { fullName = it } }
                    item { CustomerInput("NIC *", nic, !saving) { nic = it } }
                    item { CustomerInput("Phone (optional)", phone, !saving) { phone = it } }
                    item { CustomerInput("Email (optional)", email, !saving) { email = it } }
                    item { CustomerInput("Address (optional)", address, !saving) { address = it } }
                    item {
                        Button(
                            enabled = !saving && fullName.isNotBlank() && nic.isNotBlank(),
                            onClick = {
                                saving = true
                                message = ""
                                scope.launch {
                                    try {
                                        val response = RetrofitClient.apiService.registerCustomer(
                                            RegisterCustomerRequest(
                                                fullName = fullName.trim(),
                                                nic = nic.trim().uppercase(Locale.ROOT),
                                                phone = phone.trim().ifBlank { null },
                                                email = email.trim().ifBlank { null },
                                                address = address.trim().ifBlank { null }
                                            )
                                        )
                                        val created = response.body()
                                        if (response.isSuccessful && created?.customerId != null) {
                                            customers = (customers.filter {
                                                it.customerId != created.customerId
                                            } + created).sortedBy { it.fullName.orEmpty() }
                                            customerId = created.customerId
                                            fullName = ""; nic = ""; phone = ""; email = ""; address = ""
                                            page = "TYPE"
                                        } else {
                                            message = when (response.code()) {
                                                400 -> "Check the customer fields. Each field must be at most 255 characters."
                                                409 -> "This NIC already exists. Go back and select the existing customer."
                                                in 200..299 -> "Registration response was incomplete. Refresh customers before retrying."
                                                else -> preparationError(response.code())
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        message = "Could not confirm registration. Go back and refresh customers before retrying."
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        ) { Text(if (saving) "Saving…" else "Save and select customer") }
                        OutlinedButton(
                            enabled = !saving,
                            onClick = { page = "CUSTOMER"; message = "" }
                        ) { Text("Back to customers") }
                    }
                }
                "TYPE" -> {
                    item {
                        Text("2. Select document type", style = MaterialTheme.typography.titleLarge)
                        Text("Customer: ${selectedCustomer?.fullName ?: "Please select again"}")
                        OutlinedButton(onClick = { page = "CUSTOMER" }) { Text("Change customer") }
                    }
                    if (types.isEmpty()) {
                        item {
                            Text("No active document types are available. Ask an administrator to configure them.")
                            OutlinedButton(onClick = { reload++ }) { Text("Refresh") }
                        }
                    }
                    items(types, key = { it.documentTypeId!! }) { type ->
                        OutlinedButton(
                            enabled = selectedCustomer != null,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { typeCode = type.typeCode; page = "SUMMARY" }
                        ) {
                            Text(type.typeName?.takeIf { it.isNotBlank() } ?: type.typeCode.orEmpty())
                        }
                    }
                }
                "SUMMARY" -> {
                    item {
                        Text("3. Capture details", style = MaterialTheme.typography.titleLarge)
                        Text("Customer: ${selectedCustomer?.fullName ?: "Selection unavailable"}")
                        Text("NIC: ${selectedCustomer?.nic.orEmpty()}")
                        Text("Document: ${selectedType?.typeName ?: selectedType?.typeCode ?: "Selection unavailable"}")
                        Text("Capture a photo and save an encrypted draft on this phone. Backend submission is not connected yet.")
                        if (draftSaved) Text("Draft saved on this phone. It has not been submitted.")
                        Button(
                            enabled = selectedCustomer != null && selectedType != null,
                            onClick = {
                                val customer = selectedCustomer
                                val type = selectedType
                                val selectedId = customer?.customerId
                                val selectedCode = type?.typeCode
                                if (selectedId != null && !selectedCode.isNullOrBlank()) {
                                    draftSaved = false
                                    cameraLauncher.launch(
                                        Intent(context, LiveCaptureActivity::class.java)
                                            .putExtra("customerId", selectedId)
                                            .putExtra("customerName", customer?.fullName.orEmpty())
                                            .putExtra("documentTypeCode", selectedCode)
                                            .putExtra("documentTypeName", type?.typeName)
                                    )
                                }
                            }
                        ) { Text("Open live camera") }
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(context, CaptureDraftsActivity::class.java))
                        }) { Text("View local drafts") }
                        OutlinedButton(onClick = { page = "TYPE" }) { Text("Change document type") }
                        OutlinedButton(onClick = { page = "CUSTOMER" }) { Text("Change customer") }
                    }
                }
            }
        }
        item { Text("", modifier = Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun CustomerInput(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 255) onChange(it) },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun preparationError(code: Int): String = when (code) {
    401 -> "Your session has expired. Log out and sign in again."
    403 -> "Access denied. Check your account role and restart the updated backend."
    else -> "Request failed (HTTP $code). Please check the backend and retry."
}
