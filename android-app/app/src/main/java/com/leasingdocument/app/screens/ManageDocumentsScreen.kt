package com.leasingdocument.app.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.util.Locale

private enum class DocumentPage {
    AGENTS,
    CLIENTS,
    DOCUMENTS,
    DETAILS
}

@Composable
fun ManageDocumentsScreen() {

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

    val scope = rememberCoroutineScope()

    var agents by remember {
        mutableStateOf<List<Agent>>(emptyList())
    }

    var clients by remember {
        mutableStateOf<List<Customer>>(emptyList())
    }

    var documents by remember {
        mutableStateOf<List<Document>>(emptyList())
    }

    var types by remember {
        mutableStateOf<List<DocumentType>>(emptyList())
    }

    var integrityResults by remember {
        mutableStateOf<List<IntegrityResult>>(emptyList())
    }

    var page by remember {
        mutableStateOf(DocumentPage.AGENTS)
    }

    var selectedAgent by remember {
        mutableStateOf<Agent?>(null)
    }

    var selectedClient by remember {
        mutableStateOf<Customer?>(null)
    }

    var selectedDocument by remember {
        mutableStateOf<Document?>(null)
    }

    var documentImage by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var documentToDelete by remember {
        mutableStateOf<Document?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf("")
    }

    var notice by remember {
        mutableStateOf("")
    }

    suspend fun refreshData() {

        val api = RetrofitClient.apiService

        agents =
            api.getAgents()
                .requireDocumentBody()

        clients =
            api.getCustomers()
                .requireDocumentBody()

        documents =
            api.getDocuments()
                .requireDocumentBody()

        types =
            api.getDocumentTypes()
                .requireDocumentBody()

        integrityResults =
            api.getIntegrityResults()
                .requireDocumentBody()
    }

    fun goBack() {

        when (page) {

            DocumentPage.DETAILS -> {
                selectedDocument = null
                documentImage = null
                page = DocumentPage.DOCUMENTS
            }

            DocumentPage.DOCUMENTS -> {
                selectedClient = null
                page = DocumentPage.CLIENTS
            }

            DocumentPage.CLIENTS -> {
                selectedAgent = null
                page = DocumentPage.AGENTS
            }

            DocumentPage.AGENTS ->
                Unit
        }

        error = ""
    }

    BackHandler(
        enabled =
            page != DocumentPage.AGENTS &&
                    !loading
    ) {
        goBack()
    }

    fun execute(
        action: suspend () -> Unit
    ) {

        if (loading) {
            return
        }

        scope.launch {

            loading = true
            error = ""

            try {

                action()

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                error =
                    e.message
                        ?: "Operation failed"

            } finally {

                loading = false
            }
        }
    }

    suspend fun openDocument(
        doc: Document
    ) {

        selectedDocument = doc
        documentImage = null
        page = DocumentPage.DETAILS

        val name =
            doc.fileName

        if (!name.isNullOrBlank()) {

            val response =
                RetrofitClient
                    .apiService
                    .getDocumentImage(name)
                    .requireDocumentBody()

            val bytes =
                withContext(
                    Dispatchers.IO
                ) {
                    response.use {
                        it.bytes()
                    }
                }

            try {

                documentImage =
                    withContext(
                        Dispatchers.Default
                    ) {
                        decodeManagedDocument(
                            bytes
                        )
                    }

            } finally {

                bytes.fill(0)
            }
        }
    }

    LaunchedEffect(Unit) {
        execute {
            refreshData()
        }
    }

    if (documentToDelete != null) {

        val target =
            documentToDelete

        AlertDialog(
            onDismissRequest = {
                if (!loading) {
                    documentToDelete = null
                }
            },
            title = {
                Text(
                    text = "Delete document?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text =
                        "Delete document #${target?.documentId ?: "-"} from the active system? Existing evidence and audit history remain subject to backend rules."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !loading,
                    onClick = {

                        documentToDelete = null

                        val id =
                            target?.documentId

                        if (id != null) {

                            execute {

                                RetrofitClient
                                    .apiService
                                    .deleteDocument(id)
                                    .requireDocumentBody()

                                notice =
                                    "Document deleted successfully."

                                goBack()
                                refreshData()
                            }
                        }
                    }
                ) {
                    Text(
                        text = "DELETE",
                        color = Color(0xFFC34757),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !loading,
                    onClick = {
                        documentToDelete = null
                    }
                ) {
                    Text(
                        text = "CANCEL",
                        color = Color(0xFF777184)
                    )
                }
            }
        )
    }

    val agentDocs =
        documents.filter {
            selectedAgent?.agentId != null &&
                    it.agentId ==
                    selectedAgent?.agentId
        }

    val clientDocs =
        agentDocs.filter {
            selectedClient?.customerId != null &&
                    it.customerId ==
                    selectedClient?.customerId
        }

    val agentClients =
        agentDocs
            .mapNotNull {
                it.customerId
            }
            .toSet()
            .mapNotNull { id ->
                clients.firstOrNull {
                    it.customerId == id
                }
            }
            .sortedBy {
                it.fullName.orEmpty()
            }

    val verifiedCount =
        documents.count {
            it.verificationStatus
                ?.equals(
                    "VERIFIED",
                    ignoreCase = true
                ) == true
        }

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

        ManageDocumentsPremiumBackground()

        when (page) {

            DocumentPage.AGENTS -> {

                DocumentsAgentsPage(
                    agents = agents,
                    documents = documents,
                    verifiedCount = verifiedCount,
                    loading = loading,
                    error = error,
                    notice = notice,
                    onRefresh = {
                        execute {
                            refreshData()
                        }
                    },
                    onAgentOpen = { agent ->
                        selectedAgent = agent
                        selectedClient = null
                        selectedDocument = null
                        notice = ""
                        page = DocumentPage.CLIENTS
                    }
                )
            }

            DocumentPage.CLIENTS -> {

                DocumentsClientsPage(
                    agent = selectedAgent,
                    clients = agentClients,
                    agentDocuments = agentDocs,
                    loading = loading,
                    error = error,
                    onClientOpen = { client ->
                        selectedClient = client
                        selectedDocument = null
                        page = DocumentPage.DOCUMENTS
                    }
                )
            }

            DocumentPage.DOCUMENTS -> {

                DocumentsListPage(
                    client = selectedClient,
                    documents = clientDocs,
                    types = types,
                    integrityResults = integrityResults,
                    loading = loading,
                    error = error,
                    onDocumentOpen = { doc ->
                        execute {
                            openDocument(doc)
                        }
                    }
                )
            }

            DocumentPage.DETAILS -> {

                selectedDocument?.let { doc ->

                    val type =
                        types.firstOrNull {
                            it.documentTypeId ==
                                    doc.documentTypeId
                        }

                    val integrity =
                        integrityResults
                            .filter {
                                it.documentId ==
                                        doc.documentId
                            }
                            .maxByOrNull {
                                it.integrityResultId
                                    ?: 0L
                            }

                    DocumentDetailsPage(
                        document = doc,
                        type = type,
                        integrity = integrity,
                        client = selectedClient,
                        agent = selectedAgent,
                        image = documentImage,
                        loading = loading,
                        error = error,
                        onDelete = {
                            documentToDelete = doc
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
private fun ManageDocumentsPremiumBackground() {

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
            path = topShape,
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color(0xFFFFD1AF)
                                .copy(
                                    alpha = 0.58f
                                ),

                            Color(0xFFFFE8DE)
                                .copy(
                                    alpha = 0.24f
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
            path = ribbon,
            color =
                Color(0xFFFF8B42)
                    .copy(
                        alpha = 0.70f
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
}


// =====================================================================
// PAGE 1 — AGENTS
// =====================================================================

@Composable
private fun DocumentsAgentsPage(
    agents: List<Agent>,
    documents: List<Document>,
    verifiedCount: Int,
    loading: Boolean,
    error: String,
    notice: String,
    onRefresh: () -> Unit,
    onAgentOpen: (Agent) -> Unit
) {

    val agentsWithDocuments =
        agents
            .filter { agent ->
                documents.any {
                    it.agentId ==
                            agent.agentId
                }
            }
            .sortedBy {
                it.fullName.orEmpty()
            }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = 22.dp
                ),
        contentPadding =
            PaddingValues(
                bottom = 28.dp
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
                text = "Manage Documents",
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
                    "Browse submitted documents by agent and customer.",
                fontSize = 12.sp,
                color = Color(0xFF817A8B)
            )
        }

        item {

            DocumentOverviewHero(
                total =
                    documents.size,
                verified =
                    verifiedCount,
                agents =
                    agentsWithDocuments.size
            )
        }

        if (loading) {

            item {
                PremiumLoadingCard(
                    text =
                        "Loading document records..."
                )
            }
        }

        if (error.isNotBlank()) {

            item {
                PremiumMessageCard(
                    message = error,
                    error = true
                )
            }
        }

        if (notice.isNotBlank()) {

            item {
                PremiumMessageCard(
                    message = notice,
                    error = false
                )
            }
        }

        item {

            PremiumMiniAction(
                text = "REFRESH DATA",
                enabled = !loading,
                onClick = onRefresh
            )
        }

        item {

            Text(
                text = "Agents",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1D30)
            )
        }

        if (
            !loading &&
            agentsWithDocuments.isEmpty()
        ) {

            item {
                PremiumEmptyCard(
                    title =
                        "No agent submissions",
                    message =
                        "Agents with submitted documents will appear here."
                )
            }

        } else {

            items(
                items =
                    agentsWithDocuments,
                key = {
                    it.agentId
                        ?: -1L
                }
            ) { agent ->

                val agentDocuments =
                    documents.filter {
                        it.agentId ==
                                agent.agentId
                    }

                val customerCount =
                    agentDocuments
                        .mapNotNull {
                            it.customerId
                        }
                        .toSet()
                        .size

                AgentNavigationCard(
                    agent = agent,
                    customerCount = customerCount,
                    documentCount =
                        agentDocuments.size,
                    enabled = !loading,
                    onClick = {
                        onAgentOpen(
                            agent
                        )
                    }
                )
            }
        }
    }
}


// =====================================================================
// PAGE 2 — CLIENTS
// =====================================================================

@Composable
private fun DocumentsClientsPage(
    agent: Agent?,
    clients: List<Customer>,
    agentDocuments: List<Document>,
    loading: Boolean,
    error: String,
    onClientOpen: (Customer) -> Unit
) {

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = 22.dp
                ),
        contentPadding =
            PaddingValues(
                bottom = 28.dp
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
                text = "Agent Clients",
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
                    agent?.fullName
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "Selected agent",
                fontSize = 12.sp,
                color = Color(0xFF817A8B)
            )
        }

        item {

            AgentSummaryHero(
                agent = agent,
                customerCount = clients.size,
                documentCount =
                    agentDocuments.size
            )
        }

        if (loading) {

            item {
                PremiumLoadingCard(
                    text =
                        "Loading clients..."
                )
            }
        }

        if (error.isNotBlank()) {

            item {
                PremiumMessageCard(
                    message = error,
                    error = true
                )
            }
        }

        item {

            Text(
                text = "Customers",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1D30)
            )
        }

        if (
            !loading &&
            clients.isEmpty()
        ) {

            item {
                PremiumEmptyCard(
                    title =
                        "No customer submissions",
                    message =
                        "No registered customers are linked to this agent's documents."
                )
            }

        } else {

            items(
                items = clients,
                key = {
                    it.customerId
                        ?: -1L
                }
            ) { client ->

                val count =
                    agentDocuments.count {
                        it.customerId ==
                                client.customerId
                    }

                CustomerNavigationCard(
                    customer = client,
                    documentCount = count,
                    enabled = !loading,
                    onClick = {
                        onClientOpen(
                            client
                        )
                    }
                )
            }
        }
    }
}


// =====================================================================
// PAGE 3 — DOCUMENTS
// =====================================================================

@Composable
private fun DocumentsListPage(
    client: Customer?,
    documents: List<Document>,
    types: List<DocumentType>,
    integrityResults: List<IntegrityResult>,
    loading: Boolean,
    error: String,
    onDocumentOpen: (Document) -> Unit
) {

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = 22.dp
                ),
        contentPadding =
            PaddingValues(
                bottom = 28.dp
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
                    client?.fullName
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "Selected customer",
                fontSize = 12.sp,
                color = Color(0xFF817A8B)
            )
        }

        item {

            CustomerSummaryHero(
                client = client,
                documentCount =
                    documents.size
            )
        }

        if (loading) {

            item {
                PremiumLoadingCard(
                    text =
                        "Loading documents..."
                )
            }
        }

        if (error.isNotBlank()) {

            item {
                PremiumMessageCard(
                    message = error,
                    error = true
                )
            }
        }

        item {

            Text(
                text = "Submitted Documents",
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
                PremiumEmptyCard(
                    title =
                        "No documents found",
                    message =
                        "This customer has no submitted documents for the selected agent."
                )
            }

        } else {

            items(
                items =
                    documents.sortedByDescending {
                        it.documentId
                            ?: 0L
                    },
                key = {
                    it.documentId
                        ?: -1L
                }
            ) { doc ->

                val type =
                    types.firstOrNull {
                        it.documentTypeId ==
                                doc.documentTypeId
                    }

                val integrity =
                    integrityResults
                        .filter {
                            it.documentId ==
                                    doc.documentId
                        }
                        .maxByOrNull {
                            it.integrityResultId
                                ?: 0L
                        }

                DocumentNavigationCardPremium(
                    document = doc,
                    type = type,
                    integrity = integrity,
                    enabled = !loading,
                    onClick = {
                        onDocumentOpen(
                            doc
                        )
                    }
                )
            }
        }
    }
}


