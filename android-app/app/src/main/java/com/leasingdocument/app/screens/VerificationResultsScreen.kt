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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import com.leasingdocument.app.network.AlterationResult
import com.leasingdocument.app.network.Customer
import com.leasingdocument.app.network.Document
import com.leasingdocument.app.network.DocumentType
import com.leasingdocument.app.network.IntegrityResult
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import java.util.Locale

private enum class VerificationPage {
    OVERVIEW,
    INTEGRITY_LIST,
    ALTERATION_LIST,
    INTEGRITY_TYPE_DOCUMENTS,
    ALTERATION_TYPE_DOCUMENTS,
    INTEGRITY_DETAILS,
    ALTERATION_DETAILS
}

@Composable
fun VerificationResultsScreen() {

    var documents by remember {
        mutableStateOf<List<Document>>(emptyList())
    }

    var customers by remember {
        mutableStateOf<List<Customer>>(emptyList())
    }

    var documentTypes by remember {
        mutableStateOf<List<DocumentType>>(emptyList())
    }

    var integrityResults by remember {
        mutableStateOf<List<IntegrityResult>>(emptyList())
    }

    var alterationResults by remember {
        mutableStateOf<List<AlterationResult>>(emptyList())
    }

    var page by remember {
        mutableStateOf(VerificationPage.OVERVIEW)
    }

    var selectedIntegrityDocumentId by remember {
        mutableStateOf<Long?>(null)
    }

    var selectedAlterationDocumentId by remember {
        mutableStateOf<Long?>(null)
    }

    var selectedIntegrityTypeId by remember {
        mutableStateOf<Long?>(null)
    }

    var selectedAlterationTypeId by remember {
        mutableStateOf<Long?>(null)
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    // =========================================================
    // LOAD AGENT-SCOPED VERIFICATION DATA
    // =========================================================

    LaunchedEffect(Unit) {

        loading = true
        errorMessage = ""

        try {

            val api =
                RetrofitClient.apiService

            val documentResponse =
                api.getMyDocuments()

            val integrityResponse =
                api.getIntegrityResults()

            val alterationResponse =
                api.getAlterationResults()

            if (
                !documentResponse.isSuccessful ||
                !integrityResponse.isSuccessful ||
                !alterationResponse.isSuccessful
            ) {

                errorMessage =
                    when {

                        documentResponse.code() == 401 ||
                                integrityResponse.code() == 401 ||
                                alterationResponse.code() == 401 ->
                            "Your session has expired. Please sign in again."

                        documentResponse.code() == 403 ||
                                integrityResponse.code() == 403 ||
                                alterationResponse.code() == 403 ->
                            "You do not have permission to view verification results."

                        else ->
                            "Failed to load verification results."
                    }

                return@LaunchedEffect
            }


            val myDocuments =
                documentResponse
                    .body()
                    .orEmpty()
                    .sortedByDescending {
                        it.documentId ?: 0L
                    }

            val myDocumentIds =
                myDocuments
                    .mapNotNull {
                        it.documentId
                    }
                    .toSet()


            documents =
                myDocuments

            integrityResults =
                integrityResponse
                    .body()
                    .orEmpty()
                    .filter {
                        it.documentId != null &&
                                it.documentId in myDocumentIds
                    }
                    .sortedWith(
                        compareByDescending<IntegrityResult> {
                            it.processedAt.orEmpty()
                        }.thenByDescending {
                            it.integrityResultId ?: 0L
                        }
                    )

            alterationResults =
                alterationResponse
                    .body()
                    .orEmpty()
                    .filter {
                        it.documentId != null &&
                                it.documentId in myDocumentIds
                    }
                    .sortedWith(
                        compareByDescending<AlterationResult> {
                            it.processedAt.orEmpty()
                        }.thenByDescending {
                            it.alterationResultId ?: 0L
                        }
                    )


            val customerResponse =
                api.getCustomers()

            customers =
                if (customerResponse.isSuccessful) {
                    customerResponse.body().orEmpty()
                } else {
                    emptyList()
                }


            val typeResponse =
                api.getDocumentTypes()

            documentTypes =
                if (typeResponse.isSuccessful) {
                    typeResponse.body().orEmpty()
                } else {
                    emptyList()
                }

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            errorMessage =
                "Cannot connect to server."

        } finally {

            loading = false
        }
    }


    // =========================================================
    // SYSTEM BACK ONLY
    // =========================================================

    fun goBack() {

        when (page) {

            VerificationPage.INTEGRITY_DETAILS -> {
                selectedIntegrityDocumentId = null
                page = VerificationPage.INTEGRITY_TYPE_DOCUMENTS
            }

            VerificationPage.ALTERATION_DETAILS -> {
                selectedAlterationDocumentId = null
                page = VerificationPage.ALTERATION_TYPE_DOCUMENTS
            }

            VerificationPage.INTEGRITY_TYPE_DOCUMENTS -> {
                selectedIntegrityDocumentId = null
                selectedIntegrityTypeId = null
                page = VerificationPage.INTEGRITY_LIST
            }

            VerificationPage.ALTERATION_TYPE_DOCUMENTS -> {
                selectedAlterationDocumentId = null
                selectedAlterationTypeId = null
                page = VerificationPage.ALTERATION_LIST
            }

            VerificationPage.INTEGRITY_LIST,
            VerificationPage.ALTERATION_LIST -> {
                selectedIntegrityDocumentId = null
                selectedAlterationDocumentId = null
                selectedIntegrityTypeId = null
                selectedAlterationTypeId = null
                page = VerificationPage.OVERVIEW
            }

            VerificationPage.OVERVIEW ->
                Unit
        }
    }

    BackHandler(
        enabled =
            page != VerificationPage.OVERVIEW
    ) {
        goBack()
    }


    // =========================================================
    // DERIVED DATA
    // =========================================================

    val integrityByDocument =
        integrityResults
            .groupBy {
                it.documentId
            }

    val alterationByDocument =
        alterationResults
            .groupBy {
                it.documentId
            }

    val verifiedIntegrityDocuments =
        integrityByDocument.count { (_, history) ->
            history.latestIntegrityResult()
                ?.let(::integrityPassed) == true
        }

    val integrityAttentionCount =
        integrityByDocument.size -
                verifiedIntegrityDocuments

    val highRiskAlterationDocuments =
        alterationByDocument.count { (_, history) ->
            history.latestAlterationResult()
                ?.riskLevel
                ?.equals(
                    "HIGH",
                    ignoreCase = true
                ) == true
        }

    val pendingReviewAlterationDocuments =
        alterationByDocument.count { (_, history) ->

            val latest =
                history.latestAlterationResult()

            latest != null &&
                    (
                            latest.finalDecision.isNullOrBlank() ||
                                    latest.reviewStatus
                                        ?.contains(
                                            "PENDING",
                                            ignoreCase = true
                                        ) == true
                            )
        }


    val selectedIntegrityHistory =
        integrityByDocument[
            selectedIntegrityDocumentId
        ]
            .orEmpty()
            .sortedWith(
                compareByDescending<IntegrityResult> {
                    it.processedAt.orEmpty()
                }.thenByDescending {
                    it.integrityResultId ?: 0L
                }
            )

    val selectedAlterationHistory =
        alterationByDocument[
            selectedAlterationDocumentId
        ]
            .orEmpty()
            .sortedWith(
                compareByDescending<AlterationResult> {
                    it.processedAt.orEmpty()
                }.thenByDescending {
                    it.alterationResultId ?: 0L
                }
            )


    // =========================================================
    // ROOT
    // =========================================================

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

        VerificationPremiumBackground()


        when (page) {

            VerificationPage.OVERVIEW -> {

                VerificationOverviewPage(
                    integrityRecordCount =
                        integrityResults.size,

                    integrityDocumentCount =
                        integrityByDocument.size,

                    verifiedIntegrityDocuments =
                        verifiedIntegrityDocuments,

                    integrityAttentionCount =
                        integrityAttentionCount,

                    alterationRecordCount =
                        alterationResults.size,

                    alterationDocumentCount =
                        alterationByDocument.size,

                    highRiskAlterationDocuments =
                        highRiskAlterationDocuments,

                    pendingReviewAlterationDocuments =
                        pendingReviewAlterationDocuments,

                    loading =
                        loading,

                    errorMessage =
                        errorMessage,

                    onIntegrityOpen = {
                        page =
                            VerificationPage.INTEGRITY_LIST
                    },

                    onAlterationOpen = {
                        page =
                            VerificationPage.ALTERATION_LIST
                    }
                )
            }


            VerificationPage.INTEGRITY_LIST -> {

                IntegrityDocumentsPage(
                    groupedResults =
                        integrityByDocument,

                    documents =
                        documents,

                    documentTypes =
                        documentTypes,

                    loading =
                        loading,

                    errorMessage =
                        errorMessage,

                    onTypeOpen = { typeId ->

                        selectedIntegrityTypeId =
                            typeId

                        selectedIntegrityDocumentId =
                            null

                        page =
                            VerificationPage.INTEGRITY_TYPE_DOCUMENTS
                    }
                )
            }


            VerificationPage.INTEGRITY_TYPE_DOCUMENTS -> {

                IntegrityTypeDocumentsPage(
                    typeId =
                        selectedIntegrityTypeId,

                    groupedResults =
                        integrityByDocument,

                    documents =
                        documents,

                    customers =
                        customers,

                    documentTypes =
                        documentTypes,

                    loading =
                        loading,

                    errorMessage =
                        errorMessage,

                    onDocumentOpen = { documentId ->

                        selectedIntegrityDocumentId =
                            documentId

                        page =
                            VerificationPage.INTEGRITY_DETAILS
                    }
                )
            }


            VerificationPage.ALTERATION_LIST -> {

                AlterationDocumentsPage(
                    groupedResults =
                        alterationByDocument,

                    documents =
                        documents,

                    documentTypes =
                        documentTypes,

                    loading =
                        loading,

                    errorMessage =
                        errorMessage,

                    onTypeOpen = { typeId ->

                        selectedAlterationTypeId =
                            typeId

                        selectedAlterationDocumentId =
                            null

                        page =
                            VerificationPage.ALTERATION_TYPE_DOCUMENTS
                    }
                )
            }


            VerificationPage.ALTERATION_TYPE_DOCUMENTS -> {

                AlterationTypeDocumentsPage(
                    typeId =
                        selectedAlterationTypeId,

                    groupedResults =
                        alterationByDocument,

                    documents =
                        documents,

                    customers =
                        customers,

                    documentTypes =
                        documentTypes,

                    loading =
                        loading,

                    errorMessage =
                        errorMessage,

                    onDocumentOpen = { documentId ->

                        selectedAlterationDocumentId =
                            documentId

                        page =
                            VerificationPage.ALTERATION_DETAILS
                    }
                )
            }


            VerificationPage.INTEGRITY_DETAILS -> {

                val document =
                    documents.firstOrNull {
                        it.documentId ==
                                selectedIntegrityDocumentId
                    }

                val type =
                    documentTypes.firstOrNull {
                        it.documentTypeId ==
                                document?.documentTypeId
                    }

                val customer =
                    customers.firstOrNull {
                        it.customerId ==
                                document?.customerId
                    }


                IntegrityDetailsPage(
                    document =
                        document,

                    type =
                        type,

                    customer =
                        customer,

                    history =
                        selectedIntegrityHistory
                )
            }


            VerificationPage.ALTERATION_DETAILS -> {

                val document =
                    documents.firstOrNull {
                        it.documentId ==
                                selectedAlterationDocumentId
                    }

                val type =
                    documentTypes.firstOrNull {
                        it.documentTypeId ==
                                document?.documentTypeId
                    }

                val customer =
                    customers.firstOrNull {
                        it.customerId ==
                                document?.customerId
                    }


                AlterationDetailsPage(
                    document =
                        document,

                    type =
                        type,

                    customer =
                        customer,

                    history =
                        selectedAlterationHistory
                )
            }
        }
    }
}


