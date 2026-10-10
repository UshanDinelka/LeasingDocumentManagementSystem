package com.leasingdocument.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.AuthSession
import com.leasingdocument.app.network.LoginRequest
import com.leasingdocument.app.network.LogoutRequest
import com.leasingdocument.app.network.RetrofitClient
import com.leasingdocument.app.screens.AdminDashboard
import com.leasingdocument.app.screens.AgentDashboard
import com.leasingdocument.app.ui.theme.LeasingDocumentAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            LeasingDocumentAppTheme {

                // =========================================================
                // MAIN APPLICATION SCREEN STATE
                // =========================================================

                var currentScreen by remember {
                    mutableStateOf("LOGIN")
                }

                var isLoggingOut by remember {
                    mutableStateOf(false)
                }

                val appScope = rememberCoroutineScope()


                // =========================================================
                // LOGOUT FUNCTION
                // =========================================================

                val performLogout: () -> Unit = {

                    if (!isLoggingOut) {

                        isLoggingOut = true

                        appScope.launch {

                            try {

                                // Close backend session
                                RetrofitClient
                                    .apiService
                                    .logout(
                                        LogoutRequest(
                                            deviceId = 1
                                        )
                                    )

                            } catch (e: Exception) {

                                // Even if backend connection fails,
                                // local authentication must still be cleared.
                            }

                            // Clear JWT / user session locally
                            AuthSession.clear()

                            // Return to login screen
                            currentScreen = "LOGIN"

                            isLoggingOut = false
                        }
                    }
                }


                // =========================================================
                // PHONE / EMULATOR SYSTEM BACK
                //
                // Agent Dashboard -> Login
                // Admin Dashboard -> Login
                //
                // Sub-screen back navigation is handled inside
                // AgentDashboard and AdminDashboard.
                // =========================================================

                BackHandler(
                    enabled =
                        (
                                currentScreen == "AGENT" ||
                                        currentScreen == "ADMIN"
                                ) &&
                                !isLoggingOut
                ) {

                    performLogout()
                }


                // =========================================================
                // APPLICATION NAVIGATION
                // =========================================================

                when (currentScreen) {


                    // =====================================================
                    // AGENT
                    // =====================================================

                    "AGENT" -> {

                        AgentDashboard(
                            onLogout = {
                                performLogout()
                            }
                        )
                    }


                    // =====================================================
                    // ADMIN
                    // =====================================================

                    "ADMIN" -> {

                        AdminDashboard(
                            onLogout = {
                                performLogout()
                            }
                        )
                    }


                    // =====================================================
                    // LOGIN
                    // =====================================================

                    else -> {

                        Scaffold(
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->

                            LoginScreen(
                                modifier =
                                    Modifier.padding(innerPadding),

                                onAgentLoginSuccess = {
                                    currentScreen = "AGENT"
                                },

                                onAdminLoginSuccess = {
                                    currentScreen = "ADMIN"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


// =====================================================================
// LOGIN SCREEN
// =====================================================================

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onAgentLoginSuccess: () -> Unit,
    onAdminLoginSuccess: () -> Unit
) {

    // =============================================================
    // LOGIN FORM STATE
    // =============================================================

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var message by remember {
        mutableStateOf("")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    val coroutineScope =
        rememberCoroutineScope()


    // =============================================================
    // LOGIN SCREEN UI
    // =============================================================

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),

        verticalArrangement =
            Arrangement.Center,

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {


        // =========================================================
        // APPLICATION TITLE
        // =========================================================

        Text(
            text = "Leasing Document",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Secure Document Management System",
            fontSize = 15.sp,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            textAlign = TextAlign.Center
        )


        Spacer(
            modifier =
                Modifier.height(40.dp)
        )


        // =========================================================
        // LOGIN TITLE
        // =========================================================

        Text(
            text = "Welcome Back",
            fontSize = 22.sp,
            fontWeight =
                FontWeight.SemiBold
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Text(
            text = "Sign in to continue",
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )


        Spacer(
            modifier =
                Modifier.height(28.dp)
        )


        // =========================================================
        // EMAIL
        // =========================================================

        OutlinedTextField(
            value = email,

            onValueChange = {
                email = it
            },

            label = {
                Text("Email Address")
            },

            placeholder = {
                Text("example@email.com")
            },

            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Email
                ),

            singleLine = true,

            modifier =
                Modifier.fillMaxWidth()
        )


        Spacer(
            modifier =
                Modifier.height(16.dp)
        )


        // =========================================================
        // PASSWORD
        // =========================================================

        OutlinedTextField(
            value = password,

            onValueChange = {
                password = it
            },

            label = {
                Text("Password")
            },

            visualTransformation =
                PasswordVisualTransformation(),

            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Password
                ),

            singleLine = true,

            modifier =
                Modifier.fillMaxWidth()
        )


        Spacer(
            modifier =
                Modifier.height(28.dp)
        )


        // =========================================================
        // SIGN IN BUTTON
        // =========================================================

        Button(
            onClick = {

                // =================================================
                // VALIDATION
                // =================================================

                if (
                    email.isBlank() ||
                    password.isBlank()
                ) {

                    message =
                        "Please enter email and password"

                } else {

                    coroutineScope.launch {

                        isLoading = true

                        message = ""

                        try {

                            // =====================================
                            // CREATE LOGIN REQUEST
                            // =====================================

                            val request =
                                LoginRequest(
                                    email = email,
                                    password = password,
                                    deviceId = 1
                                )


                            // =====================================
                            // CALL BACKEND
                            // =====================================

                            val response =
                                RetrofitClient
                                    .apiService
                                    .login(request)


                            // =====================================
                            // SUCCESS
                            // =====================================

                            if (
                                response.isSuccessful
                            ) {

                                val loginResponse =
                                    response.body()


                                if (
                                    loginResponse?.token != null
                                ) {

                                    // =================================
                                    // SAVE AUTHENTICATION SESSION
                                    // =================================

                                    AuthSession.token =
                                        loginResponse.token

                                    AuthSession.userId =
                                        loginResponse.userId

                                    AuthSession.role =
                                        loginResponse.role


                                    // =================================
                                    // ROLE BASED NAVIGATION
                                    // =================================

                                    when (
                                        loginResponse.role
                                    ) {

                                        "AGENT" -> {

                                            onAgentLoginSuccess()
                                        }


                                        "ADMIN" -> {

                                            onAdminLoginSuccess()
                                        }


                                        else -> {

                                            message =
                                                "Unknown user role"
                                        }
                                    }

                                } else {

                                    message =
                                        loginResponse
                                            ?.message
                                            ?: "Login failed"
                                }

                            } else {

                                message =
                                    "Login failed"
                            }

                        } catch (
                            e: Exception
                        ) {

                            message =
                                "Cannot connect to server"
                        }


                        isLoading = false
                    }
                }
            },

            enabled = !isLoading,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
        ) {


            // =====================================================
            // LOGIN LOADING
            // =====================================================

            if (isLoading) {

                CircularProgressIndicator()

            } else {

                Text(
                    text = "SIGN IN",
                    fontSize = 16.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        // =========================================================
        // MESSAGE
        // =========================================================

        if (
            message.isNotEmpty()
        ) {

            Text(
                text = message,
                textAlign =
                    TextAlign.Center
            )
        }


        Spacer(
            modifier =
                Modifier.height(24.dp)
        )


        // =========================================================
        // SECURITY MESSAGE
        // =========================================================

        Text(
            text = "Authorized users only",
            fontSize = 13.sp,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}