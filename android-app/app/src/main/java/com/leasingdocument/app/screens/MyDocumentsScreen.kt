package com.leasingdocument.app.screens

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.Customer
import com.leasingdocument.app.network.Document
import com.leasingdocument.app.network.DocumentType
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private enum class AgentDocumentsPage {
    OVERVIEW,
    CUSTOMER_TYPES,
    TYPE_DOCUMENTS,
    DOCUMENT_DETAILS
}

private enum class AgentDocumentsFilter {
    ALL,
    VERIFIED,
    PENDING
}

@Composable
fun MyDocumentsScreen(
    onBack: () -> Unit
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var documents by remember {
        mutableStateOf<List<Document>>(emptyList())
    }

    var customers by remember {
        mutableStateOf<List<Customer>>(emptyList())
    }

    var documentTypes by remember {
        mutableStateOf<List<DocumentType>>(emptyList())
    }

    var page by remember {
        mutableStateOf(AgentDocumentsPage.OVERVIEW)
    }

    var selectedCustomer by remember {
        mutableStateOf<Customer?>(null)
    }

    var selectedCustomerId by remember {
        mutableStateOf<Long?>(null)
    }

    var selectedDocumentTypeId by remember {
        mutableStateOf<Long?>(null)
    }

    var selectedDocument by remember {
        mutableStateOf<Document?>(null)
    }

    var selectedFileName by remember {
        mutableStateOf<String?>(null)
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    var notice by remember {
        mutableStateOf("")
    }

    var downloadingFileName by remember {
        mutableStateOf<String?>(null)
    }

    var search by remember {
        mutableStateOf("")
    }

    var selectedFilter by remember {
        mutableStateOf(AgentDocumentsFilter.ALL)
    }


    // =========================================================
    // BACK NAVIGATION
    // =========================================================

    fun goBack() {

        if (selectedFileName != null) {
            selectedFileName = null
            return
        }

        when (page) {

            AgentDocumentsPage.DOCUMENT_DETAILS -> {
                selectedDocument = null
                page = AgentDocumentsPage.TYPE_DOCUMENTS
            }

            AgentDocumentsPage.TYPE_DOCUMENTS -> {
                selectedDocument = null
                selectedDocumentTypeId = null
                page = AgentDocumentsPage.CUSTOMER_TYPES
            }

            AgentDocumentsPage.CUSTOMER_TYPES -> {
                selectedCustomer = null
                selectedCustomerId = null
                selectedDocumentTypeId = null
                selectedDocument = null
                page = AgentDocumentsPage.OVERVIEW
            }

            AgentDocumentsPage.OVERVIEW -> {
                onBack()
            }
        }
    }

    BackHandler {
        goBack()
    }


    // =========================================================
    // FULL DOCUMENT VIEW
    // =========================================================

    if (selectedFileName != null) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFCF9FF))
        ) {

            DocumentImageScreen(
                fileName = selectedFileName!!
            )
        }

        return
    }


    // =========================================================
    // LOAD DATA
    // =========================================================

    LaunchedEffect(Unit) {

        loading = true
        errorMessage = ""

        try {

            val api = RetrofitClient.apiService

            val documentResponse = api.getMyDocuments()

            if (!documentResponse.isSuccessful) {

                errorMessage =
                    "Failed to load documents (${documentResponse.code()})"

                return@LaunchedEffect
            }

            documents =
                documentResponse
                    .body()
                    .orEmpty()
                    .sortedByDescending {
                        it.documentId ?: 0L
                    }

            val customerResponse = api.getCustomers()

            customers =
                if (customerResponse.isSuccessful) {
                    customerResponse.body().orEmpty()
                } else {
                    emptyList()
                }

            val typeResponse = api.getDocumentTypes()

            documentTypes =
                if (typeResponse.isSuccessful) {
                    typeResponse.body().orEmpty()
                } else {
                    emptyList()
                }

        } catch (e: Exception) {

            errorMessage =
                "Cannot connect to server"

        } finally {

            loading = false
        }
    }


    // =========================================================
    // OVERVIEW DATA
    // =========================================================

    val verifiedCount =
        documents.count {
            isVerifiedAgentDocument(it)
        }

    val pendingCount =
        documents.size - verifiedCount

    val liveCount =
        documents.count {
            it.captureSource
                ?.equals(
                    "LIVE_CAMERA",
                    ignoreCase = true
                ) == true
        }

    val searchText =
        search.trim()

    val filteredOverviewDocuments =
        documents.filter { document ->

            val matchesFilter =
                when (selectedFilter) {

                    AgentDocumentsFilter.ALL ->
                        true

                    AgentDocumentsFilter.VERIFIED ->
                        isVerifiedAgentDocument(document)

                    AgentDocumentsFilter.PENDING ->
                        !isVerifiedAgentDocument(document)
                }

            val customer =
                customers.firstOrNull {
                    it.customerId == document.customerId
                }

            val matchesSearch =
                searchText.isBlank() ||
                        listOf(
                            document.documentId?.toString(),
                            document.fileName,
                            document.customerId?.toString(),
                            document.captureStatus,
                            document.verificationStatus,
                            document.status,
                            customer?.fullName,
                            customer?.nic,
                            customer?.phone,
                            customer?.email
                        ).any {
                            it
                                .orEmpty()
                                .contains(
                                    searchText,
                                    ignoreCase = true
                                )
                        }

            matchesFilter && matchesSearch
        }

    val customerGroups =
        filteredOverviewDocuments
            .groupBy {
                it.customerId
            }
            .entries
            .sortedBy { entry ->
                customerNameForAgentDocuments(
                    customerId = entry.key,
                    customers = customers
                ).lowercase(Locale.ROOT)
            }

    val selectedCustomerDocuments =
        documents
            .filter {
                it.customerId == selectedCustomerId
            }
            .sortedByDescending {
                it.documentId ?: 0L
            }

    val selectedDocumentType =
        documentTypes.firstOrNull {
            it.documentTypeId == selectedDocumentTypeId
        }

    val selectedTypeDocuments =
        selectedCustomerDocuments
            .filter {
                it.documentTypeId == selectedDocumentTypeId
            }
            .sortedByDescending {
                it.documentId ?: 0L
            }


    // =========================================================
    // DOWNLOAD
    // =========================================================

    fun downloadDocument(
        fileName: String
    ) {

        if (downloadingFileName != null) {
            return
        }

        downloadingFileName = fileName
        notice = "Downloading document..."

        scope.launch {

            var bytesToClear: ByteArray? = null

            try {

                val response =
                    RetrofitClient
                        .apiService
                        .getDocumentImage(fileName)

                if (!response.isSuccessful) {

                    notice =
                        "Download failed (${response.code()})"

                    return@launch
                }

                bytesToClear =
                    response
                        .body()
                        ?.bytes()

                val bytes =
                    bytesToClear

                if (bytes == null) {

                    notice =
                        "Download failed"

                    return@launch
                }

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {

                    val values =
                        ContentValues().apply {

                            put(
                                MediaStore.Downloads.DISPLAY_NAME,
                                fileName
                            )

                            put(
                                MediaStore.Downloads.MIME_TYPE,
                                "application/octet-stream"
                            )

                            put(
                                MediaStore.Downloads.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS
                            )
                        }

                    val uri =
                        context
                            .contentResolver
                            .insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                values
                            )

                    if (uri != null) {

                        context
                            .contentResolver
                            .openOutputStream(uri)
                            ?.use {
                                it.write(bytes)
                            }

                        notice =
                            "Downloaded successfully"

                    } else {

                        notice =
                            "Download failed"
                    }

                } else {

                    val directory =
                        context
                            .getExternalFilesDir(
                                Environment.DIRECTORY_DOWNLOADS
                            )

                    val file =
                        File(
                            directory,
                            fileName
                        )

                    FileOutputStream(file).use {
                        it.write(bytes)
                    }

                    notice =
                        "Downloaded successfully"
                }

            } catch (e: Exception) {

                notice =
                    "Download failed"

            } finally {

                bytesToClear?.fill(0)
                downloadingFileName = null
            }
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

        AgentDocumentsBackground()

        when (page) {

            AgentDocumentsPage.OVERVIEW -> {

                AgentDocumentsOverviewPage(
                    totalDocuments = documents.size,
                    verifiedDocuments = verifiedCount,
                    pendingDocuments = pendingCount,
                    liveDocuments = liveCount,
                    customerGroups = customerGroups,
                    customers = customers,
                    loading = loading,
                    errorMessage = errorMessage,
                    notice = notice,
                    search = search,
                    selectedFilter = selectedFilter,
                    onSearchChange = {
                        search = it
                    },
                    onFilterChange = {
                        selectedFilter = it
                    },
                    onCustomerOpen = { customerId ->

                        selectedCustomerId = customerId

                        selectedCustomer =
                            customers.firstOrNull {
                                it.customerId == customerId
                            }

                        selectedDocumentTypeId = null
                        selectedDocument = null
                        page = AgentDocumentsPage.CUSTOMER_TYPES
                    }
                )
            }


            AgentDocumentsPage.CUSTOMER_TYPES -> {

                AgentCustomerDocumentTypesPage(
                    customer = selectedCustomer,
                    customerId = selectedCustomerId,
                    documents = selectedCustomerDocuments,
                    documentTypes = documentTypes,
                    loading = loading,
                    errorMessage = errorMessage,
                    notice = notice,
                    onTypeOpen = { typeId ->

                        selectedDocumentTypeId = typeId
                        selectedDocument = null
                        page = AgentDocumentsPage.TYPE_DOCUMENTS
                    }
                )
            }


            AgentDocumentsPage.TYPE_DOCUMENTS -> {

                AgentDocumentTypeDocumentsPage(
                    customer = selectedCustomer,
                    type = selectedDocumentType,
                    documents = selectedTypeDocuments,
                    downloadingFileName = downloadingFileName,
                    loading = loading,
                    errorMessage = errorMessage,
                    notice = notice,
                    onDocumentOpen = { document ->
                        selectedDocument = document
                        page = AgentDocumentsPage.DOCUMENT_DETAILS
                    },
                    onDownload = { document ->

                        val fileName =
                            document.fileName

                        if (!fileName.isNullOrBlank()) {
                            downloadDocument(fileName)
                        }
                    }
                )
            }


            AgentDocumentsPage.DOCUMENT_DETAILS -> {

                selectedDocument?.let { document ->

                    AgentDocumentDetailsPage(
                        document = document,
                        customer = selectedCustomer,
                        type =
                            documentTypes.firstOrNull {
                                it.documentTypeId ==
                                        document.documentTypeId
                            },
                        downloading =
                            downloadingFileName ==
                                    document.fileName,
                        notice = notice,
                        onView = {

                            val fileName =
                                document.fileName

                            if (!fileName.isNullOrBlank()) {
                                selectedFileName = fileName
                            }
                        },
                        onDownload = {

                            val fileName =
                                document.fileName

                            if (!fileName.isNullOrBlank()) {
                                downloadDocument(fileName)
                            }
                        }
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
private fun AgentDocumentsBackground() {

    Canvas(
        modifier =
            Modifier.fillMaxSize()
    ) {

        val w = size.width
        val h = size.height

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
            path = topShape,
            brush =
                Brush.linearGradient(
                    colors = listOf(
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
            path = ribbon,
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
                    colors = listOf(
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
// PAGE 1 — PROFESSIONAL OVERVIEW
// =====================================================================

@Composable
private fun AgentDocumentsOverviewPage(
    totalDocuments: Int,
    verifiedDocuments: Int,
    pendingDocuments: Int,
    liveDocuments: Int,
    customerGroups: List<Map.Entry<Long?, List<Document>>>,
    customers: List<Customer>,
    loading: Boolean,
    errorMessage: String,
    notice: String,
    search: String,
    selectedFilter: AgentDocumentsFilter,
    onSearchChange: (String) -> Unit,
    onFilterChange: (AgentDocumentsFilter) -> Unit,
    onCustomerOpen: (Long?) -> Unit
) {

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                horizontal = 22.dp
            ),
        contentPadding =
            PaddingValues(
                bottom = 30.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Text(
                text = "My Documents",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A2C)
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "Manage secure submissions by customer.",
                fontSize = 12.sp,
                color = Color(0xFF817A8B)
            )
        }


        item {

            AgentDocumentsOverviewHero(
                total = totalDocuments,
                verified = verifiedDocuments,
                pending = pendingDocuments,
                live = liveDocuments
            )
        }


        if (notice.isNotBlank()) {

            item {

                AgentDocumentsMessageCard(
                    message = notice,
                    error = false
                )
            }
        }


        if (loading) {

            item {

                AgentDocumentsLoadingCard(
                    text =
                        "Loading document workspace..."
                )
            }
        }


        if (errorMessage.isNotBlank()) {

            item {

                AgentDocumentsMessageCard(
                    message = errorMessage,
                    error = true
                )
            }
        }


        item {

            OutlinedTextField(
                value = search,
                onValueChange =
                    onSearchChange,
                label = {
                    Text(
                        "Search documents"
                    )
                },
                placeholder = {
                    Text(
                        "Customer, NIC, file or ID"
                    )
                },
                singleLine = true,
                modifier =
                    Modifier.fillMaxWidth()
            )
        }


        item {

            AgentDocumentFilterRow(
                selected = selectedFilter,
                onSelected =
                    onFilterChange
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
                    text = "Customer Accounts",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1D30)
                )

                Spacer(
                    modifier =
                        Modifier.weight(1f)
                )

                if (!loading) {

                    Text(
                        text =
                            "${customerGroups.size} CUSTOMER${if (customerGroups.size == 1) "" else "S"}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF8D8695)
                    )
                }
            }
        }


        if (
            !loading &&
            errorMessage.isBlank() &&
            customerGroups.isEmpty()
        ) {

            item {

                AgentDocumentsEmptyCard(
                    title =
                        "No matching documents",
                    message =
                        if (totalDocuments == 0) {
                            "Documents submitted by this agent will appear here."
                        } else {
                            "Try another search or verification filter."
                        }
                )
            }
        }


        customerGroups.forEach { entry ->

            val customerId =
                entry.key

            val customerDocuments =
                entry.value

            val customer =
                customers.firstOrNull {
                    it.customerId == customerId
                }

            item(
                key =
                    "customer-${customerId ?: "unknown"}"
            ) {

                ProfessionalCustomerCard(
                    customer = customer,
                    customerId = customerId,
                    documents = customerDocuments,
                    onClick = {
                        onCustomerOpen(customerId)
                    }
                )
            }
        }
    }
}


// =====================================================================
// PAGE 2 — CUSTOMER DOCUMENT TYPES
// =====================================================================

@Composable
private fun AgentCustomerDocumentTypesPage(
    customer: Customer?,
    customerId: Long?,
    documents: List<Document>,
    documentTypes: List<DocumentType>,
    loading: Boolean,
    errorMessage: String,
    notice: String,
    onTypeOpen: (Long?) -> Unit
) {

    val verified =
        documents.count {
            isVerifiedAgentDocument(it)
        }

    val pending =
        documents.size - verified

    val customerName =
        customer?.fullName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Customer #${customerId ?: "-"}"

    val typeGroups =
        documents
            .groupBy {
                it.documentTypeId
            }
            .entries
            .sortedBy { entry ->

                documentTypes
                    .firstOrNull {
                        it.documentTypeId == entry.key
                    }
                    ?.typeName
                    .orEmpty()
                    .lowercase(Locale.ROOT)
            }


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                horizontal = 22.dp
            ),
        contentPadding =
            PaddingValues(
                bottom = 30.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Text(
                text = "Customer Documents",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A2C)
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    customerName,
                fontSize = 12.sp,
                color = Color(0xFF817A8B)
            )
        }


        item {

            CustomerProfileHero(
                customer = customer,
                customerId = customerId,
                total = documents.size,
                verified = verified,
                pending = pending
            )
        }


        if (notice.isNotBlank()) {

            item {

                AgentDocumentsMessageCard(
                    message = notice,
                    error = false
                )
            }
        }


        if (loading) {

            item {

                AgentDocumentsLoadingCard(
                    text =
                        "Loading customer document categories..."
                )
            }
        }


        if (errorMessage.isNotBlank()) {

            item {

                AgentDocumentsMessageCard(
                    message = errorMessage,
                    error = true
                )
            }
        }


        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = "Document Categories",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1D30)
                )

                Spacer(
                    modifier =
                        Modifier.weight(1f)
                )

                if (!loading) {

                    Text(
                        text =
                            "${typeGroups.size} TYPE${if (typeGroups.size == 1) "" else "S"}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF8D8695)
                    )
                }
            }
        }


        if (
            !loading &&
            typeGroups.isEmpty()
        ) {

            item {

                AgentDocumentsEmptyCard(
                    title =
                        "No documents found",
                    message =
                        "This customer has no submitted documents for this agent."
                )
            }
        }


        typeGroups.forEach { entry ->

            val typeId =
                entry.key

            val type =
                documentTypes.firstOrNull {
                    it.documentTypeId == typeId
                }

            item(
                key =
                    "document-type-${typeId ?: "unknown"}"
            ) {

                ProfessionalDocumentTypeCard(
                    type = type,
                    typeId = typeId,
                    documents = entry.value,
                    onClick = {
                        onTypeOpen(typeId)
                    }
                )
            }
        }
    }
}