// =====================================================================
// BACKGROUND
// =====================================================================

@Composable
private fun VerificationPremiumBackground() {

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
            path =
                ribbon,

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
// OVERVIEW
// =====================================================================

@Composable
private fun VerificationOverviewPage(
    integrityRecordCount: Int,
    integrityDocumentCount: Int,
    verifiedIntegrityDocuments: Int,
    integrityAttentionCount: Int,
    alterationRecordCount: Int,
    alterationDocumentCount: Int,
    highRiskAlterationDocuments: Int,
    pendingReviewAlterationDocuments: Int,
    loading: Boolean,
    errorMessage: String,
    onIntegrityOpen: () -> Unit,
    onAlterationOpen: () -> Unit
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
                text =
                    "Verification Results",

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
                    "Integrity evidence and alteration-analysis results for your documents.",

                fontSize =
                    12.sp,

                lineHeight =
                    17.sp,

                color =
                    Color(0xFF817A8B)
            )
        }


        item {

            VerificationOverviewHero(
                integrityDocuments =
                    integrityDocumentCount,

                verifiedDocuments =
                    verifiedIntegrityDocuments,

                alterationDocuments =
                    alterationDocumentCount,

                highRiskDocuments =
                    highRiskAlterationDocuments
            )
        }


        if (loading) {

            item {

                VerificationLoadingCard(
                    text =
                        "Loading verification evidence..."
                )
            }
        }


        if (errorMessage.isNotBlank()) {

            item {

                VerificationMessageCard(
                    message =
                        errorMessage,

                    error =
                        true
                )
            }
        }


        item {

            Text(
                text =
                    "Security Checks",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF1B1D30)
            )
        }


        item {

            VerificationSecurityDistributionCard(
                integrityTotal =
                    integrityDocumentCount,

                integrityVerified =
                    verifiedIntegrityDocuments,

                integrityAttention =
                    integrityAttentionCount,

                alterationTotal =
                    alterationDocumentCount,

                alterationHighRisk =
                    highRiskAlterationDocuments,

                alterationPendingReview =
                    pendingReviewAlterationDocuments
            )
        }


        item {

            VerificationCategoryCard(
                badge =
                    "INT",

                title =
                    "Integrity Verification",

                subtitle =
                    "SHA-256 verification, encrypted storage and retrieval checks.",

                recordCount =
                    integrityRecordCount,

                documentCount =
                    integrityDocumentCount,

                statusLabel =
                    if (integrityAttentionCount == 0) {
                        "ALL VERIFIED"
                    } else {
                        "$integrityAttentionCount NEED ATTENTION"
                    },

                good =
                    integrityAttentionCount == 0,

                colors =
                    listOf(
                        Color(0xFF6653BC),
                        Color(0xFF9C4FC1)
                    ),

                onClick =
                    onIntegrityOpen
            )
        }


        item {

            VerificationCategoryCard(
                badge =
                    "ALT",

                title =
                    "Alteration Analysis",

                subtitle =
                    "Automated risk analysis and recorded review decisions.",

                recordCount =
                    alterationRecordCount,

                documentCount =
                    alterationDocumentCount,

                statusLabel =
                    when {
                        highRiskAlterationDocuments > 0 ->
                            "$highRiskAlterationDocuments HIGH RISK"

                        pendingReviewAlterationDocuments > 0 ->
                            "$pendingReviewAlterationDocuments PENDING REVIEW"

                        else ->
                            "NO HIGH-RISK FLAGS"
                    },

                good =
                    highRiskAlterationDocuments == 0,

                colors =
                    listOf(
                        Color(0xFFEE7656),
                        Color(0xFFD64E94)
                    ),

                onClick =
                    onAlterationOpen
            )
        }


    }
}


