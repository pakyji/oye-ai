package com.oyeai.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal client for OpenAI-compatible chat completion endpoints. Call from a background thread. */
object OpenAiCompatClient {

    fun chat(
        spec: ProviderSpec,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>
    ): String {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.4)
            .put("messages", JSONArray().apply {
                messages.forEach { (role, content) ->
                    put(JSONObject().put("role", role).put("content", content))
                }
            })

        val url = URL(spec.baseUrl.trimEnd('/') + "/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code: ${text.take(200)}")
            }
            return JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } finally {
            conn.disconnect()
        }
    }
}
