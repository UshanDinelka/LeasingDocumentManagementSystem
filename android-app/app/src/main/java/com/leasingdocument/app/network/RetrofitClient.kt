package com.leasingdocument.app.network

import com.leasingdocument.app.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object RetrofitClient {

    // =========================================================
    // CONFIGURABLE HTTPS BACKEND
    // =========================================================
    //
    // Source:
    // BuildConfig.DOCSECURE_BASE_URL
    //
    // Configure without editing Kotlin:
    //
    // Option 1 - android-app/local.properties
    // DOCSECURE_BASE_URL=https://<BACKEND-IP>:8443/
    //
    // Option 2 - environment variable
    // DOCSECURE_BASE_URL=https://<BACKEND-IP>:8443/
    //
    // Security:
    // - HTTPS is mandatory.
    // - Cleartext HTTP remains blocked by Android configuration.
    // - The URL must include a trailing slash for Retrofit.
    //
    // TLS certificate trust is configured separately through
    // network_security_config.xml and the bundled DOC SECURE
    // Development CA certificate.
    //
    // =========================================================

    private val baseUrl by lazy {

        val configured =
            BuildConfig
                .DOCSECURE_BASE_URL
                .trim()

        require(
            configured.isNotBlank()
        ) {
            "DOCSECURE_BASE_URL is not configured."
        }

        val url =
            configured.toHttpUrl()

        require(
            url.isHttps
        ) {
            "DOCSECURE_BASE_URL must use HTTPS."
        }

        require(
            configured.endsWith("/")
        ) {
            "DOCSECURE_BASE_URL must end with '/'."
        }

        configured
    }


    // =========================================================
    // AUTHORIZATION INTERCEPTOR
    // =========================================================

    private val authInterceptor =
        Interceptor { chain ->

            val originalRequest =
                chain.request()

            val requestBuilder =
                originalRequest
                    .newBuilder()

            AuthSession.token
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let { token ->

                    requestBuilder.header(
                        "Authorization",
                        "Bearer $token"
                    )
                }

            chain.proceed(
                requestBuilder.build()
            )
        }


    // =========================================================
    // LOGGING
    // =========================================================
    //
    // Logging is enabled only for DEBUG builds.
    //
    // Authorization header is redacted so JWT tokens are not
    // exposed in Logcat.
    //
    // BASIC logging records request method/status information
    // without logging sensitive request/response bodies.
    //
    // =========================================================

    private val loggingInterceptor =
        HttpLoggingInterceptor().apply {

            redactHeader(
                "Authorization"
            )

            level =
                HttpLoggingInterceptor
                    .Level
                    .BASIC
        }


    // =========================================================
    // OKHTTP CLIENT
    // =========================================================

    private val client:
            OkHttpClient by lazy {

        OkHttpClient
            .Builder()

            .addInterceptor(
                authInterceptor
            )

            .readTimeout(
                240,
                TimeUnit.SECONDS
            )

            .connectTimeout(
                30,
                TimeUnit.SECONDS
            )

            .writeTimeout(
                240,
                TimeUnit.SECONDS
            )

            .callTimeout(
                260,
                TimeUnit.SECONDS
            )

            .apply {

                if (BuildConfig.DEBUG) {

                    addInterceptor(
                        loggingInterceptor
                    )
                }
            }

            .build()
    }


    // =========================================================
    // RETROFIT API SERVICE
    // =========================================================

    val apiService:
            ApiService by lazy {

        Retrofit
            .Builder()

            .baseUrl(
                baseUrl
            )

            .client(
                client
            )

            .addConverterFactory(
                GsonConverterFactory
                    .create()
            )

            .build()

            .create(
                ApiService::class.java
            )
    }
}