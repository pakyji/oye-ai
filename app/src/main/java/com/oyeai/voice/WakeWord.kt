package com.oyeai.voice

import java.util.Locale

/** Decides whether a spoken sentence starts with the wake word "Oye". */
object WakeWord {

    sealed class Match {
        /** The wake word followed by a command. */
        data class Command(val text: String) : Match()

        /** Only the wake word was said. Oye answers and waits for the command. */
        object WakeOnly : Match()
    }

    // Spellings that speech recognizers commonly produce for "Oye" in Italian, English and Urdu.
    private val wakeWords = listOf(
        "oye", "oyé", "oyè", "oi", "oy", "oyee", "ooye", "oiye", "hoye", "oie", "ohye", "oyi",
        "اوئے", "اوے", "اوئی"
    )

    /** Returns null when the sentence does not start with the wake word. */
    fun parse(transcript: String): Match? {
        val text = transcript.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase(Locale.ROOT)
        val start = lower.indexOfFirst { it.isLetter() }
        if (start < 0) return null

        for (wake in wakeWords) {
            if (!lower.startsWith(wake, start)) continue
            val rest = text.substring(start + wake.length)
            if (rest.isEmpty()) return Match.WakeOnly
            // "oyester" is not "oye" followed by a command.
            if (rest.first().isLetterOrDigit()) continue
            val command = rest.trimStart { !it.isLetterOrDigit() }.trim()
            return if (command.isEmpty()) Match.WakeOnly else Match.Command(command)
        }
        return null
    }
}
