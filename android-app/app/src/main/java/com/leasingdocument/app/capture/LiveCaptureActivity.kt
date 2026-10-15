
package com.leasingdocument.app.capture

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.leasingdocument.app.network.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.OffsetDateTime
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.display.DisplayManager
import android.media.ExifInterface
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import java.io.ByteArrayInputStream
import kotlin.math.abs

class LiveCaptureActivity : ComponentActivity() {
    private lateinit var preview: TextureView
    private lateinit var review: ImageView
    private lateinit var status: TextView
    private lateinit var capture: Button
    private lateinit var retake: Button
    private lateinit var save: Button
    private lateinit var permission: Button
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var characteristics: CameraCharacteristics? = null
    private var previewSize: Size? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var opening = false
    private var cameraGeneration = 0
    private var captureTime: String? = null
    private var awaitingImage = false
    private val captureTimeout = Runnable {
        if (awaitingImage) {
            closeCamera()
            status.text = "Capture timed out. Reopening the camera; please retry."
            checkCameraPermissionAndOpen()
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (::preview.isInitialized && preview.display?.displayId == displayId) configureTransform()
        }
    }
    private var jpeg: ByteArray? = null
    private var metadata: JSONObject? = null
    private var bitmap: Bitmap? = null
    private var busy = false
    private var agentId = 0L
    private var customerId = 0L
    private var typeCode = ""
    private var customerName = ""
    private var typeName = ""

