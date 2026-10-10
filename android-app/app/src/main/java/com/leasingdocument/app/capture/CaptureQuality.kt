package com.leasingdocument.app.capture

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc

/** Retains the source module's Laplacian and mean-brightness checks. */
object CaptureQuality {
    const val BLUR_THRESHOLD = 5.0
    const val MIN_BRIGHTNESS = 40.0
    const val MAX_BRIGHTNESS = 220.0
    // Provisional floor for integration testing, not an OCR readability guarantee.
    const val MIN_LONG_EDGE = 1280
    const val MIN_SHORT_EDGE = 720

    data class Result(
        val blurScore: Double,
        val brightnessScore: Double,
        val blurPassed: Boolean,
        val brightnessPassed: Boolean,
        val resolutionPassed: Boolean
    ) {
        val passed: Boolean get() = blurPassed && brightnessPassed && resolutionPassed
        fun feedback(): String = buildList {
            if (!blurPassed) add("Hold the phone steady and focus on the document.")
            if (!brightnessPassed) add("Use even lighting without shadows or glare.")
            if (!resolutionPassed) add("Image resolution is too low. Use the rear camera on a physical phone.")
        }.joinToString(" ")
    }

    @Synchronized
    fun check(bitmap: Bitmap): Result {
        kotlin.check(OpenCVLoader.initLocal()) { "OpenCV could not initialize" }
        val source = Mat()
        val gray = Mat()
        val laplacian = Mat()
        val mean = MatOfDouble()
        val deviation = MatOfDouble()
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            // CV_16S is the named equivalent of the original module's numeric 3.
            Imgproc.Laplacian(gray, laplacian, CvType.CV_16S)
            Core.meanStdDev(laplacian, mean, deviation)
            val stdDev = deviation.toArray()[0]
            val variance = stdDev * stdDev
            val brightness = Core.mean(gray).`val`[0]
            return Result(
                variance, brightness,
                variance.isFinite() && variance >= BLUR_THRESHOLD,
                brightness.isFinite() && brightness in MIN_BRIGHTNESS..MAX_BRIGHTNESS,
                maxOf(bitmap.width, bitmap.height) >= MIN_LONG_EDGE &&
                    minOf(bitmap.width, bitmap.height) >= MIN_SHORT_EDGE
            )
        } finally {
            source.release(); gray.release(); laplacian.release()
            mean.release(); deviation.release()
        }
    }
}
