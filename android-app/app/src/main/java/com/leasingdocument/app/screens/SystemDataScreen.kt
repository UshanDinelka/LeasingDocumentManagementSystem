package com.leasingdocument.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.leasingdocument.app.network.Agent
import com.leasingdocument.app.network.AuthSession
import com.leasingdocument.app.network.Customer
import com.leasingdocument.app.network.Device
import com.leasingdocument.app.network.DocumentType
import com.leasingdocument.app.network.RetrofitClient
import com.leasingdocument.app.network.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response
import java.util.Locale

private enum class SystemPage {
    OVERVIEW,
    CATEGORY,
    DETAILS
}

private enum class SystemCategory(
    val title: String,
    val description: String
) {
    CUSTOMERS(
        "Customers",
        "Registered customer information"
    ),

    DOCUMENT_TYPES(
        "Document Types",
        "Supported document categories and requirements"
    ),

    DEVICES(
        "Devices",
        "Registered devices and agent assignments"
    ),

    SESSIONS(
        "Sessions",
        "Administrator and agent login sessions"
    )
}

private data class SystemRecord(
    val key: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val fields: List<Pair<String, String?>>
)

@Composable
fun SystemDataScreen() {

    if (AuthSession.role != "ADMIN") {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF8FB)),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = "Administrator access required.",
                color = Color(0xFFA13A4B),
                fontWeight = FontWeight.Bold
            )
        }

        return
    }

    val scope =
        rememberCoroutineScope()

    var customers by remember {
        mutableStateOf<List<Customer>>(emptyList())
    }

    var types by remember {
        mutableStateOf<List<DocumentType>>(emptyList())
    }

    var devices by remember {
        mutableStateOf<List<Device>>(emptyList())
    }

    var sessions by remember {
        mutableStateOf<List<Session>>(emptyList())
    }

    var agents by remember {
        mutableStateOf<List<Agent>>(emptyList())
    }

    var page by remember {
        mutableStateOf(SystemPage.OVERVIEW)
    }

    var category by remember {
        mutableStateOf<SystemCategory?>(null)
    }

    var selectedRecord by remember {
        mutableStateOf<SystemRecord?>(null)
    }

    var search by remember {
        mutableStateOf("")
    }

    var statusFilter by remember {
        mutableStateOf("ALL")
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf("")
    }

    suspend fun refresh() {

        loading = true
        error = ""

        try {

            val api =
                RetrofitClient.apiService

            customers =
                api.getCustomers()
                    .systemBody()

            types =
                api.getDocumentTypes()
                    .systemBody()

            devices =
                api.getDevices()
                    .systemBody()

            sessions =
                api.getSessions()
                    .systemBody()

            agents =
                api.getAgents()
                    .systemBody()

        } finally {

            loading = false
        }
    }

    LaunchedEffect(Unit) {

        try {

            refresh()

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            error =
                e.message
                    ?: "Failed to load system data"
        }
    }

    fun goBack() {

        when (page) {

            SystemPage.DETAILS -> {

                selectedRecord = null
                page = SystemPage.CATEGORY
            }

            SystemPage.CATEGORY -> {

                category = null
                search = ""
                statusFilter = "ALL"
                page = SystemPage.OVERVIEW
            }

            SystemPage.OVERVIEW ->
                Unit
        }
    }

    BackHandler(
        enabled =
            page != SystemPage.OVERVIEW &&
                    !loading
    ) {
        goBack()
    }

    val allRecords =
        remember(
            customers,
            types,
            devices,
            sessions,
            agents,
            category
        ) {

            when (category) {

                SystemCategory.CUSTOMERS ->

                    customers.map { customer ->

                        SystemRecord(
                            key =
                                "customer-${customer.customerId}",

                            title =
                                customer.fullName
                                    ?: "Customer #${customer.customerId}",

                            subtitle =
                                "NIC: ${customer.nic.orEmpty()}",

                            status =
                                "REGISTERED",

                            fields =
                                listOf(
                                    "Customer ID" to
                                            customer.customerId
                                                ?.toString(),

                                    "Full name" to
                                            customer.fullName,

                                    "NIC number" to
                                            customer.nic,

                                    "Phone" to
                                            customer.phone,

                                    "Email address" to
                                            customer.email,

                                    "Address" to
                                            customer.address,

                                    "Registered at" to
                                            customer.createdAt
                                )
                        )
                    }


                SystemCategory.DOCUMENT_TYPES ->

                    types.map { type ->

                        SystemRecord(
                            key =
                                "type-${type.documentTypeId}",

                            title =
                                type.typeName
                                    ?: "Document Type #${type.documentTypeId}",

                            subtitle =
                                type.typeCode.orEmpty(),

                            status =
                                type.status
                                    ?: "UNKNOWN",

                            fields =
                                listOf(
                                    "Document type ID" to
                                            type.documentTypeId
                                                ?.toString(),

                                    "Type code" to
                                            type.typeCode,

                                    "Type name" to
                                            type.typeName,

                                    "Description" to
                                            type.description,

                                    "Required document" to
                                            yesNo(
                                                type.requiredFlag
                                            ),

                                    "Alteration analysis enabled" to
                                            yesNo(
                                                type.analysisEnabled
                                            ),

                                    "Status" to
                                            type.status
                                )
                        )
                    }


                SystemCategory.DEVICES ->

                    devices.map { device ->

                        val assignedAgent =
                            agents.firstOrNull {
                                it.agentId ==
                                        device.assignedAgentId
                            }

                        SystemRecord(
                            key =
                                "device-${device.deviceId}",

                            title =
                                device.deviceNumber
                                    ?: "Device #${device.deviceId}",

                            subtitle =
                                device.deviceType
                                    ?: "Device type unavailable",

                            status =
                                device.status
                                    ?: "UNKNOWN",

                            fields =
                                listOf(
                                    "Device ID" to
                                            device.deviceId
                                                ?.toString(),

                                    "Device number" to
                                            device.deviceNumber,

                                    "Device type" to
                                            device.deviceType,

                                    "Device identifier" to
                                            device.deviceIdentifier,

                                    "Identifier type" to
                                            device.identifierType,

                                    "IMEI number" to
                                            device.imeiNumber,

                                    "Assigned agent" to
                                            assignedAgent?.fullName,

                                    "Assigned agent ID" to
                                            device.assignedAgentId
                                                ?.toString(),

                                    "Operating system version" to
                                            device.osVersion,

                                    "Status" to
                                            device.status,

                                    "Assigned date" to
                                            device.assignedDate,

                                    "Last seen" to
                                            device.lastSeenAt
                                )
                        )
                    }


                SystemCategory.SESSIONS ->

                    sessions.map { session ->

                        val agent =
                            agents.firstOrNull {
                                it.agentId ==
                                        session.agentId
                            }

                        val actor =
                            when {

                                session.adminId != null ->
                                    "Administrator #${session.adminId}"

                                session.agentId != null ->
                                    agent?.fullName
                                        ?: "Agent #${session.agentId}"

                                else ->
                                    "Unknown user"
                            }

                        SystemRecord(
                            key =
                                "session-${session.sessionId}",

                            title =
                                "Session #${session.sessionId}",

                            subtitle =
                                actor,

                            status =
                                session.status
                                    ?: "UNKNOWN",

                            fields =
                                listOf(
                                    "Session ID" to
                                            session.sessionId
                                                ?.toString(),

                                    "User" to
                                            actor,

                                    "Administrator ID" to
                                            session.adminId
                                                ?.toString(),

                                    "Agent ID" to
                                            session.agentId
                                                ?.toString(),

                                    "Device ID" to
                                            session.deviceId
                                                ?.toString(),

                                    "Login time" to
                                            session.loginTime,

                                    "Logout time" to
                                            session.logoutTime,

                                    "Status" to
                                            session.status
                                )
                        )
                    }


                null ->
                    emptyList()
            }
        }

    val statusOptions =
        listOf("ALL") +
                allRecords
                    .map {
                        it.status
                            .uppercase(
                                Locale.ROOT
                            )
                    }
                    .distinct()
                    .sorted()

    val filteredRecords =
        allRecords.filter { record ->

            val statusMatches =
                statusFilter == "ALL" ||
                        record.status.equals(
                            statusFilter,
                            ignoreCase = true
                        )

            val query =
                search.trim()

            val textMatches =
                query.isBlank() ||
                        record.title.contains(
                            query,
                            true
                        ) ||
                        record.subtitle.contains(
                            query,
                            true
                        ) ||
                        record.fields.any { pair ->

                            pair.first.contains(
                                query,
                                true
                            ) ||
                                    pair.second
                                        .orEmpty()
                                        .contains(
                                            query,
                                            true
                                        )
                        }

            statusMatches &&
                    textMatches
        }

    val activeDevices =
        devices.count {
            it.status.equals(
                "ACTIVE",
                true
            )
        }

    val activeSessions =
        sessions.count {
            it.status.equals(
                "ACTIVE",
                true
            )
        }

    val analysisEnabledTypes =
        types.count {
            it.analysisEnabled == true
        }

    Box(
        modifier =
            Modifier
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

        SystemPremiumBackground()


        when (page) {

            SystemPage.OVERVIEW -> {

                SystemOverviewPage(
                    customers =
                        customers,

                    types =
                        types,

                    devices =
                        devices,

                    sessions =
                        sessions,

                    activeDevices =
                        activeDevices,

                    activeSessions =
                        activeSessions,

                    analysisEnabledTypes =
                        analysisEnabledTypes,

                    loading =
                        loading,

                    error =
                        error,

                    onRefresh = {

                        if (!loading) {

                            scope.launch {

                                try {

                                    refresh()

                                } catch (e: CancellationException) {

                                    throw e

                                } catch (e: Exception) {

                                    error =
                                        e.message
                                            ?: "Refresh failed"
                                }
                            }
                        }
                    },

                    onCategoryOpen = { selected ->

                        category =
                            selected

                        selectedRecord =
                            null

                        search =
                            ""

                        statusFilter =
                            "ALL"

                        page =
                            SystemPage.CATEGORY
                    }
                )
            }


            SystemPage.CATEGORY -> {

                SystemCategoryPage(
                    category =
                        category
                            ?: SystemCategory.CUSTOMERS,

                    allRecords =
                        allRecords,

                    filteredRecords =
                        filteredRecords,

                    statusOptions =
                        statusOptions,

                    search =
                        search,

                    statusFilter =
                        statusFilter,

                    loading =
                        loading,

                    error =
                        error,

                    onSearchChange = {
                        search = it
                    },

                    onStatusChange = {
                        statusFilter = it
                    },

                    onRecordOpen = { record ->

                        selectedRecord =
                            record

                        page =
                            SystemPage.DETAILS
                    }
                )
            }


            SystemPage.DETAILS -> {

                selectedRecord?.let { record ->

                    SystemDetailsPage(
                        category =
                            category
                                ?: SystemCategory.CUSTOMERS,

                        record =
                            record
                    )
                }
            }
        }
    }
}


