package com.example.securedocumentcapture3

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentViewerActivity : AppCompatActivity() {

    private lateinit var documentImage: ImageView
    private lateinit var documentTitle: TextView
    private lateinit var documentStatus: TextView
    private lateinit var documentDate: TextView
    private lateinit var backButton: Button


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_document_viewer
        )


        // =========================
        // FIND VIEWS
        // =========================

        documentImage =
            findViewById(
                R.id.documentImage
            )

        documentTitle =
            findViewById(
                R.id.documentTitle
            )

        documentStatus =
            findViewById(
                R.id.documentStatus
            )

        documentDate =
            findViewById(
                R.id.documentDate
            )

        backButton =
            findViewById(
                R.id.viewerBackButton
            )


        // =========================
        // BACK BUTTON
        // =========================

        backButton.setOnClickListener {

            finish()
        }


        // =========================
        // GET ENCRYPTED FILE
        // =========================

        val filePath =
            intent.getStringExtra(
                "ENCRYPTED_FILE"
            )


        if (filePath == null) {

            Toast.makeText(
                this,
                "Document not found",
                Toast.LENGTH_LONG
            ).show()

            finish()

            return
        }


        val encryptedFile =
            File(filePath)


        if (!encryptedFile.exists()) {

            Toast.makeText(
                this,
                "Encrypted document does not exist",
                Toast.LENGTH_LONG
            ).show()

            finish()

            return
        }


        // =========================
        // DISPLAY DOCUMENT INFO
        // =========================

        displayDocumentInformation(
            encryptedFile
        )


        // =========================
        // DECRYPT DOCUMENT
        // =========================

        decryptAndDisplay(
            encryptedFile
        )
    }


    // =====================================================
    // DISPLAY DOCUMENT INFORMATION
    // =====================================================

    private fun displayDocumentInformation(
        encryptedFile: File
    ) {

        val fileName =
            encryptedFile.name.removeSuffix(
                ".enc"
            )


        val lastUnderscore =
            fileName.lastIndexOf("_")


        val documentType =

            if (lastUnderscore > 0) {

                fileName.substring(
                    0,
                    lastUnderscore
                )

            } else {

                "Unknown Document"
            }


        val timeString =

            if (lastUnderscore > 0) {

                fileName.substring(
                    lastUnderscore + 1
                )

            } else {

                ""
            }


        val captureTime =
            timeString.toLongOrNull()
                ?: encryptedFile.lastModified()


        // =========================
        // DOCUMENT TITLE
        // =========================

        documentTitle.text =
            documentType.replace(
                "_",
                " "
            )


        // =========================
        // SECURITY STATUS
        // =========================

        documentStatus.text =
            "🔐 Encrypted • Admin Access"


        // =========================
        // CAPTURE DATE
        // =========================

        val dateFormat =
            SimpleDateFormat(
                "dd MMMM yyyy, hh:mm a",
                Locale.getDefault()
            )


        documentDate.text =
            "Captured: ${
                dateFormat.format(
                    Date(captureTime)
                )
            }"
    }


    // =====================================================
    // DECRYPT AND DISPLAY
    // =====================================================

    private fun decryptAndDisplay(
        encryptedFile: File
    ) {

        try {

            val secureStorage =
                SecureStorage(this)


            // =========================
            // DECRYPT
            // =========================

            val imageBytes =
                secureStorage.decryptImage(
                    encryptedFile
                )


            // =========================
            // CONVERT TO BITMAP
            // =========================

            val bitmap =
                BitmapFactory.decodeByteArray(
                    imageBytes,
                    0,
                    imageBytes.size
                )


            if (bitmap == null) {

                Toast.makeText(
                    this,
                    "Unable to display document",
                    Toast.LENGTH_LONG
                ).show()

                return
            }


            // =========================
            // DISPLAY IMAGE
            // =========================

            documentImage.setImageBitmap(
                bitmap
            )


        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Unable to decrypt document",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}