// =====================================================================
// PAGE 4 — FULL DETAILS
// =====================================================================

@Composable
private fun DocumentDetailsPage(
    document: Document,
    type: DocumentType?,
    integrity: IntegrityResult?,
    client: Customer?,
    agent: Agent?,
    image: Bitmap?,
    loading: Boolean,
    error: String,
    onDelete: () -> Unit
) {

    val originalHash =
        integrity?.sha256Hash

    val backendHash =
        integrity?.backendSha256Hash

    val validHashes =
        originalHash.isValidSha256() &&
                backendHash.isValidSha256()

    val hashesMatch =
        validHashes &&
                originalHash.equals(
                    backendHash,
                    ignoreCase = true
                )

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = 22.dp
                ),
        contentPadding =
            PaddingValues(
                bottom = 32.dp
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

            DocumentDetailsHero(
                document = document,
                type = type,
                integrity = integrity
            )
        }

        if (loading) {

            item {
                PremiumLoadingCard(
                    text =
                        "Loading secure document..."
                )
            }
        }

        if (error.isNotBlank()) {

            item {
                PremiumMessageCard(
                    message = error,
                    error = true
                )
            }
        }

        item {

            PremiumMetadataSection(
                title =
                    "Original Submitted Document"
            ) {

                Text(
                    text =
                        type?.typeName
                            ?: "Document #${document.documentId}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF171A2C)
                )

                image?.let { bitmap ->

                    Image(
                        bitmap =
                            bitmap.asImageBitmap(),
                        contentDescription =
                            "Original document image",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    max = 420.dp
                                )
                                .clip(
                                    RoundedCornerShape(
                                        16.dp
                                    )
                                ),
                        contentScale =
                            ContentScale.Fit
                    )

                } ?: Text(
                    text =
                        if (loading) {
                            "Loading image..."
                        } else {
                            "Image unavailable"
                        },
                    fontSize = 11.sp,
                    color = Color(0xFF817A8B)
                )
            }
        }

        item {

            PremiumMetadataSection(
                title =
                    "Customer Information"
            ) {

                PremiumMetadataRow(
                    "Full name",
                    client?.fullName
                )

                PremiumMetadataRow(
                    "NIC",
                    client?.nic
                )

                PremiumMetadataRow(
                    "Phone",
                    client?.phone
                )

                PremiumMetadataRow(
                    "Email",
                    client?.email
                )

                PremiumMetadataRow(
                    "Address",
                    client?.address
                )

                PremiumMetadataRow(
                    "Customer ID",
                    document.customerId
                        ?.toString()
                )
            }
        }

        item {

            PremiumMetadataSection(
                title =
                    "Submission Information"
            ) {

                PremiumMetadataRow(
                    "Submitted by",
                    agent?.fullName
                )

                PremiumMetadataRow(
                    "Agent ID",
                    document.agentId
                        ?.toString()
                )

                PremiumMetadataRow(
                    "Device ID",
                    document.deviceId
                        ?.toString()
                )

                PremiumMetadataRow(
                    "Document ID",
                    document.documentId
                        ?.toString()
                )

                PremiumMetadataRow(
                    "Document type",
                    type?.typeName
                )

                PremiumMetadataRow(
                    "Original filename",
                    document.fileName
                )

                PremiumMetadataRow(
                    "Capture source",
                    document.captureSource
                )

                PremiumMetadataRow(
                    "Capture status",
                    document.captureStatus
                )

                PremiumMetadataRow(
                    "Document status",
                    document.status
                )
            }
        }

        item {

            PremiumMetadataSection(
                title =
                    "Capture Time & Location"
            ) {

                PremiumMetadataRow(
                    "Captured at",
                    document.captureDateTime
                )

                PremiumMetadataRow(
                    "Uploaded at",
                    document.uploadedAt
                )

                PremiumMetadataRow(
                    "Location",
                    document.captureLocation
                )

                PremiumMetadataRow(
                    "Latitude",
                    document.captureLatitude
                        ?.toString()
                )

                PremiumMetadataRow(
                    "Longitude",
                    document.captureLongitude
                        ?.toString()
                )
            }
        }

        item {

            PremiumMetadataSection(
                title =
                    "File Information"
            ) {

                PremiumMetadataRow(
                    "Filename",
                    document.fileName
                )

                PremiumMetadataRow(
                    "File size",
                    readableFileSize(
                        document.fileSizeBytes
                    )
                )

                PremiumMetadataRow(
                    "MIME type",
                    document.mimeType
                )

                PremiumMetadataRow(
                    "Width",
                    document.imageWidth
                        ?.let {
                            "$it px"
                        }
                )

                PremiumMetadataRow(
                    "Height",
                    document.imageHeight
                        ?.let {
                            "$it px"
                        }
                )

                PremiumMetadataRow(
                    "Resolution",
                    if (
                        document.imageWidth != null &&
                        document.imageHeight != null
                    ) {
                        "${document.imageWidth} × ${document.imageHeight}"
                    } else {
                        null
                    }
                )

                PremiumMetadataRow(
                    "Image quality",
                    document.imageQuality
                )
            }
        }

        item {

            PremiumMetadataSection(
                title =
                    "Camera Quality Checks"
            ) {

                PremiumMetadataRow(
                    "Overall quality",
                    document.qualityStatus
                )

                PremiumMetadataRow(
                    "Blur check",
                    qualityLabel(
                        document.blurPassed
                    )
                )

                PremiumMetadataRow(
                    "Blur score",
                    document.blurScore
                        ?.toString()
                )

                PremiumMetadataRow(
                    "Brightness check",
                    qualityLabel(
                        document.brightnessPassed
                    )
                )

                PremiumMetadataRow(
                    "Brightness score",
                    document.brightnessScore
                        ?.toString()
                )

                PremiumMetadataRow(
                    "Resolution check",
                    qualityLabel(
                        document.resolutionPassed
                    )
                )
            }
        }

        item {

            PremiumMetadataSection(
                title =
                    "SHA-256 Integrity Verification"
            ) {

                Text(
                    text =
                        "Original Mobile Hash",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF353146)
                )

                CopyableHashPremium(
                    value =
                        integrity?.sha256Hash
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        "Backend Generated Hash",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF353146)
                )

                CopyableHashPremium(
                    value =
                        integrity?.backendSha256Hash
                )

                HashComparisonCard(
                    validHashes =
                        validHashes,
                    hashesMatch =
                        hashesMatch
                )

                PremiumMetadataRow(
                    "Initial verification",
                    integrity?.initialVerificationResult
                )

                PremiumMetadataRow(
                    "Verification result",
                    integrity?.verificationResult
                )

                Text(
                    text =
                        "Final SHA-256 Hash",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF353146)
                )

                CopyableHashPremium(
                    value =
                        integrity?.finalSha256Hash
                )

                PremiumMetadataRow(
                    "Final verification",
                    integrity?.finalVerificationResult
                )

                PremiumMetadataRow(
                    "Last verified",
                    integrity?.finalVerifiedAt
                )

                PremiumMetadataRow(
                    "Integrity processed",
                    integrity?.processedAt
                )
            }
        }

        item {

            PremiumMetadataSection(
                title =
                    "Secure Storage & Encryption"
            ) {

                PremiumMetadataRow(
                    "Encryption algorithm",
                    integrity?.encryptionAlgorithm
                )

                PremiumMetadataRow(
                    "Storage status",
                    integrity?.storageStatus
                )

                PremiumMetadataRow(
                    "Key version",
                    integrity?.encryptionKeyVersion
                )

                PremiumMetadataRow(
                    "Encrypted at",
                    integrity?.encryptedAt
                )

                PremiumMetadataRow(
                    "Stored document path",
                    integrity?.encryptedFilePath
                        ?.takeIf { it.isNotBlank() }
                        ?: document.filePath
                )
            }
        }

        item {

            PremiumMetadataSection(
                title =
                    "Processing & Verification"
            ) {

                PremiumMetadataRow(
                    "Processing status",
                    document.processingStatus
                )

                PremiumMetadataRow(
                    "Document verification",
                    document.verificationStatus
                )

                PremiumMetadataRow(
                    "Capture status",
                    document.captureStatus
                )

                PremiumMetadataRow(
                    "Database document ID",
                    document.documentId
                        ?.toString()
                )

                PremiumMetadataRow(
                    "Integrity record ID",
                    integrity?.integrityResultId
                        ?.toString()
                )
            }
        }

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
                            Color(0xFFFFEEF1)
                        )
                        .clickable(
                            enabled =
                                !loading
                        ) {
                            onDelete()
                        }
                        .padding(
                            vertical = 14.dp
                        ),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = "DELETE DOCUMENT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFFC34757)
                )
            }
        }
    }
}