// =====================================================================
// BACKGROUND
// =====================================================================

@Composable
private fun SystemPremiumBackground() {

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

                moveTo(
                    0f,
                    0f
                )

                lineTo(
                    w * 0.72f,
                    0f
                )

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
                                .copy(
                                    alpha =
                                        0.58f
                                ),

                            Color(0xFFFFE8DE)
                                .copy(
                                    alpha =
                                        0.24f
                                ),

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
                    .copy(
                        alpha =
                            0.70f
                    ),

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
                                .copy(
                                    alpha =
                                        0.08f
                                ),

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
}


// =====================================================================
// OVERVIEW PAGE
// =====================================================================

@Composable
private fun SystemOverviewPage(
    customers: List<Customer>,
    types: List<DocumentType>,
    devices: List<Device>,
    sessions: List<Session>,
    activeDevices: Int,
    activeSessions: Int,
    analysisEnabledTypes: Int,
    loading: Boolean,
    error: String,
    onRefresh: () -> Unit,
    onCategoryOpen: (SystemCategory) -> Unit
) {

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal =
                        22.dp
                ),

        contentPadding =
            PaddingValues(
                bottom =
                    28.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {

        item {

            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
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
                    Modifier.height(
                        4.dp
                    )
            )


            Text(
                text =
                    "System records and operational information.",

                fontSize =
                    12.sp,

                color =
                    Color(0xFF817A8B)
            )
        }


        item {

            SystemOverviewHero(
                customers =
                    customers.size,

                documentTypes =
                    types.size,

                devices =
                    devices.size,

                sessions =
                    sessions.size
            )
        }


        if (loading) {

            item {

                SystemLoadingCard(
                    text =
                        "Loading system data..."
                )
            }
        }


        if (error.isNotBlank()) {

            item {

                SystemMessageCard(
                    message =
                        error,

                    error =
                        true
                )
            }
        }


        item {

            SystemMiniAction(
                text =
                    "REFRESH DATA",

                enabled =
                    !loading,

                onClick =
                    onRefresh
            )
        }


        item {

            Text(
                text =
                    "Operational Summary",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF1B1D30)
            )
        }


        item {

            SystemOperationalCard(
                activeDevices =
                    activeDevices,

                activeSessions =
                    activeSessions,

                analysisEnabled =
                    analysisEnabledTypes
            )
        }


        item {

            Text(
                text =
                    "Document Type Configuration",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF1B1D30)
            )
        }


        item {

            SystemConfigurationCard(
                total =
                    types.size,

                enabled =
                    analysisEnabledTypes
            )
        }


        item {

            Text(
                text =
                    "Browse Categories",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF1B1D30)
            )
        }


        SystemCategory.values()
            .forEach { selected ->

                val count =
                    when (selected) {

                        SystemCategory.CUSTOMERS ->
                            customers.size

                        SystemCategory.DOCUMENT_TYPES ->
                            types.size

                        SystemCategory.DEVICES ->
                            devices.size

                        SystemCategory.SESSIONS ->
                            sessions.size
                    }


                item(
                    key =
                        selected.name
                ) {

                    SystemNavigationCardPremium(
                        category =
                            selected,

                        count =
                            count,

                        onClick = {
                            onCategoryOpen(
                                selected
                            )
                        }
                    )
                }
            }
    }
}


