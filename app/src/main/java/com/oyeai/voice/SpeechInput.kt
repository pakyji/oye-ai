package com.oyeai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.oyeai.R

/** One-shot speech recognition. Call from the main thread. */
class SpeechInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun listen(languageTag: String, onText: (String) -> Unit, onFail: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFail(context.getString(R.string.no_speech))
            return
        }
        recognizer?.destroy()
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) onFail(context.getString(R.string.heard_nothing)) else onText(text)
            }

            override fun onError(error: Int) {
                onFail(context.getString(R.string.heard_nothing))
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        rec.startListening(intent)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
