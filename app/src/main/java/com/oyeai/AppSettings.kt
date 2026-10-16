package com.oyeai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.oyeai.ai.PROVIDERS
import com.oyeai.ai.ProviderSpec
import com.oyeai.ai.providerById
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Plain preferences for normal settings, and encrypted preferences for API keys.
 * Language values: "auto", "it", "en", "ur".
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("oyeai_prefs", Context.MODE_PRIVATE)

    // Opened once per process, only when a key or token is first needed, so building
    // AppSettings (which happens often, sometimes on the main thread) stays cheap.
    // Null when the Android Keystore is broken: keys then read as empty instead of crashing the app.
    private val appContext: Context = context.applicationContext
    private val secure: SharedPreferences? get() = secureStore(appContext)

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

    /** Time between automatic scrolls, in milliseconds. */
    var scrollIntervalMs: Long
        get() = prefs.getLong("scroll_interval_ms", 6_000L)
        set(value) = prefs.edit().putLong("scroll_interval_ms", value).apply()

    /** Longest auto-scroll session, in minutes. */
    var maxSessionMinutes: Int
        get() = prefs.getInt("max_session_minutes", 15)
        set(value) = prefs.edit().putInt("max_session_minutes", value).apply()

    /** Background "Oye" listening, started and stopped from Settings. */
    var listenForOye: Boolean
        get() = prefs.getBoolean("listen_for_oye", false)
        set(value) = prefs.edit().putBoolean("listen_for_oye", value).apply()

    var speakReplies: Boolean
        get() = prefs.getBoolean("speak_replies", true)
        set(value) = prefs.edit().putBoolean("speak_replies", value).apply()

    var githubRepo: String
        get() = prefs.getString("github_repo", "") ?: ""
        set(value) = prefs.edit().putString("github_repo", value.trim()).apply()

    var githubBranch: String
        get() = prefs.getString("github_branch", "main") ?: "main"
        set(value) = prefs.edit().putString("github_branch", value.trim().ifBlank { "main" }).apply()

    /** The GitHub token is kept in the encrypted store only. It is never shown or logged. */
    var githubToken: String
        get() = secure?.getString("github.token", "") ?: ""
        set(value) { secure?.edit()?.putString("github.token", value.trim())?.apply() }

    fun key(providerId: String): String = secure?.getString("key.$providerId", "") ?: ""

    fun model(providerId: String): String {
        val saved = secure?.getString("model.$providerId", "") ?: ""
        if (saved.isNotBlank()) return saved
        return allProviders().firstOrNull { it.id == providerId }?.defaultModel ?: ""
    }

    fun savedModel(providerId: String): String = secure?.getString("model.$providerId", "") ?: ""

    fun saveProvider(providerId: String, apiKey: String, model: String) {
        secure?.edit()
            ?.putString("key.$providerId", apiKey.trim())
            ?.putString("model.$providerId", model.trim())
            ?.apply()
    }

    /** Providers added by the user. Only the URL and model are stored here; keys stay encrypted. */
    fun customProviders(): List<ProviderSpec> = try {
        val array = JSONArray(prefs.getString("custom_providers", "[]") ?: "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ProviderSpec(o.getString("id"), o.getString("name"), o.getString("baseUrl"), o.getString("model"), "")
        }
    } catch (e: org.json.JSONException) {
        emptyList()
    }

    fun addCustomProvider(name: String, baseUrl: String, model: String, apiKey: String): ProviderSpec {
        // Keys are sent as a Bearer token, so only encrypted (https) endpoints are allowed.
        require(baseUrl.trim().startsWith("https://")) { "The URL must start with https://" }
        val spec = ProviderSpec(
            "custom-" + UUID.randomUUID().toString().take(8),
            name.trim(), baseUrl.trim().trimEnd('/'), model.trim(), ""
        )
        val array = JSONArray(prefs.getString("custom_providers", "[]") ?: "[]")
        array.put(
            JSONObject().put("id", spec.id).put("name", spec.name)
                .put("baseUrl", spec.baseUrl).put("model", spec.defaultModel)
        )
        prefs.edit().putString("custom_providers", array.toString()).apply()
        saveProvider(spec.id, apiKey, spec.defaultModel)
        return spec
    }

    fun removeCustomProvider(id: String) {
        val array = JSONArray(prefs.getString("custom_providers", "[]") ?: "[]")
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            if (o.getString("id") != id) kept.put(o)
        }
        prefs.edit().putString("custom_providers", kept.toString()).apply()
        secure?.edit()?.remove("key.$id")?.remove("model.$id")?.apply()
        if (selectedProvider == id) selectedProvider = "pollinations"
    }

    /** Built-in providers first, then the user's own. */
    fun allProviders(): List<ProviderSpec> = PROVIDERS + customProviders()

    /** Providers that have a key, with the selected one first. */
    fun providerOrder(): List<ProviderSpec> {
        val withKey = allProviders().filter { key(it.id).isNotBlank() }
        val selected = withKey.firstOrNull { it.id == selectedProvider }
        return if (selected == null) withKey else listOf(selected) + withKey.filter { it.id != selectedProvider }
    }

    companion object {
        private const val SECURE_FILE = "oyeai_secure"
        private val lock = Any()
        @Volatile private var shared: SharedPreferences? = null
        @Volatile private var failed = false

        /** Opens the encrypted store once. Returns null if the Keystore cannot be used. Never deletes saved keys. */
        private fun secureStore(context: Context): SharedPreferences? {
            shared?.let { return it }
            if (failed) return null
            synchronized(lock) {
                shared?.let { return it }
                return try {
                    createSecure(context).also { shared = it }
                } catch (e: Exception) {
                    failed = true
                    android.util.Log.e("OyeAI", "Secure storage unavailable", e)
                    null
                }
            }
        }

        private fun createSecure(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
            context,
            SECURE_FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
