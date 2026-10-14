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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.AddAgentRequest
import com.leasingdocument.app.network.Agent
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class ManageAgentsPage {
    LIST,
    ADD
}

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

    var page by remember {
        mutableStateOf(ManageAgentsPage.LIST)
    }

    var createInProgress by remember {
        mutableStateOf(false)
    }

    var createError by remember {
        mutableStateOf("")
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


    BackHandler(
        enabled =
            page == ManageAgentsPage.ADD &&
                    !createInProgress
    ) {
        page = ManageAgentsPage.LIST
        createError = ""
    }


    // =========================================================
    // DISABLE / REACTIVATE CONFIRMATION
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
                                        responseErrorMessage(
                                            response.code(),
                                            response.errorBody()
                                                ?.use { it.string() }
                                                .orEmpty(),
                                            fallback =
                                                "Could not update agent status (${response.code()})."
                                        )
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

        ManageAgentsBackground()


        when (page) {

            ManageAgentsPage.LIST -> {

                ManageAgentsListPage(
                    agents =
                        agents,

                    activeCount =
                        activeCount,

                    disabledCount =
                        disabledCount,

                    isLoading =
                        isLoading,

                    errorMessage =
                        errorMessage,

                    actionMessage =
                        actionMessage,

                    actionInProgress =
                        actionInProgress,

                    onAddAgent = {
                        createError = ""
                        actionMessage = ""
                        page = ManageAgentsPage.ADD
                    },

                    onDisable = { agent ->
                        selectedAgent = agent
                        selectedAction = "DISABLE"
                    },

                    onReactivate = { agent ->
                        selectedAgent = agent
                        selectedAction = "REACTIVATE"
                    }
                )
            }


            ManageAgentsPage.ADD -> {

                AddAgentPage(
                    busy =
                        createInProgress,

                    serverError =
                        createError,

                    onCreate = { request ->

                        if (!createInProgress) {

                            createInProgress = true
                            createError = ""

                            scope.launch {

                                try {

                                    val response =
                                        RetrofitClient
                                            .apiService
                                            .createAgent(request)

                                    if (response.isSuccessful) {

                                        actionMessage =
                                            "Agent created successfully. The account is ACTIVE and ready for login."

                                        createError = ""
                                        page = ManageAgentsPage.LIST
                                        refreshKey++

                                    } else {

                                        createError =
                                            responseErrorMessage(
                                                response.code(),
                                                response.errorBody()
                                                    ?.use { it.string() }
                                                    .orEmpty(),
                                                fallback =
                                                    "Could not create the agent (${response.code()})."
                                            )
                                    }

                                } catch (e: Exception) {

                                    createError =
                                        "Cannot connect to server."

                                } finally {

                                    createInProgress = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}


// =====================================================================
// LIST PAGE
// =====================================================================

@Composable
private fun ManageAgentsListPage(
    agents: List<Agent>,
    activeCount: Int,
    disabledCount: Int,
    isLoading: Boolean,
    errorMessage: String,
    actionMessage: String,
    actionInProgress: Boolean,
    onAddAgent: () -> Unit,
    onDisable: (Agent) -> Unit,
    onReactivate: (Agent) -> Unit
) {

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


        AgentOverviewCard(
            registeredCount =
                agents.size,

            activeCount =
                activeCount,

            disabledCount =
                disabledCount
        )


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        GradientActionButton(
            text =
                "+ ADD NEW AGENT",

            enabled =
                !actionInProgress,

            onClick =
                onAddAgent
        )


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

                    lineHeight =
                        17.sp,

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


        when {

            isLoading -> {

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),

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
                        "Create the first leasing-agent account using the button above.",

                    background =
                        Color.White,

                    titleColor =
                        Color(0xFF171A2C)
                )
            }


            else -> {

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),

                    contentPadding =
                        PaddingValues(
                            bottom =
                                20.dp
                        )
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
                                onDisable(agent)
                            },

                            onReactivate = {
                                onReactivate(agent)
                            }
                        )


                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )
                    }
                }
            }
        }
    }
}


// =====================================================================
// ADD AGENT PAGE
// =====================================================================

