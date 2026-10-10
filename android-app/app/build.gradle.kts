plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {

    namespace = "com.leasingdocument.app"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }


    defaultConfig {

        applicationId = "com.leasingdocument.app"

        minSdk = 26
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }


    // =========================================================
    // BUILD TYPES
    // =========================================================

    buildTypes {

        debug {

            isMinifyEnabled = false
        }


        release {

            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }


    // =========================================================
    // JAVA COMPATIBILITY
    // =========================================================

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }


    // =========================================================
    // BUILD FEATURES
    // =========================================================

    buildFeatures {

        compose = true

        // Required so RetrofitClient can use BuildConfig.DEBUG
        buildConfig = true
    }
}


dependencies {

    // Bundled on-device Latin OCR only; Camera2 and alteration methods remain unchanged.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Existing CaptureDocument OpenCV checks. Camera uses Android Camera2 directly.
    implementation("org.opencv:opencv:4.10.0")


    // =========================================================
    // ANDROID
    // =========================================================

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )

    implementation(
        libs.androidx.activity.compose
    )


    // =========================================================
    // JETPACK COMPOSE
    // =========================================================

    implementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )

    implementation(
        libs.androidx.compose.material3
    )


    // =========================================================
    // RETROFIT
    // =========================================================

    implementation(
        "com.squareup.retrofit2:retrofit:2.11.0"
    )

    implementation(
        "com.squareup.retrofit2:converter-gson:2.11.0"
    )


    // =========================================================
    // OKHTTP
    // =========================================================

    implementation(
        "com.squareup.okhttp3:okhttp:4.12.0"
    )

    implementation(
        "com.squareup.okhttp3:logging-interceptor:4.12.0"
    )


    // =========================================================
    // TESTING
    // =========================================================

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
}