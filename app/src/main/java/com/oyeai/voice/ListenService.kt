package com.oyeai.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.oyeai.AppSettings
import com.oyeai.MainActivity
import com.oyeai.R
import com.oyeai.ai.Reply
import com.oyeai.control.PhoneCommandParser
import com.oyeai.control.PhoneCommandRunner
import com.oyeai.ui.ChatViewModel
import com.oyeai.voice.WakeWord.Match
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Listens for "Oye" in the background, as a foreground service with the microphone type.
 * Speech is recognised one sentence at a time with the system recognizer. Only sentences that
 * start with "Oye" are handled. A bare "Oye" gets a short answer, then the next sentence is the command.
 * Listening pauses while Oye speaks, so it does not hear itself.
 */
class ListenService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settings: AppSettings
    private lateinit var assistant: VoiceAssistant
    private var speaker: Speaker? = null
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var busy = false
    private var awaitingCommandUntil = 0L
    private var lastError: Int? = null

    private val restart = Runnable { startRecognizer() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        assistant = VoiceAssistant(this)
        speaker = Speaker(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.listenForOye = false
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForegroundCompat()
        } catch (e: SecurityException) {
            // Android refused a microphone service from the background (after a restart or a kill).
            // Keep the user's ON setting, so the listener starts again when the app is opened.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasMicPermission() || !SpeechRecognizer.isRecognitionAvailable(this)) {
            settings.listenForOye = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            startRecognizer()
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        createChannel()
        val notification = buildNotification(status.ifEmpty { getString(R.string.listen_notification) })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ListenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, getString(R.string.listen_stop), stop)
            .build()
    }

    /** Shows what the listener is doing in the notification, so a failure is visible without a PC. */
    private fun updateStatus(text: String) {
        if (text == status) return
        status = text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.listen_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Starts one recognition. Must run on the main thread, which the service callbacks already do. */
    private fun startRecognizer() {
        if (!running || busy) return
        recognizer?.destroy()
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = rec
        rec.setRecognitionListener(listener)
        rec.startListening(recognizerIntent())
        updateStatus(getString(R.string.listen_notification))
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, ChatViewModel.speechTag(settings.language))
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            handleSpeech(text)
        }

        override fun onError(error: Int) {
            lastError = error
            updateStatus("Oye ruka: ${errorName(error)} (code $error), dobara try kar raha hoon")
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    settings.listenForOye = false
                    stopSelf()
                }
                // The recognizer is still busy with an earlier request: give it more time.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartSoon(BUSY_MS)
                // Silence, no match, or network errors: wait a little, then listen again.
                else -> restartSoon(RETRY_MS)
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {
            updateStatus("Sun raha hoon. \"Oye\" bolein")
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun errorName(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_AUDIO -> "mic/audio"
        SpeechRecognizer.ERROR_SERVER -> "speech server"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "koi awaaz nahi"
        SpeechRecognizer.ERROR_NO_MATCH -> "samajh nahi aaya"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "mic permission nahi"
        else -> "unknown"
    }

    private fun handleSpeech(raw: String) {
        val now = System.currentTimeMillis()
        val match = WakeWord.parse(raw)
        when {
            match is Match.Command -> {
                awaitingCommandUntil = 0L
                runCommand(match.text)
            }
            match == Match.WakeOnly -> say(acknowledgement(), followUp = true)
            now < awaitingCommandUntil && raw.isNotBlank() -> {
                awaitingCommandUntil = 0L
                runCommand(raw.trim())
            }
            else -> restartSoon(SHORT_MS)
        }
    }

    private fun runCommand(text: String) {
        busy = true
        recognizer?.cancel()
        scope.launch {
            val phone = PhoneCommandParser.parse(text)
            if (phone != null) {
                speakThenListen(PhoneCommandRunner.run(this@ListenService, phone), null, followUp = false)
            } else {
                val reply: Reply = assistant.handle(text)
                speakThenListen(reply.text, reply.lang, followUp = false)
            }
        }
    }

    private fun say(text: String, followUp: Boolean = false) {
        busy = true
        recognizer?.cancel()
        scope.launch { speakThenListen(text, null, followUp) }
    }

    /** Speaks [text], then listens again. After a bare "Oye", the next sentence counts as the command. */
    private suspend fun speakThenListen(text: String, lang: String?, followUp: Boolean) {
        if (text.isNotBlank()) {
            val language = lang ?: settings.language.takeIf { it != "auto" } ?: Locale.getDefault().language
            speaker?.speak(text, ChatViewModel.localeFor(language))
        }
        // Give the voice time to finish before listening again.
        delay((text.length * MS_PER_CHAR).coerceIn(MIN_SPEAK_MS, MAX_SPEAK_MS))
        awaitingCommandUntil = if (followUp) System.currentTimeMillis() + FOLLOW_UP_MS else 0L
        busy = false
        restartSoon(SHORT_MS)
    }

    private fun acknowledgement(): String {
        val language = settings.language.takeIf { it != "auto" } ?: Locale.getDefault().language
        return when (language) {
            "it" -> "Sì?"
            "ur" -> "جی؟"
            else -> "Yes?"
        }
    }

    private fun restartSoon(ms: Long) {
        main.removeCallbacks(restart)
        main.postDelayed(restart, ms)
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacks(restart)
        recognizer?.destroy()
        recognizer = null
        speaker?.shutdown()
        speaker = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "oyeai_listening"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_STOP = "com.oyeai.voice.STOP_LISTENING"
        private const val FOLLOW_UP_MS = 8_000L
        private const val RETRY_MS = 1_500L
        private const val BUSY_MS = 3_000L

        /** Last status line, shown in the notification. Kept in memory only. */
        @Volatile var status: String = ""
        private const val SHORT_MS = 300L
        private const val MS_PER_CHAR = 80L
        private const val MIN_SPEAK_MS = 1_500L
        private const val MAX_SPEAK_MS = 15_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ListenService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListenService::class.java))
        }
    }
}
