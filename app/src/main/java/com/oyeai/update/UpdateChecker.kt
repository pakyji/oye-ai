package com.oyeai.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds a newer OYE build on GitHub and installs it.
 *
 * Every push to main publishes a release tagged `build-<run number>` with `app-release.apk`.
 * CI sets versionCode = 1000 + run number, so a higher versionCode means a newer build.
 * Android still asks the user to confirm each install. Only a build signed with the same key
 * as the installed app can replace it.
 */
object UpdateChecker {

    const val REPO = "pakyji/oye-ai"
    private const val APK_NAME = "app-release.apk"
    private const val BUILD_BASE = 1000
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000
    private val BUILD_TAG = Regex("""build-(\d+)""")

    data class Update(val versionCode: Int, val tag: String, val apkUrl: String)

    /** versionCode for a `build-<n>` tag, or null for any other tag (for example `v0.2.0`). */
    fun versionCodeForTag(tag: String): Int? =
        BUILD_TAG.matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull()?.let { BUILD_BASE + it }

    /** The newest build in a GitHub releases list that is newer than [installedCode]. */
    fun pickNewest(releasesJson: String, installedCode: Int): Update? {
        val releases = JSONArray(releasesJson)
        var best: Update? = null
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft", false)) continue
            val tag = release.optString("tag_name")
            val code = versionCodeForTag(tag) ?: continue
            if (code <= installedCode) continue
            val assets = release.optJSONArray("assets") ?: continue
            var url: String? = null
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                if (asset.optString("name") == APK_NAME) url = asset.optString("browser_download_url")
            }
            if (url.isNullOrBlank()) continue
            if (best == null || code > best.versionCode) best = Update(code, tag, url)
        }
        return best
    }

    /** Asks GitHub for the newest build. Returns null when offline, on error, or when up to date. */
    suspend fun fetchNewest(installedCode: Int): Update? = withIo {
        try {
            val conn = URL("https://api.github.com/repos/$REPO/releases?per_page=10").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "OyeAI")
                if (conn.responseCode !in 200..299) return@withIo null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                pickNewest(body, installedCode)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads the APK to the app cache and returns it. Throws on failure. */
    suspend fun download(context: Context, update: Update): File = withIo {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, "oye-ai-update.apk")
        val partial = File(dir, "oye-ai-update.apk.part")
        val conn = URL(update.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "OyeAI")
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        if (partial.length() < 100_000) {
            partial.delete()
            throw IllegalStateException("Downloaded file is too small")
        }
        if (target.exists()) target.delete()
        partial.renameTo(target)
        target
    }

    /**
     * Opens the Android installer for [apk]. Returns false if the user must first allow
     * installs from OYE; the settings screen is opened for that.
     */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    /** True once every six hours, so GitHub's rate limit is never a problem. */
    fun checkDue(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences("oyeai_update", Context.MODE_PRIVATE)
        return now - prefs.getLong("last_check", 0L) >= CHECK_EVERY_MS
    }

    fun markChecked(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences("oyeai_update", Context.MODE_PRIVATE)
            .edit().putLong("last_check", now).apply()
    }

    private suspend fun <T> withIo(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}
