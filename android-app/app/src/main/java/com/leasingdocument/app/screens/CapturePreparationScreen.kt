

package com.leasingdocument.app.screens

import android.app.Activity
import android.content.Intent

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.leasingdocument.app.capture.CaptureDraftsActivity
import com.leasingdocument.app.capture.LiveCaptureActivity
import com.leasingdocument.app.network.Customer
import com.leasingdocument.app.network.DocumentType
import com.leasingdocument.app.network.RegisterCustomerRequest
import com.leasingdocument.app.network.RetrofitClient

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

import java.util.Locale


@Composable
fun CapturePreparationScreen() {

    var page by rememberSaveable {
        mutableStateOf("CUSTOMER")
    }

    var query by rememberSaveable {
        mutableStateOf("")
    }

    var customerId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    var typeCode by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var customers by remember {
        mutableStateOf<List<Customer>>(emptyList())
    }

    var types by remember {
        mutableStateOf<List<DocumentType>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var loaded by remember {
        mutableStateOf(false)
    }

    var saving by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("")
    }

    var reload by remember {
        mutableStateOf(0)
    }


    // =========================================================
    // CREATE CUSTOMER FIELDS
    // =========================================================

    var fullName by rememberSaveable {
        mutableStateOf("")
    }

    var nic by rememberSaveable {
        mutableStateOf("")
    }

    var phone by rememberSaveable {
        mutableStateOf("")
    }

    var email by rememberSaveable {
        mutableStateOf("")
    }

    var address by rememberSaveable {
        mutableStateOf("")
    }


    val scope =
        rememberCoroutineScope()

    val context =
        LocalContext.current


    var draftSaved by rememberSaveable {
        mutableStateOf(false)
    }


    // =========================================================
    // CAMERA RESULT
    // =========================================================

    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode ==
                Activity.RESULT_OK
            ) {

                draftSaved = true
            }
        }


    val selectedCustomer =
        customers.firstOrNull {
            it.customerId == customerId
        }


    val selectedType =
        types.firstOrNull {
            it.typeCode == typeCode
        }


    // =========================================================
    // LOAD CUSTOMERS + DOCUMENT TYPES
    // =========================================================

    LaunchedEffect(reload) {

        loading = true
        loaded = false
        message = ""

        try {

            val customerResponse =
                RetrofitClient
                    .apiService
                    .getCustomers()


            if (
                !customerResponse.isSuccessful
            ) {

                message =
                    preparationError(
                        customerResponse.code()
                    )

                return@LaunchedEffect
            }


            val typeResponse =
                RetrofitClient
                    .apiService
                    .getDocumentTypes()


            if (
                !typeResponse.isSuccessful
            ) {

                message =
                    preparationError(
                        typeResponse.code()
                    )

                return@LaunchedEffect
            }


            val customerBody =
                customerResponse.body()

            val typeBody =
                typeResponse.body()


            if (
                customerBody == null ||
                typeBody == null
            ) {

                message =
                    "The server returned an empty response. Please retry."

                return@LaunchedEffect
            }


            customers =
                customerBody
                    .filter {

                        it.customerId != null &&
                                it.customerId > 0
                    }
                    .sortedBy {

                        it.fullName
                            .orEmpty()
                            .lowercase(
                                Locale.ROOT
                            )
                    }


            types =
                typeBody
                    .filter {

                        it.documentTypeId != null &&
                                it.documentTypeId > 0 &&
                                !it.typeCode.isNullOrBlank() &&
                                (
                                        it.status == null ||
                                                it.status.equals(
                                                    "ACTIVE",
                                                    ignoreCase = true
                                                )
                                        )
                    }
                    .sortedBy {

                        it.typeName
                            .orEmpty()
                            .lowercase(
                                Locale.ROOT
                            )
                    }


            loaded = true

        } catch (
            e: CancellationException
        ) {

            throw e

        } catch (
            e: Exception
        ) {

            message =
                "Cannot load customers and document types. Check the connection and retry."

        } finally {

            loading = false
        }
    }


    // =========================================================
    // BACK HANDLING
    // =========================================================

    BackHandler(
        enabled =
            page != "CUSTOMER" ||
                    saving
    ) {

        if (
            !saving
        ) {

            page =
                when (
                    page
                ) {

                    "SUMMARY" ->
                        "TYPE"

                    "TYPE",
                    "CREATE" ->
                        "CUSTOMER"

                    else ->
                        "CUSTOMER"
                }


            message = ""
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
                    colors = listOf(
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


            val topPeach =
                Path().apply {

                    moveTo(
                        0f,
                        0f
                    )

                    lineTo(
                        w * 0.70f,
                        0f
                    )

                    cubicTo(
                        w * 0.52f,
                        h * 0.025f,

                        w * 0.34f,
                        h * 0.045f,

                        w * 0.18f,
                        h * 0.09f
                    )

                    cubicTo(
                        w * 0.08f,
                        h * 0.12f,

                        w * 0.03f,
                        h * 0.15f,

                        0f,
                        h * 0.18f
                    )

                    close()
                }


            drawPath(
                path =
                    topPeach,

                brush =
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFCFAC)
                                .copy(
                                    alpha = 0.45f
                                ),

                            Color(0xFFFFE8DD)
                                .copy(
                                    alpha = 0.22f
                                ),

                            Color.Transparent
                        )
                    )
            )


            val orangeLine =
                Path().apply {

                    moveTo(
                        0f,
                        h * 0.095f
                    )

                    cubicTo(
                        w * 0.20f,
                        h * 0.07f,

                        w * 0.36f,
                        h * 0.085f,

                        w * 0.57f,
                        h * 0.025f
                    )
                }


            drawPath(
                path =
                    orangeLine,

                color =
                    Color(0xFFFF933E)
                        .copy(
                            alpha = 0.60f
                        ),

                style =
                    Stroke(
                        width =
                            1.dp.toPx()
                    )
            )


            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFAA44E6)
                                .copy(
                                    alpha = 0.08f
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


        // =====================================================
        // CONTENT
        // =====================================================

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal = 20.dp
                ),

            verticalArrangement =
                Arrangement.spacedBy(
                    14.dp
                )
        ) {


            // =================================================
            // PAGE HEADER
            // =================================================

            item {

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )


                Text(
                    text =
                        "Secure Capture",

                    fontSize =
                        27.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF15192B)
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            4.dp
                        )
                )


                Text(
                    text =
                        "Prepare your document before opening the secure camera.",

                    fontSize =
                        13.sp,

                    lineHeight =
                        18.sp,

                    color =
                        Color(0xFF7D7688)
                )
            }


            // =================================================
            // STEPS
            // =================================================

            item {

                CaptureStepIndicator(
                    page =
                        page
                )
            }


            // =================================================
            // LOADING
            // =================================================

            if (
                loading
            ) {

                item {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical =
                                        40.dp
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        CircularProgressIndicator(
                            color =
                                Color(0xFF7650BD)
                        )
                    }
                }
            }


            // =================================================
            // ERROR
            // =================================================

            if (
                message.isNotBlank()
            ) {

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
                                    Color(0xFFFFEBEE)
                                )
                                .padding(
                                    14.dp
                                )
                    ) {

                        Text(
                            text =
                                message,

                            color =
                                Color(0xFFB3261E),

                            fontSize =
                                13.sp,

                            lineHeight =
                                18.sp
                        )
                    }
                }
            }


            // =================================================
            // RETRY
            // =================================================

            if (
                !loading &&
                !loaded
            ) {

                item {

                    Button(
                        onClick = {
                            reload++
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(
                                16.dp
                            )
                    ) {

                        Text(
                            "RETRY LOADING"
                        )
                    }
                }
            }


            // =================================================
            // MAIN PAGES
            // =================================================

            if (
                loaded &&
                !loading
            ) {

                when (
                    page
                ) {


                    // =================================================
                    // CUSTOMER
                    // =================================================

                    "CUSTOMER" -> {


                        item {

                            CaptureSectionHeader(
                                step = "01",
                                title =
                                    "Select Customer",
                                subtitle =
                                    "Search for an existing customer or create a new one."
                            )
                        }


                        item {

                            OutlinedTextField(
                                value =
                                    query,

                                onValueChange = {
                                    query = it
                                },

                                label = {
                                    Text(
                                        "Search by name or NIC"
                                    )
                                },

                                singleLine =
                                    true,

                                shape =
                                    RoundedCornerShape(
                                        16.dp
                                    ),

                                modifier =
                                    Modifier.fillMaxWidth()
                            )
                        }


                        // =============================================
                        // CREATE CUSTOMER
                        // =============================================

                        item {

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(
                                        52.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            16.dp
                                        )
                                    )
                                    .background(
                                        Brush.horizontalGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFF7351BD),
                                                    Color(0xFF9450C5)
                                                )
                                        )
                                    )
                                    .clickable {

                                        page =
                                            "CREATE"

                                        message =
                                            ""
                                    },

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text =
                                        "+  CREATE CUSTOMER",

                                    color =
                                        Color.White,

                                    fontSize =
                                        12.sp,

                                    fontWeight =
                                        FontWeight.Bold,

                                    letterSpacing =
                                        0.3.sp,

                                    maxLines =
                                        1
                                )
                            }
                        }


                        val filtered =
                            customers.filter {

                                it.fullName
                                    .orEmpty()
                                    .contains(
                                        query.trim(),
                                        ignoreCase = true
                                    ) ||

                                        it.nic
                                            .orEmpty()
                                            .contains(
                                                query.trim(),
                                                ignoreCase = true
                                            )
                            }


                        if (
                            filtered.isEmpty()
                        ) {

                            item {

                                EmptyStateCard(
                                    title =
                                        "No customers found",
                                    message =
                                        "Try another name or NIC, or create a new customer."
                                )
                            }
                        }


                        items(
                            items =
                                filtered,

                            key = {
                                it.customerId!!
                            }
                        ) { customer ->


                            CustomerSelectionCard(
                                customer =
                                    customer,

                                selected =
                                    customerId ==
                                            customer.customerId,

                                onClick = {

                                    customerId =
                                        customer.customerId

                                    page =
                                        "TYPE"

                                    message =
                                        ""
                                }
                            )
                        }
                    }


                    // =================================================
                    // CREATE CUSTOMER
                    // =================================================

                    "CREATE" -> {


                        item {

                            CaptureSectionHeader(
                                step =
                                    "NEW",
                                title =
                                    "Create Customer",
                                subtitle =
                                    "Enter the customer details below."
                            )
                        }


                        item {

                            CustomerInput(
                                label =
                                    "Full name *",

                                value =
                                    fullName,

                                enabled =
                                    !saving
                            ) {

                                fullName =
                                    it
                            }
                        }


                        item {

                            CustomerInput(
                                label =
                                    "NIC *",

                                value =
                                    nic,

                                enabled =
                                    !saving
                            ) {

                                nic =
                                    it
                            }
                        }


                        item {

                            CustomerInput(
                                label =
                                    "Phone (optional)",

                                value =
                                    phone,

                                enabled =
                                    !saving
                            ) {

                                phone =
                                    it
                            }
                        }


                        item {

                            CustomerInput(
                                label =
                                    "Email (optional)",

                                value =
                                    email,

                                enabled =
                                    !saving
                            ) {

                                email =
                                    it
                            }
                        }


                        item {

                            CustomerInput(
                                label =
                                    "Address (optional)",

                                value =
                                    address,

                                enabled =
                                    !saving
                            ) {

                                address =
                                    it
                            }
                        }


                        item {

                            Button(
                                enabled =
                                    !saving &&
                                            fullName.isNotBlank() &&
                                            nic.isNotBlank(),

                                onClick = {

                                    saving =
                                        true

                                    message =
                                        ""

                                    scope.launch {

                                        try {

                                            val response =
                                                RetrofitClient
                                                    .apiService
                                                    .registerCustomer(
                                                        RegisterCustomerRequest(
                                                            fullName =
                                                                fullName
                                                                    .trim(),

                                                            nic =
                                                                nic
                                                                    .trim()
                                                                    .uppercase(
                                                                        Locale.ROOT
                                                                    ),

                                                            phone =
                                                                phone
                                                                    .trim()
                                                                    .ifBlank {
                                                                        null
                                                                    },

                                                            email =
                                                                email
                                                                    .trim()
                                                                    .ifBlank {
                                                                        null
                                                                    },

                                                            address =
                                                                address
                                                                    .trim()
                                                                    .ifBlank {
                                                                        null
                                                                    }
                                                        )
                                                    )


                                            val created =
                                                response.body()


                                            if (
                                                response.isSuccessful &&
                                                created?.customerId != null
                                            ) {

                                                customers =
                                                    (
                                                            customers.filter {

                                                                it.customerId !=
                                                                        created.customerId

                                                            } + created
                                                            )
                                                        .sortedBy {

                                                            it.fullName
                                                                .orEmpty()
                                                        }


                                                customerId =
                                                    created.customerId


                                                fullName =
                                                    ""

                                                nic =
                                                    ""

                                                phone =
                                                    ""

                                                email =
                                                    ""

                                                address =
                                                    ""


                                                page =
                                                    "TYPE"

                                            } else {

                                                message =
                                                    when (
                                                        response.code()
                                                    ) {

                                                        400 ->
                                                            "Check the customer fields. Each field must be at most 255 characters."

                                                        409 ->
                                                            "This NIC already exists. Go back and select the existing customer."

                                                        in 200..299 ->
                                                            "Registration response was incomplete. Refresh customers before retrying."

                                                        else ->
                                                            preparationError(
                                                                response.code()
                                                            )
                                                    }
                                            }

                                        } catch (
                                            e: CancellationException
                                        ) {

                                            throw e

                                        } catch (
                                            e: Exception
                                        ) {

                                            message =
                                                "Could not confirm registration. Go back and refresh customers before retrying."

                                        } finally {

                                            saving =
                                                false
                                        }
                                    }
                                },

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(
                                            54.dp
                                        ),

                                shape =
                                    RoundedCornerShape(
                                        16.dp
                                    ),

                                colors =
                                    ButtonDefaults
                                        .buttonColors(
                                            containerColor =
                                                Color(0xFF7650BD)
                                        )
                            ) {

                                if (
                                    saving
                                ) {

                                    CircularProgressIndicator(
                                        modifier =
                                            Modifier.size(
                                                22.dp
                                            ),

                                        strokeWidth =
                                            2.dp,

                                        color =
                                            Color.White
                                    )

                                } else {

                                    Text(
                                        text =
                                            "SAVE AND SELECT CUSTOMER",

                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                }
                            }
                        }


                        item {

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(
                                        RoundedCornerShape(16.dp)
                                    )
                                    .background(
                                        Color(0xFFF2ECFA)
                                    )
                                    .clickable(
                                        enabled = !saving
                                    ) {
                                        page = "CUSTOMER"
                                        message = ""
                                    },

                                contentAlignment = Alignment.Center
                            ) {

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {

                                    Text(
                                        text = "‹",
                                        fontSize = 24.sp,
                                        color = Color(0xFF7650BD)
                                    )

                                    Spacer(
                                        modifier = Modifier.width(8.dp)
                                    )

                                    Text(
                                        text = "BACK TO CUSTOMERS",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.4.sp,
                                        color = Color(0xFF7650BD)
                                    )
                                }
                            }
                        }
                    }


                    // =================================================
                    // DOCUMENT TYPE
                    // =================================================

                    "TYPE" -> {


                        item {

                            CaptureSectionHeader(
                                step =
                                    "02",
                                title =
                                    "Document Type",
                                subtitle =
                                    "Choose the type of document you are going to capture."
                            )
                        }


                        item {

                            SelectedCustomerCard(
                                customer =
                                    selectedCustomer,

                                onChange = {
                                    page =
                                        "CUSTOMER"
                                }
                            )
                        }


                        if (
                            types.isEmpty()
                        ) {

                            item {

                                EmptyStateCard(
                                    title =
                                        "No document types",
                                    message =
                                        "No active document types are available. Ask an administrator to configure them."
                                )
                            }


                            item {

                                OutlinedButton(
                                    onClick = {
                                        reload++
                                    },

                                    modifier =
                                        Modifier.fillMaxWidth(),

                                    shape =
                                        RoundedCornerShape(
                                            16.dp
                                        )
                                ) {

                                    Text(
                                        "REFRESH"
                                    )
                                }
                            }
                        }


                        items(
                            items =
                                types,

                            key = {
                                it.documentTypeId!!
                            }
                        ) { type ->


                            DocumentTypeCard(
                                type =
                                    type,

                                enabled =
                                    selectedCustomer != null,

                                onClick = {

                                    typeCode =
                                        type.typeCode

                                    page =
                                        "SUMMARY"
                                }
                            )
                        }
                    }


                    // =================================================
                    // SUMMARY
                    // =================================================

                    "SUMMARY" -> {


                        item {

                            CaptureSectionHeader(
                                step =
                                    "03",
                                title =
                                    "Ready to Capture",
                                subtitle =
                                    "Confirm the details before opening the secure camera."
                            )
                        }


                        item {

                            CaptureSummaryCard(
                                customer =
                                    selectedCustomer,

                                type =
                                    selectedType
                            )
                        }


                        // =============================================
                        // DRAFT SAVED
                        // =============================================

                        if (
                            draftSaved
                        ) {

                            item {

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(
                                                RoundedCornerShape(
                                                    18.dp
                                                )
                                            )
                                            .background(
                                                Color(0xFFEAF7EF)
                                            )
                                            .padding(
                                                16.dp
                                            )
                                ) {

                                    Column {

                                        Text(
                                            text =
                                                "Draft saved successfully",

                                            fontSize =
                                                15.sp,

                                            fontWeight =
                                                FontWeight.Bold,

                                            color =
                                                Color(0xFF247446)
                                        )


                                        Spacer(
                                            modifier =
                                                Modifier.height(
                                                    4.dp
                                                )
                                        )


                                        Text(
                                            text =
                                                "The encrypted draft is stored securely on this device.",

                                            fontSize =
                                                12.sp,

                                            lineHeight =
                                                17.sp,

                                            color =
                                                Color(0xFF587363)
                                        )
                                    }
                                }
                            }
                        }


                        // =============================================
                        // OPEN SECURE CAMERA
                        // =============================================

                        item {

                            val canCapture =
                                selectedCustomer != null &&
                                        selectedType != null


                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(
                                        58.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            18.dp
                                        )
                                    )
                                    .background(
                                        if (
                                            canCapture
                                        ) {

                                            Brush.horizontalGradient(
                                                colors =
                                                    listOf(
                                                        Color(0xFF6E50B7),
                                                        Color(0xFF9650C1),
                                                        Color(0xFFD04E93)
                                                    )
                                            )

                                        } else {

                                            Brush.horizontalGradient(
                                                colors =
                                                    listOf(
                                                        Color(0xFFD5CEDD),
                                                        Color(0xFFD5CEDD)
                                                    )
                                            )
                                        }
                                    )
                                    .clickable(
                                        enabled =
                                            canCapture
                                    ) {

                                        val customer =
                                            selectedCustomer

                                        val type =
                                            selectedType

                                        val selectedId =
                                            customer
                                                ?.customerId

                                        val selectedCode =
                                            type
                                                ?.typeCode


                                        if (
                                            selectedId != null &&
                                            !selectedCode.isNullOrBlank()
                                        ) {

                                            draftSaved =
                                                false


                                            cameraLauncher.launch(

                                                Intent(
                                                    context,
                                                    LiveCaptureActivity::class.java
                                                )
                                                    .putExtra(
                                                        "customerId",
                                                        selectedId
                                                    )
                                                    .putExtra(
                                                        "customerName",
                                                        customer
                                                            .fullName
                                                            .orEmpty()
                                                    )
                                                    .putExtra(
                                                        "documentTypeCode",
                                                        selectedCode
                                                    )
                                                    .putExtra(
                                                        "documentTypeName",
                                                        type
                                                            .typeName
                                                    )
                                            )
                                        }
                                    },

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text =
                                        "OPEN SECURE CAMERA",

                                    color =
                                        Color.White,

                                    fontSize =
                                        13.sp,

                                    fontWeight =
                                        FontWeight.Bold,

                                    letterSpacing =
                                        0.7.sp
                                )
                            }
                        }


                        // =============================================
                        // VIEW LOCAL DRAFTS
                        // =============================================

                        item {

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(
                                        54.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            17.dp
                                        )
                                    )
                                    .background(
                                        Color(0xFFF1EBFA)
                                    )
                                    .clickable {

                                        context.startActivity(
                                            Intent(
                                                context,
                                                CaptureDraftsActivity::class.java
                                            )
                                        )
                                    }
                                    .padding(
                                        horizontal =
                                            18.dp
                                    ),

                                contentAlignment =
                                    Alignment.Center
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
                                                    32.dp
                                                )
                                                .clip(
                                                    RoundedCornerShape(
                                                        10.dp
                                                    )
                                                )
                                                .background(
                                                    Brush.linearGradient(
                                                        colors =
                                                            listOf(
                                                                Color(0xFF7650BD),
                                                                Color(0xFFB44BC8)
                                                            )
                                                    )
                                                ),

                                        contentAlignment =
                                            Alignment.Center
                                    ) {

                                        Text(
                                            text =
                                                "DR",

                                            color =
                                                Color.White,

                                            fontSize =
                                                9.sp,

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


                                    Text(
                                        text =
                                            "View Local Drafts",

                                        modifier =
                                            Modifier.weight(
                                                1f
                                            ),

                                        fontSize =
                                            13.sp,

                                        fontWeight =
                                            FontWeight.Bold,

                                        color =
                                            Color(0xFF5E438F)
                                    )


                                    Text(
                                        text =
                                            "›",

                                        fontSize =
                                            26.sp,

                                        color =
                                            Color(0xFF7650BD)
                                    )
                                }
                            }
                        }


                        // =============================================
                        // CHANGE OPTIONS
                        // =============================================

                        item {

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),

                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        12.dp
                                    )
                            ) {


                                // CHANGE TYPE
                                Box(
                                    modifier = Modifier
                                        .weight(
                                            1f
                                        )
                                        .height(
                                            50.dp
                                        )
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

                                            page =
                                                "TYPE"
                                        },

                                    contentAlignment =
                                        Alignment.Center
                                ) {

                                    Text(
                                        text =
                                            "CHANGE TYPE",

                                        fontSize =
                                            11.sp,

                                        fontWeight =
                                            FontWeight.Bold,

                                        letterSpacing =
                                            0.3.sp,

                                        color =
                                            Color(0xFF7650BD),

                                        maxLines =
                                            1
                                    )
                                }


                                // CHANGE CUSTOMER
                                Box(
                                    modifier = Modifier
                                        .weight(
                                            1f
                                        )
                                        .height(
                                            50.dp
                                        )
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

                                            page =
                                                "CUSTOMER"
                                        },

                                    contentAlignment =
                                        Alignment.Center
                                ) {

                                    Text(
                                        text =
                                            "CHANGE CUSTOMER",

                                        fontSize =
                                            10.sp,

                                        fontWeight =
                                            FontWeight.Bold,

                                        letterSpacing =
                                            0.2.sp,

                                        color =
                                            Color(0xFF7650BD),

                                        maxLines =
                                            1
                                    )
                                }
                            }
                        }
                    }
                }
            }


            item {

                Spacer(
                    modifier =
                        Modifier.height(
                            30.dp
                        )
                )
            }
        }
    }
}