// =====================================================================
// HERO CARDS
// =====================================================================

@Composable
private fun DocumentOverviewHero(
    total: Int,
    verified: Int,
    agents: Int
) {

    PremiumGradientHero {

        Text(
            text = "Document Overview",
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
                "$total Document${if (total == 1) "" else "s"}",
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
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            PremiumStatPill(
                label = "VERIFIED",
                value = verified.toString()
            )

            PremiumStatPill(
                label = "AGENTS",
                value = agents.toString()
            )
        }
    }
}

@Composable
private fun AgentSummaryHero(
    agent: Agent?,
    customerCount: Int,
    documentCount: Int
) {

    PremiumGradientHero {

        Text(
            text =
                agent?.fullName
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "Selected Agent",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text =
                agent?.email
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "Agent account",
            fontSize = 10.sp,
            color =
                Color.White.copy(
                    alpha = 0.82f
                )
        )

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            PremiumStatPill(
                label = "CUSTOMERS",
                value = customerCount.toString()
            )

            PremiumStatPill(
                label = "DOCUMENTS",
                value = documentCount.toString()
            )
        }
    }
}

@Composable
private fun CustomerSummaryHero(
    client: Customer?,
    documentCount: Int
) {

    PremiumGradientHero {

        Text(
            text =
                client?.fullName
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "Selected Customer",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text =
                "NIC: ${client?.nic.displayMeta()}",
            fontSize = 10.sp,
            color =
                Color.White.copy(
                    alpha = 0.82f
                )
        )

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        PremiumStatPill(
            label = "DOCUMENTS",
            value = documentCount.toString()
        )
    }
}

