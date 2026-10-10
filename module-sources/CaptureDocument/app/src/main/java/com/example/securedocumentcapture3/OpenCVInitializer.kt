
package com.example.securedocumentcapture3

import android.content.Context
import org.opencv.android.OpenCVLoader

object OpenCVInitializer {

    fun initialize(context: Context): Boolean {
        return OpenCVLoader.initLocal()
    }
}

