package com.leasingdocument.app.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** OCR only. Component 4's original Python code performs alteration detection. */
object DocumentOcrProcessor {
    suspend fun extract(jpeg: ByteArray): String = withContext(Dispatchers.Default) {
        val bitmap = uprightBitmap(jpeg)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        // Wait until ML Kit finishes before recycling its input, even on activity cancellation.
        try {
            val text = suspendCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            val lines = text.textBlocks.flatMap { it.lines }
            val lineJson = JSONArray()
            for (line in lines) {
                val box = line.boundingBox
                lineJson.put(JSONObject().put("text", line.text).put("boundingBox",
                    if (box == null) JSONObject.NULL else JSONObject()
                        .put("left", box.left).put("top", box.top)
                        .put("right", box.right).put("bottom", box.bottom)))
            }
            JSONObject()
                .put("engine", "ML_KIT_LATIN_16.0.1")
                .put("status", if (text.text.isBlank()) "EMPTY" else "COMPLETED")
                .put("fullText", text.text)
                .put("imageWidth", bitmap.width).put("imageHeight", bitmap.height)
                .put("coordinateSpace", "EXIF_UPRIGHT_FULL_IMAGE")
                .put("lines", lineJson)
                .put("fields", OcrFieldExtractor.extract(lines.map { it.text }))
                .toString()
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    fun uprightBitmap(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 12_000_000L) {
            "Image dimensions are invalid or exceed the capture limit."
        }
        val orientation = ByteArrayInputStream(bytes).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> 0
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> error("Mirrored image orientation is not supported by the capture workflow.")
        }
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        if (rotation == 0) return decoded
        return try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } finally { decoded.recycle() }
    }
}
