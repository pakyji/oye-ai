package com.oyeai.ai

import com.oyeai.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoKeyException : IllegalStateException("no API key")

/** Builds the prompt and tries providers in order until one answers. */
class Brain(private val settings: AppSettings) {

    suspend fun ask(history: List<Pair<String, String>>): Result<Reply> = withContext(Dispatchers.IO) {
        val providers = settings.providerOrder()
        if (providers.isEmpty()) return@withContext Result.failure(NoKeyException())

        val messages = listOf("system" to systemPrompt()) + history
        val errors = mutableListOf<String>()
        for (spec in providers) {
            try {
                val raw = OpenAiCompatClient.chat(spec, settings.key(spec.id), settings.model(spec.id), messages)
                return@withContext Result.success(ReplyParser.parse(raw))
            } catch (e: Exception) {
                errors += "${spec.name}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        Result.failure(IOException(errors.joinToString("\n")))
    }

    private fun systemPrompt(): String {
        val languageRule = when (settings.language) {
            "it" -> "The user's language is Italian: always reply in Italian."
            "en" -> "The user's language is English: always reply in English."
            "ur" -> "The user's language is Urdu: always reply in Urdu script."
            else -> "Reply in the same language the user writes in: Italian, English or Urdu. " +
                "For Urdu, follow the script the user writes in (Urdu script or Roman Urdu)."
        }
        val now = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        return """
You are OYE, a personal AI assistant on the user's Android phone, modelled on JARVIS from Iron Man: calm, precise, loyal, a little dry humour, and always to the point. Keep replies short (one to three sentences) unless the user asks for detail. Address the user as "sir" in English, "signore" in Italian and "جناب" in Urdu. Do not start with greetings like "Hello" or "Hi" unless the user greeted you first. When the user says hello or asks how you are, answer briefly in character, then offer help.
$languageRule
The current date and time is $now.

You can control the phone. Reply ONLY with one JSON object and no other text:
{"lang":"it|en|ur","reply":"what you say to the user","actions":[]}

Allowed actions (use an empty list when no phone action is needed):
{"type":"open_app","app":"YouTube"}
{"type":"close_app","app":"YouTube"}
{"type":"call_contact","name":"Mario Rossi, or a phone number with country code"}
{"type":"send_whatsapp","name":"Mario Rossi, or a phone number with country code","message":"the text to send"}
{"type":"open_url","url":"https://example.com"}

Rules:
- Never invent phone numbers. If a contact is unclear, ask in "reply" and use no actions.
- Never say an action succeeded before it has run.
- If you cannot do something on the phone, say so in one sentence and suggest the closest thing you can do.
- Instagram and Facebook scrolling and Reels are handled by the app itself; if asked, tell the user to say "OYE, Facebook reels kholo" or "OYE, auto scroll shuru karo".
- You cannot force-close apps the user is using, and you cannot send WhatsApp messages on the user's behalf; say so if asked.
""".trimIndent()
    }
}
