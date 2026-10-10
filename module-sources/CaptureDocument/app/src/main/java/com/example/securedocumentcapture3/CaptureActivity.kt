package com.example.securedocumentcapture3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class CaptureActivity : AppCompatActivity() {

    private val cameraPermissionCode = 100

    private lateinit var cameraManager: CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private lateinit var cameraPreview: TextureView
    private lateinit var imageReader: ImageReader

    private lateinit var photoReview: LinearLayout
    private lateinit var cameraControls: LinearLayout
    private lateinit var storageSuccess: LinearLayout

    private lateinit var capturedImage: ImageView
    private lateinit var storedDocumentType: TextView
    private lateinit var storageMessage: TextView

    // Captured image stays in memory
    // until user presses "Use Photo".
    private var capturedImageBytes: ByteArray? = null


    // =====================================================
    // ON CREATE
    // =====================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_capture)


        // =====================================================
        // INITIALIZE OPENCV
        // =====================================================

        val openCVReady =
            OpenCVInitializer.initialize(this)

        if (!openCVReady) {

            Toast.makeText(
                this,
                "OpenCV initialization failed",
                Toast.LENGTH_LONG
            ).show()
        }


        // =====================================================
        // FIND VIEWS
        // =====================================================

        cameraPreview =
            findViewById(R.id.cameraPreview)

        val captureButton =
            findViewById<Button>(R.id.captureButton)

        val backButton =
            findViewById<Button>(R.id.backButton)

        val retakeButton =
            findViewById<Button>(R.id.retakeButton)

        val usePhotoButton =
            findViewById<Button>(R.id.usePhotoButton)

        val addAnotherButton =
            findViewById<Button>(R.id.addAnotherButton)

        val viewDocumentsButton =
            findViewById<Button>(R.id.viewDocumentsButton)

        val exitButton =
            findViewById<Button>(R.id.exitButton)

        photoReview =
            findViewById(R.id.photoReview)

        cameraControls =
            findViewById(R.id.cameraControls)

        storageSuccess =
            findViewById(R.id.storageSuccess)

        capturedImage =
            findViewById(R.id.capturedImage)

        storedDocumentType =
            findViewById(R.id.storedDocumentType)

        storageMessage =
            findViewById(R.id.storageMessage)


        // =====================================================
        // CAPTURE BUTTON
        // =====================================================

        captureButton.setOnClickListener {

            capturePhoto()
        }


        // =====================================================
        // BACK BUTTON
        // =====================================================

        backButton.setOnClickListener {

            finish()
        }


        // =====================================================
        // RETAKE BUTTON
        // =====================================================

        retakeButton.setOnClickListener {

            capturedImageBytes = null

            capturedImage.setImageBitmap(null)

            photoReview.visibility =
                View.GONE

            cameraControls.visibility =
                View.VISIBLE

            cameraPreview.visibility =
                View.VISIBLE

            startCameraPreview()
        }


        // =====================================================
        // USE PHOTO BUTTON
        // =====================================================

        usePhotoButton.setOnClickListener {

            saveDocumentSecurely()
        }


        // =====================================================
        // ADD ANOTHER BUTTON
        // =====================================================

        addAnotherButton.setOnClickListener {

            val intent =
                Intent(
                    this,
                    DocumentSelectionActivity::class.java
                )

            startActivity(intent)

            finish()
        }


        // =====================================================
        // VIEW DOCUMENTS BUTTON
        // =====================================================

        viewDocumentsButton.setOnClickListener {

            val intent =
                Intent(
                    this,
                    SecureDocumentsActivity::class.java
                )

            startActivity(intent)
        }


        // =====================================================
        // EXIT BUTTON
        // =====================================================

        exitButton.setOnClickListener {

            finishAffinity()
        }


        // =====================================================
        // CAMERA PREVIEW LISTENER
        // =====================================================

        cameraPreview.surfaceTextureListener =
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
                }


                override fun onSurfaceTextureDestroyed(
                    surface: SurfaceTexture
                ): Boolean {

                    return true
                }


                override fun onSurfaceTextureUpdated(
                    surface: SurfaceTexture
                ) {
                }
            }
    }


    // =====================================================
    // CAMERA PERMISSION
    // =====================================================

    private fun checkCameraPermissionAndOpen() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            openCamera()

        } else {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionCode
            )
        }
    }


    // =====================================================
    // OPEN CAMERA
    // =====================================================

    private fun openCamera() {

        cameraManager =
            getSystemService(CAMERA_SERVICE)
                    as CameraManager

        try {

            val cameraId =
                cameraManager.cameraIdList.firstOrNull()

            if (cameraId == null) {

                Toast.makeText(
                    this,
                    "No camera found",
                    Toast.LENGTH_LONG
                ).show()

                return
            }


            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                return
            }


            // =================================================
            // IMAGE READER
            // =================================================

            imageReader =
                ImageReader.newInstance(
                    1920,
                    1080,
                    ImageFormat.JPEG,
                    1
                )


            // =================================================
            // RECEIVE CAPTURED IMAGE
            // =================================================

            imageReader.setOnImageAvailableListener(

                { reader ->

                    val image =
                        reader.acquireLatestImage()

                    if (image != null) {

                        val buffer =
                            image.planes[0].buffer

                        val bytes =
                            ByteArray(
                                buffer.remaining()
                            )

                        buffer.get(bytes)

                        image.close()


                        // =====================================
                        // DECODE IMAGE
                        // =====================================

                        val bitmap =
                            BitmapFactory.decodeByteArray(
                                bytes,
                                0,
                                bytes.size
                            )


                        if (bitmap == null) {

                            runOnUiThread {

                                Toast.makeText(
                                    this@CaptureActivity,
                                    "Unable to read captured image",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            return@setOnImageAvailableListener
                        }


                        // =====================================
                        // QUALITY CHECK
                        // =====================================

                        val sharpEnough =
                            DocumentQualityChecker
                                .isSharpEnough(bitmap)

                        val brightnessAcceptable =
                            DocumentQualityChecker
                                .isBrightnessAcceptable(bitmap)


                        // =====================================
                        // QUALITY PASSED
                        // =====================================

                        if (
                            sharpEnough &&
                            brightnessAcceptable
                        ) {

                            // Keep original captured image.
                            // Document edge detection and
                            // auto-cropping will be added later.

                            capturedImageBytes =
                                bytes

                            bitmap.recycle()


                            runOnUiThread {

                                showPhotoReview(
                                    bytes
                                )
                            }


                        } else {

                            // =================================
                            // QUALITY CHECK FAILED
                            // =================================

                            bitmap.recycle()

                            capturedImageBytes =
                                null


                            runOnUiThread {

                                val message =
                                    when {

                                        !sharpEnough &&
                                                !brightnessAcceptable ->

                                            "Image is blurry and brightness is not acceptable. Please recapture."


                                        !sharpEnough ->

                                            "Image is too blurry. Please recapture."


                                        else ->

                                            "Image brightness is not acceptable. Please recapture."
                                    }


                                Toast.makeText(
                                    this@CaptureActivity,
                                    message,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                },

                null
            )


            // =================================================
            // OPEN CAMERA DEVICE
            // =================================================

            cameraManager.openCamera(

                cameraId,

                object :
                    CameraDevice.StateCallback() {


                    override fun onOpened(
                        camera: CameraDevice
                    ) {

                        cameraDevice =
                            camera

                        startCameraPreview()
                    }


                    override fun onDisconnected(
                        camera: CameraDevice
                    ) {

                        camera.close()

                        cameraDevice =
                            null
                    }


                    override fun onError(
                        camera: CameraDevice,
                        error: Int
                    ) {

                        camera.close()

                        cameraDevice =
                            null


                        runOnUiThread {

                            Toast.makeText(
                                this@CaptureActivity,
                                "Camera error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },

                null
            )


        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Unable to open camera",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // =====================================================
    // START CAMERA PREVIEW
    // =====================================================

    private fun startCameraPreview() {

        val camera =
            cameraDevice ?: return

        val texture =
            cameraPreview.surfaceTexture
                ?: return


        texture.setDefaultBufferSize(
            cameraPreview.width,
            cameraPreview.height
        )


        val surface =
            Surface(texture)


        try {

            val previewRequestBuilder =
                camera.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
                )


            previewRequestBuilder.addTarget(
                surface
            )


            camera.createCaptureSession(

                listOf(
                    surface,
                    imageReader.surface
                ),

                object :
                    CameraCaptureSession.StateCallback() {


                    override fun onConfigured(
                        session: CameraCaptureSession
                    ) {

                        captureSession =
                            session


                        try {

                            session.setRepeatingRequest(

                                previewRequestBuilder.build(),

                                null,

                                null
                            )

                        } catch (e: Exception) {

                            e.printStackTrace()
                        }
                    }


                    override fun onConfigureFailed(
                        session: CameraCaptureSession
                    ) {

                        runOnUiThread {

                            Toast.makeText(
                                this@CaptureActivity,
                                "Unable to start camera preview",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },

                null
            )


        } catch (e: Exception) {

            e.printStackTrace()
        }
    }


    // =====================================================
    // CAPTURE PHOTO
    // =====================================================

    private fun capturePhoto() {

        val camera =
            cameraDevice ?: return

        val session =
            captureSession ?: return


        try {

            val captureRequest =
                camera.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE
                )


            captureRequest.addTarget(
                imageReader.surface
            )


            // =================================================
            // JPEG ORIENTATION
            // =================================================

            val cameraId =
                camera.id


            val characteristics =
                cameraManager.getCameraCharacteristics(
                    cameraId
                )


            val sensorOrientation =
                characteristics.get(
                    CameraCharacteristics.SENSOR_ORIENTATION
                ) ?: 0


            @Suppress("DEPRECATION")
            val rotation =
                windowManager.defaultDisplay.rotation


            val deviceDegrees =
                when (rotation) {

                    Surface.ROTATION_0 ->
                        0

                    Surface.ROTATION_90 ->
                        90

                    Surface.ROTATION_180 ->
                        180

                    Surface.ROTATION_270 ->
                        270

                    else ->
                        0
                }


            val jpegOrientation =
                (
                        sensorOrientation +
                                deviceDegrees +
                                360
                        ) % 360


            captureRequest.set(
                CaptureRequest.JPEG_ORIENTATION,
                jpegOrientation
            )


            // =================================================
            // CAPTURE
            // =================================================

            session.capture(

                captureRequest.build(),

                object :
                    CameraCaptureSession.CaptureCallback() {
                },

                null
            )


        } catch (e: Exception) {

            e.printStackTrace()


            Toast.makeText(
                this,
                "Capture failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // =====================================================
    // SHOW PHOTO REVIEW
    // =====================================================

    private fun showPhotoReview(
        imageBytes: ByteArray
    ) {

        val bitmap =
            BitmapFactory.decodeByteArray(
                imageBytes,
                0,
                imageBytes.size
            )


        if (bitmap != null) {

            capturedImage.setImageBitmap(
                bitmap
            )


            cameraPreview.visibility =
                View.GONE

            cameraControls.visibility =
                View.GONE

            photoReview.visibility =
                View.VISIBLE


        } else {

            Toast.makeText(
                this,
                "Unable to display captured photo",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // =====================================================
    // ENCRYPT AND STORE DOCUMENT
    // =====================================================

    private fun saveDocumentSecurely() {

        val imageBytes =
            capturedImageBytes


        if (imageBytes == null) {

            Toast.makeText(
                this,
                "No captured document found",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        try {

            val secureStorage =
                SecureStorage(this)


            val documentType =
                intent.getStringExtra(
                    "documentType"
                ) ?: "Document"


            val timeStamp =
                System.currentTimeMillis()


            val fileName =
                "${documentType}_${timeStamp}"


            secureStorage.saveEncryptedImage(
                imageBytes,
                fileName
            )


            secureStorage.saveDocumentMetadata(
                fileName,
                documentType,
                timeStamp
            )


            // Remove image from memory
            capturedImageBytes =
                null


            capturedImage.setImageBitmap(
                null
            )


            showStorageSuccess()


        } catch (e: Exception) {

            e.printStackTrace()


            Toast.makeText(
                this,
                "Secure storage failed",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // =====================================================
    // SHOW STORAGE SUCCESS
    // =====================================================

    private fun showStorageSuccess() {

        val documentType =
            intent.getStringExtra(
                "documentType"
            ) ?: "Document"


        val displayName =
            documentType.replace(
                "_",
                " "
            )


        storedDocumentType.text =
            displayName


        storageMessage.text =
            "$displayName is now securely encrypted and stored."


        cameraPreview.visibility =
            View.GONE

        cameraControls.visibility =
            View.GONE

        photoReview.visibility =
            View.GONE

        storageSuccess.visibility =
            View.VISIBLE
    }


    // =====================================================
    // CAMERA PERMISSION RESULT
    // =====================================================

    override fun onRequestPermissionsResult(

        requestCode: Int,

        permissions: Array<String>,

        grantResults: IntArray

    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )


        if (
            requestCode ==
            cameraPermissionCode
        ) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {

                checkCameraPermissionAndOpen()

            } else {

                Toast.makeText(
                    this,
                    "Camera permission is required",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
        }
    }


    // =====================================================
    // PAUSE
    // =====================================================

    override fun onPause() {

        super.onPause()


        captureSession?.close()

        captureSession =
            null


        cameraDevice?.close()

        cameraDevice =
            null
    }


    // =====================================================
    // DESTROY
    // =====================================================

    override fun onDestroy() {

        super.onDestroy()


        if (::imageReader.isInitialized) {

            imageReader.close()
        }
    }
}