@Composable
private fun AddAgentPage(
    busy: Boolean,
    serverError: String,
    onCreate: (AddAgentRequest) -> Unit
) {

    var fullName by remember {
        mutableStateOf("")
    }

    var email by remember {
        mutableStateOf("")
    }

    var nic by remember {
        mutableStateOf("")
    }

    var phone by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var confirmPassword by remember {
        mutableStateOf("")
    }

    var localError by remember {
        mutableStateOf("")
    }


    val displayError =
        localError.ifBlank {
            serverError
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
                    34.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {

        item {

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Text(
                text =
                    "Create Agent",

                fontSize =
                    27.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )
        }


        item {

            Box(
                modifier =
                    Modifier
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
                        .padding(19.dp)
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
                                .size(56.dp)
                                .clip(
                                    CircleShape
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
                                initialsFromName(
                                    fullName.ifBlank {
                                        "New Agent"
                                    }
                                ),

                            color =
                                Color.White,

                            fontSize =
                                16.sp,

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
                                fullName.ifBlank {
                                    "New Agent Account"
                                },

                            color =
                                Color.White,

                            fontSize =
                                17.sp,

                            fontWeight =
                                FontWeight.Bold,

                            maxLines =
                                1,

                            overflow =
                                TextOverflow.Ellipsis
                        )


                        if (email.isNotBlank()) {

                            Spacer(
                                modifier =
                                    Modifier.height(4.dp)
                            )

                            Text(
                                text =
                                    email,

                                color =
                                    Color.White.copy(
                                        alpha =
                                            0.82f
                                    ),

                                fontSize =
                                    10.sp,

                                maxLines =
                                    1,

                                overflow =
                                    TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }


        item {

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(22.dp)
                        )
                        .background(
                            Color.White.copy(
                                alpha =
                                    0.97f
                            )
                        )
                        .padding(17.dp)
            ) {

                Column {

                    Text(
                        text =
                            "Agent Information",

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF171A2C)
                    )
                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )


                    OutlinedTextField(
                        value =
                            fullName,

                        onValueChange = {
                            if (it.length <= 120) {
                                fullName = it
                                localError = ""
                            }
                        },

                        label = {
                            Text(
                                "Full name"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        enabled =
                            !busy,

                        singleLine =
                            true,

                        keyboardOptions =
                            KeyboardOptions(
                                imeAction =
                                    ImeAction.Next
                            )
                    )


                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )


                    OutlinedTextField(
                        value =
                            email,

                        onValueChange = {
                            if (it.length <= 190) {
                                email = it
                                localError = ""
                            }
                        },

                        label = {
                            Text(
                                "Email address"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        enabled =
                            !busy,

                        singleLine =
                            true,

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Email,

                                imeAction =
                                    ImeAction.Next
                            )
                    )


                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )


                    OutlinedTextField(
                        value =
                            nic,

                        onValueChange = {
                            if (it.length <= 30) {
                                nic = it
                                localError = ""
                            }
                        },

                        label = {
                            Text(
                                "NIC"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        enabled =
                            !busy,

                        singleLine =
                            true,

                        keyboardOptions =
                            KeyboardOptions(
                                imeAction =
                                    ImeAction.Next
                            )
                    )


                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )


                    OutlinedTextField(
                        value =
                            phone,

                        onValueChange = {
                            if (it.length <= 30) {
                                phone = it
                                localError = ""
                            }
                        },

                        label = {
                            Text(
                                "Phone number"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        enabled =
                            !busy,

                        singleLine =
                            true,

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Phone,

                                imeAction =
                                    ImeAction.Next
                            )
                    )
                }
            }
        }


        item {

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(22.dp)
                        )
                        .background(
                            Color.White.copy(
                                alpha =
                                    0.97f
                            )
                        )
                        .padding(17.dp)
            ) {

                Column {

                    Text(
                        text =
                            "Secure Login",

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF171A2C)
                    )
                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )


                    OutlinedTextField(
                        value =
                            password,

                        onValueChange = {
                            if (it.length <= 128) {
                                password = it
                                localError = ""
                            }
                        },

                        label = {
                            Text(
                                "Temporary password"
                            )
                        },

                        supportingText = {
                            Text(
                                "Minimum 8 characters"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        enabled =
                            !busy,

                        singleLine =
                            true,

                        visualTransformation =
                            PasswordVisualTransformation(),

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Password,

                                imeAction =
                                    ImeAction.Next
                            )
                    )


                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )


                    OutlinedTextField(
                        value =
                            confirmPassword,

                        onValueChange = {
                            if (it.length <= 128) {
                                confirmPassword = it
                                localError = ""
                            }
                        },

                        label = {
                            Text(
                                "Confirm password"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        enabled =
                            !busy,

                        singleLine =
                            true,

                        visualTransformation =
                            PasswordVisualTransformation(),

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Password,

                                imeAction =
                                    ImeAction.Done
                            )
                    )
                }
            }
        }


        if (displayError.isNotBlank()) {

            item {

                StatusCard(
                    title =
                        "Could not create agent",

                    message =
                        displayError,

                    background =
                        Color(0xFFFFEEF1),

                    titleColor =
                        Color(0xFFA13A4B)
                )
            }
        }


        item {

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(16.dp)
                        )
                        .background(
                            if (busy) {

                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            Color(0xFFD8D0DF),
                                            Color(0xFFD8D0DF)
                                        )
                                )

                            } else {

                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            Color(0xFFFF7B4D),
                                            Color(0xFFF04D8E),
                                            Color(0xFF8C4DD1)
                                        )
                                )
                            }
                        )
                        .clickable(
                            enabled =
                                !busy
                        ) {

                            val cleanName =
                                fullName.trim()

                            val cleanEmail =
                                email.trim()

                            val cleanNic =
                                nic.trim()

                            val cleanPhone =
                                phone.trim()


                            localError =
                                when {

                                    cleanName.length < 2 ->
                                        "Enter the agent's full name."

                                    !cleanEmail.matches(
                                        Regex(
                                            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
                                        )
                                    ) ->
                                        "Enter a valid email address."

                                    cleanNic.length < 5 ->
                                        "Enter a valid NIC."

                                    cleanPhone.length < 7 ->
                                        "Enter a valid phone number."

                                    password.length < 8 ->
                                        "Password must contain at least 8 characters."

                                    password != confirmPassword ->
                                        "Passwords do not match."

                                    else ->
                                        ""
                                }


                            if (localError.isBlank()) {

                                onCreate(
                                    AddAgentRequest(
                                        fullName =
                                            cleanName,

                                        email =
                                            cleanEmail,

                                        password =
                                            password,

                                        nic =
                                            cleanNic,

                                        phone =
                                            cleanPhone
                                    )
                                )
                            }
                        }
                        .padding(
                            vertical =
                                15.dp
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                if (busy) {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(18.dp),

                            strokeWidth =
                                2.dp,

                            color =
                                Color.White
                        )


                        Spacer(
                            modifier =
                                Modifier.width(10.dp)
                        )


                        Text(
                            text =
                                "CREATING AGENT...",

                            fontSize =
                                11.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color.White,

                            letterSpacing =
                                0.5.sp
                        )
                    }

                } else {

                    Text(
                        text =
                            "CREATE AGENT ACCOUNT",

                        fontSize =
                            11.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color.White,

                        letterSpacing =
                            0.5.sp
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
private fun ManageAgentsBackground() {

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
}


// =====================================================================
// OVERVIEW CARD
// =====================================================================

@Composable
private fun AgentOverviewCard(
    registeredCount: Int,
    activeCount: Int,
    disabledCount: Int
) {

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
                    Color.White.copy(
                        alpha =
                            0.84f
                    ),

                fontSize =
                    12.sp
            )


            Spacer(
                modifier =
                    Modifier.height(5.dp)
            )


            Text(
                text =
                    "$registeredCount Registered Agent${if (registeredCount == 1) "" else "s"}",

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
}


// =====================================================================
// GRADIENT ACTION BUTTON
// =====================================================================

@Composable
private fun GradientActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(16.dp)
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
                        14.dp
                ),

        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                text,

            color =
                Color.White,

            fontSize =
                11.sp,

            fontWeight =
                FontWeight.Bold,

            letterSpacing =
                0.5.sp
        )
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
                    Color.White.copy(
                        alpha =
                            0.15f
                    )
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
                Color.White.copy(
                    alpha =
                        0.82f
                )
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
                    Color.White.copy(
                        alpha =
                            0.96f
                    )
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
                            .clip(
                                CircleShape
                            )
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
// RESPONSE ERROR
// =====================================================================

private fun responseErrorMessage(
    code: Int,
    raw: String,
    fallback: String
): String {

    val detail =
        runCatching {

            val obj =
                JSONObject(raw)

            obj.optString(
                "detail"
            )
                .ifBlank {
                    obj.optString(
                        "message"
                    )
                }

        }.getOrDefault(
            ""
        )


    if (detail.isNotBlank()) {
        return detail
    }


    return when (code) {

        400 ->
            "Check the entered agent details and try again."

        401 ->
            "Your session is no longer valid. Sign in again."

        403 ->
            "Only an administrator can perform this action."

        409 ->
            "An agent with these details already exists."

        else ->
            fallback
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