// =====================================================================
// PAGE 3 — DOCUMENTS INSIDE ONE TYPE
// =====================================================================

@Composable
private fun AgentDocumentTypeDocumentsPage(
    customer: Customer?,
    type: DocumentType?,
    documents: List<Document>,
    downloadingFileName: String?,
    loading: Boolean,
    errorMessage: String,
    notice: String,
    onDocumentOpen: (Document) -> Unit,
    onDownload: (Document) -> Unit
) {

    val typeName =
        type?.typeName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Documents"

    val verified =
        documents.count {
            isVerifiedAgentDocument(it)
        }

    val pending =
        documents.size - verified


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                horizontal = 22.dp
            ),
        contentPadding =
            PaddingValues(
                bottom = 30.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Text(
                text = typeName,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A2C)
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "${customer?.fullName.agentMeta()} • ${documents.size} submitted",
                fontSize = 12.sp,
                color = Color(0xFF817A8B)
            )
        }


        item {

            DocumentTypeProfileHero(
                type = type,
                total = documents.size,
                verified = verified,
                pending = pending
            )
        }


        if (notice.isNotBlank()) {

            item {

                AgentDocumentsMessageCard(
                    message = notice,
                    error = false
                )
            }
        }


        if (loading) {

            item {

                AgentDocumentsLoadingCard(
                    text =
                        "Loading $typeName documents..."
                )
            }
        }


        if (errorMessage.isNotBlank()) {

            item {

                AgentDocumentsMessageCard(
                    message = errorMessage,
                    error = true
                )
            }
        }


        item {

            Text(
                text = "Submitted $typeName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1D30)
            )
        }


        if (
            !loading &&
            documents.isEmpty()
        ) {

            item {

                AgentDocumentsEmptyCard(
                    title =
                        "No documents found",
                    message =
                        "No $typeName documents are available for this customer."
                )
            }
        }


        documents.forEachIndexed { index, document ->

            item(
                key =
                    "type-document-${document.documentId ?: document.hashCode()}"
            ) {

                ProfessionalDocumentCard(
                    document = document,
                    type = type,
                    downloading =
                        downloadingFileName ==
                                document.fileName,
                    sequenceNumber =
                        documents.size - index,
                    onOpen = {
                        onDocumentOpen(document)
                    },
                    onDownload = {
                        onDownload(document)
                    }
                )
            }
        }
    }
}


