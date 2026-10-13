package com.leasingdocument.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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


    // =========================================================
    // LOAD SYSTEM DATA
    // =========================================================

    LaunchedEffect(Unit) {

        isLoading = true
        errorMessage = ""

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
                    customerResponse.body()
                        ?: emptyList()

                documentTypes =
                    documentTypeResponse.body()
                        ?: emptyList()

                devices =
                    deviceResponse.body()
                        ?: emptyList()

                sessions =
                    sessionResponse.body()
                        ?: emptyList()

            } else {

                errorMessage =
                    "Failed to load system data"
            }

        } catch (e: Exception) {

            errorMessage =
                "Cannot connect to server"

        } finally {

            isLoading = false
        }
    }


    // =========================================================
    // ROOT
    // =========================================================

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


        // =====================================================
        // BACKGROUND DECORATION
        // =====================================================

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

                    moveTo(0f, 0f)
                    lineTo(w * 0.72f, 0f)

                    cubicTo(
                        w * 0.58f,
                        h * 0.028f,

                        w * 0.40f,
                        h * 0.060f,

                        0f,
                        h * 0.16f
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
                                Color(0xFFFFD1AF)
                                    .copy(alpha = 0.58f),

                                Color(0xFFFFE8DE)
                                    .copy(alpha = 0.24f),

                                Color.Transparent
                            )
                    )
            )


            val ribbon =
                Path().apply {

                    moveTo(
                        0f,
                        h * 0.075f
                    )

                    cubicTo(
                        w * 0.19f,
                        h * 0.052f,

                        w * 0.35f,
                        h * 0.080f,

                        w * 0.58f,
                        h * 0.022f
                    )
                }


            drawPath(
                path =
                    ribbon,

                color =
                    Color(0xFFFF8B42)
                        .copy(alpha = 0.70f),

                style =
                    Stroke(
                        width =
                            1.1.dp.toPx()
                    )
            )


            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(0xFF9F47DD)
                                    .copy(alpha = 0.08f),

                                Color.Transparent
                            )
                    ),

                radius =
                    w * 0.75f,

                center =
                    Offset(
                        x =
                            w * 0.55f,

                        y =
                            h
                    )
            )
        }


        // =====================================================
        // CONTENT
        // =====================================================

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal =
                        22.dp
                )
        ) {


            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )


            Text(
                text =
                    "System Data",

                fontSize =
                    27.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )


            Text(
                text =
                    "Review supporting customer, document type, device and session records.",

                fontSize =
                    12.sp,

                color =
                    Color(0xFF817A8B)
            )


            Spacer(
                modifier =
                    Modifier.height(22.dp)
            )


            // =================================================
            // OVERVIEW
            // =================================================

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(24.dp)
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color(0xFF694CB7),
                                    Color(0xFF9C4FC2),
                                    Color(0xFFD34E92)
                                )
                        )
                    )
                    .padding(20.dp)
            ) {

                Column {

                    Text(
                        text =
                            "System Overview",

                        color =
                            Color.White.copy(alpha = 0.84f),

                        fontSize =
                            12.sp
                    )


                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )


                    Text(
                        text =
                            "${customers.size + documentTypes.size + devices.size + sessions.size} Records",

                        color =
                            Color.White,

                        fontSize =
                            22.sp,

                        fontWeight =
                            FontWeight.Bold
                    )


                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )


                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        SystemOverviewPill(
                            label =
                                "CUSTOMERS",

                            value =
                                customers.size.toString()
                        )


                        SystemOverviewPill(
                            label =
                                "TYPES",

                            value =
                                documentTypes.size.toString()
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )


                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        SystemOverviewPill(
                            label =
                                "DEVICES",

                            value =
                                devices.size.toString()
                        )


                        SystemOverviewPill(
                            label =
                                "SESSIONS",

                            value =
                                sessions.size.toString()
                        )
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )


            // =================================================
            // BODY
            // =================================================

            when {

                isLoading -> {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(30.dp),

                                strokeWidth =
                                    3.dp,

                                color =
                                    Color(0xFF7650BD)
                            )


                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )


                            Text(
                                text =
                                    "Loading system data...",

                                fontSize =
                                    12.sp,

                                color =
                                    Color(0xFF817A8B)
                            )
                        }
                    }
                }


                errorMessage.isNotBlank() -> {

                    SystemStatusCard(
                        title =
                            "Unable to load system data",

                        message =
                            errorMessage,

                        background =
                            Color(0xFFFFEEF1),

                        titleColor =
                            Color(0xFFA13A4B)
                    )
                }


                else -> {

                    LazyColumn(
                        modifier =
                            Modifier.fillMaxSize()
                    ) {


                        // =============================================
                        // CUSTOMERS
                        // =============================================

                        item {

                            SystemSectionTitle(
                                title =
                                    "Customers",

                                subtitle =
                                    "${customers.size} registered"
                            )
                        }


                        if (customers.isEmpty()) {

                            item {

                                SystemEmptyCard(
                                    message =
                                        "No customers found."
                                )
                            }

                        } else {

                            items(
                                items =
                                    customers,

                                key = {
                                    it.customerId
                                        ?: it.hashCode()
                                }
                            ) { customer ->

                                CustomerSystemCard(
                                    customer =
                                        customer
                                )


                                Spacer(
                                    modifier =
                                        Modifier.height(10.dp)
                                )
                            }
                        }


                        // =============================================
                        // DOCUMENT TYPES
                        // =============================================

                        item {

                            Spacer(
                                modifier =
                                    Modifier.height(18.dp)
                            )


                            SystemSectionTitle(
                                title =
                                    "Document Types",

                                subtitle =
                                    "${documentTypes.size} configured"
                            )
                        }


                        if (documentTypes.isEmpty()) {

                            item {

                                SystemEmptyCard(
                                    message =
                                        "No document types found."
                                )
                            }

                        } else {

                            items(
                                items =
                                    documentTypes,

                                key = {
                                    it.documentTypeId
                                        ?: it.hashCode()
                                }
                            ) { documentType ->

                                DocumentTypeSystemCard(
                                    documentType =
                                        documentType
                                )


                                Spacer(
                                    modifier =
                                        Modifier.height(10.dp)
                                )
                            }
                        }


                        // =============================================
                        // DEVICES
                        // =============================================

                        item {

                            Spacer(
                                modifier =
                                    Modifier.height(18.dp)
                            )


                            SystemSectionTitle(
                                title =
                                    "Devices",

                                subtitle =
                                    "${devices.size} registered"
                            )
                        }


                        if (devices.isEmpty()) {

                            item {

                                SystemEmptyCard(
                                    message =
                                        "No devices found."
                                )
                            }

                        } else {

                            items(
                                items =
                                    devices,

                                key = {
                                    it.deviceId
                                        ?: it.hashCode()
                                }
                            ) { device ->

                                DeviceSystemCard(
                                    device =
                                        device
                                )


                                Spacer(
                                    modifier =
                                        Modifier.height(10.dp)
                                )
                            }
                        }


                        // =============================================
                        // SESSIONS
                        // =============================================

                        item {

                            Spacer(
                                modifier =
                                    Modifier.height(18.dp)
                            )


                            SystemSectionTitle(
                                title =
                                    "Sessions",

                                subtitle =
                                    "${sessions.size} recorded"
                            )
                        }


                        if (sessions.isEmpty()) {

                            item {

                                SystemEmptyCard(
                                    message =
                                        "No sessions found."
                                )
                            }

                        } else {

                            items(
                                items =
                                    sessions,

                                key = {
                                    it.sessionId
                                        ?: it.hashCode()
                                }
                            ) { session ->

                                SessionSystemCard(
                                    session =
                                        session
                                )


                                Spacer(
                                    modifier =
                                        Modifier.height(10.dp)
                                )
                            }
                        }


                        item {

                            Spacer(
                                modifier =
                                    Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


// =====================================================================
// OVERVIEW PILL
// =====================================================================

@Composable
private fun SystemOverviewPill(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(14.dp)
                )
                .background(
                    Color.White.copy(alpha = 0.15f)
                )
                .padding(
                    horizontal =
                        11.dp,

                    vertical =
                        9.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text =
                value,

            fontSize =
                13.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                Color.White
        )


        Spacer(
            modifier =
                Modifier.width(5.dp)
        )


        Text(
            text =
                label,

            fontSize =
                8.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color.White.copy(alpha = 0.82f)
        )
    }
}


// =====================================================================
// SECTION TITLE
// =====================================================================

@Composable
private fun SystemSectionTitle(
    title: String,
    subtitle: String
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    bottom =
                        10.dp
                )
    ) {

        Text(
            text =
                title,

            fontSize =
                18.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                Color(0xFF1B1D30)
        )


        Spacer(
            modifier =
                Modifier.height(3.dp)
        )


        Text(
            text =
                subtitle,

            fontSize =
                11.sp,

            color =
                Color(0xFF8B8495)
        )
    }
}