// =====================================================================
// STEP INDICATOR
// =====================================================================

@Composable
private fun CaptureStepIndicator(
    page: String
) {

    val currentStep =
        when (
            page
        ) {

            "CUSTOMER",
            "CREATE" ->
                1

            "TYPE" ->
                2

            else ->
                3
        }


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
                            0.94f
                    )
                )
                .padding(
                    16.dp
                )
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically,

            horizontalArrangement =
                Arrangement.Center
        ) {

            CaptureStepCircle(
                number =
                    1,

                title =
                    "Customer",

                active =
                    currentStep >= 1
            )


            CaptureStepLine(
                active =
                    currentStep >= 2
            )


            CaptureStepCircle(
                number =
                    2,

                title =
                    "Type",

                active =
                    currentStep >= 2
            )


            CaptureStepLine(
                active =
                    currentStep >= 3
            )


            CaptureStepCircle(
                number =
                    3,

                title =
                    "Capture",

                active =
                    currentStep >= 3
            )
        }
    }
}


@Composable
private fun CaptureStepCircle(
    number: Int,
    title: String,
    active: Boolean
) {

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Box(
            modifier =
                Modifier
                    .size(
                        34.dp
                    )
                    .clip(
                        CircleShape
                    )
                    .background(
                        if (
                            active
                        ) {

                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        Color(0xFFFF7E42),
                                        Color(0xFFF04F89),
                                        Color(0xFF8E4FD0)
                                    )
                            )

                        } else {

                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        Color(0xFFE6E0EB),
                                        Color(0xFFE6E0EB)
                                    )
                            )
                        }
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    number.toString(),

                color =
                    if (
                        active
                    ) {

                        Color.White

                    } else {

                        Color(0xFF8B8494)
                    },

                fontSize =
                    13.sp,

                fontWeight =
                    FontWeight.Bold
            )
        }


        Spacer(
            modifier =
                Modifier.height(
                    5.dp
                )
        )


        Text(
            text =
                title,

            fontSize =
                9.sp,

            color =
                if (
                    active
                ) {

                    Color(0xFF5E477F)

                } else {

                    Color(0xFF99929F)
                }
        )
    }
}