// =====================================================================
// PAGE 4 — DOCUMENT DETAILS
// =====================================================================

@Composable
private fun AgentDocumentDetailsPage(
    document: Document,
    customer: Customer?,
    type: DocumentType?,
    downloading: Boolean,
    notice: String,
    onView: () -> Unit,
    onDownload: () -> Unit
) {

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                horizontal = 22.dp
            ),
        contentPadding =
            PaddingValues(
                bottom = 30.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Text(
                text = "Document Details",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A2C)
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "Document #${document.documentId ?: "-"}",
                fontSize = 12.sp,
                color = Color(0xFF817A8B)
            )
        }


        item {

            DocumentProfessionalHero(
                document = document,
                type = type
            )
        }


        if (notice.isNotBlank()) {

            item {

                AgentDocumentsMessageCard(
                    message = notice,
                    error = false
                )
            }
        }


        item {

            AgentDetailSection(
                title =
                    "Customer Information"
            ) {

                AgentDetailRow(
                    "Customer",
                    customer?.fullName
                )

                AgentDetailRow(
                    "NIC",
                    customer?.nic
                )

                AgentDetailRow(
                    "Customer ID",
                    document.customerId
                        ?.toString()
                )
            }
        }


        item {

            AgentDetailSection(
                title =
                    "Document Information"
            ) {

                AgentDetailRow(
                    "Document ID",
                    document.documentId
                        ?.toString()
                )

                AgentDetailRow(
                    "Document type",
                    type?.typeName
                )

                AgentDetailRow(
                    "Filename",
                    document.fileName
                )

                AgentDetailRow(
                    "Verification",
                    document.verificationStatus
                )

                AgentDetailRow(
                    "Status",
                    document.status
                )
            }
        }


        item {

            AgentDetailSection(
                title =
                    "Capture Information"
            ) {

                AgentDetailRow(
                    "Capture source",
                    document.captureSource
                )

                AgentDetailRow(
                    "Capture status",
                    document.captureStatus
                )

                AgentDetailRow(
                    "Captured at",
                    document.captureDateTime
                )

                AgentDetailRow(
                    "Uploaded at",
                    document.uploadedAt
                )

                AgentDetailRow(
                    "Location",
                    document.captureLocation
                )

                AgentDetailRow(
                    "Quality",
                    document.qualityStatus
                        ?: document.imageQuality
                )
            }
        }


        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                AgentDocumentPrimaryAction(
                    modifier =
                        Modifier.weight(1f),
                    text =
                        "VIEW ORIGINAL",
                    enabled =
                        !document.fileName.isNullOrBlank(),
                    primary =
                        true,
                    onClick =
                        onView
                )

                AgentDocumentPrimaryAction(
                    modifier =
                        Modifier.weight(1f),
                    text =
                        if (downloading) {
                            "SAVING..."
                        } else {
                            "SAVE COPY"
                        },
                    enabled =
                        !downloading &&
                                !document.fileName.isNullOrBlank(),
                    primary =
                        false,
                    onClick =
                        onDownload
                )
            }
        }
    }
}


