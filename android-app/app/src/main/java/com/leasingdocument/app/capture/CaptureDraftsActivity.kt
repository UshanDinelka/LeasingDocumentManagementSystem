package com.leasingdocument.app.capture

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
    private var page = 0

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

        val scroll = ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.rgb(255, 252, 250),
                    Color.rgb(255, 247, 251),
                    Color.rgb(248, 242, 255)
                )
            )
            isFillViewport = true
            clipToPadding = false
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(24)
                rightMargin = dp(24)
            }
        )

        val bottomPad = dp(28)

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                bars.left,
                dp(8) + bars.top,
                bars.right,
                bottomPad + bars.bottom
            )

            insets
        }

        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (loading) return

                    if (selectedId != null) {
                        showList()
                    } else {
                        finish()
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
                textSize = 25f
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

        val badge = TextView(this).apply {
            text = "DOC SECURE"
            textSize = 8f
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            setTextColor(PURPLE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBackground(Color.WHITE, 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        row.addView(badge)

        content.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(22)
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
    // DRAFT LIST
    // =========================================================

    private fun showList() {
        if (!authorized()) {
            finish()
            return
        }

        selectedId = null
        reset()
        loading = true

        addHeader(
            title = "Local Capture Drafts",
            subtitle = "Encrypted drafts stored securely on this device",
            showBack = false
        )

        lifecycleScope.launch {
            try {
                val ids = withContext(Dispatchers.IO) {
                    store.ids()
                }

                if (page * 20 >= ids.size) {
                    page = 0
                }

                addHeroCard(
                    countText = "${ids.size} Secure Draft${if (ids.size == 1) "" else "s"}",
                    subtitle = "Drafts remain on this device until secure backend submission succeeds."
                )

                if (ids.isEmpty()) {
                    addStatusCard(
                        title = "No saved drafts",
                        message = "New encrypted captures will appear here after you save them from the secure camera."
                    )
                }

                for (id in ids.drop(page * 20).take(20)) {
                    val summary = try {
                        withContext(Dispatchers.IO) {
                            val draft = store.load(id)

                            try {
                                Triple(
                                    draft.metadata
                                        .optString("documentTypeName")
                                        .ifBlank { "Secure Document" },
                                    draft.metadata
                                        .optString("customerName")
                                        .ifBlank { "Unknown customer" },
                                    draft.metadata
                                        .optString("capturedAtOffset")
                                        .ifBlank { "Capture time unavailable" }
                                )
                            } finally {
                                draft.jpeg.fill(0)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }

                    addDraftCard(
                        id = id,
                        documentName = summary?.first ?: "Unreadable draft",
                        customerName = summary?.second ?: "Local integrity check required",
                        capturedAt = summary?.third ?: id
                    )
                }

                if (page > 0) {
                    actionButton(
                        text = "PREVIOUS PAGE",
                        background = roundedBackground(SOFT_PURPLE, 16),
                        textColor = PURPLE
                    ) {
                        page--
                        showList()
                    }
                }

                if ((page + 1) * 20 < ids.size) {
                    actionButton(
                        text = "NEXT PAGE",
                        background = roundedBackground(SOFT_PURPLE, 16),
                        textColor = PURPLE
                    ) {
                        page++
                        showList()
                    }
                }

                addSpace(18)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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

                actionButton(
                    text = "BACK TO DASHBOARD",
                    background = roundedBackground(SOFT_PURPLE, 16),
                    textColor = PURPLE
                ) {
                    finish()
                }

            } finally {
                loading = false
            }
        }
    }

    private fun addDraftCard(
        id: String,
        documentName: String,
        customerName: String,
        capturedAt: String
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 22)
            setPadding(
                dp(17),
                dp(16),
                dp(17),
                dp(16)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (!loading) {
                    showDraft(id)
                }
            }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = TextView(this).apply {
            text = "DR"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = gradientBackground(
                intArrayOf(
                    Color.rgb(255, 130, 71),
                    Color.rgb(240, 82, 145),
                    Color.rgb(154, 78, 209)
                ),
                15
            )
        }

        top.addView(
            icon,
            LinearLayout.LayoutParams(
                dp(48),
                dp(48)
            )
        )

        val middle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, dp(8), 0)
        }

        middle.addView(
            TextView(this).apply {
                text = documentName
                textSize = 15f
                setTextColor(TEXT)
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        middle.addView(
            TextView(this).apply {
                text = customerName
                textSize = 11.5f
                setTextColor(MUTED)
                setPadding(0, dp(4), 0, 0)
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
                text = "›"
                textSize = 27f
                setTextColor(PURPLE)
            }
        )

        card.addView(top)

        card.addView(
            TextView(this).apply {
                text = capturedAt
                textSize = 10.5f
                setTextColor(MUTED)
                setPadding(0, dp(13), 0, 0)
            }
        )

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

    // =========================================================
    // DRAFT DETAIL
    // =========================================================

    private fun showDraft(id: String) {
        if (!authorized()) {
            finish()
            return
        }

        reset()
        selectedId = id
        loading = true

        addHeader(
            title = "Encrypted Draft",
            subtitle = "Review security details before submission",
            showBack = true
        )

        lifecycleScope.launch {
            var readyBitmap: Bitmap? = null

            try {
                val meta = withContext(Dispatchers.IO) {
                    val draft = store.load(id)

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

                        val decoded = requireNotNull(
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
                            readyBitmap = Bitmap.createBitmap(
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

                val imageContainer = LinearLayout(this@CaptureDraftsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundedBackground(Color.WHITE, 22)
                    setPadding(
                        dp(10),
                        dp(10),
                        dp(10),
                        dp(10)
                    )
                }

                imageContainer.addView(
                    ImageView(this@CaptureDraftsActivity).apply {
                        setImageBitmap(displayed)
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        background = roundedBackground(
                            Color.rgb(248, 245, 250),
                            16
                        )
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(360)
                    )
                )

                content.addView(
                    imageContainer,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                addSpace(16)

                showMetadata(meta)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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

            actionButton(
                text = "SUBMIT SECURELY TO BACKEND",
                background = gradientBackground(
                    intArrayOf(
                        Color.rgb(110, 80, 184),
                        Color.rgb(150, 80, 195),
                        PINK
                    ),
                    17
                ),
                textColor = Color.WHITE
            ) {
                submitDraft(id)
            }

            actionButton(
                text = "DELETE LOCAL DRAFT",
                background = roundedBackground(SOFT_PINK, 17),
                textColor = Color.rgb(174, 55, 77)
            ) {
                confirmDelete(id)
            }

            actionButton(
                text = "BACK TO DRAFTS",
                background = roundedBackground(SOFT_PURPLE, 17),
                textColor = PURPLE
            ) {
                showList()
            }
        }
    }

    private fun showMetadata(meta: JSONObject) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 22)
            setPadding(
                dp(18),
                dp(17),
                dp(18),
                dp(17)
            )
        }

        card.addView(
            TextView(this).apply {
                text = "Capture Details"
                textSize = 16f
                setTextColor(TEXT)
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        addMetaRow(
            card,
            "Customer",
            meta.optString("customerName").ifBlank { "-" }
        )

        addMetaRow(
            card,
            "Document",
            meta.optString("documentTypeName").ifBlank { "-" }
        )

        addMetaRow(
            card,
            "Captured",
            meta.optString("capturedAtOffset").ifBlank { "-" }
        )

        addMetaRow(
            card,
            "Resolution",
            "${meta.optInt("imageWidth")} × ${meta.optInt("imageHeight")}"
        )

        addMetaRow(
            card,
            "Blur score",
            meta.optDouble("blurScore").toString()
        )

        addMetaRow(
            card,
            "Brightness",
            meta.optDouble("brightnessScore").toString()
        )

        val securityBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(SUCCESS_BG, 15)
            setPadding(
                dp(13),
                dp(12),
                dp(13),
                dp(12)
            )
        }

        securityBox.addView(
            TextView(this).apply {
                text = "Local integrity verified"
                textSize = 12.5f
                setTextColor(SUCCESS_TEXT)
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        securityBox.addView(
            TextView(this).apply {
                text = "AES-GCM authentication and SHA-256 verification passed. Backend verification runs again when you submit."
                textSize = 10.5f
                setTextColor(Color.rgb(80, 113, 93))
                setPadding(0, dp(5), 0, 0)
            }
        )

        card.addView(
            securityBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        )

        val hash = meta.optString("mobileSha256").ifBlank { "-" }

        val hashBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(SOFT_PURPLE, 15)
            setPadding(
                dp(13),
                dp(12),
                dp(13),
                dp(12)
            )
        }

        hashBox.addView(
            TextView(this).apply {
                text = "SHA-256"
                textSize = 10f
                setTextColor(MUTED)
            }
        )

        hashBox.addView(
            TextView(this).apply {
                text = hash
                textSize = 9.5f
                setTextColor(Color.rgb(96, 76, 120))
                setPadding(0, dp(5), 0, 0)
            }
        )

        card.addView(
            hashBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
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

    private fun addMetaRow(
        parent: LinearLayout,
        label: String,
        value: String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(11), 0, 0)
        }

        row.addView(
            TextView(this).apply {
                text = label
                textSize = 10.5f
                setTextColor(MUTED)
            },
            LinearLayout.LayoutParams(
                dp(94),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        row.addView(
            TextView(this).apply {
                text = value
                textSize = 11.5f
                setTextColor(TEXT)
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        parent.addView(row)
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