@Composable
private fun DocumentDetailsHero(
    document: Document,
    type: DocumentType?,
    integrity: IntegrityResult?
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
                                Color(0xFF9C4FC1),
                                Color(0xFFD34D92)
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
                        .size(52.dp)
                        .clip(
                            RoundedCornerShape(
                                16.dp
                            )
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
                            ?: "Document #${document.documentId}",
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
                        document.fileName.displayMeta(),
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

            Spacer(
                modifier =
                    Modifier.width(8.dp)
            )

            SecurityStatusBadge(
                status =
                    integrity?.finalVerificationResult
                        ?: integrity?.verificationResult
                        ?: document.verificationStatus
            )
        }
    }
}

@Composable
private fun PremiumGradientHero(
    content: @Composable ColumnScope.() -> Unit
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
                                Color(0xFF6950B7),
                                Color(0xFF9950BE),
                                Color(0xFFD54E91)
                            )
                    )
                )
                .padding(
                    20.dp
                )
    ) {

        Column(
            content = content
        )
    }
}


// =====================================================================
// NAVIGATION CARDS
// =====================================================================

@Composable
private fun AgentNavigationCard(
    agent: Agent,
    customerCount: Int,
    documentCount: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {

    val name =
        agent.fullName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Agent #${agent.agentId ?: "-"}"

    PremiumNavigationCard(
        badge = initialsFromName(
            name
        ),
        title = name,
        subtitle =
            agent.email.displayMeta(),
        detail =
            "$customerCount customer${if (customerCount == 1) "" else "s"} • $documentCount document${if (documentCount == 1) "" else "s"}",
        enabled = enabled,
        onClick = onClick,
        badgeGradient =
            listOf(
                Color(0xFF7650BD),
                Color(0xFFD14D92)
            )
    )
}

@Composable
private fun CustomerNavigationCard(
    customer: Customer,
    documentCount: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {

    PremiumNavigationCard(
        badge = "CU",
        title =
            customer.fullName
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "Customer #${customer.customerId ?: "-"}",
        subtitle =
            "NIC: ${customer.nic.displayMeta()}",
        detail =
            "$documentCount submitted document${if (documentCount == 1) "" else "s"}",
        enabled = enabled,
        onClick = onClick,
        badgeGradient =
            listOf(
                Color(0xFFF07862),
                Color(0xFFD64E94)
            )
    )
}

@Composable
private fun DocumentNavigationCardPremium(
    document: Document,
    type: DocumentType?,
    integrity: IntegrityResult?,
    enabled: Boolean,
    onClick: () -> Unit
) {

    val status =
        integrity?.finalVerificationResult
            ?: integrity?.verificationResult
            ?: document.verificationStatus
            ?: "UNKNOWN"

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
                        alpha = 0.97f
                    )
                )
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(
                    16.dp
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
                            .size(48.dp)
                            .clip(
                                RoundedCornerShape(
                                    15.dp
                                )
                            )
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
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
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
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
                                ?: "Document #${document.documentId}",
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
                            document.fileName.displayMeta(),
                        fontSize = 9.sp,
                        color = Color(0xFF817A8B),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )
                }

                SecurityStatusBadge(
                    status = status
                )
            }

            Spacer(
                modifier =
                    Modifier.height(11.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {

                CompactInfoPill(
                    modifier =
                        Modifier.weight(1f),
                    label = "DOCUMENT",
                    value =
                        "#${document.documentId ?: "-"}"
                )

                CompactInfoPill(
                    modifier =
                        Modifier.weight(1f),
                    label = "UPLOADED",
                    value =
                        document.uploadedAt
                            ?.substringBefore(
                                "T"
                            )
                            .displayMeta()
                )
            }
        }
    }
}