// =====================================================================
// INTEGRITY — DOCUMENT TYPE LIST
// =====================================================================

@Composable
private fun IntegrityDocumentsPage(
    groupedResults: Map<Long?, List<IntegrityResult>>,
    documents: List<Document>,
    documentTypes: List<DocumentType>,
    loading: Boolean,
    errorMessage: String,
    onTypeOpen: (Long?) -> Unit
) {

    val groups =
        groupedResults
            .entries
            .sortedByDescending { entry ->

                entry.value
                    .latestIntegrityResult()
                    ?.processedAt
                    .orEmpty()
            }

    val typeGroups =
        groups
            .groupBy { entry ->

                documents
                    .firstOrNull {
                        it.documentId == entry.key
                    }
                    ?.documentTypeId
            }
            .entries
            .sortedBy { typeEntry ->

                documentTypes
                    .firstOrNull {
                        it.documentTypeId == typeEntry.key
                    }
                    ?.typeName
                    .orEmpty()
                    .lowercase(Locale.ROOT)
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
                text =
                    "Integrity Verification",

                fontSize =
                    27.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )
        }


        item {

            VerificationSectionHero(
                badge =
                    "INT",

                title =
                    "${groups.size} Secured Document${if (groups.size == 1) "" else "s"}",

                subtitle =
                    "${typeGroups.size} document categor${if (typeGroups.size == 1) "y" else "ies"}",

                colors =
                    listOf(
                        Color(0xFF6553BD),
                        Color(0xFF9C4FC1),
                        Color(0xFFD34D92)
                    )
            )
        }


        if (loading) {

            item {

                VerificationLoadingCard(
                    text =
                        "Loading integrity results..."
                )
            }
        }


        if (errorMessage.isNotBlank()) {

            item {

                VerificationMessageCard(
                    message =
                        errorMessage,

                    error =
                        true
                )
            }
        }


        if (
            !loading &&
            typeGroups.isEmpty()
        ) {

            item {

                VerificationEmptyCard(
                    title =
                        "No integrity results",

                    message =
                        "No integrity-verification records are available."
                )
            }
        }


        typeGroups.forEach { typeEntry ->

            val typeId =
                typeEntry.key

            val type =
                documentTypes.firstOrNull {
                    it.documentTypeId == typeId
                }

            val typeName =
                type?.typeName
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "Document Type #${typeId ?: "-"}"

            val documentEntries =
                typeEntry.value

            val verifiedCount =
                documentEntries.count { entry ->
                    entry.value
                        .latestIntegrityResult()
                        ?.let(::integrityPassed) == true
                }

            val attentionCount =
                documentEntries.size -
                        verifiedCount


            item(
                key =
                    "integrity-type-${typeId ?: "unknown"}"
            ) {

                VerificationTypeNavigationCard(
                    badge =
                        "INT",

                    title =
                        typeName,

                    subtitle =
                        "${documentEntries.size} verification document${if (documentEntries.size == 1) "" else "s"}",

                    primaryLabel =
                        "VERIFIED",

                    primaryValue =
                        verifiedCount,

                    secondaryLabel =
                        "ATTENTION",

                    secondaryValue =
                        attentionCount,

                    colors =
                        listOf(
                            Color(0xFF6553BD),
                            Color(0xFF9C4FC1)
                        ),

                    onClick = {
                        onTypeOpen(typeId)
                    }
                )
            }
        }
    }
}


// =====================================================================
// INTEGRITY — DOCUMENTS INSIDE SELECTED TYPE
// =====================================================================

@Composable
private fun IntegrityTypeDocumentsPage(
    typeId: Long?,
    groupedResults: Map<Long?, List<IntegrityResult>>,
    documents: List<Document>,
    customers: List<Customer>,
    documentTypes: List<DocumentType>,
    loading: Boolean,
    errorMessage: String,
    onDocumentOpen: (Long?) -> Unit
) {

    val type =
        documentTypes.firstOrNull {
            it.documentTypeId == typeId
        }

    val typeName =
        type?.typeName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Integrity Documents"

    val documentEntries =
        groupedResults
            .entries
            .filter { entry ->

                documents
                    .firstOrNull {
                        it.documentId == entry.key
                    }
                    ?.documentTypeId == typeId
            }
            .sortedByDescending { entry ->

                entry.value
                    .latestIntegrityResult()
                    ?.processedAt
                    .orEmpty()
            }

    val verified =
        documentEntries.count { entry ->
            entry.value
                .latestIntegrityResult()
                ?.let(::integrityPassed) == true
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
                text =
                    typeName,

                fontSize =
                    27.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )
        }


        item {

            VerificationTypePageHero(
                badge =
                    "INT",

                title =
                    typeName,

                total =
                    documentEntries.size,

                primaryLabel =
                    "VERIFIED",

                primaryValue =
                    verified,

                secondaryLabel =
                    "ATTENTION",

                secondaryValue =
                    documentEntries.size - verified,

                colors =
                    listOf(
                        Color(0xFF6553BD),
                        Color(0xFF9C4FC1),
                        Color(0xFFD34D92)
                    )
            )
        }


        if (loading) {

            item {

                VerificationLoadingCard(
                    text =
                        "Loading $typeName results..."
                )
            }
        }


        if (errorMessage.isNotBlank()) {

            item {

                VerificationMessageCard(
                    message =
                        errorMessage,

                    error =
                        true
                )
            }
        }


        item {

            Text(
                text =
                    "Verification Records",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF1B1D30)
            )
        }


        documentEntries.forEach { entry ->

            val documentId =
                entry.key

            val history =
                entry.value

            val latest =
                history.latestIntegrityResult()
                    ?: return@forEach

            val document =
                documents.firstOrNull {
                    it.documentId == documentId
                }

            val customer =
                customers.firstOrNull {
                    it.customerId ==
                            document?.customerId
                }


            item(
                key =
                    "integrity-${documentId ?: latest.integrityResultId ?: latest.hashCode()}"
            ) {

                IntegrityDocumentCard(
                    result =
                        latest,

                    historyCount =
                        history.size,

                    document =
                        document,

                    type =
                        type,

                    customer =
                        customer,

                    onClick = {
                        onDocumentOpen(
                            documentId
                        )
                    }
                )
            }
        }
    }
}


// =====================================================================
// ALTERATION — DOCUMENT TYPE LIST
// =====================================================================

