package com.leasingdocument.app.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

@Composable
fun DocumentImageScreen(
    fileName: String
) {

    var sourceBitmap by remember(fileName) {
        mutableStateOf<Bitmap?>(null)
    }

    var loading by remember(fileName) {
        mutableStateOf(true)
    }

    var errorMessage by remember(fileName) {
        mutableStateOf("")
    }

    var manualRotation by remember(fileName) {
        mutableIntStateOf(0)
    }


    // =========================================================
    // LOAD SECURE DOCUMENT + APPLY EXIF ORIENTATION
    // =========================================================

    LaunchedEffect(fileName) {

        loading = true
        errorMessage = ""
        manualRotation = 0

        var bytesToClear: ByteArray? = null

        try {

            val response =
                RetrofitClient
                    .apiService
                    .getDocumentImage(fileName)

            if (!response.isSuccessful) {

                errorMessage =
                    "Failed to retrieve document (${response.code()})"

                return@LaunchedEffect
            }

            bytesToClear =
                response
                    .body()
                    ?.bytes()

            val bytes =
                bytesToClear

            if (
                bytes == null ||
                bytes.isEmpty()
            ) {

                errorMessage =
                    "The server returned an empty document image"

                return@LaunchedEffect
            }

            sourceBitmap =
                withContext(Dispatchers.Default) {
                    decodeProfessionalPreview(bytes)
                }

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            errorMessage =
                e.message
                    ?: "Unable to display this document"

        } finally {

            bytesToClear?.fill(0)
            loading = false
        }
    }


    val previewBitmap =
        remember(
            sourceBitmap,
            manualRotation
        ) {

            val source =
                sourceBitmap

            if (
                source == null ||
                manualRotation == 0
            ) {

                source

            } else {

                rotatePreviewBitmap(
                    bitmap = source,
                    degrees = manualRotation.toFloat()
                )
            }
        }


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

        ProfessionalPreviewBackground()


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
                Arrangement.spacedBy(14.dp)
        ) {

            // =================================================
            // HEADER
            // =================================================

            item {

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )


                Text(
                    text =
                        "Original Document",

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


            }


            // =================================================
            // SECURE FILE SUMMARY
            // =================================================

            item {

                PreviewSecurityHeader(
                    fileName = fileName
                )
            }


            // =================================================
            // CONTENT
            // =================================================

            when {

                loading -> {

                    item {

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(360.dp)
                                    .clip(
                                        RoundedCornerShape(24.dp)
                                    )
                                    .background(
                                        Color.White.copy(
                                            alpha = 0.97f
                                        )
                                    ),

                            contentAlignment =
                                Alignment.Center
                        ) {

                            Column(
                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {

                                CircularProgressIndicator(
                                    modifier =
                                        Modifier.size(32.dp),

                                    strokeWidth = 3.dp,

                                    color =
                                        Color(0xFF7650BD)
                                )


                                Spacer(
                                    modifier =
                                        Modifier.height(13.dp)
                                )


                                Text(
                                    text =
                                        "Retrieving secure document...",

                                    fontSize =
                                        11.sp,

                                    color =
                                        Color(0xFF817A8B)
                                )
                            }
                        }
                    }
                }


                errorMessage.isNotBlank() -> {

                    item {

                        PreviewMessageCard(
                            title =
                                "Preview unavailable",

                            message =
                                errorMessage,

                            error =
                                true
                        )
                    }
                }


                previewBitmap != null -> {

                    item {

                        ProfessionalImageViewer(
                            bitmap =
                                previewBitmap,

                            rotation =
                                manualRotation,

                            onRotate = {

                                manualRotation =
                                    (manualRotation + 90) % 360
                            }
                        )
                    }


                    item {

                        PreviewSecurityInfoCard()
                    }
                }
            }
        }
    }
}


// =====================================================================
// BACKGROUND
// =====================================================================

@Composable
private fun ProfessionalPreviewBackground() {

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
            path = topShape,

            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color(0xFFFFD1AF)
                                .copy(alpha = 0.58f),

                            Color(0xFFFFE8DE)
                                .copy(alpha = 0.24f),

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
            path = ribbon,

            color =
                Color(0xFFFF8B42)
                    .copy(alpha = 0.70f),

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
                                .copy(alpha = 0.08f),

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
// SECURITY HEADER
// =====================================================================

@Composable
private fun PreviewSecurityHeader(
    fileName: String
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(22.dp)
                )
                .background(
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                Color(0xFF6553BD),
                                Color(0xFF9C4FC1),
                                Color(0xFFD34D92)
                            )
                    )
                )
                .padding(17.dp)
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
                        .size(50.dp)
                        .clip(
                            RoundedCornerShape(15.dp)
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
                        "DOC",

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
                        "Secure Original",

                    fontSize =
                        15.sp,

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
                        fileName,

                    fontSize =
                        9.sp,

                    color =
                        Color.White.copy(
                            alpha = 0.82f
                        ),

                    maxLines = 1,

                    overflow =
                        TextOverflow.Ellipsis
                )
            }


            Box(
                modifier =
                    Modifier
                        .clip(
                            RoundedCornerShape(50.dp)
                        )
                        .background(
                            Color.White.copy(
                                alpha = 0.16f
                            )
                        )
                        .padding(
                            horizontal = 9.dp,
                            vertical = 6.dp
                        )
            ) {

                Text(
                    text =
                        "SECURE",

                    fontSize =
                        7.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color.White
                )
            }
        }
    }
}


// =====================================================================
// IMAGE VIEWER
// =====================================================================

