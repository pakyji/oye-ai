package com.oyeai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.oyeai.ai.PROVIDERS
import com.oyeai.ai.ProviderSpec
import com.oyeai.ai.providerById

/**
 * Plain preferences for normal settings, and encrypted preferences for API keys.
 * Language values: "auto", "it", "en", "ur".
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("oyeai_prefs", Context.MODE_PRIVATE)

    private val secure: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "oyeai_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var language: String
        get() = prefs.getString("language", "auto") ?: "auto"
        set(value) = prefs.edit().putString("language", value).apply()

    var selectedProvider: String
        get() = prefs.getString("selected_provider", "pollinations") ?: "pollinations"
        set(value) = prefs.edit().putString("selected_provider", value).apply()

    var runInBackground: Boolean
        get() = prefs.getBoolean("run_in_background", false)
        set(value) = prefs.edit().putBoolean("run_in_background", value).apply()

    var startOnBoot: Boolean
        get() = prefs.getBoolean("start_on_boot", false)
        set(value) = prefs.edit().putBoolean("start_on_boot", value).apply()

    /** Background "Oye" listening, started and stopped from Settings. */
    var listenForOye: Boolean
        get() = prefs.getBoolean("listen_for_oye", false)
        set(value) = prefs.edit().putBoolean("listen_for_oye", value).apply()

    var speakReplies: Boolean
        get() = prefs.getBoolean("speak_replies", true)
        set(value) = prefs.edit().putBoolean("speak_replies", value).apply()

    fun key(providerId: String): String = secure.getString("key.$providerId", "") ?: ""

    fun model(providerId: String): String {
        val saved = secure.getString("model.$providerId", "") ?: ""
        if (saved.isNotBlank()) return saved
        return providerById(providerId)?.defaultModel ?: ""
    }

    fun savedModel(providerId: String): String = secure.getString("model.$providerId", "") ?: ""

    fun saveProvider(providerId: String, apiKey: String, model: String) {
        secure.edit()
            .putString("key.$providerId", apiKey.trim())
            .putString("model.$providerId", model.trim())
            .apply()
    }

    /** Providers that have a key, with the selected one first. */
    fun providerOrder(): List<ProviderSpec> {
        val withKey = PROVIDERS.filter { key(it.id).isNotBlank() }
        val selected = withKey.firstOrNull { it.id == selectedProvider }
        return if (selected == null) withKey else listOf(selected) + withKey.filter { it.id != selectedProvider }
    }
}