@Composable
private fun AlterationDocumentsPage(
    groupedResults: Map<Long?, List<AlterationResult>>,
    documents: List<Document>,
    documentTypes: List<DocumentType>,
    loading: Boolean,
    errorMessage: String,
    onTypeOpen: (Long?) -> Unit
) {

    val groups =
        groupedResults
            .entries
            .sortedByDescending { entry ->

                entry.value
                    .latestAlterationResult()
                    ?.processedAt
                    .orEmpty()
            }

    val typeGroups =
        groups
            .groupBy { entry ->

                documents
                    .firstOrNull {
                        it.documentId == entry.key
                    }
                    ?.documentTypeId
            }
            .entries
            .sortedBy { typeEntry ->

                documentTypes
                    .firstOrNull {
                        it.documentTypeId == typeEntry.key
                    }
                    ?.typeName
                    .orEmpty()
                    .lowercase(Locale.ROOT)
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
                text =
                    "Alteration Analysis",

                fontSize =
                    27.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )
        }


        item {

            VerificationSectionHero(
                badge =
                    "ALT",

                title =
                    "${groups.size} Analyzed Document${if (groups.size == 1) "" else "s"}",

                subtitle =
                    "${typeGroups.size} document categor${if (typeGroups.size == 1) "y" else "ies"}",

                colors =
                    listOf(
                        Color(0xFFEE7656),
                        Color(0xFFF0528D),
                        Color(0xFF9C4FC1)
                    )
            )
        }


        if (loading) {

            item {

                VerificationLoadingCard(
                    text =
                        "Loading alteration-analysis results..."
                )
            }
        }


        if (errorMessage.isNotBlank()) {

            item {

                VerificationMessageCard(
                    message =
                        errorMessage,

                    error =
                        true
                )
            }
        }


        if (
            !loading &&
            typeGroups.isEmpty()
        ) {

            item {

                VerificationEmptyCard(
                    title =
                        "No alteration results",

                    message =
                        "No alteration-analysis records are available."
                )
            }
        }


        typeGroups.forEach { typeEntry ->

            val typeId =
                typeEntry.key

            val type =
                documentTypes.firstOrNull {
                    it.documentTypeId == typeId
                }

            val typeName =
                type?.typeName
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "Document Type #${typeId ?: "-"}"

            val documentEntries =
                typeEntry.value

            val highRiskCount =
                documentEntries.count { entry ->
                    entry.value
                        .latestAlterationResult()
                        ?.riskLevel
                        ?.equals(
                            "HIGH",
                            ignoreCase = true
                        ) == true
                }

            val pendingReviewCount =
                documentEntries.count { entry ->

                    val latest =
                        entry.value
                            .latestAlterationResult()

                    latest != null &&
                            (
                                    latest.finalDecision.isNullOrBlank() ||
                                            latest.reviewStatus
                                                ?.contains(
                                                    "PENDING",
                                                    ignoreCase = true
                                                ) == true
                                    )
                }


            item(
                key =
                    "alteration-type-${typeId ?: "unknown"}"
            ) {

                VerificationTypeNavigationCard(
                    badge =
                        "ALT",

                    title =
                        typeName,

                    subtitle =
                        "${documentEntries.size} analyzed document${if (documentEntries.size == 1) "" else "s"}",

                    primaryLabel =
                        "HIGH RISK",

                    primaryValue =
                        highRiskCount,

                    secondaryLabel =
                        "PENDING",

                    secondaryValue =
                        pendingReviewCount,

                    colors =
                        listOf(
                            Color(0xFFEE7656),
                            Color(0xFFD64E94)
                        ),

                    onClick = {
                        onTypeOpen(typeId)
                    }
                )
            }
        }
    }
}


// =====================================================================
// ALTERATION — DOCUMENTS INSIDE SELECTED TYPE
// =====================================================================

@Composable
private fun AlterationTypeDocumentsPage(
    typeId: Long?,
    groupedResults: Map<Long?, List<AlterationResult>>,
    documents: List<Document>,
    customers: List<Customer>,
    documentTypes: List<DocumentType>,
    loading: Boolean,
    errorMessage: String,
    onDocumentOpen: (Long?) -> Unit
) {

    val type =
        documentTypes.firstOrNull {
            it.documentTypeId == typeId
        }

    val typeName =
        type?.typeName
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Alteration Documents"

    val documentEntries =
        groupedResults
            .entries
            .filter { entry ->

                documents
                    .firstOrNull {
                        it.documentId == entry.key
                    }
                    ?.documentTypeId == typeId
            }
            .sortedByDescending { entry ->

                entry.value
                    .latestAlterationResult()
                    ?.processedAt
                    .orEmpty()
            }

    val highRisk =
        documentEntries.count { entry ->
            entry.value
                .latestAlterationResult()
                ?.riskLevel
                ?.equals(
                    "HIGH",
                    ignoreCase = true
                ) == true
        }

    val pending =
        documentEntries.count { entry ->

            val latest =
                entry.value
                    .latestAlterationResult()

            latest != null &&
                    (
                            latest.finalDecision.isNullOrBlank() ||
                                    latest.reviewStatus
                                        ?.contains(
                                            "PENDING",
                                            ignoreCase = true
                                        ) == true
                            )
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
                text =
                    typeName,

                fontSize =
                    27.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )
        }


        item {

            VerificationTypePageHero(
                badge =
                    "ALT",

                title =
                    typeName,

                total =
                    documentEntries.size,

                primaryLabel =
                    "HIGH RISK",

                primaryValue =
                    highRisk,

                secondaryLabel =
                    "PENDING",

                secondaryValue =
                    pending,

                colors =
                    listOf(
                        Color(0xFFEE7656),
                        Color(0xFFF0528D),
                        Color(0xFF9C4FC1)
                    )
            )
        }


        if (loading) {

            item {

                VerificationLoadingCard(
                    text =
                        "Loading $typeName results..."
                )
            }
        }


        if (errorMessage.isNotBlank()) {

            item {

                VerificationMessageCard(
                    message =
                        errorMessage,

                    error =
                        true
                )
            }
        }


        item {

            Text(
                text =
                    "Analysis Records",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF1B1D30)
            )
        }


        documentEntries.forEach { entry ->

            val documentId =
                entry.key

            val history =
                entry.value

            val latest =
                history.latestAlterationResult()
                    ?: return@forEach

            val document =
                documents.firstOrNull {
                    it.documentId == documentId
                }

            val customer =
                customers.firstOrNull {
                    it.customerId ==
                            document?.customerId
                }


            item(
                key =
                    "alteration-${documentId ?: latest.alterationResultId ?: latest.hashCode()}"
            ) {

                AlterationDocumentCard(
                    result =
                        latest,

                    historyCount =
                        history.size,

                    document =
                        document,

                    type =
                        type,

                    customer =
                        customer,

                    onClick = {
                        onDocumentOpen(
                            documentId
                        )
                    }
                )
            }
        }
    }
}


// =====================================================================
// INTEGRITY DETAILS
// =====================================================================

