package com.oyeai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Text-to-speech for replies. Falls back to US English if a language voice is missing. */
class Speaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Locale>? = null

    init {
        synchronized(live) { live.add(this) }
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                pending?.let { (text, locale) -> speak(text, locale) }
            }
            pending = null
        }
    }

    fun speak(text: String, locale: Locale) {
        val engine = tts ?: return
        if (!ready) {
            pending = text to locale
            return
        }
        if (engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
            engine.setLanguage(Locale.US)
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "oyeai-reply")
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
    }

    companion object {
        /** Every live speaker, so Stop All can silence them all. */
        private val live = mutableSetOf<Speaker>()

        /** Stops speech on all speakers. Returns true when any of them was speaking. */
        fun stopAllSpeech(): Boolean = synchronized(live) {
            live.fold(false) { acc, speaker -> speaker.stopSpeaking() || acc }
        }
    }
}
