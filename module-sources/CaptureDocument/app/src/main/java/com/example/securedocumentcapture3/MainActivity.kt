package com.example.securedocumentcapture3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )


        // =========================
        // CAPTURE DOCUMENT
        // =========================

        val captureDocumentButton =
            findViewById<Button>(
                R.id.captureDocumentButton
            )


        captureDocumentButton.setOnClickListener {

            val intent =
                Intent(
                    this,
                    DocumentSelectionActivity::class.java
                )

            startActivity(intent)
        }


        // =========================
        // ADMIN LOGIN
        // =========================

        val adminLoginButton =
            findViewById<Button>(
                R.id.adminLoginButton
            )


        adminLoginButton.setOnClickListener {

            val intent =
                Intent(
                    this,
                    AdminLoginActivity::class.java
                )

            startActivity(intent)
        }
    }
}