package com.leasingdocument.app.capture

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local drafts only. Encrypts image AND metadata together, scoped to the agent. */
class EncryptedCaptureStore(context: Context, private val agentId: Long) {
    companion object {
        const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
        private const val MAX_METADATA_BYTES = 64 * 1024
        private const val MAGIC = 0x4C444331 // LDC1
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
    data class Draft(val id: String, val metadata: JSONObject, val jpeg: ByteArray)
    private val directory = File(context.noBackupFilesDir, "capture_drafts/agent_$agentId")
    private val alias = "leasing_capture_agent_$agentId"

    init {
        require(agentId > 0)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create draft storage" }
    }

    private fun file(id: String): File {
        require(UUID.fromString(id).toString() == id) { "Invalid draft ID" }
        return File(directory, "$id.ldc")
    }

    private fun aad(id: String) = "LDC1:AGENT:$agentId:$id".toByteArray(Charsets.UTF_8)

    private fun key(create: Boolean): SecretKey {
        synchronized(EncryptedCaptureStore::class.java) {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = ks.getKey(alias, null)
            if (existing is SecretKey) return existing
            check(create) { "The local encryption key is unavailable" }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build())
                generateKey()
            }
        }
    }

    fun save(jpeg: ByteArray, metadata: JSONObject): String {
        require(jpeg.isNotEmpty() && jpeg.size <= MAX_IMAGE_BYTES)
        require(metadata.getLong("agentId") == agentId)
        require(metadata.getString("mobileSha256") == sha256(jpeg))
        val id = UUID.randomUUID().toString()
        val meta = metadata.toString().toByteArray(Charsets.UTF_8)
        require(meta.size <= MAX_METADATA_BYTES)
        val plain = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(meta.size); out.write(meta)
                out.writeInt(jpeg.size); out.write(jpeg)
            }
            buffer.toByteArray()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        cipher.updateAAD(aad(id))
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val target = AtomicFile(file(id))
        val stream = target.startWrite()
        try {
            // Only ciphertext, IV and a format marker are written to disk.
            val out = DataOutputStream(stream)
            out.writeInt(MAGIC)
            out.writeInt(cipher.iv.size); out.write(cipher.iv)
            out.writeInt(encrypted.size); out.write(encrypted); out.flush()
            target.finishWrite(stream)
        } catch (e: Exception) {
            target.failWrite(stream)
            throw e
        }
        // Verify disk round-trip before reporting success.
        try {
            val verified = load(id)
            verified.jpeg.fill(0)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return id
    }

    fun ids(): List<String> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "ldc" }
        .sortedByDescending { it.lastModified() }
        .map { it.nameWithoutExtension }

    fun load(id: String): Draft {
        val target = file(id)
        require(target.length() <= MAX_IMAGE_BYTES + MAX_METADATA_BYTES + 1024L)
        val plain = DataInputStream(AtomicFile(target).openRead()).use { input ->
            require(input.readInt() == MAGIC) { "Invalid draft format" }
            val ivSize = input.readInt()
            require(ivSize == 12)
            val iv = ByteArray(ivSize).also { input.readFully(it) }
            val length = input.readInt()
            require(length in 16..(MAX_IMAGE_BYTES + MAX_METADATA_BYTES + 24))
            val encrypted = ByteArray(length).also { input.readFully(it) }
            require(input.read() == -1)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, iv))
                updateAAD(aad(id))
                doFinal(encrypted)
            }
        }
        try {
            DataInputStream(ByteArrayInputStream(plain)).use { input ->
                val metaSize = input.readInt()
                require(metaSize in 1..MAX_METADATA_BYTES)
                val metadata = JSONObject(ByteArray(metaSize).also { input.readFully(it) }.toString(Charsets.UTF_8))
                require(metadata.getLong("agentId") == agentId)
                val imageSize = input.readInt()
                require(imageSize in 1..MAX_IMAGE_BYTES)
                val jpeg = ByteArray(imageSize).also { input.readFully(it) }
                require(input.read() == -1)
                require(sha256(jpeg) == metadata.getString("mobileSha256")) { "Draft hash mismatch" }
                return Draft(id, metadata, jpeg)
            }
        } finally { plain.fill(0) }
    }

    fun delete(id: String) = AtomicFile(file(id)).delete()
}