// =====================================================================
// CATEGORY PAGE
// =====================================================================

@Composable
private fun SystemCategoryPage(
    category: SystemCategory,
    allRecords: List<SystemRecord>,
    filteredRecords: List<SystemRecord>,
    statusOptions: List<String>,
    search: String,
    statusFilter: String,
    loading: Boolean,
    error: String,
    onSearchChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onRecordOpen: (SystemRecord) -> Unit
) {

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal =
                        22.dp
                ),

        contentPadding =
            PaddingValues(
                bottom =
                    28.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {

        item {

            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            Text(
                text =
                    category.title,

                fontSize =
                    27.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        4.dp
                    )
            )


            Text(
                text =
                    category.description,

                fontSize =
                    12.sp,

                lineHeight =
                    17.sp,

                color =
                    Color(0xFF817A8B)
            )
        }


        item {

            SystemCategoryHero(
                category =
                    category,

                total =
                    allRecords.size,

                filtered =
                    filteredRecords.size
            )
        }


        if (loading) {

            item {

                SystemLoadingCard(
                    text =
                        "Loading records..."
                )
            }
        }


        if (error.isNotBlank()) {

            item {

                SystemMessageCard(
                    message =
                        error,

                    error =
                        true
                )
            }
        }


        item {

            OutlinedTextField(
                value =
                    search,

                onValueChange =
                    onSearchChange,

                label = {
                    Text(
                        "Search records"
                    )
                },

                placeholder = {
                    Text(
                        "Name, ID, status or other value"
                    )
                },

                singleLine =
                    true,

                modifier =
                    Modifier.fillMaxWidth()
            )
        }


        item {

            SystemStatusSelector(
                selected =
                    statusFilter,

                options =
                    statusOptions,

                onSelected =
                    onStatusChange
            )
        }


        item {

            SystemStatusDistributionCard(
                records =
                    allRecords
            )
        }


        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        "Records",

                    fontSize =
                        18.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF1B1D30)
                )


                Spacer(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                )


                Text(
                    text =
                        "${filteredRecords.size} OF ${allRecords.size}",

                    fontSize =
                        9.sp,

                    fontWeight =
                        FontWeight.Bold,

                    letterSpacing =
                        0.5.sp,

                    color =
                        Color(0xFF8D8695)
                )
            }
        }


        if (filteredRecords.isEmpty()) {

            item {

                SystemEmptyCard(
                    title =
                        "No matching records",

                    message =
                        "No system records match the current search and status filter."
                )
            }

        } else {

            items(
                items =
                    filteredRecords,

                key = {
                    it.key
                }
            ) { record ->

                SystemRecordCardPremium(
                    category =
                        category,

                    record =
                        record,

                    onClick = {
                        onRecordOpen(
                            record
                        )
                    }
                )
            }
        }
    }
}


