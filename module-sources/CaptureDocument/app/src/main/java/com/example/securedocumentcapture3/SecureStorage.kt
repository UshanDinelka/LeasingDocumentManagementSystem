package com.example.securedocumentcapture3

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorage(private val context: Context) {

    companion object {

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

        private const val KEY_ALIAS =
            "SecureDocumentEncryptionKey"

        private const val TRANSFORMATION =
            "AES/GCM/NoPadding"

        private const val IV_SIZE = 12

        private const val TAG_SIZE = 128
    }

    private fun getOrCreateKey(): SecretKey {

        val keyStore =
            KeyStore.getInstance(
                KEYSTORE_PROVIDER
            ).apply {
                load(null)
            }

        if (keyStore.containsAlias(KEY_ALIAS)) {

            val entry =
                keyStore.getEntry(
                    KEY_ALIAS,
                    null
                ) as KeyStore.SecretKeyEntry

            return entry.secretKey
        }

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )

        val keySpec =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                        KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .build()

        keyGenerator.init(keySpec)

        return keyGenerator.generateKey()
    }

    fun saveEncryptedImage(
        imageBytes: ByteArray,
        fileName: String
    ): File {

        // App-private directory
        val secureDirectory =
            File(
                context.filesDir,
                "secure_documents"
            )

        if (!secureDirectory.exists()) {
            secureDirectory.mkdirs()
        }

        val encryptedFile =
            File(
                secureDirectory,
                "$fileName.enc"
            )

        val secretKey =
            getOrCreateKey()

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey
        )

        val iv =
            cipher.iv

        FileOutputStream(
            encryptedFile
        ).use { outputStream ->

            // Store IV at the beginning of the file
            outputStream.write(iv)

            val encryptedData =
                cipher.doFinal(imageBytes)

            outputStream.write(encryptedData)
        }

        return encryptedFile
    }

    fun decryptImage(
        encryptedFile: File
    ): ByteArray {

        val secretKey =
            getOrCreateKey()

        FileInputStream(
            encryptedFile
        ).use { inputStream ->

            val iv =
                ByteArray(IV_SIZE)

            inputStream.read(iv)

            val encryptedData =
                inputStream.readBytes()

            val cipher =
                Cipher.getInstance(
                    TRANSFORMATION
                )

            val gcmSpec =
                GCMParameterSpec(
                    TAG_SIZE,
                    iv
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                gcmSpec
            )

            return cipher.doFinal(
                encryptedData
            )
        }
    }

    fun saveDocumentMetadata(
        fileName: String,
        documentType: String,
        captureTime: Long
    ) {

        val secureDirectory =
            File(
                context.filesDir,
                "secure_documents"
            )

        if (!secureDirectory.exists()) {
            secureDirectory.mkdirs()
        }

        val metadataFile =
            File(
                secureDirectory,
                "$fileName.info"
            )

        metadataFile.writeText(
            "documentType=$documentType\n" +
                    "captureTime=$captureTime"
        )
    }
}