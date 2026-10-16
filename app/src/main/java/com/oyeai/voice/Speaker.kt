package com.oyeai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Text-to-speech for replies. Falls back to US English if a language voice is missing. */
class Speaker(context: Context) {

    private class Pending(val text: String, val locale: Locale, val id: String)

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var pending: Pending? = null

    /** Callbacks waiting for an utterance to finish, by utterance id. */
    private val waiters = ConcurrentHashMap<String, () -> Unit>()

    init {
        synchronized(live) { live.add(this) }
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(progressListener)
                pending?.let { speakNow(it.text, it.locale, it.id) }
            }
            pending = null
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = finished(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    private fun finished(utteranceId: String?) {
        if (utteranceId != null) waiters.remove(utteranceId)?.invoke()
    }

    fun speak(text: String, locale: Locale) {
        speakNow(text, locale, REPLY_ID)
    }

    /**
     * Speaks [text] and returns when the voice has really finished (or after [timeoutMs] at most).
     * The microphone listener uses this so it neither hears Oye itself nor stays deaf too long.
     */
    suspend fun speakAndWait(text: String, locale: Locale, timeoutMs: Long) {
        val id = "oyeai-" + System.nanoTime()
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                waiters[id] = { if (cont.isActive) cont.resume(Unit) }
                cont.invokeOnCancellation { waiters.remove(id) }
                if (!speakNow(text, locale, id)) {
                    waiters.remove(id)
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
        waiters.remove(id)
    }

    /** Returns false when nothing could be started. */
    private fun speakNow(text: String, locale: Locale, id: String): Boolean {
        val engine = tts ?: return false
        if (!ready) {
            pending = Pending(text, locale, id)
            return true
        }
        if (engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
            engine.setLanguage(Locale.US)
        }
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
    }

    /** Stops speech in progress and drops any queued reply. Returns true when something was being said. */
    fun stopSpeaking(): Boolean {
        val engine = tts ?: return false
        pending = null
        val wasSpeaking = engine.isSpeaking
        engine.stop()
        return wasSpeaking
    }

    fun shutdown() {
        synchronized(live) { live.remove(this) }
        tts?.stop()
        tts?.shutdown()
        tts = null
        // Anyone still waiting for this voice can stop waiting.
        waiters.values.toList().forEach { it() }
        waiters.clear()
    }

    companion object {
        private const val REPLY_ID = "oyeai-reply"

        /** Every live speaker, so Stop All can silence them all. */
        private val live = mutableSetOf<Speaker>()

        /** Stops speech on all speakers. Returns true when any of them was speaking. */
        fun stopAllSpeech(): Boolean = synchronized(live) {
            live.fold(false) { acc, speaker -> speaker.stopSpeaking() || acc }
        }
    }
}
