package com.example.securedocumentcapture3

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecureDocumentsActivity : AppCompatActivity() {

    private lateinit var documentListView: ListView
    private lateinit var storageInfoText: TextView
    private lateinit var backButton: Button

    // True when opened by Admin
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_secure_documents
        )


        // =========================
        // GET ADMIN STATUS
        // =========================

        isAdmin =
            intent.getBooleanExtra(
                "ADMIN_MODE",
                false
            )


        // =========================
        // FIND VIEWS
        // =========================

        documentListView =
            findViewById(
                R.id.documentListView
            )

        storageInfoText =
            findViewById(
                R.id.storageInfoText
            )

        backButton =
            findViewById(
                R.id.backButton
            )


        // =========================
        // BACK BUTTON
        // =========================

        backButton.setOnClickListener {

            finish()
        }


        // =========================
        // LOAD DOCUMENTS
        // =========================

        loadEncryptedDocuments()
    }


    // =====================================================
    // LOAD ENCRYPTED DOCUMENTS
    // =====================================================

    private fun loadEncryptedDocuments() {

        val secureDirectory =
            File(
                filesDir,
                "secure_documents"
            )


        if (!secureDirectory.exists()) {

            storageInfoText.text =
                "No encrypted documents found."

            return
        }


        // =========================
        // GET ENCRYPTED FILES
        // =========================

        val encryptedFiles =
            secureDirectory
                .listFiles()
                ?.filter { file ->

                    file.isFile &&
                            file.name.endsWith(".enc")
                }
                ?.sortedByDescending {

                    it.lastModified()
                }
                ?: emptyList()


        // =========================
        // NO DOCUMENTS
        // =========================

        if (encryptedFiles.isEmpty()) {

            storageInfoText.text =
                "No encrypted documents found."

            return
        }


        // =========================
        // DOCUMENT COUNT
        // =========================

        storageInfoText.text =
            if (isAdmin) {

                "${encryptedFiles.size} encrypted document(s) - Admin Access"

            } else {

                "${encryptedFiles.size} encrypted document(s) - View Only"
            }


        // =========================
        // CREATE DISPLAY LIST
        // =========================

        val documentList =
            encryptedFiles.map { encryptedFile ->

                getDocumentDisplay(
                    encryptedFile
                )
            }


        // =========================
        // DISPLAY LIST
        // =========================

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                documentList
            )


        documentListView.adapter =
            adapter


        // =========================
        // DOCUMENT CLICK
        // =========================

        documentListView.setOnItemClickListener {

                _,
                _,
                position,
                _ ->

            val selectedFile =
                encryptedFiles[position]


            if (isAdmin) {

                // Admin can open document
                val intent =
                    Intent(
                        this,
                        DocumentViewerActivity::class.java
                    )


                intent.putExtra(
                    "ENCRYPTED_FILE",
                    selectedFile.absolutePath
                )


                startActivity(intent)

            } else {

                // Agent cannot view document
                Toast.makeText(
                    this,
                    "Admin access required to view documents",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    // =====================================================
    // GET DOCUMENT DISPLAY INFORMATION
    // =====================================================

    private fun getDocumentDisplay(
        encryptedFile: File
    ): String {


        // Example:
        //
        // National Identity Card_1756301234567.enc

        val fileName =
            encryptedFile.name.removeSuffix(
                ".enc"
            )


        // =========================
        // FIND LAST UNDERSCORE
        // =========================

        val lastUnderscore =
            fileName.lastIndexOf("_")


        // =========================
        // GET DOCUMENT TYPE
        // =========================

        val documentType =

            if (lastUnderscore > 0) {

                fileName.substring(
                    0,
                    lastUnderscore
                )

            } else {

                "Unknown Document"
            }


        // =========================
        // GET TIMESTAMP
        // =========================

        val timeString =

            if (lastUnderscore > 0) {

                fileName.substring(
                    lastUnderscore + 1
                )

            } else {

                ""
            }


        // =========================
        // CONVERT TIMESTAMP
        // =========================

        val captureTime =

            timeString.toLongOrNull()
                ?: encryptedFile.lastModified()


        // =========================
        // FORMAT DOCUMENT NAME
        // =========================

        val displayName =
            documentType.replace(
                "_",
                " "
            )


        // =========================
        // FORMAT DATE
        // =========================

        val dateFormat =
            SimpleDateFormat(
                "dd MMMM yyyy, hh:mm a",
                Locale.getDefault()
            )


        val formattedDate =
            dateFormat.format(
                Date(captureTime)
            )


        // =========================
        // FINAL DISPLAY
        // =========================

        return """
            🔐 $displayName
            Captured: $formattedDate
            Status: Encrypted
        """.trimIndent()
    }
}