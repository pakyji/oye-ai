package com.oyeai.github

import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Creates or updates one file in a GitHub repository, as one commit.
 * Only the Settings screen calls this, after the user confirms. Voice commands never push.
 */
object GitHub {

    class Result(val ok: Boolean, val message: String)

    private val REPO = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")

    fun pushFile(token: String, repo: String, branch: String, path: String, content: String, message: String): Result =
        try {
            doPush(token, repo, branch, path, content, message)
        } catch (e: IOException) {
            Result(false, "Network error (${e.javaClass.simpleName}). Check the internet and try again.")
        } catch (e: Exception) {
            Result(false, "Could not read the GitHub response (${e.javaClass.simpleName}).")
        }

    private fun doPush(token: String, repo: String, branch: String, path: String, content: String, message: String): Result {
        if (token.isBlank()) return Result(false, "Save the GitHub token first.")
        if (!REPO.matches(repo)) return Result(false, "Repo must look like owner/name.")
        val parts = path.trim().split("/")
        if (parts.any { it.isEmpty() || it == ".." }) return Result(false, "The file path is not valid.")
        val base = "https://api.github.com/repos/$repo/contents/" + parts.joinToString("/") { encode(it) }

        // An existing file needs its current sha to be updated.
        val (code, body) = request("GET", "$base?ref=${encode(branch)}", token, null)
        val sha = when (code) {
            in 200..299 -> JSONObject(body).optString("sha").ifBlank { null }
            404 -> null
            else -> return Result(false, "GitHub error $code: ${errorText(body)}")
        }

        val payload = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("branch", branch)
        if (sha != null) payload.put("sha", sha)

        val (putCode, putBody) = request("PUT", base, token, payload.toString())
        return if (putCode in 200..299) {
            val commit = JSONObject(putBody).optJSONObject("commit")?.optString("sha").orEmpty().take(7)
            Result(true, "Pushed to $branch. Commit $commit")
        } else {
            Result(false, "GitHub error $putCode: ${errorText(putBody)}")
        }
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun errorText(body: String): String = try {
        JSONObject(body).optString("message", "unknown error")
    } catch (e: Exception) {
        "unknown error"
    }

    private fun request(method: String, url: String, token: String, body: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("User-Agent", "OyeAI")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to text
        } finally {
            conn.disconnect()
        }
    }
}