// =====================================================================
// DETAILS PAGE
// =====================================================================

@Composable
private fun SystemDetailsPage(
    category: SystemCategory,
    record: SystemRecord
) {

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal =
                        22.dp
                ),

        contentPadding =
            PaddingValues(
                bottom =
                    30.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {

        item {

            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            Text(
                text =
                    "System Record",

                fontSize =
                    27.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        4.dp
                    )
            )


            Text(
                text =
                    category.title,

                fontSize =
                    12.sp,

                color =
                    Color(0xFF817A8B)
            )
        }


        item {

            SystemRecordHero(
                category =
                    category,

                record =
                    record
            )
        }


        item {

            PremiumSystemDetailSection(
                title =
                    "${category.title} — Full Details",

                fields =
                    record.fields
            )
        }
    }
}


// =====================================================================
// HERO
// =====================================================================

@Composable
private fun SystemOverviewHero(
    customers: Int,
    documentTypes: Int,
    devices: Int,
    sessions: Int
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        24.dp
                    )
                )
                .background(
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                Color(0xFF6553BD),
                                Color(0xFF994FC1),
                                Color(0xFFD34D92)
                            )
                    )
                )
                .padding(
                    20.dp
                )
    ) {

        Column {

            Text(
                text =
                    "Database Overview",

                fontSize =
                    12.sp,

                color =
                    Color.White.copy(
                        alpha =
                            0.84f
                    )
            )


            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )


            Text(
                text =
                    "${customers + documentTypes + devices + sessions} System Records",

                fontSize =
                    22.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color.White
            )


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {

                SystemOverviewPill(
                    label =
                        "CUSTOMERS",

                    value =
                        customers.toString()
                )


                SystemOverviewPill(
                    label =
                        "TYPES",

                    value =
                        documentTypes.toString()
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {

                SystemOverviewPill(
                    label =
                        "DEVICES",

                    value =
                        devices.toString()
                )


                SystemOverviewPill(
                    label =
                        "SESSIONS",

                    value =
                        sessions.toString()
                )
            }
        }
    }
}


