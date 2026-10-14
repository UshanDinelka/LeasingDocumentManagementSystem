package com.leasingdocument.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import com.leasingdocument.app.network.Agent
import com.leasingdocument.app.network.AuditLog
import com.leasingdocument.app.network.AuthSession
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AuditPage {
    OVERVIEW,
    CATEGORY,
    DETAILS
}

private enum class AuditCategory(
    val title: String,
    val description: String
) {
    AUTHENTICATION(
        "Authentication & Sessions",
        "Logins, logouts and session activity"
    ),

    DOCUMENTS(
        "Document Operations",
        "Document capture, submission and file activity"
    ),

    INTEGRITY(
        "Integrity & Security",
        "Hash verification, encryption and security events"
    ),

    ALTERATION(
        "Alteration Analysis",
        "OCR analysis, screening and manual review activity"
    ),

    ADMINISTRATION(
        "Administration",
        "Agent, customer, device and system management"
    ),

    OTHER(
        "Other Events",
        "Other recorded system actions"
    )
}

private fun categoryFor(
    action: String?
): AuditCategory {

    val value =
        action
            .orEmpty()
            .uppercase(Locale.ROOT)

    return when {

        listOf(
            "LOGIN",
            "LOGOUT",
            "SESSION",
            "AUTH",
            "SIGN_IN",
            "SIGN_OUT"
        ).any {
            it in value
        } ->
            AuditCategory.AUTHENTICATION

        listOf(
            "ALTERATION",
            "ANALYSIS",
            "OCR",
            "MANUAL_REVIEW"
        ).any {
            it in value
        } ->
            AuditCategory.ALTERATION

        listOf(
            "INTEGRITY",
            "HASH",
            "ENCRYPT",
            "DECRYPT",
            "TAMPER",
            "SECURITY"
        ).any {
            it in value
        } ->
            AuditCategory.INTEGRITY

        listOf(
            "DOCUMENT",
            "UPLOAD",
            "CAPTURE",
            "DOWNLOAD",
            "FILE"
        ).any {
            it in value
        } ->
            AuditCategory.DOCUMENTS

        listOf(
            "ADMIN",
            "AGENT",
            "CUSTOMER",
            "DEVICE",
            "REGISTER",
            "USER",
            "CONFIG"
        ).any {
            it in value
        } ->
            AuditCategory.ADMINISTRATION

        else ->
            AuditCategory.OTHER
    }
}

private fun failedLog(
    log: AuditLog
): Boolean {

    val status =
        log.eventStatus
            .orEmpty()
            .uppercase(Locale.ROOT)

    return status.contains("FAIL") ||
            status.contains("ERROR") ||
            status.contains("DENIED") ||
            status.contains("REJECT")
}

private fun statusDisplay(
    status: String?
): String {

    return status
        ?.replace(
            '_',
            ' '
        )
        ?.takeIf {
            it.isNotBlank()
        }
        ?: "UNKNOWN"
}

private fun displayTime(
    timestamp: String?
): String {

    if (timestamp.isNullOrBlank()) {
        return "Not recorded"
    }

    return timestamp
        .replace(
            'T',
            ' '
        )
        .substringBefore('.')
}

private fun auditDate(
    timestamp: String?
): String {

    val value =
        timestamp.orEmpty()

    return if (value.length >= 10) {
        value.substring(
            0,
            10
        )
    } else {
        "Unknown"
    }
}

