package com.casureco2.outages.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStorage {

    private const val ENCRYPTED_PREFS = "secure_prefs"
    private const val PLAIN_PREFS = "plain_prefs"

    private lateinit var encrypted: EncryptedSharedPreferences
    private lateinit var plain: SharedPreferences

    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        encrypted = EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences

        plain = context.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE)
    }

    // Encrypted
    var openCodeApiKey: String?
        get() = encrypted.getString("OPENCODE_API_KEY", null)
        set(value) = encrypted.edit().putString("OPENCODE_API_KEY", value).apply()

    var githubToken: String?
        get() = encrypted.getString("GITHUB_TOKEN", null)
        set(value) = encrypted.edit().putString("GITHUB_TOKEN", value).apply()

    // Plain
    var githubOwner: String?
        get() = plain.getString("GITHUB_OWNER", null)
        set(value) = plain.edit().putString("GITHUB_OWNER", value).apply()

    var githubRepo: String?
        get() = plain.getString("GITHUB_REPO", null)
        set(value) = plain.edit().putString("GITHUB_REPO", value).apply()

    fun clearAll() {
        encrypted.edit().clear().apply()
        plain.edit().clear().apply()
    }
}
