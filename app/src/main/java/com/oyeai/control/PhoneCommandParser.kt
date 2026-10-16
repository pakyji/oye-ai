package com.oyeai.control

import com.oyeai.social.SocialCommand
import com.oyeai.social.SocialCommands
import java.util.Locale

/**
 * Turns a spoken or typed sentence into a [PhoneCommand]. Returns null when the sentence is not
 * a phone command, so the normal AI assistant can answer it.
 *
 * Understands English, Italian, Urdu (Urdu script and Roman Urdu) and Hindi (Devanagari and Roman).
 * Words are matched by prefix, so "kholo" matches "khol", and "ruko" matches "ruk".
 * Nothing is executed here: parsing is pure and is covered by unit tests.
 */
object PhoneCommandParser {

    fun parse(text: String): PhoneCommand? {
        val t = tokenize(text)
        if (t.isEmpty()) return null
        val norm = t.joinToString(" ")

        // A message's text is never read as a command, so messages are checked first.
        if (has(t, MESSAGE) && (text.contains(':') || has(t, WRITE))) {
            val colon = text.indexOf(':')
            val left = if (colon >= 0) text.substring(0, colon) else text
            val body = if (colon >= 0) text.substring(colon + 1).trim() else ""
            val name = nameTokens(tokenize(left)).joinToString(" ")
            if (name.isNotEmpty()) return PhoneCommand.Message(name, body)
        }

        reminderCommand(text)?.let { return it }

        if (isStopAll(t)) return PhoneCommand.StopAll

        // Media comes before social, so "stop music" or "pause music" is not read as a scrolling command.
        mediaCommand(t)?.let { return it }

        SocialCommands.parse(text)?.let { social: SocialCommand ->
            return if (social == SocialCommand.Stop) PhoneCommand.StopAll else PhoneCommand.Social(social)
        }

        val number = t.firstOrNull { tok -> tok.all { it.isDigit() } }?.toIntOrNull()
        val stepUp = has(t, UP_STEP)
        val stepDown = has(t, DOWN_STEP)

        if (has(t, BRIGHTNESS)) {
            if (number != null) return PhoneCommand.Brightness(number.coerceIn(0, 100))
            if (stepUp != stepDown) return PhoneCommand.BrightnessStep(stepUp)
            if (has(t, OPEN) || has(t, GO)) return PhoneCommand.OpenSettings(SettingsTarget.DISPLAY)
        }
        if (has(t, VOLUME)) {
            if (number != null) return PhoneCommand.MediaVolume(number.coerceIn(0, 100))
            if (stepUp != stepDown) return PhoneCommand.VolumeStep(stepUp)
            if (has(t, OPEN)) return PhoneCommand.VolumePanel
        }
        if (has(t, TIMER) && number != null) return PhoneCommand.Timer(number.coerceIn(1, 600))
        if (has(t, CALL)) {
            val who = nameTokens(t)
            // A long sentence that happens to contain "call" is a question, not a dial command.
            if (who.size <= MAX_NAME_WORDS) return PhoneCommand.Dial(who.joinToString(" "))
        }
        if (has(t, DOWNLOAD)) return PhoneCommand.OpenDownloads

        val target = settingsTarget(t, norm)
        if (target != null && (has(t, OPEN) || has(t, GO))) return PhoneCommand.OpenSettings(target)

        if (has(t, BATTERY)) return PhoneCommand.DeviceStatus
        if (has(t, BACK)) return PhoneCommand.Back
        if (has(t, HOME)) return PhoneCommand.Home

        // App, button and label names are short. A longer sentence goes to the assistant instead.
        if (has(t, CLICK)) {
            shortName(t)?.let { return PhoneCommand.ClickLabel(it) }
        }
        if (has(t, FIND)) {
            shortName(t)?.let { return PhoneCommand.FindLabel(it) }
        }
        if (has(t, SCROLL)) {
            // "scroll karo" with no direction means down, the usual way to read a feed.
            val up = has(t, UP_WORDS) && !has(t, DOWN_WORDS)
            return PhoneCommand.ScrollScreen(up)
        }
        if (has(t, OPEN)) {
            shortName(t)?.let { return PhoneCommand.OpenApp(it) }
        }

        val core = t.filter { it !in FILLER }
        if (core.isNotEmpty() && core.all { it in STOP_EXACT }) return PhoneCommand.StopAll
        return null
    }

    /** Lower-cased words. Letters, combining marks (Devanagari, Urdu) and digits are kept. */
    fun tokenize(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }

    /** A word list entry ending in "$" must match the whole word ("home$" is not "homework"). Others match by prefix. */
    private fun matches(token: String, entry: String): Boolean =
        if (entry.endsWith("$")) token == entry.dropLast(1) else token.startsWith(entry)

    private fun has(t: List<String>, prefixes: List<String>): Boolean =
        t.any { tok -> prefixes.any { matches(tok, it) } }

    /** The app or label name, or null when it is empty or too long to be a name. */
    private fun shortName(t: List<String>): String? {
        val words = nameTokens(t)
        return if (words.isEmpty() || words.size > MAX_NAME_WORDS) null else words.joinToString(" ")
    }

