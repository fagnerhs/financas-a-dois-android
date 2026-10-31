package br.com.financasdocasal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecureDataTest {
    private val password = "correct horse battery staple"
    private val plaintext = "{\"expenses\":[{\"amount\":42.5}],\"note\":\"café\"}"

    @Test fun encryptedBackupRoundTripsAndUsesFreshRandomness() {
        val first = EncryptedBackup.encrypt(plaintext, password)
        val second = EncryptedBackup.encrypt(plaintext, password)
        assertEquals(plaintext, EncryptedBackup.decrypt(first, password))
        assertNotEquals(first, second)
        val envelope = Json.parseToJsonElement(first).jsonObject
        assertEquals(EncryptedBackup.FORMAT, envelope.getValue("format").jsonPrimitive.content)
        assertEquals(EncryptedBackup.VERSION.toString(), envelope.getValue("version").jsonPrimitive.content)
        assertEquals(EncryptedBackup.PBKDF2_ITERATIONS.toString(), envelope.getValue("iterations").jsonPrimitive.content)
    }

    @Test fun wrongPassphraseIsRejectedWithoutDiagnosticDetail() {
        val encrypted = EncryptedBackup.encrypt(plaintext, password)
        val error = try {
            EncryptedBackup.decrypt(encrypted, "another sufficiently long phrase")
            fail("Expected wrong passphrase to fail")
        } catch (e: IllegalArgumentException) {
            e
        }
        assertEquals("Unable to decrypt backup", error.message)
    }

    @Test fun tamperingIsRejected() {
        val encrypted = EncryptedBackup.encrypt(plaintext, password)
        val envelope = Json.parseToJsonElement(encrypted).jsonObject.toMutableMap()
        val ciphertext = envelope.getValue("ciphertext").jsonPrimitive.content
        val replacement = if (ciphertext.first() == 'A') "B" + ciphertext.drop(1) else "A" + ciphertext.drop(1)
        envelope["ciphertext"] = kotlinx.serialization.json.JsonPrimitive(replacement)
        val tampered = Json.encodeToString(kotlinx.serialization.json.JsonObject(envelope))
        try {
            EncryptedBackup.decrypt(tampered, password)
            fail("Expected tampering to fail")
        } catch (e: IllegalArgumentException) {
            assertEquals("Unable to decrypt backup", e.message)
        }
    }

    @Test fun malformedAndUnsupportedEnvelopesAreRejected() {
        assertDecryptRejected("not-json")
        assertDecryptRejected("""{"format":"financas-casal-encrypted-backup","version":99}""")
        assertDecryptRejected("""{"format":"financas-casal-encrypted-backup","version":1,"kdf":"wrong"}""")
    }

    @Test fun legacyPlaintextPassesThroughUnchanged() {
        assertEquals(plaintext, EncryptedBackup.decodeMaybeEncrypted(plaintext, ""))
        assertEquals("{\"format\":\"some-old-format\"}",
            EncryptedBackup.decodeMaybeEncrypted("{\"format\":\"some-old-format\"}", ""))
        assertEquals(plaintext,
            EncryptedBackup.decodeMaybeEncrypted(EncryptedBackup.encrypt(plaintext, password), password))
    }

    @Test fun passwordMinimumIsEnforced() {
        for (tooShort in listOf("", "   ", "short")) {
            try {
                EncryptedBackup.encrypt(plaintext, tooShort)
                fail("Expected short passphrase rejection")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains(EncryptedBackup.MIN_PASSWORD_LENGTH.toString()))
            }
            try {
                EncryptedBackup.decrypt("{}", tooShort)
                fail("Expected short passphrase rejection")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains(EncryptedBackup.MIN_PASSWORD_LENGTH.toString()))
            }
        }
    }

    @Test fun localFieldEnvelopeRecognitionIsJvmTestableWithoutKeystore() {
        assertTrue(LocalFieldCrypto.isEncryptedEnvelope("${LocalFieldCrypto.PREFIX}abc"))
        assertFalse(LocalFieldCrypto.isEncryptedEnvelope("legacy plaintext"))
    }

    private fun assertDecryptRejected(input: String) {
        try {
            EncryptedBackup.decrypt(input, password)
            fail("Expected invalid envelope rejection")
        } catch (e: IllegalArgumentException) {
            assertEquals("Unable to decrypt backup", e.message)
        }
    }
}
