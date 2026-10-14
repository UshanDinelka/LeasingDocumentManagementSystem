package com.leasingdocument.app.capture

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.leasingdocument.app.network.AuthSession
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * DOC SECURE local encrypted capture drafts.
 *
 * Security flow is unchanged:
 * 1. EncryptedCaptureStore decrypts and authenticates the local .ldc draft.
 * 2. The local SHA-256 is rechecked by EncryptedCaptureStore.load().
 * 3. JPEG bytes + original mobile hash + capture metadata are sent to Spring.
 * 4. Spring performs server-side SHA-256, AES-256-GCM encryption, decrypt verification,
 *    encrypted-at-rest storage, database persistence and audit logging.
 * 5. The local draft is deleted only after the backend confirms every secure step.
 */
private enum class DraftNavigationPage {
    CUSTOMERS,
    TYPES,
    DRAFTS,
    DETAILS
}

private data class DraftSummary(
    val id: String,
    val documentName: String,
    val documentTypeCode: String,
    val customerName: String,
    val customerId: Long?,
    val capturedAt: String,
    val qualityStatus: String
) {
    val customerKey: String
        get() =
            customerId
                ?.let { "ID:$it" }
                ?: "NAME:$customerName"

    val typeKey: String
        get() =
            documentTypeCode
                .takeIf { it.isNotBlank() }
                ?: documentName
}



private class DraftPremiumBackgroundView(
    context: Context
) : View(context) {

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth =
                resources.displayMetrics.density * 1.1f
        }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        val w =
            width.toFloat()

        val h =
            height.toFloat()

        if (
            w <= 0f ||
            h <= 0f
        ) {
            return
        }

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

        fillPaint.shader =
            LinearGradient(
                0f,
                0f,
                w * 0.72f,
                h * 0.16f,
                intArrayOf(
                    Color.argb(
                        150,
                        255,
                        209,
                        175
                    ),
                    Color.argb(
                        66,
                        255,
                        232,
                        222
                    ),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )

        canvas.drawPath(
            topShape,
            fillPaint
        )

        fillPaint.shader =
            null

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

        strokePaint.color =
            Color.argb(
                178,
                255,
                139,
                66
            )

        canvas.drawPath(
            ribbon,
            strokePaint
        )

        fillPaint.shader =
            RadialGradient(
                w * 0.55f,
                h,
                w * 0.78f,
                intArrayOf(
                    Color.argb(
                        20,
                        159,
                        71,
                        221
                    ),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )

        canvas.drawCircle(
            w * 0.55f,
            h,
            w * 0.78f,
            fillPaint
        )

        fillPaint.shader =
            null
    }
}


class CaptureDraftsActivity : ComponentActivity() {

    companion object {
        private const val CURRENT_DEVICE_ID = 1L

        private val BG = Color.rgb(252, 249, 255)
        private val TEXT = Color.rgb(24, 26, 45)
        private val MUTED = Color.rgb(125, 118, 136)
        private val PURPLE = Color.rgb(118, 80, 189)
        private val PURPLE_2 = Color.rgb(151, 78, 194)
        private val PINK = Color.rgb(209, 78, 148)
        private val SOFT_PURPLE = Color.rgb(243, 237, 250)
        private val SOFT_PINK = Color.rgb(255, 238, 243)
        private val SUCCESS_BG = Color.rgb(234, 247, 239)
        private val SUCCESS_TEXT = Color.rgb(36, 113, 72)
    }

    private lateinit var content: LinearLayout
    private lateinit var store: EncryptedCaptureStore

    private var agentId = 0L
    private var selectedId: String? = null
    private var displayed: Bitmap? = null
    private var loading = false

    private var navigationPage =
        DraftNavigationPage.CUSTOMERS

    private var selectedCustomerKey: String? =
        null

    private var selectedTypeKey: String? =
        null

    private var draftSummaries: List<DraftSummary> =
        emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        agentId = AuthSession.userId ?: 0L

        if (!authorized()) {
            finish()
            return
        }

        try {
            store = EncryptedCaptureStore(
                applicationContext,
                agentId
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Cannot access local draft storage.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val root =
            FrameLayout(this).apply {
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            Color.rgb(255, 252, 251),
                            Color.rgb(255, 248, 251),
                            Color.rgb(248, 242, 255)
                        )
                    )
            }

        root.addView(
            DraftPremiumBackgroundView(this),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val scroll =
            ScrollView(this).apply {
                setBackgroundColor(
                    Color.TRANSPARENT
                )

                isFillViewport =
                    true

                clipToPadding =
                    false
            }

        val horizontalPad =
            dp(22)

        content =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    horizontalPad,
                    0,
                    horizontalPad,
                    0
                )

