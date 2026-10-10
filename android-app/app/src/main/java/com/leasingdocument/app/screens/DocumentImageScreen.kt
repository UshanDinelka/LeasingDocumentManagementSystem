package com.leasingdocument.app.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leasingdocument.app.network.RetrofitClient

@Composable
fun DocumentImageScreen(
    fileName: String
) {

    var imageBytes by remember {
        mutableStateOf<ByteArray?>(null)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    LaunchedEffect(fileName) {

        try {

            val response =
                RetrofitClient.apiService
                    .getDocumentImage(fileName)

            if (response.isSuccessful) {

                imageBytes =
                    response.body()?.bytes()

            } else {

                errorMessage =
                    "Failed to load image: ${response.code()}"
            }

        } catch (e: Exception) {

            errorMessage =
                "Cannot connect to server"
        }

        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {

        Text(
            text = "Document Image",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = fileName
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        if (isLoading) {

            CircularProgressIndicator()

        } else if (errorMessage.isNotEmpty()) {

            Text(errorMessage)

        } else {

            imageBytes?.let { bytes ->

                val bitmap =
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size
                    )

                if (bitmap != null) {

                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = fileName,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )

                } else {

                    Text("Unable to display this file as an image")
                }
            }
        }
    }
}