// =====================================================================
// OVERVIEW HERO
// =====================================================================

@Composable
private fun AgentDocumentsOverviewHero(
    total: Int,
    verified: Int,
    pending: Int,
    live: Int
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(24.dp)
            )
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
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
                    "Document Workspace",
                fontSize = 12.sp,
                color =
                    Color.White.copy(
                        alpha = 0.84f
                    )
            )

            Spacer(
                modifier =
                    Modifier.height(5.dp)
            )

            Text(
                text =
                    "$total Secure Document${if (total == 1) "" else "s"}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                AgentSummaryPill(
                    label = "VERIFIED",
                    value = verified.toString()
                )

                AgentSummaryPill(
                    label = "PENDING",
                    value = pending.toString()
                )

                AgentSummaryPill(
                    label = "LIVE",
                    value = live.toString()
                )
            }
        }
    }
}


// =====================================================================
// CUSTOMER CARD
// =====================================================================

@Composable
private fun ProfessionalCustomerCard(
    customer: Customer?,
    customerId: Long?,
    documents: List<Document>,
    onClick: () -> Unit
) {

    val name =
        customer?.fullName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Customer #${customerId ?: "-"}"

    val verified =
        documents.count {
            isVerifiedAgentDocument(it)
        }

    val pending =
        documents.size - verified


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(22.dp)
            )
            .background(
                Color.White.copy(
                    alpha = 0.97f
                )
            )
            .clickable {
                onClick()
            }
            .padding(16.dp)
    ) {

        Column {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFF07862),
                                    Color(0xFFD64E94)
                                )
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            agentCustomerInitials(name),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(13.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF171A2C),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            "NIC: ${customer?.nic.agentMeta()}",
                        fontSize = 9.sp,
                        color = Color(0xFF817A8B),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "›",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB2A9BC)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                AgentInfoMetric(
                    modifier =
                        Modifier.weight(1f),
                    label = "DOCUMENTS",
                    value =
                        documents.size.toString()
                )

                AgentInfoMetric(
                    modifier =
                        Modifier.weight(1f),
                    label = "VERIFIED",
                    value =
                        verified.toString()
                )

                AgentInfoMetric(
                    modifier =
                        Modifier.weight(1f),
                    label = "PENDING",
                    value =
                        pending.toString()
                )
            }
        }
    }
}