@Composable
private fun CaptureStepLine(
    active: Boolean
) {

    Box(
        modifier =
            Modifier
                .padding(
                    horizontal =
                        6.dp
                )
                .width(
                    42.dp
                )
                .height(
                    2.dp
                )
                .background(
                    if (
                        active
                    ) {

                        Color(0xFFC05DAA)

                    } else {

                        Color(0xFFE3DCE7)
                    }
                )
    )
}


// =====================================================================
// SECTION HEADER
// =====================================================================

@Composable
private fun CaptureSectionHeader(
    step: String,
    title: String,
    subtitle: String
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
                                listOf(
                                    Color(0xFFFF8247),
                                    Color(0xFFF15893),
                                    Color(0xFF9A4DCE)
                                )
                        )
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    step,

                color =
                    Color.White,

                fontWeight =
                    FontWeight.Bold,

                fontSize =
                    14.sp
            )
        }


        Spacer(
            modifier =
                Modifier.width(
                    14.dp
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
                    20.sp,

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
                    subtitle,

                fontSize =
                    12.sp,

                lineHeight =
                    16.sp,

                color =
                    Color(0xFF817A8A)
            )
        }
    }
}


// =====================================================================
// CUSTOMER SELECTION CARD
// =====================================================================

@Composable
private fun CustomerSelectionCard(
    customer: Customer,
    selected: Boolean,
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
                    if (
                        selected
                    ) {

                        Color(0xFFF2EBFA)

                    } else {

                        Color.White.copy(
                            alpha =
                                0.94f
                        )
                    }
                )
                .clickable {
                    onClick()
                }
                .padding(
                    16.dp
                )
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier =
                    Modifier
                        .size(
                            46.dp
                        )
                        .clip(
                            CircleShape
                        )
                        .background(
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        Color(0xFFFF8C45),
                                        Color(0xFFD44D9A)
                                    )
                            )
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        customer.fullName
                            .orEmpty()
                            .take(
                                1
                            )
                            .uppercase(),

                    color =
                        Color.White,

                    fontWeight =
                        FontWeight.Bold
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        14.dp
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
                        customer.fullName
                            ?: "Unnamed customer",

                    fontSize =
                        15.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF181A2D)
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            4.dp
                        )
                )


                Text(
                    text =
                        "NIC: ${
                            customer.nic
                                ?: "Not provided"
                        }",

                    fontSize =
                        12.sp,

                    color =
                        Color(0xFF7C7587)
                )
            }


            Text(
                text =
                    "›",

                fontSize =
                    28.sp,

                color =
                    Color(0xFF8C8494)
            )
        }
    }
}