@Composable
private fun SystemCategoryHero(
    category: SystemCategory,
    total: Int,
    filtered: Int
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        22.dp
                    )
                )
                .background(
                    Brush.horizontalGradient(
                        colors =
                            categoryGradient(
                                category
                            )
                    )
                )
                .padding(
                    18.dp
                )
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
                        .size(
                            52.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                16.dp
                            )
                        )
                        .background(
                            Color.White.copy(
                                alpha =
                                    0.16f
                            )
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        categoryCode(
                            category
                        ),

                    fontSize =
                        10.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color.White
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        12.dp
                    )
            )


            Column(
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {

                Text(
                    text =
                        "$total Record${if (total == 1) "" else "s"}",

                    fontSize =
                        19.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            3.dp
                        )
                )


                Text(
                    text =
                        "$filtered currently shown",

                    fontSize =
                        10.sp,

                    color =
                        Color.White.copy(
                            alpha =
                                0.82f
                        )
                )
            }
        }
    }
}


@Composable
private fun SystemRecordHero(
    category: SystemCategory,
    record: SystemRecord
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        22.dp
                    )
                )
                .background(
                    Brush.horizontalGradient(
                        colors =
                            categoryGradient(
                                category
                            )
                    )
                )
                .padding(
                    18.dp
                )
    ) {

        Column {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier
                            .size(
                                50.dp
                            )
                            .clip(
                                RoundedCornerShape(
                                    15.dp
                                )
                            )
                            .background(
                                Color.White.copy(
                                    alpha =
                                        0.16f
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            categoryCode(
                                category
                            ),

                        fontSize =
                            9.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color.White
                    )
                }


                Spacer(
                    modifier =
                        Modifier.width(
                            12.dp
                        )
                )


                Column(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    Text(
                        text =
                            record.title,

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color.White,

                        maxLines =
                            1,

                        overflow =
                            TextOverflow.Ellipsis
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                3.dp
                            )
                    )


                    Text(
                        text =
                            record.subtitle,

                        fontSize =
                            10.sp,

                        color =
                            Color.White.copy(
                                alpha =
                                    0.82f
                            ),

                        maxLines =
                            2,

                        overflow =
                            TextOverflow.Ellipsis
                    )
                }


                SystemStatusBadge(
                    status =
                        record.status,

                    inverted =
                        true
                )
            }
        }
    }
}


// =====================================================================
// OVERVIEW CARDS
// =====================================================================

@Composable
private fun SystemOperationalCard(
    activeDevices: Int,
    activeSessions: Int,
    analysisEnabled: Int
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        21.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.97f
                    )
                )
                .padding(
                    16.dp
                )
    ) {

        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {

            SystemInfoRow(
                label =
                    "Active devices",

                value =
                    activeDevices.toString()
            )


            SystemInfoRow(
                label =
                    "Active sessions",

                value =
                    activeSessions.toString()
            )


            SystemInfoRow(
                label =
                    "Analysis-enabled document types",

                value =
                    analysisEnabled.toString()
            )
        }
    }
}


@Composable
private fun SystemConfigurationCard(
    total: Int,
    enabled: Int
) {

    val maximum =
        total.coerceAtLeast(
            1
        )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        21.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.97f
                    )
                )
                .padding(
                    16.dp
                )
    ) {

        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            PremiumSystemBar(
                label =
                    "Analysis enabled",

                count =
                    enabled,

                maximum =
                    maximum
            )


            PremiumSystemBar(
                label =
                    "Analysis disabled / not configured",

                count =
                    total - enabled,

                maximum =
                    maximum
            )
        }
    }
}


