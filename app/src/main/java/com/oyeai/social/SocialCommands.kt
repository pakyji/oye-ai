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

        val app = when {
            tokens.any { it in INSTAGRAM_NAMES } -> SocialApp.INSTAGRAM
            tokens.any { it in FACEBOOK_NAMES } -> SocialApp.FACEBOOK
            else -> null
        }
        val reels = tokens.any { it.startsWith("reel") }
        val scroll = tokens.any { it.startsWith("scroll") || it.startsWith("scorr") || it == "auto" }
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
        if (startsWithAny(tokens, PREVIOUS_WORDS)) return SocialCommand.Previous
        if (startsWithAny(tokens, NEXT_WORDS)) return SocialCommand.Next

        // 5. Short standalone commands, such as "stop", "ruko" or "shuru karo".
        val core = tokens.filter { it !in FILLER_WORDS }
        if (core.isNotEmpty() && core.size <= 2) {
            return when {
                core.any { it in STOP_EXACT } -> SocialCommand.Stop
                core.any { it in PAUSE_EXACT } -> SocialCommand.Pause
                core.any { it in START_EXACT } -> SocialCommand.Start
                core.any { it in NEXT_EXACT } -> SocialCommand.Next
                else -> null
            }
        }
        return null
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() && it != "oye" }

    private fun startsWithAny(tokens: List<String>, prefixes: List<String>): Boolean =
        tokens.any { token -> prefixes.any { token.startsWith(it) } }

    private const val MAX_MINUTES = 180

    private val INSTAGRAM_NAMES = setOf("instagram", "insta")
    private val FACEBOOK_NAMES = setOf("facebook", "fb")

    private val OPEN_WORDS = listOf("khol", "open", "apri", "aprir", "apro", "launch")
    private val START_WORDS = listOf("shuru", "start", "chalu", "avvia", "inizia", "riprend", "resume", "continu", "dobara", "phir")
    private val STOP_WORDS = listOf("band", "stop", "ferma", "fermo", "basta")
    private val PAUSE_WORDS = listOf("pause", "paus", "ruk", "rok")
    private val SLOW_WORDS = listOf("dheem", "dhim", "slow", "lent", "piano")
    private val FAST_WORDS = listOf("tez", "fast", "jaldi", "veloc", "rapid")
    private val PREVIOUS_WORDS = listOf("pichl", "previous", "precedent", "indietro")
    private val NEXT_WORDS = listOf("agl", "next", "prossim", "avanti", "success")
    private val MINUTE_WORDS = listOf("minute", "minut", "min", "mint")
    private val DELAY_WORDS = setOf("baad", "after", "dopo", "tra", "fra")

    private val FILLER_WORDS = setOf("karo", "kar", "kardo", "do", "ji", "please", "abhi", "ab", "now")
    private val STOP_EXACT = setOf("stop", "ferma", "fermati", "fermo", "band")
    private val PAUSE_EXACT = setOf("pause", "pausa", "ruko", "ruk", "rok")
    private val START_EXACT = setOf("start", "shuru", "chalu", "resume", "riprendi", "avvia", "dobara")
    private val NEXT_EXACT = setOf("next", "agla", "agli", "avanti", "prossimo")
}
