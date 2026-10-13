package com.leasingdocument.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.launch


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

    var actionMessage by remember {
        mutableStateOf("")
    }

    var refreshKey by remember {
        mutableIntStateOf(0)
    }

    var selectedAgent by remember {
        mutableStateOf<Agent?>(null)
    }

    var selectedAction by remember {
        mutableStateOf<String?>(null)
    }

    var actionInProgress by remember {
        mutableStateOf(false)
    }

    val scope =
        rememberCoroutineScope()


    // =========================================================
    // LOAD AGENTS
    // =========================================================

    LaunchedEffect(refreshKey) {

        isLoading = true
        errorMessage = ""

        try {

            val response =
                RetrofitClient.apiService.getAgents()

            if (response.isSuccessful) {

                agents =
                    response.body()
                        ?: emptyList()

            } else {

                errorMessage =
                    "Failed to load agents (${response.code()})"
            }

        } catch (e: Exception) {

            errorMessage =
                "Cannot connect to server"

        } finally {

            isLoading = false
        }
    }


    val activeCount =
        agents.count {
            it.status
                ?.equals(
                    "ACTIVE",
                    ignoreCase = true
                ) == true
        }

    val disabledCount =
        agents.count {
            it.status
                ?.equals(
                    "DISABLED",
                    ignoreCase = true
                ) == true
        }


    // =========================================================
    // CONFIRMATION DIALOG
    // =========================================================

    val agentForDialog =
        selectedAgent

    val actionForDialog =
        selectedAction

    if (
        agentForDialog != null &&
        actionForDialog != null
    ) {

        val disabling =
            actionForDialog == "DISABLE"

        AlertDialog(
            onDismissRequest = {
                if (!actionInProgress) {
                    selectedAgent = null
                    selectedAction = null
                }
            },

            title = {
                Text(
                    text =
                        if (disabling) {
                            "Disable Agent?"
                        } else {
                            "Reactivate Agent?"
                        },

                    fontWeight =
                        FontWeight.Bold
                )
            },

            text = {
                Text(
                    text =
                        if (disabling) {
                            "This agent will no longer be treated as active. Historical documents and records will be preserved."
                        } else {
                            "This agent account will be marked ACTIVE again."
                        }
                )
            },

            confirmButton = {

                TextButton(
                    enabled =
                        !actionInProgress,

                    onClick = {

                        val agentId =
                            agentForDialog.agentId
                                ?: return@TextButton

                        actionInProgress = true
                        actionMessage = ""

                        scope.launch {

                            try {

                                val response =
                                    if (disabling) {

                                        RetrofitClient
                                            .apiService
                                            .disableAgent(agentId)

                                    } else {

                                        RetrofitClient
                                            .apiService
                                            .reactivateAgent(agentId)
                                    }

                                if (response.isSuccessful) {

                                    actionMessage =
                                        if (disabling) {
                                            "Agent disabled successfully."
                                        } else {
                                            "Agent reactivated successfully."
                                        }

                                    selectedAgent = null
                                    selectedAction = null

                                    refreshKey++

                                } else {

                                    actionMessage =
                                        when (response.code()) {

                                            401 ->
                                                "Your session is no longer valid."

                                            403 ->
                                                "Only an administrator can change agent status."

                                            404 ->
                                                "Agent not found."

                                            else ->
                                                "Could not update agent status (${response.code()})."
                                        }
                                }

                            } catch (e: Exception) {

                                actionMessage =
                                    "Cannot connect to server."

                            } finally {

                                actionInProgress = false
                            }
                        }
                    }
                ) {

                    if (actionInProgress) {

                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(18.dp),

                            strokeWidth =
                                2.dp
                        )

                    } else {

                        Text(
                            text =
                                if (disabling) {
                                    "DISABLE"
                                } else {
                                    "REACTIVATE"
                                },

                            color =
                                if (disabling) {
                                    Color(0xFFC34757)
                                } else {
                                    Color(0xFF6F4BB8)
                                },

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            },

            dismissButton = {

                TextButton(
                    enabled =
                        !actionInProgress,

                    onClick = {
                        selectedAgent = null
                        selectedAction = null
                    }
                ) {

                    Text(
                        text =
                            "CANCEL",

                        color =
                            Color(0xFF777184)
                    )
                }
            }
        )
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
                    "Manage Agents",

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
                    "View registered leasing agents and manage account access.",

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
                                    Color(0xFF6250B5),
                                    Color(0xFF934FC2),
                                    Color(0xFFD14D92)
                                )
                        )
                    )
                    .padding(20.dp)
            ) {

                Column {

                    Text(
                        text =
                            "Agent Overview",

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
                            "${agents.size} Registered Agent${if (agents.size == 1) "" else "s"}",

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
                            Arrangement.spacedBy(10.dp)
                    ) {

                        OverviewPill(
                            label =
                                "ACTIVE",

                            value =
                                activeCount.toString()
                        )


                        OverviewPill(
                            label =
                                "DISABLED",

                            value =
                                disabledCount.toString()
                        )
                    }
                }
            }


            if (actionMessage.isNotBlank()) {

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )


                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(16.dp)
                            )
                            .background(
                                Color(0xFFF3EEF9)
                            )
                            .padding(13.dp)
                ) {

                    Text(
                        text =
                            actionMessage,

                        fontSize =
                            12.sp,

                        color =
                            Color(0xFF6F4BB8)
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )


            Text(
                text =
                    "Agent Accounts",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF1B1D30)
            )


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            // =================================================
            // STATES
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
                                    "Loading agents...",

                                fontSize =
                                    12.sp,

                                color =
                                    Color(0xFF817A8B)
                            )
                        }
                    }
                }


                errorMessage.isNotBlank() -> {

                    StatusCard(
                        title =
                            "Unable to load agents",

                        message =
                            errorMessage,

                        background =
                            Color(0xFFFFEEF1),

                        titleColor =
                            Color(0xFFA13A4B)
                    )
                }


                agents.isEmpty() -> {

                    StatusCard(
                        title =
                            "No agents found",

                        message =
                            "Registered leasing agents will appear here.",

                        background =
                            Color.White,

                        titleColor =
                            Color(0xFF171A2C)
                    )
                }


                else -> {

                    LazyColumn(
                        modifier =
                            Modifier.fillMaxSize()
                    ) {

                        items(
                            items =
                                agents,

                            key = {
                                it.agentId
                                    ?: it.hashCode()
                            }
                        ) { agent ->

                            AgentCard(
                                agent =
                                    agent,

                                actionsEnabled =
                                    !actionInProgress,

                                onDisable = {
                                    selectedAgent = agent
                                    selectedAction = "DISABLE"
                                },

                                onReactivate = {
                                    selectedAgent = agent
                                    selectedAction = "REACTIVATE"
                                }
                            )


                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )
                        }


                        item {

                            Spacer(
                                modifier =
                                    Modifier.height(20.dp)
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
private fun OverviewPill(
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
                        12.dp,

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
                14.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                Color.White
        )


        Spacer(
            modifier =
                Modifier.width(6.dp)
        )


        Text(
            text =
                label,

            fontSize =
                9.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color.White.copy(alpha = 0.82f)
        )
    }
}


// =====================================================================
// AGENT CARD
// =====================================================================

@Composable
private fun AgentCard(
    agent: Agent,
    actionsEnabled: Boolean,
    onDisable: () -> Unit,
    onReactivate: () -> Unit
) {

    val name =
        agent.fullName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Unknown Agent"


    val email =
        agent.email
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Email unavailable"


    val status =
        agent.status
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "UNKNOWN"


    val isActive =
        status.equals(
            "ACTIVE",
            ignoreCase = true
        )


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
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF7B50CC),
                                            Color(0xFFD14E94)
                                        )
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            initialsFromName(name),

                        color =
                            Color.White,

                        fontSize =
                            14.sp,

                        fontWeight =
                            FontWeight.Bold
                    )
                }


                Spacer(
                    modifier =
                        Modifier.width(14.dp)
                )


                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            name,

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


                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )


                    Text(
                        text =
                            email,

                        fontSize =
                            11.sp,

                        color =
                            Color(0xFF817A8B),

                        maxLines =
                            1,

                        overflow =
                            TextOverflow.Ellipsis
                    )
                }


                Spacer(
                    modifier =
                        Modifier.width(10.dp)
                )


                Box(
                    modifier =
                        Modifier
                            .clip(
                                RoundedCornerShape(50.dp)
                            )
                            .background(
                                if (isActive) {
                                    Color(0xFFEAF7EF)
                                } else {
                                    Color(0xFFF3EEF8)
                                }
                            )
                            .padding(
                                horizontal =
                                    10.dp,

                                vertical =
                                    7.dp
                            )
                ) {

                    Text(
                        text =
                            status.uppercase(),

                        fontSize =
                            9.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            if (isActive) {
                                Color(0xFF247148)
                            } else {
                                Color(0xFF7650BD)
                            }
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )


            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(14.dp)
                        )
                        .background(
                            if (isActive) {
                                Color(0xFFFFEEF1)
                            } else {
                                Color(0xFFF0EBFA)
                            }
                        )
                        .clickable(
                            enabled =
                                actionsEnabled &&
                                        agent.agentId != null
                        ) {

                            if (isActive) {
                                onDisable()
                            } else {
                                onReactivate()
                            }
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
                        if (isActive) {
                            "DISABLE AGENT"
                        } else {
                            "REACTIVATE AGENT"
                        },

                    fontSize =
                        11.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        if (isActive) {
                            Color(0xFFC34757)
                        } else {
                            Color(0xFF6F4BB8)
                        },

                    letterSpacing =
                        0.5.sp
                )
            }
        }
    }
}


// =====================================================================
// STATUS CARD
// =====================================================================

@Composable
private fun StatusCard(
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


// =====================================================================
// INITIALS
// =====================================================================

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
            .take(2)
            .uppercase()
    }


    return (
            parts.first().take(1)
                    +
                    parts.last().take(1)
            ).uppercase()
}
