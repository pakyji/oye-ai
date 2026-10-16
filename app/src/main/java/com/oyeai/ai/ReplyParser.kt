package com.oyeai.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** An action the assistant wants to perform on the phone. */
sealed class Action {
    abstract val needsConfirmation: Boolean

    data class OpenApp(val app: String) : Action() {
        override val needsConfirmation = false
    }

    data class OpenUrl(val url: String) : Action() {
        // A link chosen by the model is never opened without the user's tap.
        override val needsConfirmation = true
    }

    data class CloseApp(val app: String) : Action() {
        override val needsConfirmation = true
    }

    data class CallContact(val name: String) : Action() {
        override val needsConfirmation = true
    }

    data class SendWhatsApp(val name: String, val message: String) : Action() {
        override val needsConfirmation = true
    }

    /** An action type the app does not know. It is shown but never executed. */
    data class Unknown(val type: String) : Action() {
        override val needsConfirmation = false
    }
}

data class Reply(val text: String, val lang: String?, val actions: List<Action>)

object ReplyParser {

    private val languages = setOf("it", "en", "ur")

    /**
     * Reads the model's JSON reply. If the output is not valid JSON, the raw text is
     * shown as a plain reply with no actions, so a model mistake never runs anything.
     */
    fun parse(raw: String): Reply {
        val plain = Reply(raw.trim(), null, emptyList())
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return plain

        return try {
            val obj = JSONObject(raw.substring(start, end + 1))
            // optString turns a JSON null into the text "null", so check for it first.
            val text = if (obj.isNull("reply")) "" else obj.optString("reply").trim()
            if (text.isEmpty()) return plain
            val lang = obj.optString("lang").trim().lowercase().takeIf { it in languages }
            val list: JSONArray = obj.optJSONArray("actions") ?: JSONArray()
            val actions = (0 until list.length()).mapNotNull { parseAction(list.optJSONObject(it)) }
            Reply(text, lang, actions)
        } catch (e: JSONException) {
            plain
        }
    }

    internal fun parseAction(o: JSONObject?): Action? {
        if (o == null) return null
        fun field(key: String): String = o.optString(key).trim()
        return when (val type = field("type")) {
            "" -> null
            "open_app" -> field("app").takeIf { it.isNotEmpty() }?.let { Action.OpenApp(it) }
            "close_app" -> field("app").takeIf { it.isNotEmpty() }?.let { Action.CloseApp(it) }
            "call_contact" -> field("name").takeIf { it.isNotEmpty() }?.let { Action.CallContact(it) }
            "send_whatsapp" -> {
                val name = field("name")
                val message = field("message")
                if (name.isNotEmpty() && message.isNotEmpty()) Action.SendWhatsApp(name, message) else null
            }
            "open_url" -> field("url").takeIf { it.startsWith("https://") }?.let { Action.OpenUrl(it) }
            else -> Action.Unknown(type)
        }
    }
}