@Composable
fun AuditLogsScreen() {

    if (AuthSession.role != "ADMIN") {

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Color(0xFFFFF8FB)
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    "Administrator access required.",

                color =
                    Color(0xFFA13A4B),

                fontWeight =
                    FontWeight.Bold
            )
        }

        return
    }


    var logs by remember {
        mutableStateOf<List<AuditLog>>(emptyList())
    }

    var agents by remember {
        mutableStateOf<List<Agent>>(emptyList())
    }

    var page by remember {
        mutableStateOf(AuditPage.OVERVIEW)
    }

    var selectedCategory by remember {
        mutableStateOf<AuditCategory?>(null)
    }

    var selectedLog by remember {
        mutableStateOf<AuditLog?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf("")
    }

    var search by remember {
        mutableStateOf("")
    }

    var selectedStatus by remember {
        mutableStateOf("ALL")
    }


    suspend fun loadLogs() {

        loading = true
        error = ""

        try {

            val api =
                RetrofitClient.apiService

            val logResponse =
                api.getAuditLogs()

            val agentResponse =
                api.getAgents()

            if (logResponse.isSuccessful) {

                logs =
                    logResponse
                        .body()
                        .orEmpty()
                        .sortedWith(
                            compareByDescending<AuditLog> {
                                it.createdAt.orEmpty()
                            }
                                .thenByDescending {
                                    it.logId ?: 0L
                                }
                        )

                agents =
                    if (agentResponse.isSuccessful) {
                        agentResponse.body()
                            .orEmpty()
                    } else {
                        emptyList()
                    }

            } else {

                error =
                    "Failed to load logs (HTTP ${logResponse.code()})"
            }

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            error =
                e.message
                    ?: "Cannot connect to server"

        } finally {

            loading = false
        }
    }


    LaunchedEffect(Unit) {
        loadLogs()
    }


    fun goBack() {

        when (page) {

            AuditPage.DETAILS -> {

                selectedLog = null
                page = AuditPage.CATEGORY
            }

            AuditPage.CATEGORY -> {

                selectedCategory = null
                search = ""
                selectedStatus = "ALL"
                page = AuditPage.OVERVIEW
            }

            AuditPage.OVERVIEW ->
                Unit
        }
    }


    BackHandler(
        enabled =
            page != AuditPage.OVERVIEW
    ) {
        goBack()
    }


    val today =
        LocalDate.now()
            .toString()

    val failedCount =
        logs.count(
            ::failedLog
        )

    val todayCount =
        logs.count {
            auditDate(
                it.createdAt
            ) == today
        }

    val categoryLogs =
        logs.filter {
            categoryFor(
                it.action
            ) == selectedCategory
        }

    val filteredLogs =
        categoryLogs.filter { log ->

            val matchesStatus =
                when (selectedStatus) {

                    "FAILED" ->
                        failedLog(log)

                    "OTHER" ->
                        !failedLog(log)

                    else ->
                        true
                }


            val query =
                search.trim()


            val matchesSearch =
                query.isBlank() ||
                        listOf(
                            log.action,
                            log.description,
                            log.eventStatus,
                            log.adminId?.toString(),
                            log.agentId?.toString(),
                            log.documentId?.toString(),
                            log.deviceId?.toString(),
                            log.ipAddress,
                            log.logId?.toString()
                        ).any {
                            it.orEmpty()
                                .contains(
                                    query,
                                    ignoreCase = true
                                )
                        }


            matchesStatus &&
                    matchesSearch
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

        AuditPremiumBackground()


        when (page) {

            AuditPage.OVERVIEW -> {

                AuditOverviewPage(
                    logs =
                        logs,

                    failedCount =
                        failedCount,

                    todayCount =
                        todayCount,

                    loading =
                        loading,

                    error =
                        error,

                    onCategoryOpen = { category ->

                        selectedCategory =
                            category

                        selectedLog =
                            null

                        search =
                            ""

                        selectedStatus =
                            "ALL"

                        page =
                            AuditPage.CATEGORY
                    }
                )
            }


            AuditPage.CATEGORY -> {

                AuditCategoryPage(
                    category =
                        selectedCategory
                            ?: AuditCategory.OTHER,

                    categoryLogs =
                        categoryLogs,

                    filteredLogs =
                        filteredLogs,

                    search =
                        search,

                    selectedStatus =
                        selectedStatus,

                    agents =
                        agents,

                    loading =
                        loading,

                    error =
                        error,

                    onSearchChange = {
                        search = it
                    },

                    onStatusChange = {
                        selectedStatus = it
                    },

                    onBack = {
                        goBack()
                    },

                    onLogOpen = { log ->

                        selectedLog =
                            log

                        page =
                            AuditPage.DETAILS
                    }
                )
            }


            AuditPage.DETAILS -> {

                selectedLog?.let { log ->

                    AuditDetailsPage(
                        log =
                            log,

                        agents =
                            agents,

                        onBack = {
                            goBack()
                        }
                    )
                }
            }
        }
    }
}


// =====================================================================
// PREMIUM BACKGROUND
// =====================================================================

