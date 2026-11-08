package net.kuafuai.andee.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Custom voice pipeline with WebSocket framing.
 * Supports Volcengine (ByteDance) and Google Live API.
 */
class VoicePipeline(
    private val context: Context,
    private val config: VoiceConfig
) : CoroutineScope {

    override val coroutineContext: Job = SupervisorJob() + Dispatchers.IO

    private val audioRecord: AudioRecord by lazy { createAudioRecord() }
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val eventChannel = Channel<VoiceEvent>(Channel.UNLIMITED)
    private var isRecording = false
    private var sessionId = ""

    val events: ReceiveChannel<VoiceEvent> = eventChannel

    sealed interface VoiceEvent {
        data class OnAudioLevel(val level: Float) : VoiceEvent
        data class OnPartialTranscript(val text: String) : VoiceEvent
        data class OnFinalTranscript(val text: String, val isEndpoint: Boolean) : VoiceEvent
        data class OnError(val message: String) : VoiceEvent
        data class OnConnected(val sessionId: String) : VoiceEvent
        data class OnDisconnected() : VoiceEvent
        data class OnTTSStart() : VoiceEvent
        data class OnTTSAudio(val audio: ByteArray) : VoiceEvent
        data class OnTTSEnd() : VoiceEvent
    }

    /** Start voice session */
    fun startSession() {
        launch { connectWebSocket() }
        launch { startAudioCapture() }
    }

    /** Stop voice session */
    fun stopSession() {
        isRecording = false
        audioRecord.stop()
        webSocket?.close(1000, "Client closing")
        coroutineContext.cancel()
    }

    /** Send text for TTS */
    fun speak(text: String) {
        launch { sendTTS(text) }
    }

    /** Send raw audio bytes */
    fun sendAudio(audio: ByteArray) {
        audioChannel.trySend(audio)
    }

    private fun createAudioRecord(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
    }

    private suspend fun connectWebSocket() {
        val url = when (config.provider) {
            VoiceProvider.VOLCENGINE -> config.volcengineWsUrl
            VoiceProvider.GOOGLE_LIVE -> config.googleLiveWsUrl
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("X-Session-Id", sessionId)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                sessionId = response.headers.values("X-Session-Id").firstOrNull() ?: java.util.UUID.randomUUID().toString()
                eventChannel.trySend(VoiceEvent.OnConnected(sessionId))
                // Start sending audio
                launch { sendAudioLoop(ws) }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                parseMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                handleBinaryMessage(bytes.toByteArray())
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                eventChannel.trySend(VoiceEvent.OnDisconnected())
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                eventChannel.trySend(VoiceEvent.OnError(t.message ?: "WebSocket error"))
            }
        })
    }

    private fun startAudioCapture() {
        isRecording = true
        audioRecord.startRecording()

        val buffer = ByteArray(config.chunkSize * 2) // 16-bit = 2 bytes per sample
        while (isRecording) {
            val read = audioRecord.read(buffer, 0, buffer.size)
            if (read > 0) {
                val chunk = buffer.copyOfRange(0, read)
                audioChannel.trySend(chunk)
            }
        }
    }

    private suspend fun sendAudioLoop(ws: WebSocket) {
        for (chunk in audioChannel) {
            if (!isRecording) break
            // Frame: [4-byte length][audio bytes]
            val frame = ByteBuffer.allocate(4 + chunk.size).apply {
                putInt(chunk.size)
                put(chunk)
            }.array()
            ws.send(okio.ByteString.of(frame))
        }
    }

    private fun parseMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "")
            when (type) {
                "partial_transcript" -> eventChannel.trySend(VoiceEvent.OnPartialTranscript(json.getString("text")))
                "final_transcript" -> eventChannel.trySend(VoiceEvent.OnFinalTranscript(json.getString("text"), json.optBoolean("endpoint", false)))
                "tts_start" -> eventChannel.trySend(VoiceEvent.OnTTSStart())
                "tts_audio" -> {
                    val audioB64 = json.getString("audio")
                    val audio = android.util.Base64.decode(audioB64, android.util.Base64.NO_WRAP)
                    eventChannel.trySend(VoiceEvent.OnTTSAudio(audio))
                }
                "tts_end" -> eventChannel.trySend(VoiceEvent.OnTTSEnd())
                "error" -> eventChannel.trySend(VoiceEvent.OnError(json.getString("message")))
            }
        } catch (e: Exception) {
            eventChannel.trySend(VoiceEvent.OnError("Parse error: ${e.message}"))
        }
    }

    private fun handleBinaryMessage(audio: ByteArray) {
        // TTS audio chunks come as binary
        eventChannel.trySend(VoiceEvent.OnTTSAudio(audio))
    }

    private suspend fun sendTTS(text: String) {
        val json = JSONObject().apply {
            put("type", "tts")
            put("text", text)
            put("voice", config.ttsVoice)
        }
        webSocket?.send(json.toString())
    }

    /** Shutdown */
    fun shutdown() {
        stopSession()
        coroutineContext.cancel()
    }

    data class VoiceConfig(
        val provider: VoiceProvider = VoiceProvider.VOLCENGINE,
        val apiKey: String = "",
        val volcengineWsUrl: String = "wss://openspeech.bytedance.com/api/v2/ws",
        val googleLiveWsUrl: String = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService/StreamGenerateContent",
        val sampleRate: Int = 16000,
        val chunkSize: Int = 3200, // 100ms at 16kHz
        val ttsVoice: String = "default"
    )

    enum class VoiceProvider {
        VOLCENGINE, GOOGLE_LIVE
    }
}