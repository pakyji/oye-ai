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

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
