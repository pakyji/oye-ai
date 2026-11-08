package net.kuafuai.andee.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Google Live API (Gemini Live) client.
 * REST + WebSocket for real-time multimodal.
 */
class GoogleLiveClient(
    private val apiKey: String,
    private val model: String = "gemini-1.5-flash-latest"
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model"

    /** Generate content (non-streaming) */
    suspend fun generateContent(contents: List<Content>): GenerateResponse = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("contents", contents.map { it.toJson() })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 4096)
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl:generateContent?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $body")
            GenerateResponse.fromJson(body)
        }
    }

    /** Stream generate content */
    suspend fun streamGenerateContent(contents: List<Content>, onChunk: (String) -> Unit) = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("contents", contents.map { it.toJson() })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 4096)
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl:streamGenerateContent?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.body?.string()}")
            val source = response.body?.source() ?: throw Exception("No response body")
            var buffer = ""
            while (true) {
                val line = source.readUtf8Line() ?: break
                buffer += line
                if (line.trim().isEmpty()) {
                    if (buffer.trim().startsWith("data:")) {
                        val data = buffer.trim().substringAfter("data:").trim()
                        if (data != "[DONE]") {
                            onChunk(data)
                        }
                    }
                    buffer = ""
                }
            }
        }
    }

    /** Create WebSocket session for Live API */
    fun createLiveSession(): LiveSession {
        return LiveSession(apiKey, model)
    }

    data class Content(
        val role: String, // "user" | "model"
        val parts: List<Part>
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("role", role)
                put("parts", parts.map { it.toJson() })
            }
        }
    }

    sealed class Part {
        data class Text(val text: String) : Part() {
            fun toJson() = JSONObject().put("text", text)
        }
        data class InlineData(val mimeType: String, val dataBase64: String) : Part() {
            fun toJson() = JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", mimeType)
                    put("data", dataBase64)
                })
            }
        }
    }

    data class GenerateResponse(
        val candidates: List<Candidate>,
        val usageMetadata: UsageMetadata?
    ) {
        companion object {
            fun fromJson(json: String): GenerateResponse {
                val obj = JSONObject(json)
                val candidates = obj.getJSONArray("candidates").map { cand ->
                    val c = cand as JSONObject
                    Candidate(
                        content = Content(
                            role = c.getJSONObject("content").getString("role"),
                            parts = c.getJSONObject("content").getJSONArray("parts").map { p ->
                                val part = p as JSONObject
                                if (part.has("text")) Part.Text(part.getString("text"))
                                else Part.InlineData(part.getJSONObject("inline_data").getString("mime_type"), part.getJSONObject("inline_data").getString("data"))
                            }
                        ),
                        finishReason = c.optString("finishReason", ""),
                        index = c.optInt("index", 0)
                    )
                }.toList()
                GenerateResponse(candidates, null)
            }
        }
    }

    data class Candidate(
        val content: Content,
        val finishReason: String,
        val index: Int
    )

    data class UsageMetadata(
        val promptTokenCount: Int,
        val candidatesTokenCount: Int,
        val totalTokenCount: Int
    )
}

/** Live WebSocket session */
class LiveSession(
    private val apiKey: String,
    private val model: String
) {
    // TODO: Implement WebSocket connection to wss://generativelanguage.googleapis.com/ws/...
    // This requires the bidirectional streaming protocol
}