// =====================================================================
// SELECTED CUSTOMER CARD
// =====================================================================

@Composable
private fun SelectedCustomerCard(
    customer: Customer?,
    onChange: () -> Unit
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
                    Color(0xFFF4EEFA)
                )
                .padding(
                    16.dp
                )
    ) {

        Column {

            Text(
                text =
                    "Selected Customer",

                fontSize =
                    11.sp,

                color =
                    Color(0xFF7A6D87)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )


            Text(
                text =
                    customer
                        ?.fullName
                        ?: "Please select again",

                fontSize =
                    16.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF1C1930)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        3.dp
                    )
            )


            Text(
                text =
                    "NIC: ${
                        customer
                            ?.nic
                            ?: "Not available"
                    }",

                fontSize =
                    12.sp,

                color =
                    Color(0xFF7C7587)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )


            Text(
                text =
                    "Change customer",

                modifier =
                    Modifier.clickable {
                        onChange()
                    },

                fontSize =
                    12.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF7650BD)
            )
        }
    }
}


// =====================================================================
// DOCUMENT TYPE CARD
// =====================================================================

@Composable
private fun DocumentTypeCard(
    type: DocumentType,
    enabled: Boolean,
    onClick: () -> Unit
) {

    val title =
        type.typeName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: type.typeCode
                .orEmpty()


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
                            0.94f
                    )
                )
                .clickable(
                    enabled =
                        enabled
                ) {

                    onClick()
                }
                .padding(
                    16.dp
                )
    ) {

        Row(
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
                                        Color(0xFF784FD0),
                                        Color(0xFFC253C8)
                                    )
                            )
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        type.typeCode
                            .orEmpty()
                            .take(
                                2
                            ),

                    color =
                        Color.White,

                    fontWeight =
                        FontWeight.Bold,

                    fontSize =
                        12.sp
                )
            }


            Spacer(
                modifier =
                    Modifier.width(
                        14.dp
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
                        15.sp,

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
                        type.typeCode
                            .orEmpty(),

                    fontSize =
                        11.sp,

                    color =
                        Color(0xFF81798B)
                )
            }


            Text(
                text =
                    "›",

                fontSize =
                    28.sp,

                color =
                    Color(0xFF8C8494)
            )
        }
    }
}


