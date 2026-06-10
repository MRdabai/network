package com.weaknet.simulator.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class AuthStorage(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "auth_accounts",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(accounts: List<AuthAccount>) {
        val json = accounts.joinToString(SEPARATOR) { encodeAccount(it) }
        prefs.edit().putString(KEY_ACCOUNTS, json).apply()
    }

    fun load(): List<AuthAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR).mapNotNull { decodeAccount(it) }
    }

    private fun encodeAccount(a: AuthAccount): String {
        return "${a.id}|${a.issuer}|${a.account}|${a.secret}|${a.digits}|${a.period}"
    }

    private fun decodeAccount(s: String): AuthAccount? {
        val parts = s.split("|")
        if (parts.size < 6) return null
        return try {
            AuthAccount(
                id = parts[0],
                issuer = parts[1],
                account = parts[2],
                secret = parts[3],
                digits = parts[4].toIntOrNull() ?: 6,
                period = parts[5].toIntOrNull() ?: 30
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts_data"
        private const val SEPARATOR = "\n"
    }
}