@Composable
private fun PremiumNavigationCard(
    badge: String,
    title: String,
    subtitle: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    badgeGradient: List<Color>
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
                        alpha = 0.97f
                    )
                )
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                )
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
                        .size(50.dp)
                        .clip(
                            CircleShape
                        )
                        .background(
                            Brush.linearGradient(
                                colors =
                                    badgeGradient
                            )
                        ),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = badge,
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
                    text = title,
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
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color(0xFF817A8B),
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    text = detail,
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
    }
}


// =====================================================================
// DETAILS COMPONENTS
// =====================================================================

@Composable
private fun PremiumMetadataSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
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
                        alpha = 0.97f
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
private fun PremiumMetadataRow(
    label: String,
    value: String?
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
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
        verticalAlignment =
            Alignment.Top
    ) {

        Text(
            text = label,
            modifier =
                Modifier.weight(
                    0.40f
                ),
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
                value.displayMeta(),
            modifier =
                Modifier.weight(
                    0.60f
                ),
            fontSize = 10.sp,
            color = Color(0xFF3F394A)
        )
    }
}

@Composable
private fun CopyableHashPremium(
    value: String?
) {

    val clipboard =
        LocalClipboardManager.current

    if (value.isNullOrBlank()) {

        Text(
            text = "Not available",
            fontSize = 10.sp,
            color = Color(0xFF817A8B)
        )

        return
    }

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
                    12.dp
                )
    ) {

        Column {

            Text(
                text = value,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                color = Color(0xFF4F4858)
            )

            Spacer(
                modifier =
                    Modifier.height(5.dp)
            )

            Text(
                text = "COPY FULL HASH",
                modifier =
                    Modifier.clickable {
                        clipboard.setText(
                            AnnotatedString(
                                value
                            )
                        )
                    },
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7650BD)
            )
        }
    }
}