@Composable
private fun AuditPremiumBackground() {

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
private fun AuditOverviewPage(
    logs: List<AuditLog>,
    failedCount: Int,
    todayCount: Int,
    loading: Boolean,
    error: String,
    onCategoryOpen: (AuditCategory) -> Unit
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
                    "Audit Logs",

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
                    "Review security, document and account activity.",

                fontSize =
                    12.sp,

                color =
                    Color(0xFF817A8B)
            )
        }


        item {

            AuditHeroCard(
                total =
                    logs.size,

                failed =
                    failedCount,

                today =
                    todayCount
            )
        }


        if (loading) {

            item {

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
                                Color(0xFFF4EFFA)
                            )
                            .padding(
                                14.dp
                            )
                ) {

                    Column {

                        LinearProgressIndicator(
                            modifier =
                                Modifier.fillMaxWidth()
                        )


                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )


                        Text(
                            text =
                                "Loading audit activity...",

                            fontSize =
                                11.sp,

                            color =
                                Color(0xFF6C5B7A)
                        )
                    }
                }
            }
        }


        if (error.isNotBlank()) {

            item {

                AuditStatusCard(
                    title =
                        "Unable to load audit logs",

                    message =
                        error,

                    background =
                        Color(0xFFFFEEF1),

                    titleColor =
                        Color(0xFFA13A4B)
                )
            }
        }


        if (
            !loading &&
            error.isBlank()
        ) {

            item {

                Text(
                    text =
                        "Activity by Category",

                    fontSize =
                        18.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF1B1D30)
                )
            }


            item {

                AuditCategoryChart(
                    logs =
                        logs
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


            AuditCategory.values()
                .forEach { category ->

                    val categoryItems =
                        logs.filter {
                            categoryFor(
                                it.action
                            ) == category
                        }

                    val failures =
                        categoryItems.count(
                            ::failedLog
                        )

                    item(
                        key =
                            category.name
                    ) {

                        PremiumCategoryCard(
                            category =
                                category,

                            count =
                                categoryItems.size,

                            failed =
                                failures,

                            onClick = {
                                onCategoryOpen(
                                    category
                                )
                            }
                        )
                    }
                }
        }
    }
}


// =====================================================================
// CATEGORY PAGE
// =====================================================================

@Composable
private fun AuditCategoryPage(
    category: AuditCategory,
    categoryLogs: List<AuditLog>,
    filteredLogs: List<AuditLog>,
    search: String,
    selectedStatus: String,
    agents: List<Agent>,
    loading: Boolean,
    error: String,
    onSearchChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onBack: () -> Unit,
    onLogOpen: (AuditLog) -> Unit
) {

    val groupedByDate =
        filteredLogs.groupBy {
            auditDate(
                it.createdAt
            )
        }


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
                        14.dp
                    )
            )


            Text(
                text =
                    category.title,

                fontSize =
                    25.sp,

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

            PremiumCategorySummaryCard(
                category =
                    category,

                total =
                    categoryLogs.size,

                failed =
                    categoryLogs.count(
                        ::failedLog
                    )
            )
        }


        if (loading) {

            item {

                LinearProgressIndicator(
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
        }


        if (error.isNotBlank()) {

            item {

                AuditStatusCard(
                    title =
                        "Unable to load audit logs",

                    message =
                        error,

                    background =
                        Color(0xFFFFEEF1),

                    titleColor =
                        Color(0xFFA13A4B)
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
                        "Search logs"
                    )
                },

                placeholder = {
                    Text(
                        "Action, description, ID or IP"
                    )
                },

                singleLine =
                    true,

                modifier =
                    Modifier.fillMaxWidth()
            )
        }


        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {

                PremiumFilterChip(
                    label =
                        "ALL",

                    selected =
                        selectedStatus == "ALL",

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    onClick = {
                        onStatusChange(
                            "ALL"
                        )
                    }
                )


                PremiumFilterChip(
                    label =
                        "FAILED",

                    selected =
                        selectedStatus == "FAILED",

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    onClick = {
                        onStatusChange(
                            "FAILED"
                        )
                    }
                )


                PremiumFilterChip(
                    label =
                        "OTHER",

                    selected =
                        selectedStatus == "OTHER",

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    onClick = {
                        onStatusChange(
                            "OTHER"
                        )
                    }
                )
            }
        }


        item {

            Text(
                text =
                    "Showing ${filteredLogs.size} of ${categoryLogs.size} events",

                fontSize =
                    10.sp,

                fontWeight =
                    FontWeight.SemiBold,

                color =
                    Color(0xFF8D8695)
            )
        }


        item {

            PremiumActivityChart(
                logs =
                    categoryLogs
            )
        }


        if (filteredLogs.isEmpty()) {

            item {

                AuditStatusCard(
                    title =
                        "No matching activity",

                    message =
                        "No audit events match the current search and filter.",

                    background =
                        Color.White,

                    titleColor =
                        Color(0xFF171A2C)
                )
            }

        } else {

            groupedByDate.forEach { (date, dateLogs) ->

                item(
                    key =
                        "date-$date"
                ) {

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            )
                    ) {

                        PremiumDateHeader(
                            date =
                                prettyAuditDate(
                                    date
                                ),

                            count =
                                dateLogs.size
                        )


                        val actorGroups =
                            dateLogs.groupBy {
                                actorKey(
                                    it
                                )
                            }


                        actorGroups.forEach { (_, actorLogs) ->

                            val first =
                                actorLogs.first()

                            PremiumActorGroup(
                                title =
                                    actorTitle(
                                        first,
                                        agents
                                    ),

                                subtitle =
                                    actorSubtitle(
                                        first
                                    ),

                                isAdmin =
                                    first.adminId != null,

                                logs =
                                    actorLogs,

                                onLogOpen =
                                    onLogOpen
                            )
                        }
                    }
                }
            }
        }
    }
}


