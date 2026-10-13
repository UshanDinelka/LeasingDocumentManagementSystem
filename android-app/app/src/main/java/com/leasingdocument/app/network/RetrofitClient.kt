package com.leasingdocument.app.network

import com.leasingdocument.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object RetrofitClient {

    // =========================================================
    // HTTPS BACKEND
    // =========================================================
    //
    // Spring Boot HTTPS server:
    // https://localhost:8443/
    //
    // Physical Android device / local network:
    // https://192.168.8.101:8443/
    //
    // Cleartext HTTP is disabled in AndroidManifest.xml and
    // network_security_config.xml.
    //
    // The local development certificate is trusted through:
    // res/raw/leasing_backend.crt
    //
    // =========================================================

    private const val BASE_URL =
        "https://192.168.8.101:8443/"


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
                BASE_URL
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