package com.leasingdocument.app.screens

import android.graphics.BitmapFactory

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.leasingdocument.app.network.RetrofitClient

import kotlinx.coroutines.CancellationException


@Composable
fun DocumentImageScreen(
    fileName: String
) {

    var imageBytes by remember {
        mutableStateOf<ByteArray?>(
            null
        )
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }


    // =========================================================
    // LOAD DOCUMENT IMAGE
    // =========================================================

    LaunchedEffect(
        fileName
    ) {

        isLoading =
            true

        errorMessage =
            ""

        imageBytes =
            null


        try {

            val response =
                RetrofitClient
                    .apiService
                    .getDocumentImage(
                        fileName
                    )


            if (
                response.isSuccessful
            ) {

                val bytes =
                    response
                        .body()
                        ?.bytes()


                if (
                    bytes != null &&
                    bytes.isNotEmpty()
                ) {

                    imageBytes =
                        bytes

                } else {

                    errorMessage =
                        "The server returned an empty document file."
                }


            } else {

                errorMessage =
                    when (
                        response.code()
                    ) {

                        401 ->
                            "Your session has expired. Please sign in again."

                        403 ->
                            "You do not have permission to view this document."

                        404 ->
                            "Document file could not be found."

                        else ->
                            "Failed to load document (HTTP ${response.code()})."
                    }
            }


        } catch (
            e: CancellationException
        ) {

            throw e

        } catch (
            e: Exception
        ) {

            errorMessage =
                "Cannot connect to server."

        } finally {

            isLoading =
                false
        }
    }


    // =========================================================
    // ROOT
    // =========================================================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFCFB),
                        Color(0xFFFFF8FB),
                        Color(0xFFF8F2FF)
                    )
                )
            )
    ) {


        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal =
                        18.dp,

                    vertical =
                        18.dp
                )
        ) {


            // =====================================================
            // FILE INFO
            // =====================================================

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            18.dp
                        )
                    )
                    .background(
                        Color.White.copy(
                            alpha =
                                0.96f
                        )
                    )
                    .padding(
                        16.dp
                    )
            ) {

                Column {

                    Text(
                        text =
                            "Secure Document Preview",

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF191A2C)
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                5.dp
                            )
                    )


                    Text(
                        text =
                            fileName,

                        fontSize =
                            11.sp,

                        color =
                            Color(0xFF817A8A),

                        maxLines =
                            2,

                        overflow =
                            TextOverflow.Ellipsis
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )


            // =====================================================
            // LOADING
            // =====================================================

            if (
                isLoading
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            300.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                22.dp
                            )
                        )
                        .background(
                            Color.White.copy(
                                alpha =
                                    0.95f
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
                            color =
                                Color(0xFF7650BD)
                        )


                        Spacer(
                            modifier =
                                Modifier.height(
                                    14.dp
                                )
                        )


                        Text(
                            text =
                                "Loading secure document...",

                            fontSize =
                                12.sp,

                            color =
                                Color(0xFF817A8A)
                        )
                    }
                }


            } else if (
                errorMessage.isNotBlank()
            ) {


                // =================================================
                // ERROR
                // =================================================

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                20.dp
                            )
                        )
                        .background(
                            Color(0xFFFFEFF1)
                        )
                        .padding(
                            20.dp
                        )
                ) {

                    Column {

                        Text(
                            text =
                                "Unable to display document",

                            fontSize =
                                16.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color(0xFF9E3344)
                        )


                        Spacer(
                            modifier =
                                Modifier.height(
                                    7.dp
                                )
                        )


                        Text(
                            text =
                                errorMessage,

                            fontSize =
                                12.sp,

                            lineHeight =
                                17.sp,

                            color =
                                Color(0xFF7B5960)
                        )
                    }
                }


            } else {


                // =================================================
                // DOCUMENT IMAGE
                // =================================================

                imageBytes?.let {
                        bytes ->


                    val bitmap =
                        BitmapFactory
                            .decodeByteArray(
                                bytes,
                                0,
                                bytes.size
                            )


                    if (
                        bitmap != null
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        22.dp
                                    )
                                )
                                .background(
                                    Color.White
                                )
                                .padding(
                                    10.dp
                                )
                        ) {

                            Image(
                                bitmap =
                                    bitmap
                                        .asImageBitmap(),

                                contentDescription =
                                    fileName,

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(
                                            RoundedCornerShape(
                                                14.dp
                                            )
                                        ),

                                contentScale =
                                    ContentScale.Fit
                            )
                        }


                        Spacer(
                            modifier =
                                Modifier.height(
                                    14.dp
                                )
                        )


                        // =========================================
                        // SECURITY NOTE
                        // =========================================

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        16.dp
                                    )
                                )
                                .background(
                                    Color(0xFFF2ECFA)
                                )
                                .padding(
                                    14.dp
                                )
                        ) {

                            Text(
                                text =
                                    "This document was securely retrieved through DOC SECURE.",

                                fontSize =
                                    11.sp,

                                lineHeight =
                                    16.sp,

                                color =
                                    Color(0xFF654C84)
                            )
                        }


                    } else {


                        // =========================================
                        // UNSUPPORTED FILE
                        // =========================================

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        20.dp
                                    )
                                )
                                .background(
                                    Color(0xFFFFF3E8)
                                )
                                .padding(
                                    20.dp
                                )
                        ) {

                            Column {

                                Text(
                                    text =
                                        "Preview unavailable",

                                    fontSize =
                                        16.sp,

                                    fontWeight =
                                        FontWeight.Bold,

                                    color =
                                        Color(0xFF875B32)
                                )


                                Spacer(
                                    modifier =
                                        Modifier.height(
                                            7.dp
                                        )
                                )


                                Text(
                                    text =
                                        "This file could not be displayed as an image.",

                                    fontSize =
                                        12.sp,

                                    lineHeight =
                                        17.sp,

                                    color =
                                        Color(0xFF756352)
                                )
                            }
                        }
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        24.dp
                    )
            )
        }
    }
}