@Composable
private fun IntegrityDetailsPage(
    document: Document?,
    type: DocumentType?,
    customer: Customer?,
    history: List<IntegrityResult>
) {

    val latest =
        history.latestIntegrityResult()


    if (latest == null) {

        VerificationDetailUnavailable(
            title =
                "Integrity Details",

            message =
                "No integrity result is available for this document."
        )

        return
    }


    val originalHash =
        latest.sha256Hash

    val backendHash =
        latest.backendSha256Hash

    val finalHash =
        latest.finalSha256Hash

    val receiptHashMatch =
        originalHash.isSha256() &&
                backendHash.isSha256() &&
                originalHash.equals(
                    backendHash,
                    ignoreCase = true
                )

    val finalHashMatch =
        originalHash.isSha256() &&
                finalHash.isSha256() &&
                originalHash.equals(
                    finalHash,
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
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )


            Text(
                text =
                    "Integrity Details",

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
                    "Document #${latest.documentId ?: "-"}",

                fontSize =
                    12.sp,

                color =
                    Color(0xFF817A8B)
            )
        }


        item {

            IntegrityDetailHero(
                result =
                    latest,

                type =
                    type
            )
        }


        item {

            VerificationDetailSection(
                title =
                    "Document Context"
            ) {

                VerificationDetailRow(
                    label =
                        "Document type",

                    value =
                        type?.typeName
                )

                VerificationDetailRow(
                    label =
                        "Customer",

                    value =
                        customer?.fullName
                )

                VerificationDetailRow(
                    label =
                        "Customer NIC",

                    value =
                        customer?.nic
                )

                VerificationDetailRow(
                    label =
                        "Document ID",

                    value =
                        latest.documentId
                            ?.toString()
                )

                VerificationDetailRow(
                    label =
                        "Filename",

                    value =
                        document?.fileName
                )
            }
        }


        item {

            VerificationDetailSection(
                title =
                    "Verification Outcome"
            ) {

                VerificationDetailRow(
                    label =
                        "Initial verification",

                    value =
                        latest.initialVerificationResult
                )

                VerificationDetailRow(
                    label =
                        "Current result",

                    value =
                        latest.verificationResult
                )

                VerificationDetailRow(
                    label =
                        "Final verification",

                    value =
                        latest.finalVerificationResult
                )

                VerificationDetailRow(
                    label =
                        "Storage status",

                    value =
                        latest.storageStatus
                )
            }
        }


        item {

            VerificationDetailSection(
                title =
                    "SHA-256 Evidence"
            ) {

                VerificationHashBlock(
                    label =
                        "Original Mobile Hash",

                    value =
                        latest.sha256Hash
                )

                VerificationHashBlock(
                    label =
                        "Backend Receipt Hash",

                    value =
                        latest.backendSha256Hash
                )

                VerificationComparisonCard(
                    title =
                        "Upload / receipt comparison",

                    available =
                        originalHash.isSha256() &&
                                backendHash.isSha256(),

                    matches =
                        receiptHashMatch,

                    successText =
                        "Original and backend receipt hashes match.",

                    failureText =
                        "Original and backend receipt hashes do not match."
                )

                VerificationHashBlock(
                    label =
                        "Final Retrieval Hash",

                    value =
                        latest.finalSha256Hash
                )

                VerificationComparisonCard(
                    title =
                        "Stored / retrieved comparison",

                    available =
                        originalHash.isSha256() &&
                                finalHash.isSha256(),

                    matches =
                        finalHashMatch,

                    successText =
                        "Final decrypted bytes match the original SHA-256 hash.",

                    failureText =
                        "Final decrypted bytes do not match the original SHA-256 hash."
                )

                VerificationInfoNote(
                    text =
                        "Matching SHA-256 values demonstrate byte-level integrity. They do not by themselves prove that the visible document content is genuine."
                )
            }
        }


        item {

            VerificationDetailSection(
                title =
                    "Encryption & Secure Storage"
            ) {

                VerificationDetailRow(
                    label =
                        "Encryption algorithm",

                    value =
                        latest.encryptionAlgorithm
                )

                VerificationDetailRow(
                    label =
                        "Storage status",

                    value =
                        latest.storageStatus
                )

                VerificationDetailRow(
                    label =
                        "Encryption key version",

                    value =
                        latest.encryptionKeyVersion
                )

                VerificationDetailRow(
                    label =
                        "Encrypted at",

                    value =
                        latest.encryptedAt
                )

                VerificationInfoNote(
                    text =
                        "Sensitive encryption parameters such as IV values and protected storage paths are intentionally not displayed in this screen."
                )
            }
        }


        item {

            VerificationDetailSection(
                title =
                    "Verification Timeline"
            ) {

                VerificationDetailRow(
                    label =
                        "Integrity processed",

                    value =
                        latest.processedAt
                )

                VerificationDetailRow(
                    label =
                        "Last final verification",

                    value =
                        latest.finalVerifiedAt
                )

                VerificationDetailRow(
                    label =
                        "Integrity record ID",

                    value =
                        latest.integrityResultId
                            ?.toString()
                )
            }
        }


        if (history.size > 1) {

            item {

                VerificationHistorySection(
                    title =
                        "Integrity History",

                    rows =
                        history.map { result ->

                            Triple(
                                "Record #${result.integrityResultId ?: "-"}",
                                result.finalVerificationResult
                                    ?: result.verificationResult
                                    ?: "UNKNOWN",
                                result.finalVerifiedAt
                                    ?: result.processedAt
                                    ?: "Not recorded"
                            )
                        }
                )
            }
        }
    }
}


// =====================================================================
// ALTERATION DETAILS
// =====================================================================

@Composable
private fun AlterationDetailsPage(
    document: Document?,
    type: DocumentType?,
    customer: Customer?,
    history: List<AlterationResult>
) {

    val latest =
        history.latestAlterationResult()


    if (latest == null) {

        VerificationDetailUnavailable(
            title =
                "Alteration Details",

            message =
                "No alteration-analysis result is available for this document."
        )

        return
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
                bottom = 32.dp
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
                text =
                    "Alteration Details",

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
                    "Document #${latest.documentId ?: "-"}",

                fontSize =
                    12.sp,

                color =
                    Color(0xFF817A8B)
            )
        }


        item {

            AlterationDetailHero(
                result =
                    latest,

                type =
                    type
            )
        }


        item {

            VerificationDetailSection(
                title =
                    "Document Context"
            ) {

                VerificationDetailRow(
                    label =
                        "Document type",

                    value =
                        type?.typeName
                )

                VerificationDetailRow(
                    label =
                        "Customer",

                    value =
                        customer?.fullName
                )

                VerificationDetailRow(
                    label =
                        "Customer NIC",

                    value =
                        customer?.nic
                )

                VerificationDetailRow(
                    label =
                        "Document ID",

                    value =
                        latest.documentId
                            ?.toString()
                )

                VerificationDetailRow(
                    label =
                        "Filename",

                    value =
                        document?.fileName
                )
            }
        }


        item {

            VerificationDetailSection(
                title =
                    "Analysis Result"
            ) {

                VerificationDetailRow(
                    label =
                        "Analysis status",

                    value =
                        latest.analysisStatus
                )

                VerificationDetailRow(
                    label =
                        "Risk level",

                    value =
                        latest.riskLevel
                )

                VerificationDetailRow(
                    label =
                        "Risk score",

                    value =
                        latest.riskScore
                            ?.toString()
                )

                VerificationDetailRow(
                    label =
                        "Suspicious regions",

                    value =
                        latest.suspiciousRegionCount
                            ?.toString()
                )

                VerificationDetailRow(
                    label =
                        "Recommended action",

                    value =
                        latest.recommendedAction
                )

                VerificationDetailRow(
                    label =
                        "Analysis message",

                    value =
                        latest.analysisMessage
                )
            }
        }


        item {

            VerificationDetailSection(
                title =
                    "OCR & Analysis Engine"
            ) {

                VerificationDetailRow(
                    label =
                        "OCR status",

                    value =
                        latest.ocrStatus
                )

                VerificationDetailRow(
                    label =
                        "OCR engine",

                    value =
                        latest.ocrEngine
                )

                VerificationDetailRow(
                    label =
                        "Algorithm version",

                    value =
                        latest.algorithmVersion
                )

                VerificationDetailRow(
                    label =
                        "OCR text available",

                    value =
                        if (
                            latest.ocrFullText
                                .isNullOrBlank()
                        ) {
                            "No"
                        } else {
                            "Yes"
                        }
                )

                VerificationInfoNote(
                    text =
                        "Alteration analysis is an automated risk assessment. It should be interpreted together with the recorded review decision and supporting evidence."
                )
            }
        }


        item {

            VerificationDetailSection(
                title =
                    "Manual Review"
            ) {

                VerificationDetailRow(
                    label =
                        "Review status",

                    value =
                        latest.reviewStatus
                )

                VerificationDetailRow(
                    label =
                        "Final decision",

                    value =
                        latest.finalDecision
                )

                VerificationDetailRow(
                    label =
                        "Reviewed by admin ID",

                    value =
                        latest.reviewedByAdminId
                            ?.toString()
                )

                VerificationDetailRow(
                    label =
                        "Reviewed at",

                    value =
                        latest.reviewedAt
                )

                VerificationDetailRow(
                    label =
                        "Review notes",

                    value =
                        latest.reviewNotes
                )
            }
        }


        item {

            VerificationDetailSection(
                title =
                    "Processing"
            ) {

                VerificationDetailRow(
                    label =
                        "Analysis result ID",

                    value =
                        latest.alterationResultId
                            ?.toString()
                )

                VerificationDetailRow(
                    label =
                        "Processed at",

                    value =
                        latest.processedAt
                )
            }
        }


        if (history.size > 1) {

            item {

                VerificationHistorySection(
                    title =
                        "Analysis History",

                    rows =
                        history.map { result ->

                            Triple(
                                "Result #${result.alterationResultId ?: "-"}",
                                result.finalDecision
                                    ?: result.riskLevel
                                    ?: result.analysisStatus
                                    ?: "UNKNOWN",
                                result.processedAt
                                    ?: "Not recorded"
                            )
                        }
                )
            }
        }
    }
}