// =====================================================================
// NAVIGATION + RECORD CARDS
// =====================================================================

@Composable
private fun SystemNavigationCardPremium(
    category: SystemCategory,
    count: Int,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        21.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.97f
                    )
                )
                .clickable {
                    onClick()
                }
                .padding(
                    16.dp
                )
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
                        .size(
                            48.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                15.dp
                            )
                        )
                        .background(
                            Brush.linearGradient(
                                colors =
                                    categoryGradient(
                                        category
                                    )
                            )
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        categoryCode(
                            category
                        ),

                    fontSize =
                        9.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color.White
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        13.dp
                    )
            )


            Column(
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {

                Text(
                    text =
                        category.title,

                    fontSize =
                        14.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF171A2C)
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            3.dp
                        )
                )


                Text(
                    text =
                        category.description,

                    fontSize =
                        10.sp,

                    lineHeight =
                        14.sp,

                    color =
                        Color(0xFF817A8B),

                    maxLines =
                        2,

                    overflow =
                        TextOverflow.Ellipsis
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            6.dp
                        )
                )


                Text(
                    text =
                        "$count record${if (count == 1) "" else "s"}",

                    fontSize =
                        9.sp,

                    fontWeight =
                        FontWeight.SemiBold,

                    color =
                        Color(0xFF7650BD)
                )
            }


            Text(
                text =
                    "›",

                fontSize =
                    24.sp,

                fontWeight =
                    FontWeight.SemiBold,

                color =
                    Color(0xFFB2A9BC)
            )
        }
    }
}


@Composable
private fun SystemRecordCardPremium(
    category: SystemCategory,
    record: SystemRecord,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        20.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.97f
                    )
                )
                .clickable {
                    onClick()
                }
                .padding(
                    15.dp
                )
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
                        .size(
                            44.dp
                        )
                        .clip(
                            CircleShape
                        )
                        .background(
                            Brush.linearGradient(
                                colors =
                                    categoryGradient(
                                        category
                                    )
                            )
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        categoryCode(
                            category
                        ),

                    fontSize =
                        8.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color.White
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        12.dp
                    )
            )


            Column(
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {

                Text(
                    text =
                        record.title,

                    fontSize =
                        13.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF171A2C),

                    maxLines =
                        1,

                    overflow =
                        TextOverflow.Ellipsis
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            3.dp
                        )
                )


                Text(
                    text =
                        record.subtitle,

                    fontSize =
                        9.sp,

                    color =
                        Color(0xFF817A8B),

                    maxLines =
                        2,

                    overflow =
                        TextOverflow.Ellipsis
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        8.dp
                    )
            )


            SystemStatusBadge(
                status =
                    record.status
            )
        }
    }
}


// =====================================================================
// STATUS SELECTOR + DISTRIBUTION
// =====================================================================

@Composable
private fun SystemStatusSelector(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {

    var expanded by remember {
        mutableStateOf(false)
    }


    Box(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            15.dp
                        )
                    )
                    .background(
                        Color.White.copy(
                            alpha =
                                0.97f
                        )
                    )
                    .clickable {
                        expanded = true
                    }
                    .padding(
                        horizontal =
                            14.dp,

                        vertical =
                            13.dp
                    )
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        "STATUS",

                    fontSize =
                        8.sp,

                    fontWeight =
                        FontWeight.Bold,

                    letterSpacing =
                        0.6.sp,

                    color =
                        Color(0xFF9A92A3)
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            10.dp
                        )
                )


                Text(
                    text =
                        selected,

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    fontSize =
                        11.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF6F4BB8)
                )


                Text(
                    text =
                        "▼",

                    fontSize =
                        9.sp,

                    color =
                        Color(0xFF8D8695)
                )
            }
        }


        DropdownMenu(
            expanded =
                expanded,

            onDismissRequest = {
                expanded = false
            }
        ) {

            options.forEach { status ->

                DropdownMenuItem(
                    text = {
                        Text(
                            status
                        )
                    },

                    onClick = {

                        onSelected(
                            status
                        )

                        expanded = false
                    }
                )
            }
        }
    }
}


