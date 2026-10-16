package com.oyeai.social

import java.util.Locale

/** Apps that OYE can open and scroll. Nothing else is ever touched. */
enum class SocialApp(val packageName: String, val label: String) {
    INSTAGRAM("com.instagram.android", "Instagram"),
    FACEBOOK("com.facebook.katana", "Facebook")
}

sealed class SocialCommand {
    data class Open(val app: SocialApp, val reels: Boolean) : SocialCommand()
    object Start : SocialCommand()
    object Pause : SocialCommand()
    object Stop : SocialCommand()
    data class StopAfter(val minutes: Int) : SocialCommand()
    object Next : SocialCommand()
    object Previous : SocialCommand()
    object Slower : SocialCommand()
    object Faster : SocialCommand()
}

/**
 * Recognises social media commands in English, Italian and Hinglish, for example
 * "Instagram kholo", "agla Reel", "scrolling band karo" or "10 minute ke baad scrolling rok do".
 * Returns null for anything else, so the normal AI assistant handles it.
 *
 * Words are matched by prefix where Hindi and Italian inflections matter ("khol" matches "kholo").
 */
object SocialCommands {

    fun parse(text: String): SocialCommand? {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return null

        // Speech recognition often splits or misspells the names: "face book", "insta gram", "fesbuk".
        val spaced = tokens.joinToString(" ")
        val app = when {
            tokens.any { it in INSTAGRAM_NAMES } || spaced.contains("insta gram") -> SocialApp.INSTAGRAM
            tokens.any { it in FACEBOOK_NAMES } || spaced.contains("face book") -> SocialApp.FACEBOOK
            else -> null
        }
        val reels = tokens.any { it.startsWith("reel") || it.startsWith("रील") || it.startsWith("रिल") }
        val scroll = tokens.any { it.startsWith("scroll") || it.startsWith("scorr") || it == "auto" || it.startsWith("स्क्रॉल") || it.startsWith("اسکرول") }
        val open = startsWithAny(tokens, OPEN_WORDS)

        // 1. A timed stop: "10 minute ke baad scrolling rok do".
        val minutes = tokens.firstNotNullOfOrNull { it.toIntOrNull() }
        if (minutes != null && minutes in 1..MAX_MINUTES && startsWithAny(tokens, MINUTE_WORDS) &&
            (startsWithAny(tokens, STOP_WORDS) || startsWithAny(tokens, PAUSE_WORDS) || tokens.any { it in DELAY_WORDS })
        ) {
            return SocialCommand.StopAfter(minutes)
        }

        // 2. Open an app, optionally its Reels.
        if (app != null && open) return SocialCommand.Open(app, reels)

        // 3. Scrolling commands that name scrolling explicitly.
        if (scroll && startsWithAny(tokens, START_WORDS)) return SocialCommand.Start
        if (scroll && startsWithAny(tokens, STOP_WORDS)) return SocialCommand.Stop
        if (scroll && startsWithAny(tokens, PAUSE_WORDS)) return SocialCommand.Pause
        if ((scroll || reels) && startsWithAny(tokens, SLOW_WORDS)) return SocialCommand.Slower
        if ((scroll || reels) && startsWithAny(tokens, FAST_WORDS)) return SocialCommand.Faster

        // 4. Reel navigation.
        // Only in a Reels or scrolling context, so "pichla task continue karo" is not a scroll.
        val reelContext = reels || scroll
        if (reelContext && startsWithAny(tokens, PREVIOUS_WORDS)) return SocialCommand.Previous
        if (reelContext && startsWithAny(tokens, NEXT_WORDS)) return SocialCommand.Next

        // 5. Short standalone commands, such as "stop", "ruko" or "shuru karo".
        val core = tokens.filter { it !in FILLER_WORDS }
        if (core.isNotEmpty() && core.size <= 2) {
            return when {
                core.any { it in STOP_EXACT } -> SocialCommand.Stop
                core.any { it in PAUSE_EXACT } -> SocialCommand.Pause
                core.any { it in START_EXACT } -> SocialCommand.Start
                // Next and previous alone only: "musica avanti" is a music command, not a Reel.
                core.size == 1 && core[0] in NEXT_EXACT -> SocialCommand.Next
                core.size == 1 && core[0] in PREVIOUS_EXACT -> SocialCommand.Previous
                else -> null
            }
        }
        return null
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() && it != "oye" }

    private fun startsWithAny(tokens: List<String>, prefixes: List<String>): Boolean =
        tokens.any { token -> prefixes.any { token.startsWith(it) } }

    private const val MAX_MINUTES = 180

    private val INSTAGRAM_NAMES = setOf("instagram", "instagrm", "instagran", "insta", "इंस्टाग्राम", "انسٹاگرام", "انسٹا")
    private val FACEBOOK_NAMES = setOf(
        "facebook", "facebok", "faceboook", "fesbuk", "fesbook", "feisbuk", "fb", "फेसबुक", "फ़ेसबुक",
        "فیس بک", "فیسبک", "فیس", "فیسبوک"
    )

    private val OPEN_WORDS = listOf("khol", "open", "apri", "aprir", "apro", "launch", "खोल", "کھول")
    private val START_WORDS = listOf("shuru", "start", "chalu", "avvia", "inizia", "riprend", "resume", "continu", "dobara", "phir",
        "शुरू", "चालू", "شروع", "چالو", "جاری")
    private val STOP_WORDS = listOf("band", "stop", "ferma", "fermo", "basta", "बंद", "بند")
    private val PAUSE_WORDS = listOf("pause", "paus", "ruk", "rok", "रुक", "रोक", "روک", "پاز")
    private val SLOW_WORDS = listOf("dheem", "dhim", "slow", "lent", "piano", "धीर", "آہست", "دھیم")
    private val FAST_WORDS = listOf("tez", "fast", "jaldi", "veloc", "rapid", "तेज", "تیز", "جلد")
    private val PREVIOUS_WORDS = listOf("pichl", "previous", "precedent", "पिछल", "پچھل")
    private val NEXT_WORDS = listOf("agl", "next", "prossim", "avanti", "success", "अगल", "اگل")
    private val MINUTE_WORDS = listOf("minute", "minut", "min", "mint", "मिनट", "منٹ")
    private val DELAY_WORDS = setOf("baad", "after", "dopo", "tra", "fra", "बाद", "بعد", "کے بعد")

    private val FILLER_WORDS = setOf("karo", "kar", "kardo", "do", "ji", "please", "abhi", "ab", "now",
        "करो", "कर", "दो", "अभी", "کرو", "کر", "دو", "ابھی")
    private val STOP_EXACT = setOf("stop", "ferma", "fermati", "fermo", "band", "बंद", "بند")
    private val PAUSE_EXACT = setOf("pause", "pausa", "ruko", "ruk", "rok", "रुको", "रोको", "رکو", "روکو")
    private val START_EXACT = setOf("start", "shuru", "chalu", "resume", "riprendi", "avvia", "dobara", "शुरू", "شروع")
    private val NEXT_EXACT = setOf("next", "agla", "avanti", "prossimo", "अगला", "اگلی", "اگلا")
    private val PREVIOUS_EXACT = setOf("previous", "pichla", "pichli", "pichle", "precedente", "पिछला", "پچھلی", "پچھلا")
}
