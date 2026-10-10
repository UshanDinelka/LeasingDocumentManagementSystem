package com.example.securedocumentcapture3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AdminLoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_admin_login
        )

        val usernameEditText =
            findViewById<EditText>(
                R.id.adminUsername
            )

        val passwordEditText =
            findViewById<EditText>(
                R.id.adminPassword
            )

        val loginButton =
            findViewById<Button>(
                R.id.adminLoginButton
            )


        loginButton.setOnClickListener {

            val username =
                usernameEditText.text
                    .toString()
                    .trim()

            val password =
                passwordEditText.text
                    .toString()


            // Simple prototype authentication
            if (
                username == "admin" &&
                password == "Admin@123"
            ) {

                Toast.makeText(
                    this,
                    "Admin login successful",
                    Toast.LENGTH_SHORT
                ).show()


                val intent =
                    Intent(
                        this,
                        SecureDocumentsActivity::class.java
                    )

                intent.putExtra(
                    "ADMIN_MODE",
                    true
                )

                startActivity(intent)

                finish()

            } else {

                Toast.makeText(
                    this,
                    "Invalid admin credentials",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}