// =====================================================================
// SUMMARY CARD
// =====================================================================

@Composable
private fun CaptureSummaryCard(
    customer: Customer?,
    type: DocumentType?
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
                    Color.White.copy(
                        alpha =
                            0.95f
                    )
                )
                .padding(
                    20.dp
                )
    ) {

        Column {

            Text(
                text =
                    "Capture Summary",

                fontSize =
                    17.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            SummaryRow(
                label =
                    "Customer",

                value =
                    customer
                        ?.fullName
                        ?: "Selection unavailable"
            )


            SummaryRow(
                label =
                    "NIC",

                value =
                    customer
                        ?.nic
                        .orEmpty()
                        .ifBlank {
                            "Not provided"
                        }
            )


            SummaryRow(
                label =
                    "Document",

                value =
                    type
                        ?.typeName
                        ?: type
                            ?.typeCode
                        ?: "Selection unavailable"
            )


            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
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
                            Color(0xFFFFF3EA)
                        )
                        .padding(
                            13.dp
                        )
            ) {

                Text(
                    text =
                        "The secure camera will capture the document and save an encrypted draft on this device.",

                    fontSize =
                        12.sp,

                    lineHeight =
                        17.sp,

                    color =
                        Color(0xFF725C4D)
                )
            }
        }
    }
}