// =====================================================================
// DETAILS PAGE
// =====================================================================

@Composable
private fun AuditDetailsPage(
    log: AuditLog,
    agents: List<Agent>,
    onBack: () -> Unit
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
                        14.dp
                    )
            )


            Text(
                text =
                    "Audit Event",

                fontSize =
                    25.sp,

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
                    "Complete recorded information for log #${log.logId ?: "-"}.",

                fontSize =
                    12.sp,

                color =
                    Color(0xFF817A8B)
            )
        }


        item {

            AuditEventHero(
                log =
                    log
            )
        }


        item {

            PremiumDetailSection(
                title =
                    "Event Information",

                rows =
                    listOf(
                        "Log ID" to
                                log.logId
                                    ?.toString(),

                        "Action" to
                                log.action,

                        "Event status" to
                                statusDisplay(
                                    log.eventStatus
                                ),

                        "Category" to
                                categoryFor(
                                    log.action
                                ).title,

                        "Date & time" to
                                displayTime(
                                    log.createdAt
                                )
                    )
            )
        }


        item {

            PremiumDetailSection(
                title =
                    "User Information",

                rows =
                    listOf(
                        "Actor" to
                                actorTitle(
                                    log,
                                    agents
                                ),

                        "Administrator ID" to
                                log.adminId
                                    ?.toString(),

                        "Agent ID" to
                                log.agentId
                                    ?.toString()
                    )
            )
        }


        item {

            PremiumDetailSection(
                title =
                    "Related Records",

                rows =
                    listOf(
                        "Document ID" to
                                log.documentId
                                    ?.toString(),

                        "Device ID" to
                                log.deviceId
                                    ?.toString()
                    )
            )
        }


        item {

            PremiumDetailSection(
                title =
                    "Network Information",

                rows =
                    listOf(
                        "IP Address" to
                                log.ipAddress
                    )
            )
        }
    }
}


// =====================================================================
// HERO CARDS
// =====================================================================

@Composable
private fun AuditHeroCard(
    total: Int,
    failed: Int,
    today: Int
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
                                Color(0xFF5F55BF),
                                Color(0xFF8D50C9),
                                Color(0xFFC94F99)
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
                    "Activity Overview",

                color =
                    Color.White.copy(
                        alpha =
                            0.84f
                    ),

                fontSize =
                    12.sp
            )


            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )


            Text(
                text =
                    "$total Audit Event${if (total == 1) "" else "s"}",

                color =
                    Color.White,

                fontSize =
                    22.sp,

                fontWeight =
                    FontWeight.Bold
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

                AuditOverviewPill(
                    label =
                        "FAILED",

                    value =
                        failed.toString()
                )


                AuditOverviewPill(
                    label =
                        "TODAY",

                    value =
                        today.toString()
                )
            }
        }
    }
}


@Composable
private fun PremiumCategorySummaryCard(
    category: AuditCategory,
    total: Int,
    failed: Int
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

            Text(
                text =
                    categoryShortCode(
                        category
                    ),

                fontSize =
                    10.sp,

                fontWeight =
                    FontWeight.Bold,

                letterSpacing =
                    0.8.sp,

                color =
                    Color.White.copy(
                        alpha =
                            0.82f
                    )
            )


            Spacer(
                modifier =
                    Modifier.height(
                        6.dp
                    )
            )


            Text(
                text =
                    "$total Recorded Event${if (total == 1) "" else "s"}",

                fontSize =
                    20.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color.White
            )


            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )


            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {

                AuditOverviewPill(
                    label =
                        "TOTAL",

                    value =
                        total.toString()
                )


                AuditOverviewPill(
                    label =
                        "FAILED",

                    value =
                        failed.toString()
                )
            }
        }
    }
}