                clipToPadding =
                    false
            }

        scroll.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val bottomPad =
            dp(28)

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->

            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                bottomPad + bars.bottom
            )

            insets
        }

        setContentView(root)
        ViewCompat.requestApplyInsets(scroll)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (loading) return

                    when (navigationPage) {
                        DraftNavigationPage.DETAILS -> {
                            selectedId = null

                            val customerKey =
                                selectedCustomerKey

                            val typeKey =
                                selectedTypeKey

                            if (
                                customerKey != null &&
                                typeKey != null
                            ) {
                                showTypeDrafts(
                                    customerKey,
                                    typeKey
                                )
                            } else {
                                showList()
                            }
                        }

                        DraftNavigationPage.DRAFTS -> {
                            selectedId = null
                            selectedTypeKey = null

                            selectedCustomerKey
                                ?.let {
                                    showCustomerTypes(it)
                                }
                                ?: showList()
                        }

                        DraftNavigationPage.TYPES -> {
                            selectedCustomerKey = null
                            selectedTypeKey = null
                            showList()
                        }

                        DraftNavigationPage.CUSTOMERS -> {
                            finish()
                        }
                    }
                }
            }
        )

        showList()
    }

    override fun onResume() {
        super.onResume()

        if (!authorized()) {
            finish()
        }
    }

    private fun authorized(): Boolean =
        agentId > 0L &&
                AuthSession.userId == agentId &&
                AuthSession.role == "AGENT" &&
                !AuthSession.token.isNullOrBlank()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(
        color: Int,
        radiusDp: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

    private fun gradientBackground(
        colors: IntArray,
        radiusDp: Int
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            colors
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun reset() {
        content.removeAllViews()
        displayed?.recycle()
        displayed = null
    }

    private fun addSpace(heightDp: Int) {
        content.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(heightDp)
                )
            }
        )
    }

    private fun addText(
        text: String,
        size: Float = 14f,
        color: Int = TEXT,
        bold: Boolean = false,
        top: Int = 0,
        bottom: Int = 0
    ): TextView {
        val view = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setLineSpacing(0f, 1.08f)
            if (bold) {
                setTypeface(typeface, Typeface.BOLD)
            }
            setPadding(0, dp(top), 0, dp(bottom))
        }

        content.addView(
            view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        return view
    }

    private fun addHeader(
        title: String,
        subtitle: String,
        showBack: Boolean
    ) {
        addSpace(18)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (showBack) {
            val back = TextView(this).apply {
                text = "‹"
                textSize = 30f
                gravity = Gravity.CENTER
                setTextColor(PURPLE)
                background = roundedBackground(SOFT_PURPLE, 100)
                setOnClickListener {
                    if (!loading) {
                        showList()
                    }
                }
            }

            row.addView(
                back,
                LinearLayout.LayoutParams(
                    dp(40),
                    dp(40)
                )
            )

            row.addView(
                View(this),
                LinearLayout.LayoutParams(
                    dp(12),
                    1
                )
            )
        }

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleColumn.addView(
            TextView(this).apply {
                text = title
                textSize = 27f
                setTextColor(TEXT)
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        titleColumn.addView(
            TextView(this).apply {
                text = subtitle
                textSize = 11.5f
                setTextColor(MUTED)
                setPadding(0, dp(3), 0, 0)
            }
        )

        row.addView(
            titleColumn,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        content.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(18)
    }

    private fun addHeroCard(
        countText: String,
        subtitle: String
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradientBackground(
                intArrayOf(
                    Color.rgb(101, 79, 181),
                    Color.rgb(148, 80, 193),
                    PINK
                ),
                24
            )
            setPadding(
                dp(20),
                dp(18),
                dp(20),
                dp(18)
            )
        }

        card.addView(
            TextView(this).apply {
                text = countText
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        card.addView(
            TextView(this).apply {
                text = subtitle
                textSize = 11.5f
                setTextColor(Color.argb(205, 255, 255, 255))
                setPadding(0, dp(5), 0, 0)
            }
        )

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(20)
    }

    private fun actionButton(
        text: String,
        background: GradientDrawable,
        textColor: Int,
        action: () -> Unit
    ) {
        val button = TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.04f
            this.background = background
            setOnClickListener {
                if (!loading) {
                    action()
                }
            }
        }

        content.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(10)
            }
        )
    }

    private fun addStatusCard(
        title: String,
        message: String,
        backgroundColor: Int = Color.WHITE,
        titleColor: Int = TEXT
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(backgroundColor, 20)
            setPadding(
                dp(18),
                dp(16),
                dp(18),
                dp(16)
            )
        }

        card.addView(
            TextView(this).apply {
                text = title
                textSize = 15f
                setTextColor(titleColor)
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        card.addView(
            TextView(this).apply {
                text = message
                textSize = 11.5f
                setTextColor(MUTED)
                setPadding(0, dp(6), 0, 0)
            }
        )

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    // =========================================================
    // DRAFT LIST — CUSTOMER -> DOCUMENT TYPE -> DRAFTS
    // =========================================================

    private fun showList() {
        if (!authorized()) {
            finish()
            return
        }

        selectedId = null
        selectedCustomerKey = null
        selectedTypeKey = null
        navigationPage = DraftNavigationPage.CUSTOMERS

        reset()
        loading = true

        addHeader(
            title = "Local Capture Drafts",
            subtitle = "",
            showBack = false
        )

        lifecycleScope.launch {
            try {
                val ids = withContext(Dispatchers.IO) {
                    store.ids()
                }

                val summaries =
                    ids.map { id ->
                        try {
                            loadDraftSummary(id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            DraftSummary(
                                id = id,
                                documentName = "Unreadable Draft",
                                documentTypeCode = "",
                                customerName = "Integrity check required",
                                customerId = null,
                                capturedAt = id,
                                qualityStatus = "CHECK"
                            )
                        }
                    }

                draftSummaries =
                    summaries.sortedByDescending {
                        it.capturedAt
                    }

                val customerCount =
                    draftSummaries
                        .map { it.customerKey }
                        .distinct()
                        .size

                val typeCount =
                    draftSummaries
                        .map { it.typeKey }
                        .distinct()
                        .size

                addDraftOverviewHero(
                    totalDrafts = draftSummaries.size,
                    customerCount = customerCount,
                    typeCount = typeCount
                )

                if (draftSummaries.isEmpty()) {
                    addStatusCard(
                        title = "No saved drafts",
                        message = "New encrypted captures will appear here after you save them from the secure camera."
                    )
                    return@launch
                }

                addSectionTitle(
                    title = "Customer Accounts",
                    trailing = "$customerCount CUSTOMER${if (customerCount == 1) "" else "S"}"
                )

                val customerGroups =
                    draftSummaries
                        .groupBy { it.customerKey }
                        .entries
                        .sortedBy {
                            it.value
                                .firstOrNull()
                                ?.customerName
                                .orEmpty()
                                .lowercase()
                        }

                customerGroups.forEach { entry ->
                    val group =
                        entry.value

                    val first =
                        group.first()

                    addCustomerFolderCard(
                        customerName = first.customerName,
                        customerId = first.customerId,
                        draftCount = group.size,
                        typeCount =
                            group
                                .map { it.typeKey }
                                .distinct()
                                .size
                    ) {
                        showCustomerTypes(
                            entry.key
                        )
                    }
                }

                addSpace(10)

            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                addStatusCard(
                    title = "Cannot read local drafts",
                    message = "The encrypted local draft store could not be opened.",
                    backgroundColor = SOFT_PINK,
                    titleColor = Color.rgb(161, 50, 69)
                )

                actionButton(
                    text = "TRY AGAIN",
                    background = gradientBackground(
                        intArrayOf(PURPLE, PURPLE_2),
                        16
                    ),
                    textColor = Color.WHITE
                ) {
                    showList()
                }
            } finally {
                loading = false
            }
        }
    }

    private fun showCustomerTypes(
        customerKey: String
    ) {
        if (!authorized()) {
            finish()
            return
        }

        selectedId = null
        selectedCustomerKey = customerKey
        selectedTypeKey = null
        navigationPage = DraftNavigationPage.TYPES

        reset()
        loading = false

        val customerDrafts =
            draftSummaries
                .filter {
                    it.customerKey == customerKey
                }
                .sortedByDescending {
                    it.capturedAt
                }

        val first =
            customerDrafts.firstOrNull()

        if (first == null) {
            showList()
            return
        }

        val typeGroups =
            customerDrafts
                .groupBy { it.typeKey }
                .entries
                .sortedBy {
                    it.value
                        .firstOrNull()
                        ?.documentName
                        .orEmpty()
                        .lowercase()
                }

        addHeader(
            title = "Customer Drafts",
            subtitle = first.customerName,
            showBack = false
        )

        addCustomerDraftHero(
            customerName = first.customerName,
            customerId = first.customerId,
            draftCount = customerDrafts.size,
            typeCount = typeGroups.size
        )

        addSectionTitle(
            title = "Document Categories",
            trailing = "${typeGroups.size} TYPE${if (typeGroups.size == 1) "" else "S"}"
        )

        typeGroups.forEach { entry ->
            val group =
                entry.value

            val type =
                group.first()

            addDocumentTypeFolderCard(
                documentName = type.documentName,
                documentTypeCode = type.documentTypeCode,
                draftCount = group.size,
                latestCapture =
                    group.maxByOrNull {
                        it.capturedAt
                    }
                        ?.capturedAt
                        ?: "Capture time unavailable"
            ) {
                showTypeDrafts(
                    customerKey = customerKey,
                    typeKey = entry.key
                )
            }
        }

        addSpace(10)
    }

    private fun showTypeDrafts(
        customerKey: String,
        typeKey: String
    ) {
        if (!authorized()) {
            finish()
            return
        }

        selectedId = null
        selectedCustomerKey = customerKey
        selectedTypeKey = typeKey
        navigationPage = DraftNavigationPage.DRAFTS

        reset()
        loading = false

        val drafts =
            draftSummaries
                .filter {
                    it.customerKey == customerKey &&
                            it.typeKey == typeKey
                }
                .sortedByDescending {
                    it.capturedAt
                }

        val first =
            drafts.firstOrNull()

        if (first == null) {
            showCustomerTypes(
                customerKey
            )
            return
        }

        addHeader(
            title = first.documentName,
            subtitle = first.customerName,
            showBack = false
        )

        addTypeDraftHero(
            documentName = first.documentName,
            draftCount = drafts.size,
            customerName = first.customerName
        )

        addSectionTitle(
            title = "Saved Drafts",
            trailing = "${drafts.size} DRAFT${if (drafts.size == 1) "" else "S"}"
        )

        drafts.forEachIndexed { index, summary ->
            addDraftRecordCard(
                summary = summary,
                sequence = drafts.size - index
            )
        }

        addSpace(10)
    }

    private suspend fun loadDraftSummary(
        id: String
    ): DraftSummary =
        withContext(Dispatchers.IO) {
            val draft =
                store.load(id)

            try {
                val metadata =
                    draft.metadata

                val customerId =
                    if (
                        metadata.has("customerId") &&
                        !metadata.isNull("customerId")
                    ) {
                        metadata.optLong("customerId")
                            .takeIf { it > 0L }
                    } else {
                        null
                    }

                DraftSummary(
                    id = id,
                    documentName =
                        metadata
                            .optString("documentTypeName")
                            .ifBlank {
                                "Secure Document"
                            },
                    documentTypeCode =
                        metadata
                            .optString("documentTypeCode")
                            .trim(),
                    customerName =
                        metadata
                            .optString("customerName")
                            .ifBlank {
                                customerId
                                    ?.let {
                                        "Customer #$it"
                                    }
                                    ?: "Unknown customer"
                            },
                    customerId = customerId,
                    capturedAt =
                        metadata
                            .optString("capturedAtOffset")
                            .ifBlank {
                                "Capture time unavailable"
                            },
                    qualityStatus =
                        metadata
                            .optString("qualityStatus")
                            .ifBlank {
                                "CHECK"
                            }
                )
            } finally {
                draft.jpeg.fill(0)
            }
        }

    private fun addDraftOverviewHero(
        totalDrafts: Int,
        customerCount: Int,
        typeCount: Int
    ) {
        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(101, 79, 181),
                            Color.rgb(148, 80, 193),
                            PINK
                        ),
                        24
                    )

                setPadding(
                    dp(18),
                    dp(17),
                    dp(18),
                    dp(17)
                )
            }

        card.addView(
            TextView(this).apply {
                text =
                    "Secure Draft Workspace"

                textSize =
                    11f

                setTextColor(
                    Color.argb(
                        210,
                        255,
                        255,
                        255
                    )
                )
            }
        )

        card.addView(
            TextView(this).apply {
                text =
                    "$totalDrafts Secure Draft${if (totalDrafts == 1) "" else "s"}"

                textSize =
                    21f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }
        )

        val metrics =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    dp(13),
                    0,
                    0
                )
            }

        metrics.addView(
            createHeroMetric(
                value =
                    customerCount.toString(),
                label =
                    "CUSTOMERS"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd =
                    dp(4)
            }
        )

        metrics.addView(
            createHeroMetric(
                value =
                    typeCount.toString(),
                label =
                    "TYPES"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart =
                    dp(4)

                marginEnd =
                    dp(4)
            }
        )

        metrics.addView(
            createHeroMetric(
                value =
                    totalDrafts.toString(),
                label =
                    "LOCAL"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart =
                    dp(4)
            }
        )

        card.addView(metrics)

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(20)
    }

    private fun createHeroMetric(
        value: String,
        label: String
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation =
                LinearLayout.VERTICAL

            gravity =
                Gravity.CENTER

            background =
                roundedBackground(
                    Color.argb(
                        34,
                        255,
                        255,
                        255
                    ),
                    14
                )

            setPadding(
                dp(7),
                dp(8),
                dp(7),
                dp(8)
            )

            addView(
                TextView(this@CaptureDraftsActivity).apply {
                    text =
                        value

                    textSize =
                        13f

                    gravity =
                        Gravity.CENTER

                    setTextColor(
                        Color.WHITE
                    )

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }
            )

            addView(
                TextView(this@CaptureDraftsActivity).apply {
                    text =
                        label

                    textSize =
                        7.5f

                    gravity =
                        Gravity.CENTER

                    letterSpacing =
                        0.05f

                    setTextColor(
                        Color.argb(
                            195,
                            255,
                            255,
                            255
                        )
                    )

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )

                    setPadding(
                        0,
                        dp(2),
                        0,
                        0
                    )
                }
            )
        }

    private fun addSectionTitle(
        title: String,
        trailing: String
    ) {
        val row =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        row.addView(
            TextView(this).apply {
                text =
                    title

                textSize =
                    18f

                setTextColor(
                    TEXT
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        row.addView(
            TextView(this).apply {
                text =
                    trailing

                textSize =
                    8f

                letterSpacing =
                    0.06f

                setTextColor(
                    MUTED
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }
        )

        content.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun addCustomerFolderCard(
        customerName: String,
        customerId: Long?,
        draftCount: Int,
        typeCount: Int,
        onClick: () -> Unit
    ) {
        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    roundedBackground(
                        Color.WHITE,
                        21
                    )

                setPadding(
                    dp(15),
                    dp(14),
                    dp(15),
                    dp(14)
                )

                isClickable =
                    true

                isFocusable =
                    true

                setOnClickListener {
                    if (!loading) {
                        onClick()
                    }
                }
            }

        val top =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val avatar =
            TextView(this).apply {
                text =
                    initials(
                        customerName
                    )

                textSize =
                    10.5f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(240, 120, 98),
                            Color.rgb(214, 78, 148)
                        ),
                        100
                    )
            }

        top.addView(
            avatar,
            LinearLayout.LayoutParams(
                dp(46),
                dp(46)
            )
        )

        val middle =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    0,
                    dp(8),
                    0
                )
            }

        middle.addView(
            TextView(this).apply {
                text =
                    customerName

                textSize =
                    14f

                setTextColor(
                    TEXT
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                maxLines =
                    1
            }
        )

        middle.addView(
            TextView(this).apply {
                text =
                    customerId
                        ?.let {
                            "Customer ID $it"
                        }
                        ?: "Local customer"

                textSize =
                    9f

                setTextColor(
                    MUTED
                )

                setPadding(
                    0,
                    dp(3),
                    0,
                    0
                )
            }
        )

        middle.addView(
            TextView(this).apply {
                text =
                    "$draftCount draft${if (draftCount == 1) "" else "s"}  •  " +
                            "$typeCount document type${if (typeCount == 1) "" else "s"}"

                textSize =
                    9f

                setTextColor(
                    PURPLE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }
        )

        top.addView(
            middle,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val right =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_HORIZONTAL
            }

        right.addView(
            TextView(this).apply {
                text =
                    "LOCAL"

                textSize =
                    7f

                gravity =
                    Gravity.CENTER

                letterSpacing =
                    0.04f

                setTextColor(
                    PURPLE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                background =
                    roundedBackground(
                        SOFT_PURPLE,
                        100
                    )

                setPadding(
                    dp(8),
                    dp(5),
                    dp(8),
                    dp(5)
                )
            }
        )

        right.addView(
            TextView(this).apply {
                text =
                    "›"

                textSize =
                    24f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    PURPLE
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dp(2)
            }
        )

        top.addView(right)

        card.addView(top)

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin =
                    dp(10)
            }
        )
    }

    private fun addCustomerDraftHero(
        customerName: String,
        customerId: Long?,
        draftCount: Int,
        typeCount: Int
    ) {
        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(101, 79, 181),
                            Color.rgb(148, 80, 193),
                            PINK
                        ),
                        24
                    )

                setPadding(
                    dp(18),
                    dp(17),
                    dp(18),
                    dp(17)
                )
            }

        card.addView(
            TextView(this).apply {
                text =
                    customerName

                textSize =
                    18f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }
        )

        card.addView(
            TextView(this).apply {
                text =
                    customerId
                        ?.let {
                            "Customer ID: $it"
                        }
                        ?: "Secure local customer drafts"

                textSize =
                    9.5f

                setTextColor(
                    Color.argb(
                        205,
                        255,
                        255,
                        255
                    )
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }
        )

        val metrics =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(14),
                    0,
                    0
                )
            }

        metrics.addView(
            createHeroMetric(
                value =
                    draftCount.toString(),
                label =
                    "DRAFTS"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(5)
            }
        )

        metrics.addView(
            createHeroMetric(
                value =
                    typeCount.toString(),
                label =
                    "TYPES"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(5)
            }
        )

        card.addView(metrics)

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(18)
    }

    private fun addDocumentTypeFolderCard(
        documentName: String,
        documentTypeCode: String,
        draftCount: Int,
        latestCapture: String,
        onClick: () -> Unit
    ) {
        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    roundedBackground(
                        Color.WHITE,
                        22
                    )

                setPadding(
                    dp(15),
                    dp(14),
                    dp(15),
                    dp(14)
                )

                isClickable =
                    true

                isFocusable =
                    true

                setOnClickListener {
                    if (!loading) {
                        onClick()
                    }
                }
            }

        val top =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val icon =
            TextView(this).apply {
                text =
                    "DOC"

                textSize =
                    9f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(111, 82, 189),
                            Color.rgb(209, 77, 146)
                        ),
                        15
                    )
            }

        top.addView(
            icon,
            LinearLayout.LayoutParams(
                dp(50),
                dp(50)
            )
        )

        val middle =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(13),
                    0,
                    dp(8),
                    0
                )
            }

        middle.addView(
            TextView(this).apply {
                text =
                    documentName

                textSize =
                    14.5f

                setTextColor(
                    TEXT
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                maxLines =
                    1
            }
        )

        middle.addView(
            TextView(this).apply {
                text =
                    documentTypeCode
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?: "Document category"

                textSize =
                    8.5f

                letterSpacing =
                    0.04f

                setTextColor(
                    MUTED
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }
        )

        top.addView(
            middle,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        top.addView(
            TextView(this).apply {
                text =
                    "›"

                textSize =
                    27f

                setTextColor(
                    PURPLE
                )
            }
        )

        card.addView(top)

        val info =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(12),
                    0,
                    0
                )
            }

        info.addView(
            createSoftMetric(
                label =
                    "SAVED DRAFTS",
                value =
                    draftCount.toString()
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(5)
            }
        )

        info.addView(
            createSoftMetric(
                label =
                    "LATEST CAPTURE",
                value =
                    compactCaptureTime(
                        latestCapture
                    )
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(5)
            }
        )

        card.addView(info)

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        )
    }

    private fun addTypeDraftHero(
        documentName: String,
        draftCount: Int,
        customerName: String
    ) {
        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(101, 79, 181),
                            Color.rgb(148, 80, 193),
                            PINK
                        ),
                        24
                    )

                setPadding(
                    dp(18),
                    dp(17),
                    dp(18),
                    dp(17)
                )
            }

        card.addView(
            TextView(this).apply {
                text =
                    documentName

                textSize =
                    18f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }
        )

        card.addView(
            TextView(this).apply {
                text =
                    customerName

                textSize =
                    9.5f

                setTextColor(
                    Color.argb(
                        205,
                        255,
                        255,
                        255
                    )
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }
        )

        card.addView(
            createHeroMetric(
                value =
                    draftCount.toString(),
                label =
                    "SECURE LOCAL DRAFTS"
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        )

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(18)
    }

    private fun addDraftRecordCard(
        summary: DraftSummary,
        sequence: Int
    ) {
        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    roundedBackground(
                        Color.WHITE,
                        20
                    )

                setPadding(
                    dp(15),
                    dp(15),
                    dp(15),
                    dp(15)
                )

                isClickable =
                    true

                isFocusable =
                    true

                setOnClickListener {
                    if (!loading) {
                        showDraft(
                            summary.id
                        )
                    }
                }
            }

        val top =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        top.addView(
            TextView(this).apply {
                text =
                    "DR"

                textSize =
                    9f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(255, 130, 71),
                            Color.rgb(240, 82, 145),
                            Color.rgb(154, 78, 209)
                        ),
                        14
                    )
            },
            LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        )

        val middle =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    0,
                    dp(8),
                    0
                )
            }

        middle.addView(
            TextView(this).apply {
                text =
                    "${summary.documentName} #$sequence"

                textSize =
                    13.5f

                setTextColor(
                    TEXT
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                maxLines =
                    1
            }
        )

        middle.addView(
            TextView(this).apply {
                text =
                    compactCaptureTime(
                        summary.capturedAt
                    )

                textSize =
                    9f

                setTextColor(
                    MUTED
                )

                setPadding(
                    0,
                    dp(3),
                    0,
                    0
                )
            }
        )

        top.addView(
            middle,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        top.addView(
            createQualityBadge(
                summary.qualityStatus
            )
        )

        card.addView(top)

        val bottom =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    dp(12),
                    0,
                    0
                )
            }

        bottom.addView(
            TextView(this).apply {
                text =
                    "LOCAL ENCRYPTED DRAFT"

                textSize =
                    7.5f

                letterSpacing =
                    0.05f

                setTextColor(
                    MUTED
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        bottom.addView(
            TextView(this).apply {
                text =
                    "VIEW DETAILS ›"

                textSize =
                    8f

                letterSpacing =
                    0.03f

                setTextColor(
                    PURPLE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }
        )

        card.addView(bottom)

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun createSoftMetric(
        label: String,
        value: String
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation =
                LinearLayout.VERTICAL

            background =
                roundedBackground(
                    Color.rgb(248, 245, 251),
                    13
                )

            setPadding(
                dp(10),
                dp(9),
                dp(10),
                dp(9)
            )

            addView(
                TextView(this@CaptureDraftsActivity).apply {
                    text =
                        label

                    textSize =
                        7f

                    letterSpacing =
                        0.05f

                    setTextColor(
                        Color.rgb(
                            154,
                            146,
                            163
                        )
                    )

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }
            )

            addView(
                TextView(this@CaptureDraftsActivity).apply {
                    text =
                        value

                    textSize =
                        9.5f

                    setTextColor(
                        Color.rgb(
                            53,
                            49,
                            70
                        )
                    )

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )

                    maxLines =
                        1

                    setPadding(
                        0,
                        dp(3),
                        0,
                        0
                    )
                }
            )
        }

    private fun createQualityBadge(
        qualityStatus: String
    ): TextView {
        val normalized =
            qualityStatus
                .trim()
                .uppercase()

        val passed =
            normalized.contains("PASS") ||
                    normalized.contains("GOOD") ||
                    normalized.contains("OK")

        return TextView(this).apply {
            text =
                normalized
                    .ifBlank {
                        "CHECK"
                    }

            textSize =
                7f

            gravity =
                Gravity.CENTER

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setTextColor(
                if (passed) {
                    SUCCESS_TEXT
                } else {
                    PURPLE
                }
            )

            background =
                roundedBackground(
                    if (passed) {
                        SUCCESS_BG
                    } else {
                        SOFT_PURPLE
                    },
                    100
                )

            setPadding(
                dp(9),
                dp(6),
                dp(9),
                dp(6)
            )
        }
    }

    private fun initials(
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

        return when {
            parts.isEmpty() ->
                "CU"

            parts.size == 1 ->
                parts
                    .first()
                    .take(2)
                    .uppercase()

            else ->
                (
                        parts
                            .first()
                            .take(1) +
                                parts
                                    .last()
                                    .take(1)
                        )
                    .uppercase()
        }
    }

    private fun compactCaptureTime(
        value: String
    ): String {
        if (
            value.isBlank() ||
            value == "Capture time unavailable"
        ) {
            return "Not available"
        }

        return value
            .replace(
                'T',
                ' '
            )
            .substringBefore('.')
    }

    // =========================================================
    // DRAFT DETAIL
    // =========================================================

    private fun showDraft(
        id: String
    ) {
        if (!authorized()) {
            finish()
            return
        }

        reset()
        selectedId = id
        navigationPage = DraftNavigationPage.DETAILS
        loading = true

        addHeader(
            title = "Draft Details",
            subtitle = "",
            showBack = false
        )

        lifecycleScope.launch {
            var readyBitmap: Bitmap? = null

            try {
                val meta =
                    withContext(Dispatchers.IO) {
                        val draft =
                            store.load(id)

                        try {
                            val options =
                                BitmapFactory.Options().apply {
                                    inJustDecodeBounds = true
                                }

                            BitmapFactory.decodeByteArray(
                                draft.jpeg,
                                0,
                                draft.jpeg.size,
                                options
                            )

                            require(
                                options.outWidth > 0 &&
                                        options.outHeight > 0 &&
                                        options.outWidth.toLong() *
                                        options.outHeight <= 12_000_000L
                            )

                            var sample = 1

                            while (
                                maxOf(
                                    options.outWidth,
                                    options.outHeight
                                ) / sample > 2048
                            ) {
                                sample *= 2
                            }

                            val decoded =
                                requireNotNull(
                                    BitmapFactory.decodeByteArray(
                                        draft.jpeg,
                                        0,
                                        draft.jpeg.size,
                                        BitmapFactory.Options().apply {
                                            inSampleSize = sample
                                        }
                                    )
                                )

                            val rotation =
                                draft.metadata.optInt(
                                    "rotationDegrees",
                                    0
                                )

                            if (rotation == 0) {
                                readyBitmap = decoded
                            } else {
                                readyBitmap =
                                    Bitmap.createBitmap(
                                        decoded,
                                        0,
                                        0,
                                        decoded.width,
                                        decoded.height,
                                        Matrix().apply {
                                            postRotate(
                                                rotation.toFloat()
                                            )
                                        },
                                        true
                                    )

                                if (readyBitmap !== decoded) {
                                    decoded.recycle()
                                }
                            }

                            JSONObject(
                                draft.metadata.toString()
                            )

                        } finally {
                            draft.jpeg.fill(0)
                        }
                    }

                if (!authorized()) {
                    finish()
                    return@launch
                }

                displayed = readyBitmap
                readyBitmap = null

                addDraftDetailHero(
                    meta = meta
                )

                addDraftPreviewCard(
                    bitmap = displayed,
                    meta = meta
                )

                addDraftInformationSection(
                    meta = meta
                )

                addDraftQualitySection(
                    meta = meta
                )

                addDraftSecuritySection(
                    meta = meta
                )

                addDraftSubmissionSection(
                    id = id
                )

            } catch (e: CancellationException) {
                throw e

            } catch (_: Exception) {
                addStatusCard(
                    title = "Draft verification failed",
                    message = "This draft could not be decrypted or failed its local integrity check. It cannot be submitted.",
                    backgroundColor = SOFT_PINK,
                    titleColor = Color.rgb(161, 50, 69)
                )

            } finally {
                readyBitmap?.recycle()
                loading = false
            }
        }
    }


    private fun addDraftDetailHero(
        meta: JSONObject
    ) {
        val customerName =
            meta
                .optString("customerName")
                .ifBlank {
                    "Customer"
                }

        val documentName =
            meta
                .optString("documentTypeName")
                .ifBlank {
                    "Secure Document"
                }

        val qualityStatus =
            meta
                .optString("qualityStatus")
                .ifBlank {
                    "CHECK"
                }
                .uppercase()

        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(101, 79, 181),
                            Color.rgb(148, 80, 193),
                            PINK
                        ),
                        24
                    )

                setPadding(
                    dp(18),
                    dp(17),
                    dp(18),
                    dp(17)
                )
            }

        val top =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        top.addView(
            TextView(this).apply {
                text =
                    "DR"

                textSize =
                    10f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                background =
                    roundedBackground(
                        Color.argb(
                            42,
                            255,
                            255,
                            255
                        ),
                        16
                    )
            },
            LinearLayout.LayoutParams(
                dp(52),
                dp(52)
            )
        )

        val titleArea =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    0,
                    dp(8),
                    0
                )
            }

        titleArea.addView(
            TextView(this).apply {
                text =
                    documentName

                textSize =
                    17f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                maxLines =
                    1
            }
        )

        titleArea.addView(
            TextView(this).apply {
                text =
                    customerName

                textSize =
                    10f

                setTextColor(
                    Color.argb(
                        205,
                        255,
                        255,
                        255
                    )
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }
        )

        top.addView(
            titleArea,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        top.addView(
            TextView(this).apply {
                text =
                    "LOCAL"

                textSize =
                    7f

                gravity =
                    Gravity.CENTER

                letterSpacing =
                    0.05f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                background =
                    roundedBackground(
                        Color.argb(
                            38,
                            255,
                            255,
                            255
                        ),
                        100
                    )

                setPadding(
                    dp(9),
                    dp(6),
                    dp(9),
                    dp(6)
                )
            }
        )

        card.addView(top)

        val metrics =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(14),
                    0,
                    0
                )
            }

        metrics.addView(
            createHeroMetric(
                value =
                    qualityStatus,
                label =
                    "QUALITY"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd =
                    dp(5)
            }
        )

        metrics.addView(
            createHeroMetric(
                value =
                    meta
                        .optString("captureSource")
                        .ifBlank {
                            "LIVE"
                        }
                        .replace(
                            "_",
                            " "
                        ),
                label =
                    "SOURCE"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart =
                    dp(5)
            }
        )

        card.addView(metrics)

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(14)
    }


    private fun addDraftPreviewCard(
        bitmap: Bitmap?,
        meta: JSONObject
    ) {
        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    roundedBackground(
                        Color.WHITE,
                        22
                    )

                setPadding(
                    dp(14),
                    dp(14),
                    dp(14),
                    dp(14)
                )
            }

        val header =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val textArea =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        textArea.addView(
            TextView(this).apply {
                text =
                    "Capture Preview"

                textSize =
                    15f

                setTextColor(
                    TEXT
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }
        )

        textArea.addView(
            TextView(this).apply {
                val width =
                    meta.optInt(
                        "imageWidth"
                    )

                val height =
                    meta.optInt(
                        "imageHeight"
                    )

                text =
                    if (
                        width > 0 &&
                        height > 0
                    ) {
                        "$width × $height px • local encrypted copy"
                    } else {
                        "Local encrypted copy"
                    }

                textSize =
                    9f

                setTextColor(
                    MUTED
                )

                setPadding(
                    0,
                    dp(3),
                    0,
                    0
                )
            }
        )

        header.addView(
            textArea,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(
            createQualityBadge(
                meta
                    .optString("qualityStatus")
                    .ifBlank {
                        "CHECK"
                    }
            )
        )

        card.addView(header)

        val previewFrame =
            FrameLayout(this).apply {
                background =
                    roundedBackground(
                        Color.rgb(
                            30,
                            31,
                            47
                        ),
                        18
                    )

                setPadding(
                    dp(8),
                    dp(8),
                    dp(8),
                    dp(8)
                )
            }

        previewFrame.addView(
            ImageView(this).apply {
                setImageBitmap(
                    bitmap
                )

                adjustViewBounds =
                    true

                scaleType =
                    ImageView.ScaleType.FIT_CENTER

                background =
                    roundedBackground(
                        Color.rgb(
                            30,
                            31,
                            47
                        ),
                        15
                    )

                clipToOutline =
                    true
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(300)
            )
        )

        card.addView(
            previewFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dp(12)
            }
        )

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(12)
    }


    private fun addDraftInformationSection(
        meta: JSONObject
    ) {
        val card =
            createDraftSectionCard(
                title =
                    "Draft Information"
            )

        addProfessionalMetaRow(
            parent =
                card,

            label =
                "Customer",

            value =
                meta
                    .optString("customerName")
                    .ifBlank {
                        "Not recorded"
                    }
        )

        addProfessionalMetaRow(
            parent =
                card,

            label =
                "Document type",

            value =
                meta
                    .optString("documentTypeName")
                    .ifBlank {
                        "Not recorded"
                    }
        )

        addProfessionalMetaRow(
            parent =
                card,

            label =
                "Captured",

            value =
                compactCaptureTime(
                    meta
                        .optString("capturedAtOffset")
                        .ifBlank {
                            "Capture time unavailable"
                        }
                )
        )

        addProfessionalMetaRow(
            parent =
                card,

            label =
                "Capture source",

            value =
                meta
                    .optString("captureSource")
                    .ifBlank {
                        "Not recorded"
                    }
                    .replace(
                        "_",
                        " "
                    )
        )

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(12)
    }


    private fun addDraftQualitySection(
        meta: JSONObject
    ) {
        val width =
            meta.optInt(
                "imageWidth"
            )

        val height =
            meta.optInt(
                "imageHeight"
            )

        val blurScore =
            meta.optDouble(
                "blurScore"
            )

        val brightnessScore =
            meta.optDouble(
                "brightnessScore"
            )

        val blurPassed =
            meta.optBoolean(
                "blurPassed",
                false
            )

        val brightnessPassed =
            meta.optBoolean(
                "brightnessPassed",
                false
            )

        val resolutionPassed =
            meta.optBoolean(
                "resolutionPassed",
                false
            )

        val card =
            createDraftSectionCard(
                title =
                    "Image Quality"
            )

        val metrics =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        metrics.addView(
            createDraftQualityMetric(
                label =
                    "RESOLUTION",

                value =
                    if (
                        width > 0 &&
                        height > 0
                    ) {
                        "$width × $height"
                    } else {
                        "-"
                    },

                passed =
                    resolutionPassed
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd =
                    dp(4)
            }
        )

        metrics.addView(
            createDraftQualityMetric(
                label =
                    "BLUR",

                value =
                    if (blurScore.isFinite()) {
                        String.format(
                            "%.2f",
                            blurScore
                        )
                    } else {
                        "-"
                    },

                passed =
                    blurPassed
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart =
                    dp(4)

                marginEnd =
                    dp(4)
            }
        )

        metrics.addView(
            createDraftQualityMetric(
                label =
                    "BRIGHTNESS",

                value =
                    if (brightnessScore.isFinite()) {
                        String.format(
                            "%.2f",
                            brightnessScore
                        )
                    } else {
                        "-"
                    },

                passed =
                    brightnessPassed
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart =
                    dp(4)
            }
        )

        card.addView(
            metrics,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dp(12)
            }
        )

        addProfessionalMetaRow(
            parent =
                card,

            label =
                "Overall quality",

            value =
                meta
                    .optString("qualityStatus")
                    .ifBlank {
                        "Not recorded"
                    }
        )

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(12)
    }


    private fun addDraftSecuritySection(
        meta: JSONObject
    ) {
        val card =
            createDraftSectionCard(
                title =
                    "Local Security"
            )

        val verifiedBox =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    roundedBackground(
                        SUCCESS_BG,
                        15
                    )

                setPadding(
                    dp(13),
                    dp(12),
                    dp(13),
                    dp(12)
                )
            }

        verifiedBox.addView(
            TextView(this).apply {
                text =
                    "Local integrity verified"

                textSize =
                    12.5f

                setTextColor(
                    SUCCESS_TEXT
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }
        )

        verifiedBox.addView(
            TextView(this).apply {
                text =
                    "Local SHA-256 verified."

                textSize =
                    10f

                setTextColor(
                    Color.rgb(
                        80,
                        113,
                        93
                    )
                )

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }
        )

        card.addView(
            verifiedBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dp(12)
            }
        )

        val securityMetrics =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        securityMetrics.addView(
            createSoftMetric(
                label =
                    "LOCAL STORAGE",

                value =
                    "ENCRYPTED"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd =
                    dp(5)
            }
        )

        securityMetrics.addView(
            createSoftMetric(
                label =
                    "INTEGRITY",

                value =
                    "SHA-256"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart =
                    dp(5)
            }
        )

        card.addView(
            securityMetrics,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dp(10)
            }
        )

        val hash =
            meta
                .optString("mobileSha256")
                .ifBlank {
                    "Not recorded"
                }

        val hashBox =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    roundedBackground(
                        SOFT_PURPLE,
                        15
                    )

                setPadding(
                    dp(13),
                    dp(12),
                    dp(13),
                    dp(12)
                )
            }

        hashBox.addView(
            TextView(this).apply {
                text =
                    "Original SHA-256"

                textSize =
                    9f

                setTextColor(
                    MUTED
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }
        )

        hashBox.addView(
            TextView(this).apply {
                text =
                    hash

                textSize =
                    9f

                setTextColor(
                    Color.rgb(
                        96,
                        76,
                        120
                    )
                )

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }
        )

        card.addView(
            hashBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dp(10)
            }
        )

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(12)
    }


    private fun addDraftSubmissionSection(
        id: String
    ) {
        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                background =
                    roundedBackground(
                        Color.WHITE,
                        22
                    )

                setPadding(
                    dp(16),
                    dp(15),
                    dp(16),
                    dp(16)
                )
            }

        card.addView(
            TextView(this).apply {
                text =
                    "Secure Submission"

                textSize =
                    15f

                setTextColor(
                    TEXT
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }
        )

        card.addView(
            TextView(this).apply {
                text =
                    "Secure backend submission."

                textSize =
                    10f

                setTextColor(
                    MUTED
                )

                setPadding(
                    0,
                    dp(5),
                    0,
                    dp(12)
                )
            }
        )

        val submitButton =
            TextView(this).apply {
                text =
                    "SUBMIT SECURELY"

                textSize =
                    10f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                letterSpacing =
                    0.04f

                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(
                                255,
                                123,
                                77
                            ),
                            Color.rgb(
                                240,
                                77,
                                142
                            ),
                            Color.rgb(
                                140,
                                77,
                                209
                            )
                        ),
                        15
                    )

                setOnClickListener {
                    if (!loading) {
                        submitDraft(id)
                    }
                }
            }

        card.addView(
            submitButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        val deleteButton =
            TextView(this).apply {
                text =
                    "DELETE LOCAL DRAFT"

                textSize =
                    9f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.rgb(
                        174,
                        55,
                        77
                    )
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                background =
                    roundedBackground(
                        SOFT_PINK,
                        14
                    )

                setOnClickListener {
                    if (!loading) {
                        confirmDelete(id)
                    }
                }
            }

        card.addView(
            deleteButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply {
                topMargin =
                    dp(9)
            }
        )

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(18)
    }


    private fun createDraftSectionCard(
        title: String
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation =
                LinearLayout.VERTICAL

            background =
                roundedBackground(
                    Color.WHITE,
                    21
                )

            setPadding(
                dp(16),
                dp(15),
                dp(16),
                dp(16)
            )

            addView(
                TextView(this@CaptureDraftsActivity).apply {
                    text =
                        title

                    textSize =
                        15f

                    setTextColor(
                        TEXT
                    )

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }
            )
        }
    }


    private fun addProfessionalMetaRow(
        parent: LinearLayout,
        label: String,
        value: String
    ) {
        val row =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.TOP

                background =
                    roundedBackground(
                        Color.rgb(
                            248,
                            245,
                            251
                        ),
                        13
                    )

                setPadding(
                    dp(11),
                    dp(10),
                    dp(11),
                    dp(10)
                )
            }

        row.addView(
            TextView(this).apply {
                text =
                    label

                textSize =
                    9f

                setTextColor(
                    Color.rgb(
                        145,
                        138,
                        155
                    )
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            },
            LinearLayout.LayoutParams(
                dp(102),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        row.addView(
            TextView(this).apply {
                text =
                    value

                textSize =
                    10f

                setTextColor(
                    Color.rgb(
                        63,
                        57,
                        74
                    )
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        parent.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dp(8)
            }
        )
    }


    private fun createDraftQualityMetric(
        label: String,
        value: String,
        passed: Boolean
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation =
                LinearLayout.VERTICAL

            gravity =
                Gravity.CENTER

            background =
                roundedBackground(
                    if (passed) {
                        SUCCESS_BG
                    } else {
                        SOFT_PURPLE
                    },
                    13
                )

            setPadding(
                dp(7),
                dp(9),
                dp(7),
                dp(9)
            )

            addView(
                TextView(this@CaptureDraftsActivity).apply {
                    text =
                        label

                    textSize =
                        6.5f

                    gravity =
                        Gravity.CENTER

                    letterSpacing =
                        0.04f

                    setTextColor(
                        if (passed) {
                            SUCCESS_TEXT
                        } else {
                            MUTED
                        }
                    )

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }
            )

            addView(
                TextView(this@CaptureDraftsActivity).apply {
                    text =
                        value

                    textSize =
                        9f

                    gravity =
                        Gravity.CENTER

                    setTextColor(
                        if (passed) {
                            SUCCESS_TEXT
                        } else {
                            TEXT
                        }
                    )

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )

                    setPadding(
                        0,
                        dp(3),
                        0,
                        0
                    )
                }
            )

            addView(
                TextView(this@CaptureDraftsActivity).apply {
                    text =
                        if (passed) {
                            "PASS"
                        } else {
                            "CHECK"
                        }

                    textSize =
                        6.5f

                    gravity =
                        Gravity.CENTER

                    setTextColor(
                        if (passed) {
                            SUCCESS_TEXT
                        } else {
                            PURPLE
                        }
                    )

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )

                    setPadding(
                        0,
                        dp(3),
                        0,
                        0
                    )
                }
            )
        }
    }

    // =========================================================
    // STEP 7 SECURE BACKEND SUBMISSION
    // =========================================================

    private fun submitDraft(id: String) {
        if (!authorized()) {
            finish()
            return
        }

        loading = true

        Toast.makeText(
            this,
            "Verifying and submitting securely…",
            Toast.LENGTH_SHORT
        ).show()

        lifecycleScope.launch {
            var jpeg: ByteArray? = null

            try {
                val draft = withContext(Dispatchers.IO) {
                    store.load(id)
                }

                jpeg = draft.jpeg

                val metadata = draft.metadata

                // EncryptedCaptureStore.load() already verifies this hash.
                // Recalculate once more immediately before creating the request.
                val recalculatedHash =
                    EncryptedCaptureStore.sha256(
                        requireNotNull(jpeg)
                    )

                val mobileHash =
                    metadata.getString("mobileSha256")

                require(
                    recalculatedHash.equals(
                        mobileHash,
                        ignoreCase = true
                    )
                ) {
                    "Local SHA-256 changed before submission"
                }

                require(
                    metadata.getLong("agentId") == agentId
                ) {
                    "Draft agent mismatch"
                }

                val imageRequestBody =
                    requireNotNull(jpeg)
                        .toRequestBody(
                            "image/jpeg".toMediaType()
                        )

                val filePart =
                    MultipartBody.Part.createFormData(
                        "file",
                        "capture_$id.jpg",
                        imageRequestBody
                    )

                val response =
                    RetrofitClient.apiService
                        .submitCapturedDocument(
                            file = filePart,
                            customerId = textPart(
                                metadata.getLong("customerId")
                                    .toString()
                            ),
                            documentTypeCode = textPart(
                                metadata.getString("documentTypeCode")
                            ),
                            deviceId = textPart(
                                CURRENT_DEVICE_ID.toString()
                            ),
                            originalHash = textPart(
                                mobileHash
                            ),
                            capturedAt = textPart(
                                metadata.getString("capturedAt")
                            ),
                            imageWidth = textPart(
                                metadata.getInt("imageWidth")
                                    .toString()
                            ),
                            imageHeight = textPart(
                                metadata.getInt("imageHeight")
                                    .toString()
                            ),
                            blurScore = textPart(
                                metadata.getDouble("blurScore")
                                    .toString()
                            ),
                            brightnessScore = textPart(
                                metadata.getDouble("brightnessScore")
                                    .toString()
                            ),
                            blurPassed = textPart(
                                metadata.getBoolean("blurPassed")
                                    .toString()
                            ),
                            brightnessPassed = textPart(
                                metadata.getBoolean("brightnessPassed")
                                    .toString()
                            ),
                            resolutionPassed = textPart(
                                metadata.getBoolean("resolutionPassed")
                                    .toString()
                            ),
                            qualityStatus = textPart(
                                metadata.getString("qualityStatus")
                            ),
                            captureSource = textPart(
                                metadata.getString("captureSource")
                            ),
                            captureLocation = optionalTextPart(
                                metadata,
                                "captureLocation"
                            ),
                            captureLatitude = optionalTextPart(
                                metadata,
                                "captureLatitude"
                            ),
                            captureLongitude = optionalTextPart(
                                metadata,
                                "captureLongitude"
                            )
                        )

                val body = response.body()

                val completelyVerified =
                    response.isSuccessful &&
                            body?.documentId != null &&
                            body.integrityVerified &&
                            body.encrypted &&
                            body.decryptionVerified &&
                            body.stored

                if (completelyVerified) {
                    withContext(Dispatchers.IO) {
                        store.delete(id)
                    }

                    Toast.makeText(
                        this@CaptureDraftsActivity,
                        "Secure submission complete. Document ID: ${body?.documentId}",
                        Toast.LENGTH_LONG
                    ).show()

                    loading = false
                    showList()
                    return@launch
                }

                val errorMessage =
                    body?.message
                        ?.takeIf { it.isNotBlank() }
                        ?: readServerError(
                            response.code(),
                            response.errorBody()?.string()
                        )

                Toast.makeText(
                    this@CaptureDraftsActivity,
                    errorMessage,
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    this@CaptureDraftsActivity,
                    "Secure submission failed. The local draft was kept. ${e.message.orEmpty()}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                jpeg?.fill(0)
                loading = false
            }
        }
    }

    private fun textPart(value: String): RequestBody =
        value.toRequestBody(
            "text/plain".toMediaType()
        )

    private fun optionalTextPart(
        metadata: JSONObject,
        key: String
    ): RequestBody? {
        if (!metadata.has(key) ||
            metadata.isNull(key)) {
            return null
        }

        val value =
            metadata.optString(key).trim()

        if (value.isBlank()) {
            return null
        }

        return textPart(value)
    }

    private fun readServerError(
        statusCode: Int,
        rawBody: String?
    ): String {
        if (!rawBody.isNullOrBlank()) {
            try {
                val json = JSONObject(rawBody)

                val detail =
                    json.optString("detail")
                        .takeIf { it.isNotBlank() }
                        ?: json.optString("message")
                            .takeIf { it.isNotBlank() }

                if (detail != null) {
                    return "Submission failed: $detail"
                }
            } catch (_: Exception) {
                // Fall through to the HTTP status message.
            }
        }

        return when (statusCode) {
            400 -> "Submission rejected. Check the capture metadata and image quality result."
            401 -> "Your session has expired. Sign in again."
            403 -> "Submission blocked. Check that this device is ACTIVE and assigned to your agent account."
            404 -> "A required customer, document type, or endpoint was not found."
            409 -> "Integrity verification failed. The local draft was kept for investigation."
            else -> "Secure submission failed (HTTP $statusCode). The local draft was kept."
        }
    }

    // =========================================================
    // LOCAL DRAFT DELETE
    // =========================================================

    private fun confirmDelete(id: String) {
        AlertDialog.Builder(this)
            .setMessage(
                "Permanently delete this local draft? " +
                        "Only do this if you no longer need to submit it."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (!authorized()) {
                    finish()
                    return@setPositiveButton
                }

                loading = true

                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            store.delete(id)
                        }

                        loading = false
                        showList()

                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        loading = false

                        Toast.makeText(
                            this@CaptureDraftsActivity,
                            "Could not delete draft.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .show()
    }

    override fun onDestroy() {
        if (::content.isInitialized) {
            content.removeAllViews()
        }

        displayed?.recycle()
        displayed = null

        super.onDestroy()
    }
}