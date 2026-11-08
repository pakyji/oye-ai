package net.kuafuai.andee.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Volcengine (ByteDance) Speech API client.
 * Supports ASR (speech-to-text) and TTS (text-to-speech) via WebSocket.
 */
class VolcengineClient(
    private val appId: String,
    private val accessToken: String,
    private val cluster: String = "volcengine_streaming_common"
) {

    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private val wsUrl = "wss://openspeech.bytedance.com/api/v2/ws"

    /** Create ASR WebSocket session */
    fun createAsrSession(listener: AsrListener): AsrSession {
        return AsrSession(listener)
    }

    /** Create TTS WebSocket session */
    fun createTtsSession(listener: TtsListener): TtsSession {
        return TtsSession(listener)
    }

    /** ASR session */
    inner class AsrSession(private val listener: AsrListener) {

        private var webSocket: WebSocket? = null
        private var sessionId = ""
        private var sequence = 0

        fun connect() {
            val request = Request.Builder()
                .url("$wsUrl?appid=$appId&token=$accessToken&cluster=$cluster")
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                    // Send start message
                    val startMsg = JSONObject().apply {
                        put("type", "start")
                        put("params", JSONObject().apply {
                            put("format", "pcm")
                            put("sample_rate", 16000)
                            put("enable_itn", true)
                            put("enable_punctuation", true)
                        })
                    }
                    ws.send(startMsg.toString())
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    parseAsrMessage(text)
                }

                override fun onMessage(ws: WebSocket, bytes: ByteString) {
                    // Binary audio data not expected for ASR
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    listener.onClosed()
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    listener.onError(t.message ?: "WebSocket error")
                }
            })
        }

        fun sendAudio(audio: ByteArray) {
            webSocket?.send(ByteString.of(audio))
        }

        fun finish() {
            val finishMsg = JSONObject().put("type", "finish")
            webSocket?.send(finishMsg.toString())
        }

        fun close() {
            webSocket?.close(1000, "Client closing")
        }

        private fun parseAsrMessage(text: String) {
            try {
                val json = JSONObject(text)
                val type = json.optString("type", "")
                when (type) {
                    "started" -> {
                        sessionId = json.optString("session_id", "")
                        listener.onStarted(sessionId)
                    }
                    "partial_result" -> {
                        val text = json.getJSONObject("result").getString("text")
                        val isFinal = json.getJSONObject("result").optBoolean("is_final", false)
                        listener.onPartialResult(text, isFinal)
                    }
                    "final_result" -> {
                        val text = json.getJSONObject("result").getString("text")
                        listener.onFinalResult(text)
                    }
                    "error" -> {
                        listener.onError(json.getString("message"))
                    }
                }
            } catch (e: Exception) {
                listener.onError("Parse error: ${e.message}")
            }
        }
    }

    /** TTS session */
    inner class TtsSession(private val listener: TtsListener) {

        private var webSocket: WebSocket? = null

        fun connect() {
            val request = Request.Builder()
                .url("$wsUrl?appid=$appId&token=$accessToken&cluster=$cluster")
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                    listener.onConnected()
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    parseTtsMessage(text)
                }

                override fun onMessage(ws: WebSocket, bytes: ByteString) {
                    // Audio chunk
                    listener.onAudio(bytes.toByteArray())
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    listener.onClosed()
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    listener.onError(t.message ?: "WebSocket error")
                }
            })
        }

        fun speak(text: String, voice: String = "zh_female_shuangkuaisi_moon_bigtts") {
            val msg = JSONObject().apply {
                put("type", "tts")
                put("params", JSONObject().apply {
                    put("text", text)
                    put("voice", voice)
                    put("format", "pcm")
                    put("sample_rate", 16000)
                })
            }
            webSocket?.send(msg.toString())
        }

        fun close() {
            webSocket?.close(1000, "Client closing")
        }

        private fun parseTtsMessage(text: String) {
            try {
                val json = JSONObject(text)
                val type = json.optString("type", "")
                when (type) {
                    "tts_started" -> listener.onStarted()
                    "tts_finished" -> listener.onFinished()
                    "error" -> listener.onError(json.getString("message"))
                }
            } catch (e: Exception) {
                listener.onError("Parse error: ${e.message}")
            }
        }
    }

    interface AsrListener {
        fun onStarted(sessionId: String)
        fun onPartialResult(text: String, isFinal: Boolean)
        fun onFinalResult(text: String)
        fun onError(message: String)
        fun onClosed()
    }

    interface TtsListener {
        fun onConnected()
        fun onStarted()
        fun onAudio(audio: ByteArray)
        fun onFinished()
        fun onError(message: String)
        fun onClosed()
    }
}