// =====================================================================
// OVERVIEW HERO + CATEGORY
// =====================================================================

@Composable
private fun VerificationOverviewHero(
    integrityDocuments: Int,
    verifiedDocuments: Int,
    alterationDocuments: Int,
    highRiskDocuments: Int
) {

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
                .padding(20.dp)
    ) {

        Column {

            Text(
                text =
                    "Security Overview",

                color =
                    Color.White.copy(
                        alpha = 0.84f
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
                    "${integrityDocuments + alterationDocuments} Verified / Analyzed Documents",

                color =
                    Color.White,

                fontSize =
                    21.sp,

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

                VerificationOverviewPill(
                    label =
                        "INTEGRITY VERIFIED",

                    value =
                        verifiedDocuments.toString()
                )


                VerificationOverviewPill(
                    label =
                        "HIGH RISK",

                    value =
                        highRiskDocuments.toString()
                )
            }
        }
    }
}


@Composable
private fun VerificationCategoryCard(
    badge: String,
    title: String,
    subtitle: String,
    recordCount: Int,
    documentCount: Int,
    statusLabel: String,
    good: Boolean,
    colors: List<Color>,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
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
                    modifier =
                        Modifier
                            .size(50.dp)
                            .clip(
                                RoundedCornerShape(15.dp)
                            )
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        colors
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            badge,

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
                        Modifier.width(13.dp)
                )


                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

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
                            Modifier.height(3.dp)
                    )


                    Text(
                        text =
                            subtitle,

                        fontSize =
                            10.sp,

                        lineHeight =
                            14.sp,

                        color =
                            Color(0xFF817A8B)
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


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                VerificationMetricBox(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "DOCUMENTS",

                    value =
                        documentCount.toString()
                )


                VerificationMetricBox(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "RECORDS",

                    value =
                        recordCount.toString()
                )
            }


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            VerificationStatusStrip(
                text =
                    statusLabel,

                good =
                    good
            )
        }
    }
}


// =====================================================================
// SECURITY DISTRIBUTION BARS
// =====================================================================

@Composable
private fun VerificationSecurityDistributionCard(
    integrityTotal: Int,
    integrityVerified: Int,
    integrityAttention: Int,
    alterationTotal: Int,
    alterationHighRisk: Int,
    alterationPendingReview: Int
) {

    Box(
        modifier =
            Modifier
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
                Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text =
                    "Verification Distribution",

                fontSize =
                    14.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            VerificationProgressRow(
                label =
                    "Integrity verified",

                value =
                    integrityVerified,

                total =
                    integrityTotal
            )


            VerificationProgressRow(
                label =
                    "Integrity needs attention",

                value =
                    integrityAttention,

                total =
                    integrityTotal
            )


            VerificationProgressRow(
                label =
                    "Alteration high risk",

                value =
                    alterationHighRisk,

                total =
                    alterationTotal
            )


            VerificationProgressRow(
                label =
                    "Alteration pending review",

                value =
                    alterationPendingReview,

                total =
                    alterationTotal
            )
        }
    }
}


@Composable
private fun VerificationProgressRow(
    label: String,
    value: Int,
    total: Int
) {

    val safeTotal =
        total.coerceAtLeast(1)


    Column(
        verticalArrangement =
            Arrangement.spacedBy(5.dp)
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
                    Modifier.weight(1f),

                fontSize =
                    10.sp,

                color =
                    Color(0xFF5F5968)
            )


            Text(
                text =
                    "$value / $total",

                fontSize =
                    9.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF7650BD)
            )
        }


        LinearProgressIndicator(
            progress = {
                (
                        value.toFloat() /
                                safeTotal.toFloat()
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
                        RoundedCornerShape(50.dp)
                    )
        )
    }
}


// =====================================================================
// DOCUMENT TYPE GROUP CARD
// =====================================================================

@Composable
private fun VerificationTypeNavigationCard(
    badge: String,
    title: String,
    subtitle: String,
    primaryLabel: String,
    primaryValue: Int,
    secondaryLabel: String,
    secondaryValue: Int,
    colors: List<Color>,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
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
                    modifier =
                        Modifier
                            .size(50.dp)
                            .clip(
                                RoundedCornerShape(15.dp)
                            )
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        colors
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            badge,

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
                        Modifier.width(13.dp)
                )


                Column(
                    modifier =
                        Modifier.weight(1f)
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
                            subtitle,

                        fontSize =
                            9.sp,

                        color =
                            Color(0xFF817A8B),

                        maxLines = 1,

                        overflow =
                            TextOverflow.Ellipsis
                    )
                }


                Text(
                    text =
                        "›",

                    fontSize =
                        26.sp,

                    fontWeight =
                        FontWeight.SemiBold,

                    color =
                        Color(0xFF7650BD)
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

                VerificationMetricBox(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        primaryLabel,

                    value =
                        primaryValue.toString()
                )


                VerificationMetricBox(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        secondaryLabel,

                    value =
                        secondaryValue.toString()
                )
            }
        }
    }
}


@Composable
private fun VerificationTypePageHero(
    badge: String,
    title: String,
    total: Int,
    primaryLabel: String,
    primaryValue: Int,
    secondaryLabel: String,
    secondaryValue: Int,
    colors: List<Color>
) {

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
                            colors
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
                    modifier =
                        Modifier
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
                        text =
                            badge,

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
                        Modifier.width(12.dp)
                )


                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            title,

                        fontSize =
                            17.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color.White,

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
                            "$total record${if (total == 1) "" else "s"}",

                        fontSize =
                            9.sp,

                        color =
                            Color.White.copy(
                                alpha = 0.82f
                            )
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )


            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                VerificationOverviewPill(
                    label =
                        primaryLabel,

                    value =
                        primaryValue.toString()
                )


                VerificationOverviewPill(
                    label =
                        secondaryLabel,

                    value =
                        secondaryValue.toString()
                )
            }
        }
    }
}


// =====================================================================
// LIST CARDS
// =====================================================================