// =====================================================================
// DOCUMENT TYPE FOLDER CARD
// =====================================================================

@Composable
private fun ProfessionalDocumentTypeCard(
    type: DocumentType?,
    typeId: Long?,
    documents: List<Document>,
    onClick: () -> Unit
) {

    val name =
        type?.typeName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Document Type #${typeId ?: "-"}"

    val code =
        type?.typeCode
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "DOCUMENT CATEGORY"

    val verified =
        documents.count {
            isVerifiedAgentDocument(it)
        }

    val pending =
        documents.size - verified


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(22.dp)
            )
            .background(
                Color.White.copy(
                    alpha = 0.97f
                )
            )
            .clickable {
                onClick()
            }
            .padding(16.dp)
    ) {

        Column {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(
                            RoundedCornerShape(15.dp)
                        )
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF6F52BD),
                                    Color(0xFFD14D92)
                                )
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "DOC",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(13.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF171A2C),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp)
                    )

                    Text(
                        text = code,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        color = Color(0xFF817A8B),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            "${documents.size} submitted document${if (documents.size == 1) "" else "s"}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7650BD)
                    )
                }

                Text(
                    text = "›",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB2A9BC)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                AgentInfoMetric(
                    modifier =
                        Modifier.weight(1f),
                    label = "DOCUMENTS",
                    value =
                        documents.size.toString()
                )

                AgentInfoMetric(
                    modifier =
                        Modifier.weight(1f),
                    label = "VERIFIED",
                    value =
                        verified.toString()
                )

                AgentInfoMetric(
                    modifier =
                        Modifier.weight(1f),
                    label = "PENDING",
                    value =
                        pending.toString()
                )
            }
        }
    }
}


