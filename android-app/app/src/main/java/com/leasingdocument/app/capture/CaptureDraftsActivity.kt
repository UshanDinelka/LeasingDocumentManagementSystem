package com.leasingdocument.app.capture

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
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
 * Shows encrypted Step 6 drafts belonging to the currently signed-in agent.
 *
 * Step 7 adds final backend submission without changing the Camera2 capture path:
 * 1. EncryptedCaptureStore decrypts and authenticates the local .ldc draft.
 * 2. The local SHA-256 is rechecked by EncryptedCaptureStore.load().
 * 3. JPEG bytes + the original mobile hash + capture metadata are sent to Spring.
 * 4. Spring performs its own SHA-256, AES-256-GCM encryption, decrypt verification,
 *    encrypted-at-rest storage, database persistence and audit logging.
 * 5. The local draft is deleted only after the backend confirms every secure step.
 */
class CaptureDraftsActivity : ComponentActivity() {

    companion object {
        /**
         * This must match the deviceId used by the current login workflow.
         * MainActivity currently logs in with deviceId = 1.
         * When device registration becomes dynamic, replace this with the
         * authenticated device ID stored in the application session.
         */
        private const val CURRENT_DEVICE_ID = 1L
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

        val scroll = ScrollView(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(content)

        val pad =
            (16 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                pad + bars.left,
                pad + bars.top,
                pad + bars.right,
                pad + bars.bottom
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

    private fun reset() {
        content.removeAllViews()
        displayed?.recycle()
        displayed = null
    }

    private fun label(
        text: String,
        size: Float = 16f
    ) {
        content.addView(
            TextView(this).apply {
                this.text = text
                textSize = size
                setPadding(0, 8, 0, 8)
            }
        )
    }

    private fun button(
        text: String,
        action: () -> Unit
    ) {
        content.addView(
            Button(this).apply {
                this.text = text
                setOnClickListener {
                    if (!loading) {
                        action()
                    }
                }
            }
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

        label("Local Capture Drafts", 24f)
        label(
            "Encrypted on this phone. Drafts remain here until secure backend submission succeeds."
        )

        lifecycleScope.launch {
            try {
                val ids = withContext(Dispatchers.IO) {
                    store.ids()
                }

                if (page * 20 >= ids.size) {
                    page = 0
                }

                if (ids.isEmpty()) {
                    label("No saved drafts for this agent.")
                }

                for (id in ids.drop(page * 20).take(20)) {
                    val summary = try {
                        withContext(Dispatchers.IO) {
                            val draft = store.load(id)

                            try {
                                "${draft.metadata.optString("documentTypeName")} — " +
                                    "${draft.metadata.optString("customerName")}\n" +
                                    draft.metadata.optString("capturedAtOffset")
                            } finally {
                                draft.jpeg.fill(0)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "Unreadable draft — $id"
                    }

                    button(summary) {
                        showDraft(id)
                    }
                }

                if (page > 0) {
                    button("Previous page") {
                        page--
                        showList()
                    }
                }

                if ((page + 1) * 20 < ids.size) {
                    button("Next page") {
                        page++
                        showList()
                    }
                }

                button("Refresh") {
                    showList()
                }

                button("Back") {
                    finish()
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                label("Cannot read local draft storage.")

                button("Retry") {
                    showList()
                }

                button("Back") {
                    finish()
                }
            } finally {
                loading = false
            }
        }
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

        label("Local encrypted draft", 24f)

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

                content.addView(
                    ImageView(this@CaptureDraftsActivity).apply {
                        setImageBitmap(displayed)
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    },
                    LinearLayout.LayoutParams(
                        -1,
                        (360 * resources.displayMetrics.density).toInt()
                    )
                )

                showMetadata(meta)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                label(
                    "This draft could not be decrypted or failed its local integrity check. It cannot be submitted."
                )
            } finally {
                readyBitmap?.recycle()
                loading = false
            }

            button("Submit securely to backend") {
                submitDraft(id)
            }

            button("Delete local draft") {
                confirmDelete(id)
            }

            button("Back to drafts") {
                showList()
            }
        }
    }

    private fun showMetadata(meta: JSONObject) {
        label(
            "Customer: ${meta.optString("customerName")}\n" +
                "Document: ${meta.optString("documentTypeName")}"
        )

        label(
            "Captured: ${meta.optString("capturedAtOffset")}\n" +
                "Resolution: ${meta.optInt("imageWidth")} × " +
                "${meta.optInt("imageHeight")}"
        )

        label(
            "Blur score: ${meta.optDouble("blurScore")}\n" +
                "Brightness: ${meta.optDouble("brightnessScore")}"
        )

        label(
            "Local AES-GCM authentication and SHA-256 verification passed. " +
                "Backend verification occurs when you submit."
        )

        label(
            "SHA-256: ${meta.optString("mobileSha256")}",
            12f
        )
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