// =====================================================================
// CUSTOMER CARD
// =====================================================================

@Composable
private fun CustomerSystemCard(
    customer: Customer
) {

    SystemRecordCard(
        shortName =
            "CU",

        title =
            customer.fullName
                ?: "Unknown Customer",

        accentColors =
            listOf(
                Color(0xFFFF8B4D),
                Color(0xFFF15B8F)
            )
    ) {

        SystemInfoRow(
            label =
                "Customer ID",

            value =
                customer.customerId
                    ?.toString()
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "NIC",

            value =
                customer.nic
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Phone",

            value =
                customer.phone
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Email",

            value =
                customer.email
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Address",

            value =
                customer.address
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Created At",

            value =
                customer.createdAt
                    ?: "-"
        )
    }
}


// =====================================================================
// DOCUMENT TYPE CARD
// =====================================================================

@Composable
private fun DocumentTypeSystemCard(
    documentType: DocumentType
) {

    SystemRecordCard(
        shortName =
            "DT",

        title =
            documentType.typeName
                ?: "Unknown Document Type",

        accentColors =
            listOf(
                Color(0xFF7E53CE),
                Color(0xFFBD4FD1)
            )
    ) {

        SystemInfoRow(
            label =
                "Type ID",

            value =
                documentType.documentTypeId
                    ?.toString()
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Description",

            value =
                documentType.description
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Required",

            value =
                when (documentType.requiredFlag) {

                    true ->
                        "Yes"

                    false ->
                        "No"

                    null ->
                        "-"
                }
        )
    }
}