@Composable
private fun HashComparisonCard(
    validHashes: Boolean,
    hashesMatch: Boolean
) {

    val text =
        when {

            !validHashes ->
                "Comparison unavailable"

            hashesMatch ->
                "Hashes match"

            else ->
                "Hash mismatch — investigation required"
        }

    val background =
        when {

            !validHashes ->
                Color(0xFFF3EEF8)

            hashesMatch ->
                Color(0xFFEAF7EF)

            else ->
                Color(0xFFFFEDEF)
        }

    val foreground =
        when {

            !validHashes ->
                Color(0xFF7650BD)

            hashesMatch ->
                Color(0xFF247148)

            else ->
                Color(0xFFB54050)
        }

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
                    background
                )
                .padding(
                    12.dp
                )
    ) {

        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = foreground
        )
    }
}


// =====================================================================
// SMALL COMPONENTS
// =====================================================================

@Composable
private fun PremiumStatPill(
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
                        alpha = 0.15f
                    )
                )
                .padding(
                    horizontal = 11.dp,
                    vertical = 9.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(
            modifier =
                Modifier.width(5.dp)
        )

        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            color =
                Color.White.copy(
                    alpha = 0.82f
                )
        )
    }
}

@Composable
private fun PremiumMiniAction(
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
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(
                    vertical = 12.dp
                ),
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = Color.White
        )
    }
}

