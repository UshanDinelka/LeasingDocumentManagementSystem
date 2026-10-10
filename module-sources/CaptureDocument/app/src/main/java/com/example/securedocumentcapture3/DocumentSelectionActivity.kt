package com.example.securedocumentcapture3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class DocumentSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_document_selection)

        val documentRadioGroup =
            findViewById<RadioGroup>(R.id.documentRadioGroup)

        val continueButton =
            findViewById<Button>(R.id.continueButton)

        continueButton.setOnClickListener {

            val selectedId =
                documentRadioGroup.checkedRadioButtonId

            val documentType = when (selectedId) {

                R.id.nicRadioButton ->
                    "National_Identity_Card"

                R.id.licenseRadioButton ->
                    "Driving_Licence"

                R.id.salarySlipRadioButton ->
                    "Salary_Slip"

                R.id.bankStatementRadioButton ->
                    "Bank_Statement"

                R.id.utilityBillRadioButton ->
                    "Utility_Bill"

                R.id.certificateRadioButton ->
                    "Other_Certificate"

                else ->
                    "Unknown_Document"
            }

            val intent =
                Intent(
                    this,
                    CaptureActivity::class.java
                )

            // Send selected document type to CaptureActivity
            intent.putExtra(
                "documentType",
                documentType
            )

            startActivity(intent)
        }
    }
}