@Composable
private fun IntegrityDocumentCard(
    result: IntegrityResult,
    historyCount: Int,
    document: Document?,
    type: DocumentType?,
    customer: Customer?,
    onClick: () -> Unit
) {

    val passed =
        integrityPassed(
            result
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
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(
                                RoundedCornerShape(15.dp)
                            )
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF6553BD),
                                            Color(0xFF9C4FC1)
                                        )
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "INT",

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
                        Modifier.width(12.dp)
                )


                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            type?.typeName
                                ?: "Document #${result.documentId ?: "-"}",

                        fontSize =
                            14.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF171A2C),

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
                            customer?.fullName
                                ?: document?.fileName
                                ?: "Secure document",

                        fontSize =
                            9.sp,

                        color =
                            Color(0xFF817A8B),

                        maxLines = 1,

                        overflow =
                            TextOverflow.Ellipsis
                    )
                }


                VerificationBadge(
                    text =
                        result.finalVerificationResult
                            ?: result.verificationResult
                            ?: "UNKNOWN",

                    good =
                        passed
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

                VerificationMetricBox(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "CHECKS",

                    value =
                        historyCount.toString()
                )


                VerificationMetricBox(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "STORAGE",

                    value =
                        result.storageStatus
                            .verificationMeta()
                )


                VerificationMetricBox(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "CRYPTO",

                    value =
                        result.encryptionAlgorithm
                            .verificationMeta()
                )
            }


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            Text(
                text =
                    "VIEW SECURITY DETAILS ›",

                fontSize =
                    8.sp,

                fontWeight =
                    FontWeight.Bold,

                letterSpacing =
                    0.4.sp,

                color =
                    Color(0xFF7650BD)
            )
        }
    }
}


@Composable
private fun AlterationDocumentCard(
    result: AlterationResult,
    historyCount: Int,
    document: Document?,
    type: DocumentType?,
    customer: Customer?,
    onClick: () -> Unit
) {

    val highRisk =
        result.riskLevel
            ?.equals(
                "HIGH",
                ignoreCase = true
            ) == true


    Box(
        modifier =
            Modifier
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
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(
                                RoundedCornerShape(15.dp)
                            )
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(0xFFEE7656),
                                            Color(0xFFD64E94)
                                        )
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "ALT",

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
                        Modifier.width(12.dp)
                )


                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            type?.typeName
                                ?: "Document #${result.documentId ?: "-"}",

                        fontSize =
                            14.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF171A2C),

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
                            customer?.fullName
                                ?: document?.fileName
                                ?: "Analyzed document",

                        fontSize =
                            9.sp,

                        color =
                            Color(0xFF817A8B),

                        maxLines = 1,

                        overflow =
                            TextOverflow.Ellipsis
                    )
                }


                RiskLevelBadge(
                    riskLevel =
                        result.riskLevel
                            ?: "UNKNOWN"
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

                VerificationMetricBox(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "ANALYSES",

                    value =
                        historyCount.toString()
                )


                VerificationMetricBox(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "RISK SCORE",

                    value =
                        result.riskScore
                            ?.toString()
                            ?: "-"
                )


                VerificationMetricBox(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "REGIONS",

                    value =
                        result.suspiciousRegionCount
                            ?.toString()
                            ?: "-"
                )
            }


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            VerificationStatusStrip(
                text =
                    result.finalDecision
                        ?: result.reviewStatus
                        ?: result.analysisStatus
                        ?: "UNKNOWN",

                good =
                    !highRisk &&
                            result.finalDecision
                                ?.equals(
                                    "REJECTED",
                                    ignoreCase = true
                                ) != true
            )


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            Text(
                text =
                    "VIEW ANALYSIS DETAILS ›",

                fontSize =
                    8.sp,

                fontWeight =
                    FontWeight.Bold,

                letterSpacing =
                    0.4.sp,

                color =
                    Color(0xFF7650BD)
            )
        }
    }
}


// =====================================================================
// DETAIL HERO CARDS
// =====================================================================

@Composable
private fun IntegrityDetailHero(
    result: IntegrityResult,
    type: DocumentType?
) {

    val passed =
        integrityPassed(
            result
        )


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
                            if (passed) {
                                listOf(
                                    Color(0xFF6553BD),
                                    Color(0xFF9C4FC1),
                                    Color(0xFFD34D92)
                                )
                            } else {
                                listOf(
                                    Color(0xFFC34757),
                                    Color(0xFFD64E94)
                                )
                            }
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
                    modifier =
                        Modifier
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
                        text =
                            "INT",

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
                        Modifier.width(12.dp)
                )


                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            type?.typeName
                                ?: "Secure Document",

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color.White
                    )


                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )


                    Text(
                        text =
                            if (passed) {
                                "Document integrity verified"
                            } else {
                                "Integrity requires attention"
                            },

                        fontSize =
                            10.sp,

                        color =
                            Color.White.copy(
                                alpha = 0.82f
                            )
                    )
                }


                VerificationBadge(
                    text =
                        result.finalVerificationResult
                            ?: result.verificationResult
                            ?: "UNKNOWN",

                    good =
                        passed,

                    inverted =
                        true
                )
            }
        }
    }
}


@Composable
private fun AlterationDetailHero(
    result: AlterationResult,
    type: DocumentType?
) {

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
                                Color(0xFFEE7656),
                                Color(0xFFD64E94),
                                Color(0xFF9C4FC1)
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
                    modifier =
                        Modifier
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
                        text =
                            "ALT",

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
                        Modifier.width(12.dp)
                )


                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            type?.typeName
                                ?: "Analyzed Document",

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color.White
                    )


                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )


                    Text(
                        text =
                            result.analysisStatus
                                ?: "Analysis result",

                        fontSize =
                            10.sp,

                        color =
                            Color.White.copy(
                                alpha = 0.82f
                            )
                    )
                }


                RiskLevelBadge(
                    riskLevel =
                        result.riskLevel
                            ?: "UNKNOWN",

                    inverted =
                        true
                )
            }
        }
    }
}


// =====================================================================
// DETAIL SECTIONS
// =====================================================================

@Composable
private fun VerificationDetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {

    Box(
        modifier =
            Modifier
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
                text =
                    title,

                fontSize =
                    15.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            content()
        }
    }
}


@Composable
private fun VerificationDetailRow(
    label: String,
    value: String?
) {

    Row(
        modifier =
            Modifier
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
            text =
                label,

            modifier =
                Modifier.weight(0.42f),

            fontSize =
                9.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color(0xFF918A9B)
        )


        Spacer(
            modifier =
                Modifier.width(8.dp)
        )


        Text(
            text =
                value.verificationMeta(),

            modifier =
                Modifier.weight(0.58f),

            fontSize =
                10.sp,

            color =
                Color(0xFF3F394A)
        )
    }
}


@Composable
private fun VerificationHashBlock(
    label: String,
    value: String?
) {

    Column {

        Text(
            text =
                label,

            fontSize =
                9.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                Color(0xFF6B6375)
        )


        Spacer(
            modifier =
                Modifier.height(5.dp)
        )


        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(13.dp)
                    )
                    .background(
                        Color(0xFFF5F0FA)
                    )
                    .padding(11.dp)
        ) {

            Text(
                text =
                    value.verificationMeta(),

                fontSize =
                    9.sp,

                lineHeight =
                    14.sp,

                color =
                    Color(0xFF604C78)
            )
        }
    }
}