@Composable
private fun SummaryRow(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        6.dp
                )
    ) {

        Text(
            text =
                label,

            modifier =
                Modifier.width(
                    92.dp
                ),

            fontSize =
                12.sp,

            color =
                Color(0xFF81798A)
        )


        Text(
            text =
                value,

            modifier =
                Modifier.weight(
                    1f
                ),

            fontSize =
                13.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color(0xFF1C1A2E)
        )
    }
}


// =====================================================================
// EMPTY STATE
// =====================================================================

@Composable
private fun EmptyStateCard(
    title: String,
    message: String
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        18.dp
                    )
                )
                .background(
                    Color.White.copy(
                        alpha =
                            0.92f
                    )
                )
                .padding(
                    18.dp
                )
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                text =
                    title,

                fontSize =
                    15.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF252034)
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

                textAlign =
                    TextAlign.Center,

                color =
                    Color(0xFF817A8A)
            )
        }
    }
}


// =====================================================================
// CUSTOMER INPUT
// =====================================================================

@Composable
private fun CustomerInput(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit
) {

    OutlinedTextField(
        value =
            value,

        onValueChange = {

            if (
                it.length <= 255
            ) {

                onChange(
                    it
                )
            }
        },

        label = {
            Text(
                label
            )
        },

        enabled =
            enabled,

        singleLine =
            true,

        shape =
            RoundedCornerShape(
                16.dp
            ),

        modifier =
            Modifier.fillMaxWidth()
    )
}


// =====================================================================
// ERROR MESSAGE
// =====================================================================

private fun preparationError(
    code: Int
): String {

    return when (
        code
    ) {

        401 ->
            "Your session has expired. Log out and sign in again."

        403 ->
            "Access denied. Check your account role and restart the updated backend."

        else ->
            "Request failed (HTTP $code). Please check the backend and retry."
    }
}