    private const val MAX_NAME_WORDS = 3

    private fun isStopAll(t: List<String>): Boolean {
        // "cancel the timer" is not a stop-all request, so bare cancel only counts when short.
        if (has(t, CANCEL) && !has(t, TIMER) && (has(t, EVERYTHING) || has(t, AUTOMATION) || t.size <= 2)) return true
        return has(t, STOPISH) && (has(t, EVERYTHING) || has(t, AUTOMATION))
    }

    private fun mediaCommand(t: List<String>): PhoneCommand? {
        if (!has(t, MEDIA)) return null
        return when {
            has(t, PLAY) || has(t, PAUSE) -> PhoneCommand.Media(MediaAction.PlayPause)
            has(t, NEXT) -> PhoneCommand.Media(MediaAction.Next)
            has(t, PREV) -> PhoneCommand.Media(MediaAction.Previous)
            else -> null
        }
    }

    private fun settingsTarget(t: List<String>, norm: String): SettingsTarget? {
        // Notification settings are opened for all apps: OYE cannot see which app is in front.
        if (has(t, NOTIFICATION)) return SettingsTarget.NOTIFICATIONS
        TARGET_WORDS.firstOrNull { (words, _) -> words.any { norm.contains(it) } }?.let { return it.second }
        return if (has(t, SETTINGS)) SettingsTarget.MAIN else null
    }

    /** Words that are not the name of an app or contact: filler and verbs. */
    private fun nameTokens(t: List<String>): List<String> = t.filter { tok ->
        tok !in FILLER && !tok.all { it.isDigit() } &&
            (VERB_PREFIXES + MESSAGE).none { matches(tok, it) }
    }

    private val OPEN = listOf("khol", "open$", "apri$", "apro$", "aprire$", "aprimi$", "dikha", "mostra", "کھول", "دکھا", "खोल", "दिखा")
    private val GO = listOf("jao", "jaao", "jaiye", "vai", "جاؤ", "جائیں", "जाओ", "जाइए")
    private val CLICK = listOf("click", "tap$", "dabao", "dabaa", "dabaiye", "press$", "premi", "दबा", "دبا", "کلک", "क्लिक")
    private val FIND = listOf("dhoond", "dhund", "find$", "cerca", "trova", "تلاش", "ڈھونڈ", "खोज")
    private val CALL = listOf("call$", "chiam", "कॉल", "کال")
    private val WRITE = listOf("likh", "scriv", "लिख", "لکھ")
    private val MESSAGE = listOf("message", "messag", "msg", "sms", "scriv", "likh", "संदेश", "मैसेज", "پیغام", "میسج")
    private val VERB_PREFIXES = OPEN + GO + CLICK + FIND + CALL + WRITE

    private val BRIGHTNESS = listOf("brightness", "brightnes", "roshni", "luminos", "روشنی", "चमक")
    private val VOLUME = listOf("volume", "awaaz", "awaz", "آواز", "आवाज", "volum")
    private val UP_STEP = listOf("up", "zyada", "zyad", "badh", "aument", "alza", "زیادہ", "بڑھ", "ज्यादा", "बढ़")
    private val DOWN_STEP = listOf("down", "kam", "kum", "ghat", "abbass", "diminu", "کم", "گھٹ", "कम", "घट")
    private val UP_WORDS = listOf("upar", "oopar", "up", "اوپر", "ऊपर")
    private val DOWN_WORDS = listOf("neeche", "niche", "nichay", "down", "giù", "giu", "yahan", "نیچے", "नीचे")
    private val SCROLL = listOf("scroll", "scorr", "स्क्रॉल", "اسکرول")
    private val MEDIA = listOf("music", "song", "gaan", "گان", "गान", "موسیقی", "media", "मीडिया", "میڈیا", "playback")
    private val PLAY = listOf("play", "chala", "chalu", "riproduc", "बजा", "چلا", "चला", "بجا")
    private val PAUSE = listOf("pause", "pausa", "stop$", "ferm", "band$", "ruk", "rok", "रोक", "रुक", "روک", "बंद", "بند")
    private val NEXT = listOf("agl", "next", "avanti", "successiv", "اگل", "अगल")
    private val PREV = listOf("pichl", "previous", "precedent", "پچھل", "पिछल")
    private val TIMER = listOf("timer", "टाइमर", "ٹائمر", "tajmer")
    private val DOWNLOAD = listOf("download", "डाउनलोड", "ڈاؤن", "scaric")
    private val SETTINGS = listOf("setting", "impostazion", "सेटिंग", "سیٹنگ", "ترتیبات")
    private val NOTIFICATION = listOf("notif", "सूचना", "اطلاع")
    private val BATTERY = listOf("battery$", "batteria$", "बैटरी", "بیٹری")
    private val BACK = listOf("back$", "wapas", "wapis", "indietro", "वापस", "واپس", "peeche", "पीछे", "پیچھے")
    private val HOME = listOf("home$", "ghar", "घर", "گھر")
    private val CANCEL = listOf("cancel", "annull", "रद्द", "منسوخ")
    private val EVERYTHING = listOf("everything", "saar", "sab", "tutto", "سب", "सब")
    private val AUTOMATION = listOf("automation", "automazion", "स्वचालन", "خودکار")
    private val STOPISH = listOf("stop", "band", "ferm", "बंद", "بند", "रोक", "रुक", "روک", "ruk", "rok", "basta")
    private val STOP_EXACT = setOf("stop", "ferma", "fermati", "band", "بند", "बंद")

