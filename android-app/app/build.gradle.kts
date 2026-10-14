import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}


// =============================================================
// DOC SECURE BACKEND CONFIGURATION
// =============================================================
//
// Backend URL priority:
//
// 1. Gradle property: DOCSECURE_BASE_URL
// 2. Environment variable: DOCSECURE_BASE_URL
// 3. android-app/local.properties: DOCSECURE_BASE_URL
//
// Example local.properties:
//
// DOCSECURE_BASE_URL=https://192.168.x.x:8443/
//
// IMPORTANT:
// - HTTPS is mandatory.
// - URL must end with "/".
// - No machine-specific IP is hard-coded here.
// - local.properties remains machine-local.
// - TLS hostname/IP verification remains enabled.
//
// =============================================================

val localProperties = Properties().apply {

    val localPropertiesFile =
        rootProject.file("local.properties")

    if (localPropertiesFile.exists()) {

        localPropertiesFile
            .inputStream()
            .use { input ->

                load(input)
            }
    }
}


val docSecureBaseUrl =

    providers
        .gradleProperty(
            "DOCSECURE_BASE_URL"
        )
        .orNull
        ?.trim()
        ?.takeIf {
            it.isNotEmpty()
        }

        ?: providers
            .environmentVariable(
                "DOCSECURE_BASE_URL"
            )
            .orNull
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }

        ?: localProperties
            .getProperty(
                "DOCSECURE_BASE_URL"
            )
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }

        ?: error(
            """
            DOCSECURE_BASE_URL is not configured.

            Add this to android-app/local.properties:

            DOCSECURE_BASE_URL=https://<BACKEND-IP>:8443/

            HTTPS is required and the URL must end with '/'.
            """.trimIndent()
        )


require(
    docSecureBaseUrl.startsWith(
        "https://",
        ignoreCase = true
    )
) {
    "DOCSECURE_BASE_URL must use HTTPS."
}


require(
    docSecureBaseUrl.endsWith("/")
) {
    "DOCSECURE_BASE_URL must end with '/'."
}


fun String.asBuildConfigString(): String =
    "\"${
        replace(
            "\\",
            "\\\\"
        ).replace(
            "\"",
            "\\\""
        )
    }\""


android {

    namespace =
        "com.leasingdocument.app"


    compileSdk {

        version =
            release(36) {

                minorApiLevel = 1
            }
    }


    defaultConfig {

        applicationId =
            "com.leasingdocument.app"

        minSdk = 26

        targetSdk = 36

        versionCode = 1

        versionName = "1.0"


        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"


        buildConfigField(
            "String",
            "DOCSECURE_BASE_URL",
            docSecureBaseUrl
                .asBuildConfigString()
        )
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


        // Required for:
        //
        // BuildConfig.DEBUG
        // BuildConfig.DOCSECURE_BASE_URL

        buildConfig = true
    }
}


dependencies {

    // =========================================================
    // ML KIT OCR
    // =========================================================

    implementation(
        "com.google.mlkit:text-recognition:16.0.1"
    )


    // =========================================================
    // OPENCV
    // =========================================================

    implementation(
        "org.opencv:opencv:4.10.0"
    )


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