// =====================================================================
// CATEGORY CHART + CATEGORY CARD
// =====================================================================

@Composable
private fun AuditCategoryChart(
    logs: List<AuditLog>
) {

    val categories =
        AuditCategory.values()

    val maximum =
        categories
            .maxOfOrNull { category ->

                logs.count {
                    categoryFor(
                        it.action
                    ) == category
                }
            }
            ?.coerceAtLeast(
                1
            )
            ?: 1


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
                            0.96f
                    )
                )
                .padding(
                    16.dp
                )
    ) {

        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    11.dp
                )
        ) {

            categories.forEach { category ->

                val count =
                    logs.count {
                        categoryFor(
                            it.action
                        ) == category
                    }


                Column {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            text =
                                category.title,

                            modifier =
                                Modifier.weight(
                                    1f
                                ),

                            fontSize =
                                10.sp,

                            fontWeight =
                                FontWeight.SemiBold,

                            color =
                                Color(0xFF4C4657),

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


                    Spacer(
                        modifier =
                            Modifier.height(
                                5.dp
                            )
                    )


                    LinearProgressIndicator(
                        progress = {
                            count.toFloat() /
                                    maximum.toFloat()
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
        }
    }
}


@Composable
private fun PremiumCategoryCard(
    category: AuditCategory,
    count: Int,
    failed: Int,
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
                            0.96f
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
                        categoryShortCode(
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
                        "$count events • $failed failed",

                    fontSize =
                        9.sp,

                    fontWeight =
                        FontWeight.SemiBold,

                    color =
                        Color(0xFF7650BD)
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        8.dp
                    )
            )


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


// =====================================================================
// ACTIVITY CHART
// =====================================================================

@Composable
private fun PremiumActivityChart(
    logs: List<AuditLog>
) {

    val dates =
        remember(logs) {

            val now =
                LocalDate.now()

            (6 downTo 0)
                .map { daysAgo ->

                    now.minusDays(
                        daysAgo.toLong()
                    )
                }
        }

    val counts =
        dates.map { date ->

            logs.count {
                auditDate(
                    it.createdAt
                ) == date.toString()
            }
        }

    val maximum =
        (
                counts.maxOrNull()
                    ?: 0
                )
            .coerceAtLeast(
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
                            0.96f
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
                    "Activity — Last 7 Days",

                fontSize =
                    14.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            dates.forEachIndexed { index, date ->

                val label =
                    date.format(
                        DateTimeFormatter.ofPattern(
                            "MMM d",
                            Locale.ENGLISH
                        )
                    )


                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    verticalAlignment =
                        Alignment.CenterVertically,

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    Text(
                        text =
                            label,

                        modifier =
                            Modifier.width(
                                48.dp
                            ),

                        fontSize =
                            9.sp,

                        color =
                            Color(0xFF817A8B)
                    )


                    LinearProgressIndicator(
                        progress = {
                            counts[index].toFloat() /
                                    maximum.toFloat()
                        },

                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                                .clip(
                                    RoundedCornerShape(
                                        50.dp
                                    )
                                )
                    )


                    Text(
                        text =
                            counts[index]
                                .toString(),

                        fontSize =
                            9.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF7650BD)
                    )
                }
            }
        }
    }
}


// =====================================================================
// FILTER
// =====================================================================

@Composable
private fun PremiumFilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {

    Box(
        modifier =
            modifier
                .clip(
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .background(
                    if (selected) {

                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color(0xFF6D51BD),
                                    Color(0xFFD14D92)
                                )
                        )

                    } else {

                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color.White,
                                    Color.White
                                )
                        )
                    }
                )
                .clickable {
                    onClick()
                }
                .padding(
                    vertical =
                        11.dp
                ),

        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                label,

            fontSize =
                9.sp,

            fontWeight =
                FontWeight.Bold,

            letterSpacing =
                0.5.sp,

            color =
                if (selected) {
                    Color.White
                } else {
                    Color(0xFF6F4BB8)
                }
        )
    }
}


// =====================================================================
// DATE + ACTOR GROUP
// =====================================================================

