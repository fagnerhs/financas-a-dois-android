package br.com.financasdocasal

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Encrypts individual local fields with an app-scoped, non-exportable AndroidKeyStore key. */
object LocalFieldCrypto {
    const val PREFIX = "lfc:v1:"
    private const val KEY_ALIAS = "br.com.financasdocasal.local-fields.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12

    /** Exposes only structural recognition so JVM tests do not need a fake AndroidKeyStore. */
    fun isEncryptedEnvelope(value: String): Boolean = value.startsWith(PREFIX)

    /** Plaintext values are preserved; already enveloped values are not encrypted twice. */
    fun encrypt(value: String): String {
        if (isEncryptedEnvelope(value)) return value
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(PREFIX.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    /** Legacy plaintext passes through unchanged. A malformed v1 envelope fails closed. */
    fun decrypt(value: String): String {
        if (!isEncryptedEnvelope(value)) return value
        try {
            val payload = Base64.getUrlDecoder().decode(value.removePrefix(PREFIX))
            require(payload.size > IV_BYTES) { "Invalid encrypted value" }
            val iv = payload.copyOfRange(0, IV_BYTES)
            val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(PREFIX.toByteArray(Charsets.UTF_8))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalArgumentException("Unable to decrypt local value")
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

/** Password-protected portable backup format. Passphrases are never retained by this object. */
object EncryptedBackup {
    const val FORMAT = "financas-casal-encrypted-backup"
    const val VERSION = 1
    /** Fixed PBKDF2 work factor for this format; changing it requires a new compatible version. */
    const val PBKDF2_ITERATIONS = 310_000
    const val MIN_PASSWORD_LENGTH = 12
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private val random = SecureRandom()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    @Serializable
    private data class Envelope(
        val format: String,
        val version: Int,
        val kdf: String,
        val iterations: Int,
        val cipher: String,
        val salt: String,
        val iv: String,
        val ciphertext: String
    )

    fun encrypt(plainText: String, password: String): String {
        validatePassword(password)
        try {
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad(VERSION, PBKDF2_ITERATIONS))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            return json.encodeToString(
                Envelope(FORMAT, VERSION, KDF, PBKDF2_ITERATIONS, CIPHER,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(salt),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(iv),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted))
            )
        } catch (_: Exception) {
            throw IllegalArgumentException("Unable to encrypt backup")
        }
    }

    fun decrypt(input: String, password: String): String {
        validatePassword(password)
        try {
            val envelope = json.decodeFromString<Envelope>(input)
            require(envelope.format == FORMAT && envelope.version == VERSION)
            require(envelope.kdf == KDF && envelope.cipher == CIPHER)
            require(envelope.iterations == PBKDF2_ITERATIONS)
            val salt = Base64.getUrlDecoder().decode(envelope.salt)
            val iv = Base64.getUrlDecoder().decode(envelope.iv)
            val ciphertext = Base64.getUrlDecoder().decode(envelope.ciphertext)
            require(salt.size == SALT_BYTES && iv.size == IV_BYTES && ciphertext.size >= 16)
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad(envelope.version, envelope.iterations))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            // Do not reveal whether parsing, authentication, or the passphrase caused the failure.
            throw IllegalArgumentException("Unable to decrypt backup")
        }
    }

    /** Returns ordinary legacy JSON verbatim; any recognized encrypted-format marker is strict. */
    fun decodeMaybeEncrypted(input: String, password: String): String {
        if (!looksLikeEncryptedEnvelope(input)) return input
        return decrypt(input, password)
    }

    private fun looksLikeEncryptedEnvelope(input: String): Boolean = try {
        Json.parseToJsonElement(input).jsonObject["format"]?.jsonPrimitive?.content == FORMAT
    } catch (_: Exception) {
        false
    }

    private fun validatePassword(password: String) {
        require(password.isNotBlank() && password.length >= MIN_PASSWORD_LENGTH) {
            "Passphrase must contain at least $MIN_PASSWORD_LENGTH characters"
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun aad(version: Int, iterations: Int): ByteArray =
        "$FORMAT|$version|$KDF|$iterations|$CIPHER".toByteArray(Charsets.UTF_8)
}
