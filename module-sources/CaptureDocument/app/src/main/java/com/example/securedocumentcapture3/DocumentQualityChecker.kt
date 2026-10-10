package com.example.securedocumentcapture3

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc

object DocumentQualityChecker {

    // We will adjust this value after testing
    private const val BLUR_THRESHOLD = 5.0

    fun isSharpEnough(bitmap: Bitmap): Boolean {

        val source = Mat()
        val gray = Mat()
        val laplacian = Mat()

        return try {

            // Convert Android Bitmap to OpenCV Mat
            Utils.bitmapToMat(
                bitmap,
                source
            )

            // Convert image to grayscale
            Imgproc.cvtColor(
                source,
                gray,
                Imgproc.COLOR_RGBA2GRAY
            )

            // Apply Laplacian
            Imgproc.Laplacian(
                gray,
                laplacian,
                3
            )

            // Calculate standard deviation
            val mean = MatOfDouble()
            val standardDeviation = MatOfDouble()

            Core.meanStdDev(
                laplacian,
                mean,
                standardDeviation
            )

            val stdDev =
                standardDeviation.toArray()[0]

            // Variance = standard deviation²
            val variance =
                stdDev * stdDev

            android.util.Log.d(
                "QUALITY_CHECK",
                "Laplacian variance = $variance"
            )

            mean.release()
            standardDeviation.release()

            // Higher variance = sharper image
            variance >= BLUR_THRESHOLD

        } catch (e: Exception) {

            e.printStackTrace()

            // If the quality check fails,
            // reject the image for safety.
            false

        } finally {

            source.release()
            gray.release()
            laplacian.release()
        }
    }

    fun isBrightnessAcceptable(bitmap: Bitmap): Boolean {

        val source = Mat()
        val gray = Mat()

        return try {

            // Convert Bitmap to OpenCV Mat
            Utils.bitmapToMat(
                bitmap,
                source
            )

            // Convert to grayscale
            Imgproc.cvtColor(
                source,
                gray,
                Imgproc.COLOR_RGBA2GRAY
            )

            // Calculate average brightness
            val meanBrightness =
                Core.mean(gray).`val`[0]

            android.util.Log.d(
                "QUALITY_CHECK",
                "Average brightness = $meanBrightness"
            )

            // Acceptable brightness range
            meanBrightness >= 40.0 && meanBrightness <= 220.0

        } catch (e: Exception) {

            e.printStackTrace()

            // Reject if checking fails
            false

        } finally {

            source.release()
            gray.release()
        }
    }
}