@Composable
private fun PremiumDateHeader(
    date: String,
    count: Int
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text =
                date,

            fontSize =
                13.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                Color(0xFF353146)
        )


        Spacer(
            modifier =
                Modifier.width(
                    8.dp
                )
        )


        Box(
            modifier =
                Modifier
                    .clip(
                        RoundedCornerShape(
                            50.dp
                        )
                    )
                    .background(
                        Color(0xFFF0EBFA)
                    )
                    .padding(
                        horizontal =
                            9.dp,

                        vertical =
                            5.dp
                    )
        ) {

            Text(
                text =
                    count.toString(),

                fontSize =
                    8.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF6F4BB8)
            )
        }
    }
}


@Composable
private fun PremiumActorGroup(
    title: String,
    subtitle: String,
    isAdmin: Boolean,
    logs: List<AuditLog>,
    onLogOpen: (AuditLog) -> Unit
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
                    if (isAdmin) {
                        Color(0xFFF4EEFF)
                    } else {
                        Color(0xFFFFF0F6)
                    }
                )
                .padding(
                    14.dp
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
                                44.dp
                            )
                            .clip(
                                CircleShape
                            )
                            .background(
                                if (isAdmin) {

                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                Color(0xFF6654C2),
                                                Color(0xFF9A4FCA)
                                            )
                                    )

                                } else {

                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                Color(0xFFF07862),
                                                Color(0xFFD64E94)
                                            )
                                    )
                                }
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            if (isAdmin) {
                                "AD"
                            } else {
                                initialsFromName(
                                    title
                                )
                            },

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
                            11.dp
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
                            title,

                        fontSize =
                            14.sp,

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
                            subtitle,

                        fontSize =
                            9.sp,

                        color =
                            Color(0xFF817A8B)
                    )
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
                                Color.White.copy(
                                    alpha =
                                        0.80f
                                )
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
                            "${logs.size} EVENT${if (logs.size == 1) "" else "S"}",

                        fontSize =
                            8.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            if (isAdmin) {
                                Color(0xFF6F4BB8)
                            } else {
                                Color(0xFFB64678)
                            }
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )


            logs
                .sortedByDescending {
                    it.createdAt.orEmpty()
                }
                .forEachIndexed { index, log ->

                    PremiumAuditEventCard(
                        log =
                            log,

                        onClick = {
                            onLogOpen(
                                log
                            )
                        }
                    )


                    if (
                        index != logs.lastIndex
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )
                    }
                }
        }
    }
}


// =====================================================================
// EVENT CARD
// =====================================================================

@Composable
private fun PremiumAuditEventCard(
    log: AuditLog,
    onClick: () -> Unit
) {

    val action =
        log.action
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Unknown Action"

    val description =
        log.description
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "No description available"


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
                    Color.White.copy(
                        alpha =
                            0.96f
                    )
                )
                .clickable {
                    onClick()
                }
                .padding(
                    13.dp
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
                                34.dp
                            )
                            .clip(
                                RoundedCornerShape(
                                    11.dp
                                )
                            )
                            .background(
                                eventActionBackground(
                                    action
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            eventActionCode(
                                action
                            ),

                        fontSize =
                            8.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            eventActionForeground(
                                action
                            )
                    )
                }


                Spacer(
                    modifier =
                        Modifier.width(
                            10.dp
                        )
                )


                Text(
                    text =
                        action.replace(
                            '_',
                            ' '
                        ),

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    fontSize =
                        12.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF28243A),

                    maxLines =
                        1,

                    overflow =
                        TextOverflow.Ellipsis
                )


                PremiumStatusBadge(
                    status =
                        log.eventStatus
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            Text(
                text =
                    description,

                fontSize =
                    10.sp,

                lineHeight =
                    15.sp,

                color =
                    Color(0xFF5F5968),

                maxLines =
                    2,

                overflow =
                    TextOverflow.Ellipsis
            )


            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        shortAuditTime(
                            log.createdAt
                        ),

                    fontSize =
                        8.sp,

                    color =
                        Color(0xFF9A92A3)
                )


                Spacer(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                )


                Text(
                    text =
                        "VIEW DETAILS ›",

                    fontSize =
                        8.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF7650BD)
                )
            }
        }
    }
}


// =====================================================================
// DETAILS HERO + SECTIONS
// =====================================================================