// =====================================================================
// DOCUMENT TYPE HERO
// =====================================================================

@Composable
private fun DocumentTypeProfileHero(
    type: DocumentType?,
    total: Int,
    verified: Int,
    pending: Int
) {

    val typeName =
        type?.typeName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Document Category"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(24.dp)
            )
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF6653BC),
                        Color(0xFF9550C2),
                        Color(0xFFD24D92)
                    )
                )
            )
            .padding(18.dp)
    ) {

        Column {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(
                            RoundedCornerShape(16.dp)
                        )
                        .background(
                            Color.White.copy(
                                alpha = 0.16f
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "DOC",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(13.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = typeName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            type?.typeCode.agentMeta(),
                        fontSize = 9.sp,
                        color =
                            Color.White.copy(
                                alpha = 0.82f
                            )
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(15.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                AgentSummaryPill(
                    label = "TOTAL",
                    value = total.toString()
                )

                AgentSummaryPill(
                    label = "VERIFIED",
                    value = verified.toString()
                )

                AgentSummaryPill(
                    label = "PENDING",
                    value = pending.toString()
                )
            }
        }
    }
}


// =====================================================================
// CUSTOMER HERO
// =====================================================================

@Composable
private fun CustomerProfileHero(
    customer: Customer?,
    customerId: Long?,
    total: Int,
    verified: Int,
    pending: Int
) {

    val name =
        customer?.fullName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Customer #${customerId ?: "-"}"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(24.dp)
            )
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF6653BC),
                        Color(0xFF9550C2),
                        Color(0xFFD24D92)
                    )
                )
            )
            .padding(18.dp)
    ) {

        Column {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Color.White.copy(
                                alpha = 0.16f
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            agentCustomerInitials(name),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(13.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            "NIC: ${customer?.nic.agentMeta()}",
                        fontSize = 9.sp,
                        color =
                            Color.White.copy(
                                alpha = 0.82f
                            )
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(15.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                AgentSummaryPill(
                    label = "TOTAL",
                    value = total.toString()
                )

                AgentSummaryPill(
                    label = "VERIFIED",
                    value = verified.toString()
                )

                AgentSummaryPill(
                    label = "PENDING",
                    value = pending.toString()
                )
            }
        }
    }
}


// =====================================================================
// DOCUMENT CARD
// =====================================================================

@Composable
private fun ProfessionalDocumentCard(
    document: Document,
    type: DocumentType?,
    downloading: Boolean,
    sequenceNumber: Int? = null,
    onOpen: () -> Unit,
    onDownload: () -> Unit
) {

    val verification =
        document.verificationStatus
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "UNKNOWN"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(21.dp)
            )
            .background(
                Color.White.copy(
                    alpha = 0.97f
                )
            )
            .clickable {
                onOpen()
            }
            .padding(16.dp)
    ) {

        Column {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(
                            RoundedCornerShape(15.dp)
                        )
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFEE7656),
                                    Color(0xFFF0528D)
                                )
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "DOC",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(12.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            if (sequenceNumber != null) {
                                "${type?.typeName ?: "Document"} #$sequenceNumber"
                            } else {
                                type?.typeName
                                    ?: "Document #${document.documentId ?: "-"}"
                            },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF171A2C),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            document.fileName.agentMeta(),
                        fontSize = 9.sp,
                        color = Color(0xFF817A8B),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )
                }

                AgentVerificationBadge(
                    status = verification
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                AgentInfoMetric(
                    modifier =
                        Modifier.weight(1f),
                    label = "CAPTURE",
                    value =
                        document.captureStatus.agentMeta()
                )

                AgentInfoMetric(
                    modifier =
                        Modifier.weight(1f),
                    label = "STATUS",
                    value =
                        document.status.agentMeta()
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(9.dp)
            ) {

                AgentDocumentPrimaryAction(
                    modifier =
                        Modifier.weight(1f),
                    text = "VIEW DETAILS",
                    enabled = true,
                    primary = true,
                    onClick = onOpen
                )

                AgentDocumentPrimaryAction(
                    modifier =
                        Modifier.weight(1f),
                    text =
                        if (downloading) {
                            "SAVING..."
                        } else {
                            "SAVE COPY"
                        },
                    enabled =
                        !downloading &&
                                !document.fileName.isNullOrBlank(),
                    primary = false,
                    onClick = onDownload
                )
            }
        }
    }
}


