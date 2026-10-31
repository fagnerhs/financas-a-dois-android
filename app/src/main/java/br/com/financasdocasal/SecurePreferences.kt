package br.com.financasdocasal

import android.content.SharedPreferences

/** SharedPreferences compatibility wrapper: legacy plaintext is readable and rewritten encrypted on save. */
internal fun SharedPreferences.getSecureString(key: String, defaultValue: String = ""): String {
    val stored = getString(key, null) ?: return defaultValue
    return try {
        LocalFieldCrypto.decrypt(stored)
    } catch (_: IllegalArgumentException) {
        defaultValue
    }
}

internal fun SharedPreferences.Editor.putSecureString(key: String, value: String): SharedPreferences.Editor =
    putString(key, LocalFieldCrypto.encrypt(value))