// =====================================================================
// DEVICE CARD
// =====================================================================

@Composable
private fun DeviceSystemCard(
    device: Device
) {

    SystemRecordCard(
        shortName =
            "DV",

        title =
            device.deviceType
                ?: "Unknown Device",

        accentColors =
            listOf(
                Color(0xFF5C62C8),
                Color(0xFF8752D2)
            )
    ) {

        SystemInfoRow(
            label =
                "Device ID",

            value =
                device.deviceId
                    ?.toString()
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "IMEI",

            value =
                device.imeiNumber
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Device Number",

            value =
                device.deviceNumber
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "OS Version",

            value =
                device.osVersion
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Status",

            value =
                device.status
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Assigned Date",

            value =
                device.assignedDate
                    ?: "-"
        )
    }
}


// =====================================================================
// SESSION CARD
// =====================================================================

@Composable
private fun SessionSystemCard(
    session: Session
) {

    SystemRecordCard(
        shortName =
            "SE",

        title =
            "Session ${session.sessionId ?: "-"}",

        accentColors =
            listOf(
                Color(0xFFB845B4),
                Color(0xFFE15A93)
            )
    ) {

        SystemInfoRow(
            label =
                "Admin ID",

            value =
                session.adminId
                    ?.toString()
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Agent ID",

            value =
                session.agentId
                    ?.toString()
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Device ID",

            value =
                session.deviceId
                    ?.toString()
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Login Time",

            value =
                session.loginTime
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Logout Time",

            value =
                session.logoutTime
                    ?: "-"
        )


        SystemInfoRow(
            label =
                "Status",

            value =
                session.status
                    ?: "-"
        )
    }
}


// =====================================================================
// GENERIC RECORD CARD
// =====================================================================

@Composable
private fun SystemRecordCard(
    shortName: String,
    title: String,
    accentColors: List<Color>,
    content: @Composable () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(21.dp)
                )
                .background(
                    Color.White.copy(alpha = 0.96f)
                )
                .padding(16.dp)
    ) {

        Column(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(
                                RoundedCornerShape(15.dp)
                            )
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        accentColors
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            shortName,

                        color =
                            Color.White,

                        fontSize =
                            11.sp,

                        fontWeight =
                            FontWeight.Bold
                    )
                }


                Spacer(
                    modifier =
                        Modifier.width(13.dp)
                )


                Text(
                    text =
                        title,

                    modifier =
                        Modifier.weight(1f),

                    fontSize =
                        15.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF171A2C),

                    maxLines =
                        1,

                    overflow =
                        TextOverflow.Ellipsis
                )
            }


            Spacer(
                modifier =
                    Modifier.height(13.dp)
            )


            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(14.dp)
                        )
                        .background(
                            Color(0xFFF8F5FB)
                        )
                        .padding(12.dp)
            ) {

                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    content()
                }
            }
        }
    }
}


// =====================================================================
// INFO ROW
// =====================================================================

@Composable
private fun SystemInfoRow(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        4.dp
                ),

        verticalAlignment =
            Alignment.Top
    ) {

        Text(
            text =
                label,

            modifier =
                Modifier.weight(0.40f),

            fontSize =
                10.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color(0xFF8D8698)
        )


        Spacer(
            modifier =
                Modifier.width(8.dp)
        )


        Text(
            text =
                value,

            modifier =
                Modifier.weight(0.60f),

            fontSize =
                10.sp,

            color =
                Color(0xFF3F394A),

            maxLines =
                3,

            overflow =
                TextOverflow.Ellipsis
        )
    }
}


// =====================================================================
// EMPTY CARD
// =====================================================================

@Composable
private fun SystemEmptyCard(
    message: String
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(18.dp)
                )
                .background(
                    Color.White.copy(alpha = 0.92f)
                )
                .padding(16.dp)
    ) {

        Text(
            text =
                message,

            fontSize =
                12.sp,

            color =
                Color(0xFF817A8B)
        )
    }
}


// =====================================================================
// STATUS CARD
// =====================================================================

@Composable
private fun SystemStatusCard(
    title: String,
    message: String,
    background: Color,
    titleColor: Color
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(20.dp)
                )
                .background(background)
                .padding(18.dp)
    ) {

        Column {

            Text(
                text =
                    title,

                fontSize =
                    15.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    titleColor
            )


            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )


            Text(
                text =
                    message,

                fontSize =
                    12.sp,

                lineHeight =
                    17.sp,

                color =
                    Color(0xFF817A8B)
            )
        }
    }
}