// =====================================================================
// DOCUMENT DETAILS HERO
// =====================================================================

@Composable
private fun DocumentProfessionalHero(
    document: Document,
    type: DocumentType?
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(24.dp)
            )
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF6553BD),
                        Color(0xFF9C4FC1),
                        Color(0xFFD34D92)
                    )
                )
            )
            .padding(18.dp)
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(
                        RoundedCornerShape(16.dp)
                    )
                    .background(
                        Color.White.copy(
                            alpha = 0.16f
                        )
                    ),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = "DOC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(
                modifier =
                    Modifier.width(12.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text =
                        type?.typeName
                            ?: "Document #${document.documentId ?: "-"}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        document.fileName.agentMeta(),
                    fontSize = 9.sp,
                    color =
                        Color.White.copy(
                            alpha = 0.82f
                        ),
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )
            }

            AgentVerificationBadge(
                status =
                    document.verificationStatus
                        ?: "UNKNOWN",
                light = true
            )
        }
    }
}


// =====================================================================
// FILTER
// =====================================================================

@Composable
private fun AgentDocumentFilterRow(
    selected: AgentDocumentsFilter,
    onSelected: (AgentDocumentsFilter) -> Unit
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        AgentDocumentFilterChip(
            modifier =
                Modifier.weight(1f),
            text = "ALL",
            selected =
                selected ==
                        AgentDocumentsFilter.ALL,
            onClick = {
                onSelected(
                    AgentDocumentsFilter.ALL
                )
            }
        )

        AgentDocumentFilterChip(
            modifier =
                Modifier.weight(1f),
            text = "VERIFIED",
            selected =
                selected ==
                        AgentDocumentsFilter.VERIFIED,
            onClick = {
                onSelected(
                    AgentDocumentsFilter.VERIFIED
                )
            }
        )

        AgentDocumentFilterChip(
            modifier =
                Modifier.weight(1f),
            text = "PENDING",
            selected =
                selected ==
                        AgentDocumentsFilter.PENDING,
            onClick = {
                onSelected(
                    AgentDocumentsFilter.PENDING
                )
            }
        )
    }
}


@Composable
private fun AgentDocumentFilterChip(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                if (selected) {

                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF6D51BD),
                            Color(0xFFD14D92)
                        )
                    )

                } else {

                    Brush.horizontalGradient(
                        colors = listOf(
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
                vertical = 11.dp
            ),
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text = text,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
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
// DETAIL SECTIONS
// =====================================================================
@Composable
private fun AgentDetailSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(21.dp)
            )
            .background(
                Color.White.copy(
                    alpha = 0.97f
                )
            )
            .padding(16.dp)
    ) {

        Column(
            verticalArrangement =
                Arrangement.spacedBy(9.dp)
        ) {

            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A2C)
            )

            content()
        }
    }
}


@Composable
private fun AgentDetailRow(
    label: String,
    value: String?
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(13.dp)
            )
            .background(
                Color(0xFFF8F5FB)
            )
            .padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),
        verticalAlignment =
            Alignment.Top
    ) {

        Text(
            text = label,
            modifier =
                Modifier.weight(0.40f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF918A9B)
        )

        Spacer(
            modifier =
                Modifier.width(8.dp)
        )

        Text(
            text =
                value.agentMeta(),
            modifier =
                Modifier.weight(0.60f),
            fontSize = 10.sp,
            color = Color(0xFF3F394A)
        )
    }
}


// =====================================================================
// COMMON UI
// =====================================================================