@Composable
private fun PremiumLoadingCard(
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
private fun PremiumMessageCard(
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
private fun PremiumEmptyCard(
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
                        alpha = 0.97f
                    )
                )
                .padding(
                    18.dp
                )
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

@Composable
private fun CompactInfoPill(
    modifier: Modifier,
    label: String,
    value: String
) {

    Box(
        modifier =
            modifier
                .clip(
                    RoundedCornerShape(
                        13.dp
                    )
                )
                .background(
                    Color(0xFFF8F5FB)
                )
                .padding(
                    10.dp
                )
    ) {

        Column {

            Text(
                text = label,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
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
private fun SecurityStatusBadge(
    status: String?
) {

    val normalized =
        status
            ?.uppercase(Locale.ROOT)
            ?: "UNKNOWN"

    val verified =
        normalized in setOf(
            "VERIFIED",
            "PASS",
            "PASSED",
            "MATCH",
            "MATCHED"
        )

    val failed =
        normalized.contains(
            "FAIL"
        ) ||
                normalized.contains(
                    "MISMATCH"
                ) ||
                normalized.contains(
                    "TAMPER"
                )

    val background =
        when {

            verified ->
                Color(0xFFEAF7EF)

            failed ->
                Color(0xFFFFEDEF)

            else ->
                Color(0xFFF3EEF8)
        }

    val foreground =
        when {

            verified ->
                Color(0xFF247148)

            failed ->
                Color(0xFFB54050)

            else ->
                Color(0xFF7650BD)
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


// =====================================================================
// FORMATTING
// =====================================================================

private fun String?.displayMeta(): String =
    this
        ?.takeIf {
            it.isNotBlank()
        }
        ?: "Not available"

private fun String?.isValidSha256(): Boolean =
    this != null &&
            this.matches(
                Regex(
                    "[a-fA-F0-9]{64}"
                )
            )

private fun qualityLabel(
    value: Boolean?
): String =
    when (value) {

        true ->
            "Passed"

        false ->
            "Failed"

        null ->
            "Not recorded"
    }

private fun readableFileSize(
    bytes: Long?
): String {

    if (bytes == null) {
        return "Not available"
    }

    return when {

        bytes < 1024 ->
            "$bytes bytes"

        bytes < 1024L * 1024L ->
            String.format(
                Locale.US,
                "%.1f KB",
                bytes / 1024.0
            )

        else ->
            String.format(
                Locale.US,
                "%.2f MB",
                bytes /
                        (1024.0 * 1024.0)
            )
    }
}

private fun initialsFromName(
    name: String
): String {

    val parts =
        name
            .trim()
            .split(
                Regex(
                    "\\s+"
                )
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
            parts.first()
                .take(1)
                    +
                    parts.last()
                        .take(1)
            )
        .uppercase()
}


// =====================================================================
// API RESPONSE
// =====================================================================

private fun <T> Response<T>.requireDocumentBody(): T {

    if (!isSuccessful) {

        error(
            "Server returned HTTP ${code()}"
        )
    }

    return requireNotNull(
        body()
    ) {
        "Server returned an empty response"
    }
}


// =====================================================================
// SAFE DOCUMENT PREVIEW
// =====================================================================

private fun decodeManagedDocument(
    bytes: ByteArray
): Bitmap {

    val bounds =
        BitmapFactory.Options()
            .apply {
                inJustDecodeBounds =
                    true
            }

    BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        bounds
    )

    require(
        bounds.outWidth > 0 &&
                bounds.outHeight > 0 &&
                bounds.outWidth.toLong() *
                bounds.outHeight <=
                24_000_000L
    ) {
        "Unsupported document image dimensions"
    }

    val sample =
        BitmapFactory.Options()
            .apply {

                inSampleSize =
                    when {

                        bounds.outWidth > 2500 ||
                                bounds.outHeight > 2500 ->
                            4

                        bounds.outWidth > 1300 ||
                                bounds.outHeight > 1300 ->
                            2

                        else ->
                            1
                    }
            }

    return requireNotNull(
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            sample
        )
    ) {
        "Unable to display document image"
    }
}