@Composable
private fun SystemStatusDistributionCard(
    records: List<SystemRecord>
) {

    val groups =
        records.groupBy {
            it.status.uppercase(
                Locale.ROOT
            )
        }


    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        21.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.97f
                    )
                )
                .padding(
                    16.dp
                )
    ) {

        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {

            Text(
                text =
                    "Status Distribution",

                fontSize =
                    14.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            if (groups.isEmpty()) {

                Text(
                    text =
                        "No data available",

                    fontSize =
                        10.sp,

                    color =
                        Color(0xFF817A8B)
                )

            } else {

                groups
                    .toSortedMap()
                    .forEach { pair ->

                        PremiumSystemBar(
                            label =
                                pair.key,

                            count =
                                pair.value.size,

                            maximum =
                                records.size
                                    .coerceAtLeast(
                                        1
                                    )
                        )
                    }
            }
        }
    }
}


// =====================================================================
// DETAILS
// =====================================================================

@Composable
private fun PremiumSystemDetailSection(
    title: String,
    fields: List<Pair<String, String?>>
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        21.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.97f
                    )
                )
                .padding(
                    16.dp
                )
    ) {

        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {

            Text(
                text =
                    title,

                fontSize =
                    15.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            fields.forEach { pair ->

                SystemDetailRow(
                    label =
                        pair.first,

                    value =
                        pair.second
                            .orEmpty()
                            .ifBlank {
                                "Not recorded"
                            }
                )
            }
        }
    }
}


@Composable
private fun SystemDetailRow(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        13.dp
                    )
                )
                .background(
                    Color(0xFFF8F5FB)
                )
                .padding(
                    horizontal =
                        12.dp,

                    vertical =
                        10.dp
                ),

        verticalAlignment =
            Alignment.Top
    ) {

        Text(
            text =
                label,

            modifier =
                Modifier.weight(
                    0.42f
                ),

            fontSize =
                9.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color(0xFF918A9B)
        )


        Spacer(
            modifier =
                Modifier.width(
                    8.dp
                )
        )


        Text(
            text =
                value,

            modifier =
                Modifier.weight(
                    0.58f
                ),

            fontSize =
                10.sp,

            color =
                Color(0xFF3F394A)
        )
    }
}


// =====================================================================
// SMALL COMPONENTS
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
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.15f
                    )
                )
                .padding(
                    horizontal =
                        10.dp,

                    vertical =
                        8.dp
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
                Modifier.width(
                    5.dp
                )
        )


        Text(
            text =
                label,

            fontSize =
                7.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color.White.copy(
                    alpha =
                        0.82f
                )
        )
    }
}


@Composable
private fun SystemInfoRow(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        13.dp
                    )
                )
                .background(
                    Color(0xFFF8F5FB)
                )
                .padding(
                    horizontal =
                        12.dp,

                    vertical =
                        10.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text =
                label,

            modifier =
                Modifier.weight(
                    1f
                ),

            fontSize =
                10.sp,

            color =
                Color(0xFF5F5968)
        )


        Text(
            text =
                value,

            fontSize =
                13.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                Color(0xFF6F4BB8)
        )
    }
}


@Composable
private fun PremiumSystemBar(
    label: String,
    count: Int,
    maximum: Int
) {

    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                5.dp
            )
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text =
                    label,

                modifier =
                    Modifier.weight(
                        1f
                    ),

                fontSize =
                    10.sp,

                color =
                    Color(0xFF5F5968),

                maxLines =
                    1,

                overflow =
                    TextOverflow.Ellipsis
            )


            Text(
                text =
                    count.toString(),

                fontSize =
                    10.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF7650BD)
            )
        }


        LinearProgressIndicator(
            progress = {
                (
                        count.toFloat() /
                                maximum.coerceAtLeast(
                                    1
                                )
                        )
                    .coerceIn(
                        0f,
                        1f
                    )
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            50.dp
                        )
                    )
        )
    }
}


@Composable
private fun SystemStatusBadge(
    status: String,
    inverted: Boolean = false
) {

    val normalized =
        status.uppercase(
            Locale.ROOT
        )

    val good =
        normalized in setOf(
            "ACTIVE",
            "REGISTERED",
            "VERIFIED",
            "COMPLETED",
            "PASSED"
        )

    val bad =
        normalized.contains(
            "DISABLED"
        ) ||
                normalized.contains(
                    "FAILED"
                ) ||
                normalized.contains(
                    "ERROR"
                ) ||
                normalized.contains(
                    "REJECT"
                )

    val background =
        if (inverted) {

            Color.White.copy(
                alpha =
                    0.16f
            )

        } else {

            when {

                good ->
                    Color(0xFFEAF7EF)

                bad ->
                    Color(0xFFFFEDEF)

                else ->
                    Color(0xFFF3EEF8)
            }
        }

    val foreground =
        if (inverted) {

            Color.White

        } else {

            when {

                good ->
                    Color(0xFF247148)

                bad ->
                    Color(0xFFB54050)

                else ->
                    Color(0xFF7650BD)
            }
        }


    Box(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(
                        50.dp
                    )
                )
                .background(
                    background
                )
                .padding(
                    horizontal =
                        9.dp,

                    vertical =
                        6.dp
                )
    ) {

        Text(
            text =
                normalized,

            fontSize =
                7.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                foreground,

            maxLines =
                1
        )
    }
}


