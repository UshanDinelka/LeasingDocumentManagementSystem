/*package com.example.securedocumentcapture3

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.sqrt

object DocumentScanner {

    private const val TAG = "DOCUMENT_SCANNER"

    // Minimum percentage of the image that a detected
    // document should occupy.
    private const val MIN_DOCUMENT_AREA_RATIO = 0.03


    // =====================================================
    // EXISTING DOCUMENT DETECTION
    // =====================================================

    fun detectDocument(bitmap: Bitmap): Boolean {

        val corners = findDocumentCorners(bitmap)

        val detected = corners != null

        Log.d(
            TAG,
            "Document detected = $detected"
        )

        return detected
    }


    // =====================================================
    // AUTO DOCUMENT SCANNER
    // =====================================================
    //
    // Returns:
    //
    // Bitmap -> document found and cropped
    // null   -> document could not be detected
    //
    // =====================================================

    fun scanDocument(bitmap: Bitmap): Bitmap? {

        val source = Mat()

        return try {

            // Find the four document corners
            val corners =
                findDocumentCorners(bitmap)

            if (corners == null) {

                Log.d(
                    TAG,
                    "Auto crop failed: document corners not found"
                )

                return null
            }


            // Convert original Bitmap to OpenCV Mat
            Utils.bitmapToMat(
                bitmap,
                source
            )


            // Order the four corners:
            //
            // 0 = top-left
            // 1 = top-right
            // 2 = bottom-right
            // 3 = bottom-left

            val ordered =
                orderPoints(corners)


            val topLeft =
                ordered[0]

            val topRight =
                ordered[1]

            val bottomRight =
                ordered[2]

            val bottomLeft =
                ordered[3]


            // =================================================
            // CALCULATE DOCUMENT WIDTH
            // =================================================

            val widthTop =
                distance(
                    topLeft,
                    topRight
                )

            val widthBottom =
                distance(
                    bottomLeft,
                    bottomRight
                )

            val maxWidth =
                max(
                    widthTop,
                    widthBottom
                ).toInt()


            // =================================================
            // CALCULATE DOCUMENT HEIGHT
            // =================================================

            val heightLeft =
                distance(
                    topLeft,
                    bottomLeft
                )

            val heightRight =
                distance(
                    topRight,
                    bottomRight
                )

            val maxHeight =
                max(
                    heightLeft,
                    heightRight
                ).toInt()


            if (
                maxWidth <= 0 ||
                maxHeight <= 0
            ) {

                Log.d(
                    TAG,
                    "Auto crop failed: invalid document size"
                )

                return null
            }


            // =================================================
            // SOURCE CORNERS
            // =================================================

            val sourcePoints =
                MatOfPoint2f(
                    topLeft,
                    topRight,
                    bottomRight,
                    bottomLeft
                )


            // =================================================
            // DESTINATION CORNERS
            // =================================================

            val destinationPoints =
                MatOfPoint2f(

                    Point(
                        0.0,
                        0.0
                    ),

                    Point(
                        maxWidth - 1.0,
                        0.0
                    ),

                    Point(
                        maxWidth - 1.0,
                        maxHeight - 1.0
                    ),

                    Point(
                        0.0,
                        maxHeight - 1.0
                    )
                )


            // =================================================
            // PERSPECTIVE TRANSFORMATION
            // =================================================

            val transform =
                Imgproc.getPerspectiveTransform(
                    sourcePoints,
                    destinationPoints
                )


            val cropped =
                Mat()


            Imgproc.warpPerspective(

                source,

                cropped,

                transform,

                Size(
                    maxWidth.toDouble(),
                    maxHeight.toDouble()
                )
            )


            // =================================================
            // CONVERT CROPPED MAT BACK TO BITMAP
            // =================================================

            val croppedBitmap =
                Bitmap.createBitmap(

                    cropped.cols(),

                    cropped.rows(),

                    Bitmap.Config.ARGB_8888
                )


            Utils.matToBitmap(
                cropped,
                croppedBitmap
            )


            Log.d(
                TAG,
                "Document auto-cropped: " +
                        "${croppedBitmap.width} x " +
                        "${croppedBitmap.height}"
            )


            // Release temporary OpenCV objects

            sourcePoints.release()

            destinationPoints.release()

            transform.release()

            cropped.release()


            croppedBitmap


        } catch (e: Exception) {

            Log.e(
                TAG,
                "Document scanning failed",
                e
            )

            null

        } finally {

            source.release()
        }
    }


    // =====================================================
    // FIND DOCUMENT CORNERS
    // =====================================================

    private fun findDocumentCorners(
        bitmap: Bitmap
    ): Array<Point>? {

        val source = Mat()

        val gray = Mat()

        val blurred = Mat()

        val edges = Mat()

        val hierarchy = Mat()


        return try {

            // Convert Bitmap to OpenCV Mat

            Utils.bitmapToMat(
                bitmap,
                source
            )


            // =================================================
            // GRAYSCALE
            // =================================================

            Imgproc.cvtColor(

                source,

                gray,

                Imgproc.COLOR_RGBA2GRAY
            )


            // =================================================
            // REDUCE CAMERA NOISE
            // =================================================

            Imgproc.GaussianBlur(

                gray,

                blurred,

                Size(
                    5.0,
                    5.0
                ),

                0.0
            )


            // =================================================
            // EDGE DETECTION
            // =================================================

            Imgproc.Canny(

                blurred,

                edges,

                50.0,

                150.0
            )


            // =================================================
            // MAKE EDGES STRONGER
            // =================================================

            val kernel =
                Imgproc.getStructuringElement(

                    Imgproc.MORPH_RECT,

                    Size(
                        3.0,
                        3.0
                    )
                )


            Imgproc.dilate(

                edges,

                edges,

                kernel
            )


            // =================================================
            // FIND CONTOURS
            // =================================================

            val contours =
                ArrayList<MatOfPoint>()


            Imgproc.findContours(

                edges,

                contours,

                hierarchy,

                Imgproc.RETR_LIST,

                Imgproc.CHAIN_APPROX_SIMPLE
            )


            val imageArea =

                bitmap.width.toDouble() *

                        bitmap.height.toDouble()


            val minimumArea =

                imageArea *

                        MIN_DOCUMENT_AREA_RATIO


            var bestCorners:
                    Array<Point>? = null


            var largestArea =
                0.0


            // =================================================
            // CHECK EACH CONTOUR
            // =================================================

            for (contour in contours) {

                val contourArea =
                    Imgproc.contourArea(
                        contour
                    )


                // Ignore small objects

                if (
                    contourArea <
                    minimumArea
                ) {

                    contour.release()

                    continue
                }


                val contour2f =
                    MatOfPoint2f(

                        *contour.toArray()
                    )


                val perimeter =
                    Imgproc.arcLength(

                        contour2f,

                        true
                    )

                val epsilonValues =
                    doubleArrayOf(
                        0.02,
                        0.03,
                        0.04,
                        0.05,
                        0.06
                    )

                for (epsilon in epsilonValues) {

                    val approximation =
                        MatOfPoint2f()

                    Imgproc.approxPolyDP(
                        contour2f,
                        approximation,
                        epsilon * perimeter,
                        true
                    )

                    if (approximation.total() == 4L) {

                        val points =
                            approximation.toArray()

                        val polygon =
                            MatOfPoint(*points)

                        val area =
                            Imgproc.contourArea(polygon)

                        if (
                            Imgproc.isContourConvex(polygon) &&
                            area > largestArea
                        ) {

                            largestArea = area
                            bestCorners = points
                        }

                        polygon.release()
                    }

                    approximation.release()
                }
                contour2f.release()

                contour.release()
            }


            // If normal 4-corner detection worked, use it
            if (bestCorners != null) {

                Log.d(
                    TAG,
                    "Document corners found. Area = $largestArea"
                )

                bestCorners

            } else {

                Log.d(
                    TAG,
                    "Normal corner detection failed"
                )

                // Try a simpler fallback:
                // find the largest object and create a rectangle around it

                val fallbackContours =
                    ArrayList<MatOfPoint>()

                val fallbackHierarchy =
                    Mat()

                Imgproc.findContours(
                    edges.clone(),
                    fallbackContours,
                    fallbackHierarchy,
                    Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_SIMPLE
                )

                val largestContour =
                    fallbackContours.maxByOrNull {
                        Imgproc.contourArea(it)
                    }

                if (largestContour != null &&
                    Imgproc.contourArea(largestContour) >= minimumArea
                ) {

                    val contour2f =
                        MatOfPoint2f(*largestContour.toArray())

                    val rectangle =
                        Imgproc.minAreaRect(contour2f)

                    val points =
                        arrayOf(
                            Point(),
                            Point(),
                            Point(),
                            Point()
                        )

                    rectangle.points(points)

                    contour2f.release()
                    fallbackHierarchy.release()

                    fallbackContours.forEach {
                        it.release()
                    }

                    Log.d(
                        TAG,
                        "Document found using rectangle fallback"
                    )

                    points

                } else {

                    fallbackHierarchy.release()

                    fallbackContours.forEach {
                        it.release()
                    }

                    Log.d(
                        TAG,
                        "Document detection completely failed"
                    )

                    null
                }
            }


        } catch (e: Exception) {

            Log.e(
                TAG,
                "Corner detection failed",
                e
            )

            null

        } finally {

            source.release()

            gray.release()

            blurred.release()

            edges.release()

            hierarchy.release()
        }
    }


    // =====================================================
    // ORDER DOCUMENT CORNERS
    // =====================================================

    private fun orderPoints(
        points: Array<Point>
    ): Array<Point> {

        /*
         * For each point:
         *
         * x + y:
         * smallest -> top-left
         * largest  -> bottom-right
         *
         * y - x:
         * smallest -> top-right
         * largest  -> bottom-left
         */


        val topLeft =
            points.minByOrNull {
                it.x + it.y
            }!!


        val bottomRight =
            points.maxByOrNull {
                it.x + it.y
            }!!


        val topRight =
            points.minByOrNull {
                it.y - it.x
            }!!


        val bottomLeft =
            points.maxByOrNull {
                it.y - it.x
            }!!


        return arrayOf(

            topLeft,

            topRight,

            bottomRight,

            bottomLeft
        )
    }


    // =====================================================
    // DISTANCE BETWEEN TWO POINTS
    // =====================================================

    private fun distance(
        first: Point,
        second: Point
    ): Double {

        val x =
            second.x -
                    first.x


        val y =
            second.y -
                    first.y


        return sqrt(
            x * x +
                    y * y
        )
    }
}*/