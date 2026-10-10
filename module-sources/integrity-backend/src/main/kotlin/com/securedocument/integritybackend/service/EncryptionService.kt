package com.securedocument.integritybackend.service

import org.springframework.stereotype.Service
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class EncryptionService {

    companion object {
        private const val AES_ALGORITHM = "AES"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12

        private const val KEY_FILE = "secure-storage/aes.key"
    }

    private val secretKey: SecretKey = loadOrCreateKey()

    private fun loadOrCreateKey(): SecretKey {

        val keyFile = File(KEY_FILE)

        // If key already exists, load it
        if (keyFile.exists()) {

            val encodedKey =
                keyFile.readText().trim()

            val decodedKey =
                Base64.getDecoder().decode(encodedKey)

            return SecretKeySpec(
                decodedKey,
                AES_ALGORITHM
            )
        }

        // Otherwise generate a new AES-256 key
        val keyGenerator =
            KeyGenerator.getInstance(AES_ALGORITHM)

        keyGenerator.init(256)

        val newKey =
            keyGenerator.generateKey()

        // Create storage folder if it does not exist
        keyFile.parentFile?.mkdirs()

        // Store key as Base64
        val encodedKey =
            Base64.getEncoder()
                .encodeToString(newKey.encoded)

        keyFile.writeText(encodedKey)

        return newKey
    }

    fun encrypt(
        documentBytes: ByteArray
    ): EncryptedDocument {

        val iv =
            ByteArray(IV_LENGTH)

        SecureRandom().nextBytes(iv)

        val cipher =
            Cipher.getInstance(AES_TRANSFORMATION)

        val gcmParameterSpec =
            GCMParameterSpec(
                GCM_TAG_LENGTH,
                iv
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey,
            gcmParameterSpec
        )

        val encryptedBytes =
            cipher.doFinal(documentBytes)

        return EncryptedDocument(
            encryptedData = encryptedBytes,
            iv = iv
        )
    }

    fun decrypt(
        encryptedData: ByteArray,
        iv: ByteArray
    ): ByteArray {

        val cipher =
            Cipher.getInstance(AES_TRANSFORMATION)

        val gcmParameterSpec =
            GCMParameterSpec(
                GCM_TAG_LENGTH,
                iv
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            gcmParameterSpec
        )

        return cipher.doFinal(
            encryptedData
        )
    }
}

data class EncryptedDocument(
    val encryptedData: ByteArray,
    val iv: ByteArray
)