@Composable
private fun AuditEventHero(
    log: AuditLog
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
                    Color.White.copy(
                        alpha =
                            0.97f
                    )
                )
                .padding(
                    17.dp
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
                                        listOf(
                                            Color(0xFF6557C8),
                                            Color(0xFFD14E94)
                                        )
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "LOG",

                        color =
                            Color.White,

                        fontSize =
                            10.sp,

                        fontWeight =
                            FontWeight.Bold
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
                            log.action
                                ?.replace(
                                    '_',
                                    ' '
                                )
                                ?: "Unknown Action",

                        fontSize =
                            16.sp,

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
                            displayTime(
                                log.createdAt
                            ),

                        fontSize =
                            10.sp,

                        color =
                            Color(0xFF817A8B)
                    )
                }


                PremiumStatusBadge(
                    status =
                        log.eventStatus
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        13.dp
                    )
            )


            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                14.dp
                            )
                        )
                        .background(
                            Color(0xFFF8F5FB)
                        )
                        .padding(
                            13.dp
                        )
            ) {

                Text(
                    text =
                        log.description
                            ?: "No description recorded.",

                    fontSize =
                        11.sp,

                    lineHeight =
                        16.sp,

                    color =
                        Color(0xFF5F5968)
                )
            }
        }
    }
}


@Composable
private fun PremiumDetailSection(
    title: String,
    rows: List<Pair<String, String?>>
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

        Column {

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


            Spacer(
                modifier =
                    Modifier.height(
                        11.dp
                    )
            )


            rows.forEachIndexed { index, row ->

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical =
                                    5.dp
                            ),

                    verticalAlignment =
                        Alignment.Top
                ) {

                    Text(
                        text =
                            row.first,

                        modifier =
                            Modifier.weight(
                                0.40f
                            ),

                        fontSize =
                            10.sp,

                        fontWeight =
                            FontWeight.SemiBold,

                        color =
                            Color(0xFF8D8698)
                    )


                    Spacer(
                        modifier =
                            Modifier.width(
                                8.dp
                            )
                    )


                    Text(
                        text =
                            row.second
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: "Not recorded",

                        modifier =
                            Modifier.weight(
                                0.60f
                            ),

                        fontSize =
                            10.sp,

                        color =
                            Color(0xFF3F394A)
                    )
                }


                if (
                    index != rows.lastIndex
                ) {

                    Spacer(
                        modifier =
                            Modifier.height(
                                2.dp
                            )
                    )
                }
            }
        }
    }
}


// =====================================================================
// SMALL COMPONENTS
// =====================================================================

@Composable
private fun AuditOverviewPill(
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
                Modifier.width(
                    5.dp
                )
        )


        Text(
            text =
                label,

            fontSize =
                8.sp,

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
private fun PremiumStatusBadge(
    status: String?
) {

    val failed =
        status
            .orEmpty()
            .uppercase(Locale.ROOT)
            .let {
                it.contains("FAIL") ||
                        it.contains("ERROR") ||
                        it.contains("DENIED") ||
                        it.contains("REJECT")
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
                    if (failed) {
                        Color(0xFFFFEDEF)
                    } else {
                        Color(0xFFEAF7EF)
                    }
                )
                .padding(
                    horizontal =
                        8.dp,

                    vertical =
                        6.dp
                )
    ) {

        Text(
            text =
                statusDisplay(
                    status
                ),

            fontSize =
                7.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                if (failed) {
                    Color(0xFFB54050)
                } else {
                    Color(0xFF247148)
                },

            maxLines =
                1
        )
    }
}