@Composable
private fun SystemMiniAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        15.dp
                    )
                )
                .background(
                    if (enabled) {

                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color(0xFFFF7B4D),
                                    Color(0xFFF04D8E),
                                    Color(0xFF8C4DD1)
                                )
                        )

                    } else {

                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color(0xFFD8D0DF),
                                    Color(0xFFD8D0DF)
                                )
                        )
                    }
                )
                .clickable(
                    enabled =
                        enabled,

                    onClick =
                        onClick
                )
                .padding(
                    vertical =
                        12.dp
                ),

        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                text,

            fontSize =
                9.sp,

            fontWeight =
                FontWeight.Bold,

            letterSpacing =
                0.5.sp,

            color =
                Color.White
        )
    }
}


@Composable
private fun SystemLoadingCard(
    text: String
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        17.dp
                    )
                )
                .background(
                    Color(0xFFF4EFFA)
                )
                .padding(
                    14.dp
                )
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            CircularProgressIndicator(
                modifier =
                    Modifier.size(
                        20.dp
                    ),

                strokeWidth =
                    2.dp,

                color =
                    Color(0xFF7650BD)
            )


            Spacer(
                modifier =
                    Modifier.width(
                        10.dp
                    )
            )


            Text(
                text =
                    text,

                fontSize =
                    10.sp,

                color =
                    Color(0xFF6C5B7A)
            )
        }
    }
}


@Composable
private fun SystemMessageCard(
    message: String,
    error: Boolean
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        16.dp
                    )
                )
                .background(
                    if (error) {
                        Color(0xFFFFEEF1)
                    } else {
                        Color(0xFFF3EEF9)
                    }
                )
                .padding(
                    13.dp
                )
    ) {

        Text(
            text =
                message,

            fontSize =
                11.sp,

            lineHeight =
                16.sp,

            color =
                if (error) {
                    Color(0xFFA13A4B)
                } else {
                    Color(0xFF6F4BB8)
                }
        )
    }
}


@Composable
private fun SystemEmptyCard(
    title: String,
    message: String
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        20.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.97f
                    )
                )
                .padding(
                    18.dp
                )
    ) {

        Column {

            Text(
                text =
                    title,

                fontSize =
                    14.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )


            Text(
                text =
                    message,

                fontSize =
                    11.sp,

                lineHeight =
                    16.sp,

                color =
                    Color(0xFF817A8B)
            )
        }
    }
}


// =====================================================================
// HELPERS
// =====================================================================

private fun categoryGradient(
    category: SystemCategory
): List<Color> {

    return when (category) {

        SystemCategory.CUSTOMERS ->
            listOf(
                Color(0xFF7650BD),
                Color(0xFFD14D92)
            )

        SystemCategory.DOCUMENT_TYPES ->
            listOf(
                Color(0xFFEE7656),
                Color(0xFFF0528D)
            )

        SystemCategory.DEVICES ->
            listOf(
                Color(0xFF45876B),
                Color(0xFF6EAF89)
            )

        SystemCategory.SESSIONS ->
            listOf(
                Color(0xFF6557C8),
                Color(0xFF9C4FD0)
            )
    }
}


private fun categoryCode(
    category: SystemCategory
): String {

    return when (category) {

        SystemCategory.CUSTOMERS ->
            "CUS"

        SystemCategory.DOCUMENT_TYPES ->
            "DOC"

        SystemCategory.DEVICES ->
            "DEV"

        SystemCategory.SESSIONS ->
            "SES"
    }
}


private fun yesNo(
    value: Boolean?
): String =
    when (value) {

        true ->
            "Yes"

        false ->
            "No"

        null ->
            "Not recorded"
    }


private fun <T> Response<T>.systemBody(): T {

    if (!isSuccessful) {

        error(
            "System API returned HTTP ${code()}"
        )
    }

    return requireNotNull(
        body()
    ) {
        "System API returned an empty response"
    }
}