@Composable
private fun VerificationComparisonCard(
    title: String,
    available: Boolean,
    matches: Boolean,
    successText: String,
    failureText: String
) {

    val background =
        when {
            !available ->
                Color(0xFFF3EEF8)

            matches ->
                Color(0xFFEAF7EF)

            else ->
                Color(0xFFFFEDEF)
        }

    val foreground =
        when {
            !available ->
                Color(0xFF7650BD)

            matches ->
                Color(0xFF247148)

            else ->
                Color(0xFFB54050)
        }


    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(14.dp)
                )
                .background(
                    background
                )
                .padding(12.dp)
    ) {

        Column {

            Text(
                text =
                    title,

                fontSize =
                    9.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    foreground
            )


            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )


            Text(
                text =
                    when {
                        !available ->
                            "Comparison unavailable because one or both SHA-256 values are missing."

                        matches ->
                            successText

                        else ->
                            failureText
                    },

                fontSize =
                    9.sp,

                lineHeight =
                    14.sp,

                color =
                    foreground
            )
        }
    }
}


@Composable
private fun VerificationInfoNote(
    text: String
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(14.dp)
                )
                .background(
                    Color(0xFFF1EBFA)
                )
                .padding(12.dp)
    ) {

        Text(
            text =
                text,

            fontSize =
                9.sp,

            lineHeight =
                14.sp,

            color =
                Color(0xFF6F4BB8)
        )
    }
}


@Composable
private fun VerificationHistorySection(
    title: String,
    rows: List<Triple<String, String, String>>
) {

    VerificationDetailSection(
        title =
            title
    ) {

        rows.forEach { row ->

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(13.dp)
                        )
                        .background(
                            Color(0xFFF8F5FB)
                        )
                        .padding(11.dp)
            ) {

                Column {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            text =
                                row.first,

                            modifier =
                                Modifier.weight(1f),

                            fontSize =
                                9.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color(0xFF353146)
                        )


                        Text(
                            text =
                                row.second,

                            fontSize =
                                8.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color(0xFF7650BD)
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )


                    Text(
                        text =
                            row.third,

                        fontSize =
                            8.sp,

                        color =
                            Color(0xFF918A9B)
                    )
                }
            }
        }
    }
}


// =====================================================================
// COMMON CARDS
// =====================================================================

@Composable
private fun VerificationSectionHero(
    badge: String,
    title: String,
    subtitle: String,
    colors: List<Color>
) {

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
                            colors
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
                modifier =
                    Modifier
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
                    text =
                        badge,

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
                    Modifier.width(12.dp)
            )


            Column {

                Text(
                    text =
                        title,

                    fontSize =
                        18.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )


                Text(
                    text =
                        subtitle,

                    fontSize =
                        10.sp,

                    color =
                        Color.White.copy(
                            alpha = 0.82f
                        )
                )
            }
        }
    }
}


@Composable
private fun VerificationOverviewPill(
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
                        alpha = 0.15f
                    )
                )
                .padding(
                    horizontal = 10.dp,
                    vertical = 8.dp
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
                7.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                Color.White.copy(
                    alpha = 0.82f
                )
        )
    }
}


@Composable
private fun VerificationMetricBox(
    modifier: Modifier,
    label: String,
    value: String
) {

    Box(
        modifier =
            modifier
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
                text =
                    label,

                fontSize =
                    7.sp,

                fontWeight =
                    FontWeight.Bold,

                letterSpacing =
                    0.45.sp,

                color =
                    Color(0xFF9A92A3)
            )


            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )


            Text(
                text =
                    value,

                fontSize =
                    9.sp,

                fontWeight =
                    FontWeight.SemiBold,

                color =
                    Color(0xFF353146),

                maxLines = 1,

                overflow =
                    TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun VerificationStatusStrip(
    text: String,
    good: Boolean
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(14.dp)
                )
                .background(
                    if (good) {
                        Color(0xFFEAF7EF)
                    } else {
                        Color(0xFFFFEEF1)
                    }
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                )
    ) {

        Text(
            text =
                text,

            fontSize =
                8.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                if (good) {
                    Color(0xFF247148)
                } else {
                    Color(0xFFB54050)
                }
        )
    }
}


@Composable
private fun VerificationBadge(
    text: String,
    good: Boolean,
    inverted: Boolean = false
) {

    Box(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(50.dp)
                )
                .background(
                    if (inverted) {
                        Color.White.copy(
                            alpha = 0.16f
                        )
                    } else if (good) {
                        Color(0xFFEAF7EF)
                    } else {
                        Color(0xFFFFEDEF)
                    }
                )
                .padding(
                    horizontal = 9.dp,
                    vertical = 6.dp
                )
    ) {

        Text(
            text =
                text.uppercase(
                    Locale.ROOT
                ),

            fontSize =
                7.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                if (inverted) {
                    Color.White
                } else if (good) {
                    Color(0xFF247148)
                } else {
                    Color(0xFFB54050)
                },

            maxLines = 1
        )
    }
}


@Composable
private fun RiskLevelBadge(
    riskLevel: String,
    inverted: Boolean = false
) {

    val normalized =
        riskLevel.uppercase(
            Locale.ROOT
        )

    val high =
        normalized == "HIGH"

    val medium =
        normalized == "MEDIUM"


    val background =
        when {
            inverted ->
                Color.White.copy(
                    alpha = 0.16f
                )

            high ->
                Color(0xFFFFEDEF)

            medium ->
                Color(0xFFFFF1E5)

            else ->
                Color(0xFFEAF7EF)
        }

    val foreground =
        when {
            inverted ->
                Color.White

            high ->
                Color(0xFFB54050)

            medium ->
                Color(0xFFA86428)

            else ->
                Color(0xFF247148)
        }


    Box(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(50.dp)
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
            text =
                normalized,

            fontSize =
                7.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                foreground,

            maxLines = 1
        )
    }
}


@Composable
private fun VerificationLoadingCard(
    text: String
) {

    Box(
        modifier =
            Modifier
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

                strokeWidth =
                    2.dp,

                color =
                    Color(0xFF7650BD)
            )


            Spacer(
                modifier =
                    Modifier.width(10.dp)
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
private fun VerificationMessageCard(
    message: String,
    error: Boolean
) {

    Box(
        modifier =
            Modifier
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
private fun VerificationEmptyCard(
    title: String,
    message: String
) {

    Box(
        modifier =
            Modifier
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
                    Modifier.height(5.dp)
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


@Composable
private fun VerificationDetailUnavailable(
    title: String,
    message: String
) {

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(22.dp)
    ) {

        Column {

            Text(
                text =
                    title,

                fontSize =
                    27.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            VerificationEmptyCard(
                title =
                    "Result unavailable",

                message =
                    message
            )
        }
    }
}


// =====================================================================
// HELPERS
// =====================================================================

private fun List<IntegrityResult>.latestIntegrityResult(): IntegrityResult? {

    return maxWithOrNull(
        compareBy<IntegrityResult> {
            it.processedAt.orEmpty()
        }.thenBy {
            it.integrityResultId ?: 0L
        }
    )
}


private fun List<AlterationResult>.latestAlterationResult(): AlterationResult? {

    return maxWithOrNull(
        compareBy<AlterationResult> {
            it.processedAt.orEmpty()
        }.thenBy {
            it.alterationResultId ?: 0L
        }
    )
}


private fun integrityPassed(
    result: IntegrityResult
): Boolean {

    val value =
        (
                result.finalVerificationResult
                    ?: result.verificationResult
                    ?: result.initialVerificationResult
                    ?: ""
                )
            .uppercase(
                Locale.ROOT
            )

    return value.contains("VERIFIED") ||
            value.contains("PASS") ||
            value.contains("MATCH") ||
            value.contains("VALID")
}


private fun String?.verificationMeta(): String {

    return this
        ?.takeIf {
            it.isNotBlank()
        }
        ?: "Not recorded"
}


private fun String?.isSha256(): Boolean {

    return this != null &&
            this.matches(
                Regex(
                    "[a-fA-F0-9]{64}"
                )
            )
}