    private var captureLocationName: String? = null
    private var captureLatitude: Double? = null
    private var captureLongitude: Double? = null

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            checkCameraPermissionAndOpen()
            requestLocationPermissionIfNeeded()
        } else {
            status.text = "Camera permission is required. Allow it in app settings if permission requests are blocked."
            permission.visibility = View.VISIBLE
        }
    }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                refreshCaptureLocation()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        agentId = AuthSession.userId ?: 0L
        customerId = intent.getLongExtra("customerId", 0L)
        typeCode = intent.getStringExtra("documentTypeCode").orEmpty()
        customerName = intent.getStringExtra("customerName").orEmpty()
        typeName =
            intent.getStringExtra("documentTypeName")
                ?.takeIf { it.isNotBlank() }
                ?: typeCode

        if (!authorized() || customerId <= 0 || typeCode.isBlank()) {
            Toast.makeText(
                this,
                "Sign in and select a customer and document type first.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        // =========================================================
        // MINIMAL FULL-SCREEN CAMERA UI
        // =========================================================

        val root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    Color.BLACK
                )
            }

        preview =
            TextureView(this).apply {

                surfaceTextureListener =
                    object :
                        TextureView.SurfaceTextureListener {

                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            checkCameraPermissionAndOpen()
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            configureTransform()
                        }

                        override fun onSurfaceTextureDestroyed(
                            surface: SurfaceTexture
                        ): Boolean {

                            closeCamera()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(
                            surface: SurfaceTexture
                        ) = Unit
                    }
            }

        root.addView(
            preview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )


        // =====================================================
        // REVIEW IMAGE - SHOWN ONLY AFTER CAPTURE
        // =====================================================

        review =
            ImageView(this).apply {

                scaleType =
                    ImageView.ScaleType.FIT_CENTER

                visibility =
                    View.GONE

                setBackgroundColor(
                    Color.BLACK
                )
            }

        root.addView(
            review,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )


        // =====================================================
        // HIDDEN STATUS HOLDER
        // Existing camera/security logic writes status messages here.
        // It is intentionally not added to the visible layout.
        // =====================================================

        status =
            TextView(this).apply {
                text =
                    "Keep the whole document in view with even lighting."
            }


        // =====================================================
        // CAMERA PERMISSION BUTTON
        // Only appears if Android camera permission is missing.
        // =====================================================

        permission =
            Button(this).apply {

                text =
                    "ALLOW CAMERA"

                visibility =
                    View.GONE

                isAllCaps =
                    false

                textSize =
                    12f

                setTextColor(
                    Color.WHITE
                )

                background =
                    roundedBackground(
                        Color.argb(
                            220,
                            85,
                            60,
                            140
                        ),
                        18
                    )

                setOnClickListener {

                    requestPermission.launch(
                        Manifest.permission.CAMERA
                    )
                }
            }

        root.addView(
            permission,
            FrameLayout.LayoutParams(
                dp(180),
                dp(52),
                Gravity.CENTER
            )
        )


        // =====================================================
        // CAPTURE SHUTTER
        // =====================================================

        capture =
            Button(this).apply {

                text =
                    ""

                isEnabled =
                    false

                background =
                    GradientDrawable().apply {

                        shape =
                            GradientDrawable.OVAL

                        setColor(
                            Color.WHITE
                        )

                        setStroke(
                            dp(5),
                            Color.argb(
                                180,
                                210,
                                210,
                                210
                            )
                        )
                    }

                setOnClickListener {
                    capturePhoto()
                }
            }

        root.addView(
            capture,
            FrameLayout.LayoutParams(
                dp(78),
                dp(78),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {

                bottomMargin =
                    dp(26)
            }
        )


        // =====================================================
        // REVIEW ACTIONS
        // Hidden during live camera.
        // Shown only after a photo is captured.
        // =====================================================

        val reviewActions =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(20),
                    0,
                    dp(20),
                    dp(24)
                )

                visibility =
                    View.VISIBLE
            }


        retake =
            Button(this).apply {

                text =
                    "RETAKE"

                visibility =
                    View.GONE

                isAllCaps =
                    false

                textSize =
                    12f

                setTextColor(
                    Color.WHITE
                )

                background =
                    roundedBackground(
                        Color.argb(
                            190,
                            45,
                            45,
                            50
                        ),
                        16
                    )

                setOnClickListener {

                    clearPhoto()

                    review.visibility =
                        View.GONE

                    preview.visibility =
                        View.VISIBLE

                    save.visibility =
                        View.GONE

                    visibility =
                        View.GONE

                    capture.visibility =
                        View.VISIBLE

                    checkCameraPermissionAndOpen()
                }
            }

        reviewActions.addView(
            retake,
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            ).apply {
                marginEnd =
                    dp(7)
            }
        )


        save =
            Button(this).apply {

                text =
                    "SAVE DRAFT"

                visibility =
                    View.GONE

                isAllCaps =
                    false

                textSize =
                    12f

                setTextColor(
                    Color.WHITE
                )

                background =
                    horizontalGradient(
                        intArrayOf(
                            Color.rgb(110, 80, 184),
                            Color.rgb(208, 78, 147)
                        ),
                        16
                    )

                setOnClickListener {
                    saveDraft()
                }
            }

        reviewActions.addView(
            save,
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            ).apply {
                marginStart =
                    dp(7)
            }
        )


        root.addView(
            reviewActions,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )


        setContentView(
            root
        )


        onBackPressedDispatcher.addCallback(
            this,
            object :
                OnBackPressedCallback(
                    true
                ) {

                override fun handleOnBackPressed() {
                    leave()
                }
            }
        )


        if (
            savedInstanceState != null
        ) {

            status.text =
                "Unsaved capture was cleared. Please capture again."

            Toast.makeText(
                this,
                "Unsaved capture was cleared. Please capture again.",
                Toast.LENGTH_SHORT
            ).show()
        }


        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermission.launch(
                Manifest.permission.CAMERA
            )
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissionIfNeeded()
        }
    }


    // =========================================================
    // UI HELPERS
    // =========================================================

    private fun dp(
        value: Int
    ): Int =
        (
                value *
                        resources.displayMetrics.density
                ).toInt()


    private fun roundedBackground(
        color: Int,
        radiusDp: Int
    ): GradientDrawable =
        GradientDrawable().apply {

            shape =
                GradientDrawable.RECTANGLE

            setColor(
                color
            )

            cornerRadius =
                dp(radiusDp).toFloat()
        }


    private fun horizontalGradient(
        colors: IntArray,
        radiusDp: Int
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            colors
        ).apply {

            cornerRadius =
                dp(radiusDp).toFloat()
        }


    private fun authorized() = agentId > 0 && AuthSession.userId == agentId &&
            AuthSession.role == "AGENT" && !AuthSession.token.isNullOrBlank()

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            refreshCaptureLocation()
            return
        }

        requestLocationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    private fun refreshCaptureLocation() {
        if (!hasLocationPermission() || isFinishing || isDestroyed) return

        lifecycleScope.launch {
            try {
                val location = withContext(Dispatchers.IO) { readBestAvailableLocation() } ?: return@launch
                captureLatitude = location.latitude
                captureLongitude = location.longitude
                captureLocationName =
                    withContext(Dispatchers.IO) { buildLocationLabel(location) }
            } catch (e: SecurityException) {
                android.util.Log.w("LiveCapture", "Location permission unavailable", e)
            } catch (e: Exception) {
                android.util.Log.w("LiveCapture", "Unable to resolve capture location", e)
            }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun readBestAvailableLocation(): Location? {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val fineLocationGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val candidateProviders =
            buildList {
                if (fineLocationGranted) add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
            }

        val enabledProviders =
            candidateProviders.filter { provider ->
                try {
                    manager.isProviderEnabled(provider)
                } catch (_: Exception) {
                    false
                }
            }

        val lastKnown =
            enabledProviders
                .mapNotNull { provider ->
                    try {
                        manager.getLastKnownLocation(provider)
                    } catch (_: Exception) {
                        null
                    }
                }
                .maxByOrNull { it.time }

        if (enabledProviders.isEmpty()) return lastKnown

        val provider =
            if (LocationManager.GPS_PROVIDER in enabledProviders) {
                LocationManager.GPS_PROVIDER
            } else {
                enabledProviders.first()
            }

        val fresh =
            withTimeoutOrNull(5_000L) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val listener =
                        object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                manager.removeUpdates(this)
                                if (continuation.isActive) continuation.resume(location)
                            }
                        }

                    try {
                        manager.requestLocationUpdates(
                            provider,
                            0L,
                            0f,
                            listener,
                            Looper.getMainLooper()
                        )
                        continuation.invokeOnCancellation {
                            manager.removeUpdates(listener)
                        }
                    } catch (e: Exception) {
                        manager.removeUpdates(listener)
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }

        return fresh ?: lastKnown
    }

    @Suppress("DEPRECATION")
    private fun buildLocationLabel(location: Location): String {
        // First try Android's built-in Geocoder.
        val androidLabel =
            try {
                if (Geocoder.isPresent()) {
                    val address =
                        Geocoder(this, Locale.getDefault())
                            .getFromLocation(
                                location.latitude,
                                location.longitude,
                                1
                            )
                            ?.firstOrNull()

                    if (address != null) {
                        val fullAddress =
                            address
                                .getAddressLine(0)
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }

                        fullAddress ?: listOfNotNull(
                            address.featureName,
                            address.thoroughfare,
                            address.subThoroughfare,
                            address.subLocality,
                            address.locality,
                            address.subAdminArea,
                            address.adminArea,
                            address.postalCode,
                            address.countryName
                        )
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(", ")
                            .takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    "LiveCapture",
                    "Android Geocoder could not resolve the location",
                    e
                )
                null
            }

        if (!androidLabel.isNullOrBlank()) {
            return androidLabel
        }

        // Some emulators/devices return no result from Android Geocoder.
        // Fall back to OpenStreetMap Nominatim reverse geocoding.
        val osmLabel = reverseGeocodeWithOpenStreetMap(location)

        if (!osmLabel.isNullOrBlank()) {
            return osmLabel
        }

        return "Unknown location"
    }

    private fun reverseGeocodeWithOpenStreetMap(location: Location): String? {
        var connection: HttpURLConnection? = null

        return try {
            val url =
                URL(
                    "https://nominatim.openstreetmap.org/reverse" +
                            "?format=jsonv2" +
                            "&lat=${location.latitude}" +
                            "&lon=${location.longitude}" +
                            "&zoom=18" +
                            "&addressdetails=1"
                )

            connection =
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    setRequestProperty(
                        "User-Agent",
                        "LeasingDocumentManagementSystem/1.0 (University Student Project)"
                    )
                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )
                    setRequestProperty(
                        "Accept-Language",
                        Locale.getDefault().toLanguageTag()
                    )
                }

            if (connection.responseCode !in 200..299) {
                android.util.Log.w(
                    "LiveCapture",
                    "OpenStreetMap reverse geocoding failed with HTTP ${connection.responseCode}"
                )
                return null
            }

            val response =
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

            val json = JSONObject(response)
            val address = json.optJSONObject("address")

            val readableParts =
                listOfNotNull(
                    address?.optString("house_number")?.takeIf { it.isNotBlank() },
                    address?.optString("road")?.takeIf { it.isNotBlank() },
                    address?.optString("neighbourhood")?.takeIf { it.isNotBlank() },
                    address?.optString("suburb")?.takeIf { it.isNotBlank() },
                    address?.optString("village")?.takeIf { it.isNotBlank() },
                    address?.optString("town")?.takeIf { it.isNotBlank() },
                    address?.optString("city")?.takeIf { it.isNotBlank() },
                    address?.optString("county")?.takeIf { it.isNotBlank() },
                    address?.optString("state")?.takeIf { it.isNotBlank() },
                    address?.optString("postcode")?.takeIf { it.isNotBlank() },
                    address?.optString("country")?.takeIf { it.isNotBlank() }
                )
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

            if (readableParts.isNotEmpty()) {
                readableParts.joinToString(", ")
            } else {
                json.optString("display_name")
                    .trim()
                    .takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            android.util.Log.w(
                "LiveCapture",
                "OpenStreetMap reverse geocoding could not resolve the location",
                e
            )
            null
        } finally {
            connection?.disconnect()
        }
    }

    // The same direct Camera2 entry points used by the original CaptureActivity.
    private fun checkCameraPermissionAndOpen() {
        if (!resumed || !::preview.isInitialized || !preview.isAvailable ||
            jpeg != null || busy || opening || cameraDevice != null || isFinishing || isDestroyed) return
        if (!authorized()) { finish(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permission.visibility = View.VISIBLE
            capture.isEnabled = false
            return
        }
        openCamera()
    }

    private fun openCamera() {
        val texture = preview.surfaceTexture ?: return
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val generation = ++cameraGeneration
        opening = true
        capture.isEnabled = false
        permission.visibility = View.GONE
        try {
            // Original firstOrNull() could choose the selfie camera.
            val cameraId = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it)[CameraCharacteristics.LENS_FACING] ==
                        CameraCharacteristics.LENS_FACING_BACK
            } ?: error("No rear camera available")
            val info = manager.getCameraCharacteristics(cameraId)
            characteristics = info
            val map = requireNotNull(info[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP])
            val jpegSizes = requireNotNull(map.getOutputSizes(ImageFormat.JPEG)).filter {
                it.width.toLong() * it.height <= 8_000_000L
            }
            require(jpegSizes.isNotEmpty()) { "No supported bounded JPEG size" }
            val suitable = jpegSizes.filter {
                maxOf(it.width, it.height) >= CaptureQuality.MIN_LONG_EDGE &&
                        minOf(it.width, it.height) >= CaptureQuality.MIN_SHORT_EDGE
            }
            val jpegSize = (suitable.ifEmpty { jpegSizes }).minByOrNull {
                abs(it.width.toLong() * it.height - 1920L * 1080L)
            }!!
            val sizes = requireNotNull(map.getOutputSizes(SurfaceTexture::class.java)).toList()
            val bounded = sizes.filter { maxOf(it.width, it.height) <= 1920 && minOf(it.width, it.height) <= 1080 }
            require(bounded.isNotEmpty()) { "No supported preview size" }
            val aspect = jpegSize.width.toDouble() / jpegSize.height
            val chosen = bounded.sortedWith(compareBy<Size> {
                abs(it.width.toDouble() / it.height - aspect)
            }.thenByDescending { it.width.toLong() * it.height }).first()
            previewSize = chosen
            texture.setDefaultBufferSize(chosen.width, chosen.height)
            configureTransform()
            previewSurface = Surface(texture)
            imageReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).also { reader ->
                reader.setOnImageAvailableListener({ ready ->
                    receiveCapturedImage(ready, generation)
                }, mainHandler)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                closeCamera(); permission.visibility = View.VISIBLE; return
            }
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (generation != cameraGeneration || !resumed || isDestroyed || isFinishing) {
                        camera.close(); return
                    }
                    opening = false
                    cameraDevice = camera
                    startCameraPreview(generation)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (generation == cameraGeneration) cameraError("Camera disconnected. Go back and reopen capture.")
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (generation == cameraGeneration) cameraError("Camera error ($error). Go back and reopen capture.")
                }
            }, mainHandler)
        } catch (e: Exception) {
            android.util.Log.e("LiveCapture", "Cannot open Camera2 device", e)
            cameraError("Cannot open the rear camera. Check permission and close other camera apps, then reopen capture.")
        }
    }

    @Suppress("DEPRECATION")
    private fun startCameraPreview(generation: Int) {
        val camera = cameraDevice ?: return
        val surface = previewSurface ?: return
        val reader = imageReader ?: return
        try {
            camera.createCaptureSession(listOf(surface, reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (generation != cameraGeneration || cameraDevice !== camera || !resumed || jpeg != null) {
                        session.close(); return
                    }
                    captureSession = session
                    try {
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            configureAutomaticControls(this)
                        }
                        session.setRepeatingRequest(request.build(), null, mainHandler)
                        // Devices without continuous-picture AF still get a focus trigger.
                        if (request.get(CaptureRequest.CONTROL_AF_MODE) == CaptureRequest.CONTROL_AF_MODE_AUTO) {
                            request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                            session.capture(request.build(), null, mainHandler)
                            request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                        }
                        capture.isEnabled = !busy
                        status.text = "Keep the entire document in view. Hold steady while the camera focuses."
                    } catch (e: Exception) {
                        android.util.Log.e("LiveCapture", "Preview request failed", e)
                        cameraError("Cannot start preview. Go back and reopen capture.")
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    session.close()
                    if (generation == cameraGeneration) cameraError("Camera rejected the preview configuration. Send the phone model and Logcat error.")
                }
            }, mainHandler)
        } catch (e: Exception) {
            android.util.Log.e("LiveCapture", "Camera2 session creation failed", e)
            cameraError("Cannot configure the camera. Go back and reopen capture.")
        }
    }

    private fun configureAutomaticControls(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        val afModes = characteristics?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        val mode = when {
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) -> CaptureRequest.CONTROL_AF_MODE_AUTO
            else -> CaptureRequest.CONTROL_AF_MODE_OFF
        }
        builder.set(CaptureRequest.CONTROL_AF_MODE, mode)
        val aeModes = characteristics?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        if (aeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON)) builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        val awbModes = characteristics?.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
        if (awbModes.contains(CaptureRequest.CONTROL_AWB_MODE_AUTO)) builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
    }

    private fun displayDegrees(): Int = when (preview.display?.rotation ?: Surface.ROTATION_0) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    private fun configureTransform() {
        val size = previewSize ?: return
        if (!::preview.isInitialized || preview.width == 0 || preview.height == 0) return
        val width = preview.width.toFloat()
        val height = preview.height.toFloat()
        val sensor = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        // TextureView already applies the camera sensor's native orientation.
        // Undo its nonuniform stretch, compensate for display rotation, then fit.
        val naturalWidth = if (sensor % 180 == 90) size.height.toFloat() else size.width.toFloat()
        val naturalHeight = if (sensor % 180 == 90) size.width.toFloat() else size.height.toFloat()
        val degrees = displayDegrees()
        val rotatedWidth = if (degrees % 180 == 90) naturalHeight else naturalWidth
        val rotatedHeight = if (degrees % 180 == 90) naturalWidth else naturalHeight
        val fit = minOf(width / rotatedWidth, height / rotatedHeight)
        val transform = Matrix().apply {
            setScale(naturalWidth / width, naturalHeight / height, width / 2f, height / 2f)
            postRotate(-degrees.toFloat(), width / 2f, height / 2f)
            postScale(fit, fit, width / 2f, height / 2f)
        }
        preview.setTransform(transform)
    }

    private fun capturePhoto() {
        if (busy || jpeg != null || !resumed) return
        if (!authorized()) { finish(); return }

        // Refresh the location again at the moment the document is captured.
        if (hasLocationPermission()) {
            refreshCaptureLocation()
        }

        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return
        val generation = cameraGeneration
        busy = true
        awaitingImage = true
        capture.isEnabled = false
        captureTime = OffsetDateTime.now().toString()
        status.text = "Capturing and checking image quality…"
        mainHandler.postDelayed(captureTimeout, 10_000L)
        try {
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                configureAutomaticControls(this)
                val sensor = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                // Rear camera, using Display rotation (not OrientationEventListener angles).
                set(CaptureRequest.JPEG_ORIENTATION, (sensor - displayDegrees() + 360) % 360)
            }
            session.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    if (generation == cameraGeneration && awaitingImage) {
                        cameraError("Capture failed. Go back and reopen capture.")
                    }
                }
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    // ImageReader owns delivery. Keep timeout active until the JPEG arrives.
                }
            }, mainHandler)
        } catch (e: Exception) {
            android.util.Log.e("LiveCapture", "Camera2 still capture failed", e)
            cameraError("Capture failed. Go back and reopen capture.")
        }
    }

    private fun receiveCapturedImage(reader: ImageReader, generation: Int) {
        var bytes: ByteArray? = null
        val timestamp = captureTime
        try {
            val image = reader.acquireLatestImage() ?: return
            try {
                if (generation != cameraGeneration || reader !== imageReader || !awaitingImage || !resumed || timestamp == null) return
                require(image.format == ImageFormat.JPEG)
                val buffer = image.planes[0].buffer
                require(buffer.remaining() in 1..EncryptedCaptureStore.MAX_IMAGE_BYTES)
                bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            } finally { image.close() }
        } catch (e: Exception) {
            if (generation == cameraGeneration && awaitingImage) {
                android.util.Log.e("LiveCapture", "JPEG delivery failed", e)
                cameraError("Unable to read captured image. Go back and reopen capture.")
            }
            return
        }
        val captured = bytes ?: return
        mainHandler.removeCallbacks(captureTimeout)
        awaitingImage = false
        captureTime = null
        // Release hardware while OpenCV processes the in-memory image.
        closeCamera()
        processCapturedPhoto(captured, requireNotNull(timestamp))
    }

    private fun cameraError(message: String) {
        closeCamera()
        if (::status.isInitialized && !isDestroyed) status.text = message
    }

    private fun closeCamera() {
        ++cameraGeneration // Reject callbacks belonging to a closed/replaced camera.
        opening = false
        mainHandler.removeCallbacks(captureTimeout)
        if (awaitingImage) busy = false
        awaitingImage = false; captureTime = null
        if (::capture.isInitialized) capture.isEnabled = false
        try { captureSession?.close() } catch (_: Exception) { }
        captureSession = null
        try { cameraDevice?.close() } catch (_: Exception) { }
        cameraDevice = null
        try { imageReader?.close() } catch (_: Exception) { }
        imageReader = null
        previewSurface?.release(); previewSurface = null
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!::preview.isInitialized) return
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(displayListener, mainHandler)
        checkCameraPermissionAndOpen()
        if (hasLocationPermission()) refreshCaptureLocation()
    }

    override fun onPause() {
        resumed = false
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        closeCamera()
        super.onPause()
    }

    private fun processCapturedPhoto(captured: ByteArray, capturedAt: String) {
        lifecycleScope.launch {
            var displayed: Bitmap? = null
            try {
                val result = withContext(Dispatchers.Default) {
                    // Platform EXIF handles devices that encode orientation as a tag,
                    // as well as devices that rotate JPEG pixels themselves.
                    val exifOrientation = ByteArrayInputStream(captured).use {
                        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    }
                    val rotation = when (exifOrientation) {
                        ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> 0
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> error("Unexpected mirrored rear-camera JPEG")
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(captured, 0, captured.size, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
                            bounds.outWidth.toLong() * bounds.outHeight <= 12_000_000L)
                    val decoded = requireNotNull(BitmapFactory.decodeByteArray(captured, 0, captured.size))
                    try {
                        val quality = CaptureQuality.check(decoded)
                        val meta = JSONObject()
                            .put("agentId", agentId).put("customerId", customerId)
                            .put("customerName", customerName).put("documentTypeCode", typeCode)
                            .put("documentTypeName", typeName).put("captureSource", "LIVE_CAMERA")
                            .apply {
                                captureLocationName?.takeIf { it.isNotBlank() }?.let {
                                    put("captureLocation", it)
                                }
                                captureLatitude?.let {
                                    put("captureLatitude", it.toString())
                                }
                                captureLongitude?.let {
                                    put("captureLongitude", it.toString())
                                }
                            }
                            .put("capturedAtOffset", capturedAt)
                            .put("capturedAt", OffsetDateTime.parse(capturedAt).toLocalDateTime().toString())
                            .put("imageWidth", decoded.width).put("imageHeight", decoded.height)
                            .put("rotationDegrees", rotation)
                            .put("blurScore", quality.blurScore).put("brightnessScore", quality.brightnessScore)
                            .put("blurPassed", quality.blurPassed).put("brightnessPassed", quality.brightnessPassed)
                            .put("resolutionPassed", quality.resolutionPassed)
                            .put("qualityStatus", if (quality.passed) "PASSED" else "FAILED")
                            .put("mobileSha256", EncryptedCaptureStore.sha256(captured))
                            .put("localStatus", "DRAFT_NOT_SUBMITTED").put("schemaVersion", 1)
                        displayed = if (rotation == 0) requireNotNull(decoded.copy(Bitmap.Config.ARGB_8888, false))
                        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                            Matrix().apply { postRotate(rotation.toFloat()) }, true)
                        quality to meta
                    } finally { decoded.recycle() }
                }
                if (!authorized()) { finish(); return@launch }
                bitmap = displayed; displayed = null
                review.setImageBitmap(bitmap)
                metadata = result.second
                jpeg = captured
                closeCamera()
                preview.visibility = View.GONE; review.visibility = View.VISIBLE
                capture.visibility = View.GONE; retake.visibility = View.VISIBLE
                save.visibility = View.VISIBLE; save.isEnabled = result.first.passed
                status.text =
                    if (result.first.passed) {
                        "Quality checks passed. Review the entire document before saving. This will save locally only."
                    } else {
                        "Please retake. ${result.first.feedback()}"
                    }

                Toast.makeText(
                    this@LiveCaptureActivity,
                    status.text,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: CancellationException) { throw e
            } catch (e: LinkageError) {
                android.util.Log.e("LiveCapture", "OpenCV initialization failed", e)
                status.text = "OpenCV could not load on this device. Capture is blocked; send the Android Logcat error."
            } catch (e: Exception) {
                android.util.Log.e("LiveCapture", "Image processing failed", e)
                status.text = "Image processing failed. Please retry. If it repeats, send the Android Logcat error."
            } finally {
                displayed?.recycle()
                if (jpeg !== captured) captured.fill(0)
                busy = false
                if (!isDestroyed && jpeg == null) {
                    capture.isEnabled = resumed && captureSession != null
                    checkCameraPermissionAndOpen()
                }
            }
        }
    }


    private fun saveDraft() {
        if (busy || !authorized()) return
        val bytes = jpeg?.copyOf() ?: return
        val info = metadata ?: run { bytes.fill(0); return }
        if (info.optString("qualityStatus") != "PASSED") { bytes.fill(0); return }
        busy = true; save.isEnabled = false; retake.isEnabled = false
        status.text = "Encrypting and verifying local draft…"
        lifecycleScope.launch {
            try {
                // Finish atomic storage even if Android recreates this activity mid-save.
                val id = withContext(Dispatchers.IO + NonCancellable) {
                    EncryptedCaptureStore(applicationContext, agentId).save(bytes, info)
                }
                clearPhoto()
                setResult(RESULT_OK, Intent().putExtra("draftId", id))
                Toast.makeText(this@LiveCaptureActivity, "Encrypted draft saved on this phone. Not submitted.", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                status.text = "Could not save the draft. Check available phone storage and try again."

                Toast.makeText(
                    this@LiveCaptureActivity,
                    status.text,
                    Toast.LENGTH_LONG
                ).show()

                save.isEnabled = true
                retake.isEnabled = true
            } finally { bytes.fill(0); busy = false }
        }
    }

    private fun leave() {
        if (busy) { Toast.makeText(this, "Please wait for processing to finish.", Toast.LENGTH_SHORT).show(); return }
        if (jpeg == null) finish() else AlertDialog.Builder(this)
            .setMessage("Discard this unsaved capture?")
            .setPositiveButton("Discard") { _, _ -> clearPhoto(); finish() }
            .setNegativeButton("Keep reviewing", null).show()
    }

    private fun clearPhoto() {
        if (::review.isInitialized) review.setImageDrawable(null)
        bitmap?.recycle(); bitmap = null
        jpeg?.fill(0); jpeg = null; metadata = null
    }

    override fun onDestroy() {
        closeCamera()
        // A save operation owns a separate byte copy until encryption completes.
        clearPhoto()
        super.onDestroy()
    }
}