    private val TARGET_WORDS: List<Pair<List<String>, SettingsTarget>> = listOf(
        listOf("hotspot", "hot spot", "tethering", "ہاٹ اسپاٹ", "ہاٹ سپاٹ", "हॉटस्पॉट") to SettingsTarget.HOTSPOT,
        listOf("wifi", "wi fi", "وائی فائی", "वाई फाई", "वाईफाई") to SettingsTarget.WIFI,
        listOf("bluetooth", "بلوٹوتھ", "ब्लूटूथ") to SettingsTarget.BLUETOOTH,
        listOf("display", "schermo", "ڈسپلے", "डिस्प्ले") to SettingsTarget.DISPLAY,
        listOf("sound", "suono", "ساؤنڈ", "साउंड") to SettingsTarget.SOUND,
        listOf("battery", "batteria", "بیٹری", "बैटरी") to SettingsTarget.BATTERY,
        listOf("accessib", "ایکسیسبیلیٹی", "एक्सेसिबिलिटी") to SettingsTarget.ACCESSIBILITY,
        listOf("permission", "permess", "اجازت", "अनुमति") to SettingsTarget.APP_PERMISSIONS,
        listOf("default", "ڈیفالٹ", "डिफ़ॉल्ट", "डिफॉल्ट") to SettingsTarget.DEFAULT_APPS,
        listOf("keyboard", "tastiera", "کی بورڈ", "कीबोर्ड") to SettingsTarget.KEYBOARD,
        listOf("language", "lingua", "زبان", "भाषा") to SettingsTarget.LANGUAGE,
        listOf("storage", "spazio", "اسٹوریج", "स्टोरेज") to SettingsTarget.STORAGE,
        listOf("location", "posizione", "gps", "مقام", "लोकेशन", "स्थान") to SettingsTarget.LOCATION
    )

    /** Filler words that are dropped when looking for a name. */
    private val FILLER = setOf(
        "oye", "ko", "ki", "ka", "ke", "liye", "karne", "kar", "karo", "kardo", "do", "mujhe", "meri", "mera",
        "mere", "me", "mein", "par", "pe", "pr", "ye", "yeh", "is", "iss", "app", "application", "dialer",
        "phone", "the", "a", "an", "to", "of", "on", "in", "please", "ji", "aur", "ya", "se", "wali", "wala",
        "button", "tab", "page", "screen", "ek", "ab", "abhi", "bhi", "tak", "yahan", "apne",
        "کو", "کی", "کا", "کے", "میں", "پر", "کر", "کرو", "کریں", "دو", "میری", "مجھے", "اس", "ایپ",
        "को", "की", "का", "में", "पर", "के", "करो", "दो", "मेरी", "मेरा", "और", "इस", "ऐप"
    )
}

private val REMIND_BEFORE = Regex(
    "^(?:oye[ ,]*)?(\\d+)\\s*(minutes?|minuti|mins?|hours?|ore|ora|ghante|ghanta)\\s*(?:baad|dopo|later)?\\s*(?:mein|me|in)?\\s*(?:yaad dilao|yaad dilana|ricordami|remind me)\\s*(?:to|ko|di|che)?\\s*(.+)$"
)
private val REMIND_AFTER = Regex(
    "^(?:oye[ ,]*)?(?:remind me|yaad dilao|ricordami)\\s*(?:in\\s*)?(\\d+)\\s*(minutes?|minuti|mins?|hours?|ore|ora|ghante|ghanta)\\s*(?:later|baad|dopo)?\\s*(?:to|ko|di|che)?\\s*(.+)$"
)

/** "15 minute baad yaad dilao paani pee", "remind me in 2 hours to call mom". Null for anything else. */
private fun reminderCommand(text: String): PhoneCommand.Remind? {
    val lower = text.lowercase(Locale.ROOT).trim().trimEnd('.', '!', '?')
    val m = REMIND_BEFORE.find(lower) ?: REMIND_AFTER.find(lower) ?: return null
    val amount = m.groupValues[1].toIntOrNull() ?: return null
    val unit = m.groupValues[2]
    val isHours = unit.startsWith("h") || unit.startsWith("ore") || unit.startsWith("ora") || unit.startsWith("ghant")
    val minutes = if (isHours) amount * 60 else amount
    if (minutes !in 1..1440) return null
    // Keep the user's own capitals: take the body from the original text.
    val original = text.trim().trimEnd('.', '!', '?')
    val body = original.takeLast(m.groupValues[3].length).trim()
    if (body.isEmpty()) return null
    return PhoneCommand.Remind(minutes, body)
}