@Composable
private fun AgentSummaryPill(
    label: String,
    value: String
) {

    Row(
        modifier = Modifier
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                Color.White.copy(
                    alpha = 0.15f
                )
            )
            .padding(
                horizontal = 9.dp,
                vertical = 8.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(
            modifier =
                Modifier.width(4.dp)
        )

        Text(
            text = label,
            fontSize = 7.sp,
            fontWeight = FontWeight.SemiBold,
            color =
                Color.White.copy(
                    alpha = 0.82f
                )
        )
    }
}


@Composable
private fun AgentInfoMetric(
    modifier: Modifier,
    label: String,
    value: String
) {

    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(13.dp)
            )
            .background(
                Color(0xFFF8F5FB)
            )
            .padding(10.dp)
    ) {

        Column {

            Text(
                text = label,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color(0xFF9A92A3)
            )

            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )

            Text(
                text = value,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF353146),
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun AgentVerificationBadge(
    status: String,
    light: Boolean = false
) {

    val normalized =
        status.uppercase(Locale.ROOT)

    val verified =
        normalized in setOf(
            "VERIFIED",
            "PASSED",
            "PASS"
        )

    val failed =
        normalized.contains("FAIL") ||
                normalized.contains("TAMPER") ||
                normalized.contains("REJECT")

    val background =
        if (light) {

            Color.White.copy(
                alpha = 0.16f
            )

        } else {

            when {
                verified ->
                    Color(0xFFEAF7EF)

                failed ->
                    Color(0xFFFFEDEF)

                else ->
                    Color(0xFFF3EEF8)
            }
        }

    val foreground =
        if (light) {

            Color.White

        } else {

            when {
                verified ->
                    Color(0xFF247148)

                failed ->
                    Color(0xFFB54050)

                else ->
                    Color(0xFF7650BD)
            }
        }

    Box(
        modifier = Modifier
            .clip(
                RoundedCornerShape(50.dp)
            )
            .background(background)
            .padding(
                horizontal = 9.dp,
                vertical = 6.dp
            )
    ) {

        Text(
            text = normalized,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = foreground,
            maxLines = 1
        )
    }
}


@Composable
private fun AgentDocumentPrimaryAction(
    modifier: Modifier,
    text: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                if (!enabled) {

                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFE1DCE5),
                            Color(0xFFE1DCE5)
                        )
                    )

                } else if (primary) {

                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFF7B4D),
                            Color(0xFFF04D8E),
                            Color(0xFF8C4DD1)
                        )
                    )

                } else {

                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFF0EBFA),
                            Color(0xFFF8EEF6)
                        )
                    )
                }
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(
                vertical = 11.dp
            ),
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text = text,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color =
                when {
                    !enabled ->
                        Color(0xFF99919F)

                    primary ->
                        Color.White

                    else ->
                        Color(0xFF6F4BB8)
                }
        )
    }
}


@Composable
private fun AgentDocumentsLoadingCard(
    text: String
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(17.dp)
            )
            .background(
                Color(0xFFF4EFFA)
            )
            .padding(14.dp)
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            CircularProgressIndicator(
                modifier =
                    Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF7650BD)
            )

            Spacer(
                modifier =
                    Modifier.width(10.dp)
            )

            Text(
                text = text,
                fontSize = 10.sp,
                color = Color(0xFF6C5B7A)
            )
        }
    }
}


@Composable
private fun AgentDocumentsMessageCard(
    message: String,
    error: Boolean
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(16.dp)
            )
            .background(
                if (error) {
                    Color(0xFFFFEEF1)
                } else {
                    Color(0xFFF3EEF9)
                }
            )
            .padding(13.dp)
    ) {

        Text(
            text = message,
            fontSize = 11.sp,
            lineHeight = 16.sp,
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
private fun AgentDocumentsEmptyCard(
    title: String,
    message: String
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(20.dp)
            )
            .background(
                Color.White.copy(
                    alpha = 0.97f
                )
            )
            .padding(18.dp)
    ) {

        Column {

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A2C)
            )

            Spacer(
                modifier =
                    Modifier.height(5.dp)
            )

            Text(
                text = message,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Color(0xFF817A8B)
            )
        }
    }
}


// =====================================================================
// HELPERS
// =====================================================================

private fun isVerifiedAgentDocument(
    document: Document
): Boolean {

    return document.verificationStatus
        ?.equals(
            "VERIFIED",
            ignoreCase = true
        ) == true
}


private fun customerNameForAgentDocuments(
    customerId: Long?,
    customers: List<Customer>
): String {

    return customers
        .firstOrNull {
            it.customerId == customerId
        }
        ?.fullName
        ?.takeIf {
            it.isNotBlank()
        }
        ?: "Customer #${customerId ?: "-"}"
}


private fun agentCustomerInitials(
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
        return "CU"
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
            )
        .uppercase()
}


private fun String?.agentMeta(): String =
    this
        ?.takeIf {
            it.isNotBlank()
        }
        ?: "Not available"