@Composable
private fun ProfessionalImageViewer(
    bitmap: Bitmap,
    rotation: Int,
    onRotate: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(24.dp)
                )
                .background(
                    Color.White.copy(
                        alpha = 0.97f
                    )
                )
                .padding(12.dp)
    ) {

        Column {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            "Document Preview",

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
                            "${bitmap.width} × ${bitmap.height} px",

                        fontSize =
                            8.sp,

                        color =
                            Color(0xFF918A9B)
                    )
                }


                Box(
                    modifier =
                        Modifier
                            .clip(
                                RoundedCornerShape(13.dp)
                            )
                            .background(
                                Color(0xFFF0EBFA)
                            )
                            .clickable {
                                onRotate()
                            }
                            .padding(
                                horizontal = 11.dp,
                                vertical = 9.dp
                            )
                ) {

                    Text(
                        text =
                            if (rotation == 0) {
                                "ROTATE 90°"
                            } else {
                                "ROTATE • ${rotation}°"
                            },

                        fontSize =
                            8.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF6F4BB8)
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 300.dp,
                            max = 560.dp
                        )
                        .clip(
                            RoundedCornerShape(18.dp)
                        )
                        .background(
                            Color(0xFF171A2C)
                        )
                        .padding(10.dp),

                contentAlignment =
                    Alignment.Center
            ) {

                Image(
                    bitmap =
                        bitmap.asImageBitmap(),

                    contentDescription =
                        "Original secure document",

                    modifier =
                        Modifier.fillMaxWidth(),

                    contentScale =
                        ContentScale.Fit
                )
            }
        }
    }
}


// =====================================================================
// SECURITY INFORMATION
// =====================================================================

@Composable
private fun PreviewSecurityInfoCard() {

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
                .padding(15.dp)
    ) {

        Column {

            Text(
                text =
                    "Secure Retrieval",

                fontSize =
                    14.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF171A2C)
            )


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                PreviewInfoPill(
                    modifier =
                        Modifier.weight(1f),

                    title =
                        "SOURCE",

                    value =
                        "SECURE STORAGE"
                )


                PreviewInfoPill(
                    modifier =
                        Modifier.weight(1f),

                    title =
                        "ACCESS",

                    value =
                        "AUTHORIZED"
                )
            }


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            Text(
                text =
                    "Authenticated secure retrieval.",

                fontSize =
                    10.sp,

                lineHeight =
                    15.sp,

                color =
                    Color(0xFF817A8B)
            )
        }
    }
}


@Composable
private fun PreviewInfoPill(
    modifier: Modifier,
    title: String,
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
                    title,

                fontSize =
                    7.sp,

                fontWeight =
                    FontWeight.Bold,

                letterSpacing =
                    0.5.sp,

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
                    Color(0xFF353146)
            )
        }
    }
}


// =====================================================================
// MESSAGE
// =====================================================================

@Composable
private fun PreviewMessageCard(
    title: String,
    message: String,
    error: Boolean
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(20.dp)
                )
                .background(
                    if (error) {
                        Color(0xFFFFEEF1)
                    } else {
                        Color.White
                    }
                )
                .padding(18.dp)
    ) {

        Column {

            Text(
                text =
                    title,

                fontSize =
                    15.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    if (error) {
                        Color(0xFFA13A4B)
                    } else {
                        Color(0xFF171A2C)
                    }
            )


            Spacer(
                modifier =
                    Modifier.height(6.dp)
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


// =====================================================================
// SAFE DECODE + EXIF ORIENTATION
// =====================================================================

private fun decodeProfessionalPreview(
    bytes: ByteArray
): Bitmap {

    val bounds =
        BitmapFactory.Options()
            .apply {
                inJustDecodeBounds = true
            }


    BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        bounds
    )


    require(
        bounds.outWidth > 0 &&
                bounds.outHeight > 0
    ) {
        "Unsupported document image"
    }


    require(
        bounds.outWidth.toLong() *
                bounds.outHeight.toLong() <=
                32_000_000L
    ) {
        "Document image is too large to preview safely"
    }


    val options =
        BitmapFactory.Options()
            .apply {

                inSampleSize =
                    when {

                        bounds.outWidth > 3000 ||
                                bounds.outHeight > 3000 ->
                            4

                        bounds.outWidth > 1600 ||
                                bounds.outHeight > 1600 ->
                            2

                        else ->
                            1
                    }
            }


    val decoded =
        requireNotNull(
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                options
            )
        ) {
            "Unable to decode document image"
        }


    val orientation =
        try {

            ByteArrayInputStream(bytes).use { stream ->

                ExifInterface(stream)
                    .getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
            }

        } catch (_: Exception) {

            ExifInterface.ORIENTATION_NORMAL
        }


    return applyPreviewExif(
        bitmap = decoded,
        orientation = orientation
    )
}


private fun applyPreviewExif(
    bitmap: Bitmap,
    orientation: Int
): Bitmap {

    val matrix =
        Matrix()


    when (orientation) {

        ExifInterface.ORIENTATION_ROTATE_90 ->
            matrix.postRotate(90f)

        ExifInterface.ORIENTATION_ROTATE_180 ->
            matrix.postRotate(180f)

        ExifInterface.ORIENTATION_ROTATE_270 ->
            matrix.postRotate(270f)

        ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
            matrix.postScale(-1f, 1f)

        ExifInterface.ORIENTATION_FLIP_VERTICAL ->
            matrix.postScale(1f, -1f)

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postScale(-1f, 1f)
            matrix.postRotate(270f)
        }

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postScale(-1f, 1f)
            matrix.postRotate(90f)
        }

        else ->
            return bitmap
    }


    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )
}


private fun rotatePreviewBitmap(
    bitmap: Bitmap,
    degrees: Float
): Bitmap {

    val matrix =
        Matrix().apply {
            postRotate(degrees)
        }


    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )
}