@Composable
private fun AuditStatusCard(
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
                    RoundedCornerShape(
                        20.dp
                    )
                )
                .background(
                    background
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
                    15.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    titleColor
            )


            Spacer(
                modifier =
                    Modifier.height(
                        6.dp
                    )
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


// =====================================================================
// HELPERS
// =====================================================================

private fun categoryGradient(
    category: AuditCategory
): List<Color> {

    return when (category) {

        AuditCategory.AUTHENTICATION ->
            listOf(
                Color(0xFF6654C2),
                Color(0xFF9A4FCA)
            )

        AuditCategory.DOCUMENTS ->
            listOf(
                Color(0xFFEE7656),
                Color(0xFFF0528D)
            )

        AuditCategory.INTEGRITY ->
            listOf(
                Color(0xFF45876B),
                Color(0xFF6EAF89)
            )

        AuditCategory.ALTERATION ->
            listOf(
                Color(0xFF7650BD),
                Color(0xFFD14D92)
            )

        AuditCategory.ADMINISTRATION ->
            listOf(
                Color(0xFFFF8B42),
                Color(0xFFE85B88)
            )

        AuditCategory.OTHER ->
            listOf(
                Color(0xFF766F88),
                Color(0xFF9A91AA)
            )
    }
}


private fun categoryShortCode(
    category: AuditCategory
): String {

    return when (category) {

        AuditCategory.AUTHENTICATION ->
            "AUTH"

        AuditCategory.DOCUMENTS ->
            "DOC"

        AuditCategory.INTEGRITY ->
            "SEC"

        AuditCategory.ALTERATION ->
            "AI"

        AuditCategory.ADMINISTRATION ->
            "ADM"

        AuditCategory.OTHER ->
            "LOG"
    }
}


private fun prettyAuditDate(
    date: String
): String {

    if (date == "Unknown") {
        return "Unknown Date"
    }

    return runCatching {

        LocalDate
            .parse(
                date
            )
            .format(
                DateTimeFormatter.ofPattern(
                    "dd MMM yyyy",
                    Locale.ENGLISH
                )
            )

    }.getOrDefault(
        date
    )
}


private fun shortAuditTime(
    timestamp: String?
): String {

    val value =
        timestamp.orEmpty()

    val time =
        value
            .substringAfter(
                "T",
                ""
            )
            .substringBefore(
                "."
            )

    if (time.isBlank()) {
        return "-"
    }

    val parts =
        time.split(
            ":"
        )

    return if (parts.size >= 2) {
        "${parts[0]}:${parts[1]}"
    } else {
        time
    }
}


private fun actorKey(
    log: AuditLog
): String {

    return when {

        log.adminId != null ->
            "ADMIN:${log.adminId}"

        log.agentId != null ->
            "AGENT:${log.agentId}"

        else ->
            "SYSTEM"
    }
}


private fun actorTitle(
    log: AuditLog,
    agents: List<Agent>
): String {

    if (log.adminId != null) {

        return "Administrator #${log.adminId}"
    }


    if (log.agentId != null) {

        return agents
            .firstOrNull {
                it.agentId ==
                        log.agentId
            }
            ?.fullName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Agent #${log.agentId}"
    }


    return "System"
}


private fun actorSubtitle(
    log: AuditLog
): String {

    return when {

        log.adminId != null ->
            "Administrative activity"

        log.agentId != null ->
            "Agent activity"

        else ->
            "System activity"
    }
}


private fun eventActionCode(
    action: String
): String {

    val normalized =
        action.uppercase(
            Locale.ROOT
        )

    return when {

        "LOGIN" in normalized ->
            "IN"

        "LOGOUT" in normalized ->
            "OUT"

        "DELETE" in normalized ||
                "REMOVE" in normalized ->
            "DEL"

        "CAPTURE" in normalized ||
                "UPLOAD" in normalized ->
            "DOC"

        "ANALYSIS" in normalized ->
            "AI"

        "REVIEW" in normalized ->
            "REV"

        "INTEGRITY" in normalized ->
            "SHA"

        else ->
            "LOG"
    }
}


private fun eventActionBackground(
    action: String
): Color {

    val normalized =
        action.uppercase(
            Locale.ROOT
        )

    return when {

        "DELETE" in normalized ||
                "REMOVE" in normalized ->
            Color(0xFFFFEDEF)

        "LOGIN" in normalized ->
            Color(0xFFEAF7EF)

        "LOGOUT" in normalized ->
            Color(0xFFF3EEF8)

        "ANALYSIS" in normalized ||
                "REVIEW" in normalized ->
            Color(0xFFFFF0E8)

        else ->
            Color(0xFFF0EBFA)
    }
}


private fun eventActionForeground(
    action: String
): Color {

    val normalized =
        action.uppercase(
            Locale.ROOT
        )

    return when {

        "DELETE" in normalized ||
                "REMOVE" in normalized ->
            Color(0xFFB54050)

        "LOGIN" in normalized ->
            Color(0xFF247148)

        "LOGOUT" in normalized ->
            Color(0xFF7650BD)

        "ANALYSIS" in normalized ||
                "REVIEW" in normalized ->
            Color(0xFFA86428)

        else ->
            Color(0xFF6F4BB8)
    }
}


private fun initialsFromName(
    name: String
): String {

    val parts =
        name
            .trim()
            .split(
                Regex("\\s+")
            )
            .filter {
                it.isNotBlank()
            }


    if (parts.isEmpty()) {
        return "AG"
    }


    if (parts.size == 1) {

        return parts
            .first()
            .take(
                2
            )
            .uppercase()
    }


    return (
            parts.first()
                .take(
                    1
                )
                    +
                    parts.last()
                        .take(
                            1
                        )
            )
        .uppercase()
}
