package com.oyeai.voice

import android.content.Context
import com.oyeai.AppSettings
import com.oyeai.R
import com.oyeai.ai.Brain
import com.oyeai.ai.NoKeyException
import com.oyeai.ai.Reply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Answers a spoken command without the chat screen, keeping its own short history.
 * Actions are offered as notifications, since a background service cannot open screens on its own.
 */
class VoiceAssistant(private val context: Context) {

    private val brain = Brain(AppSettings(context))
    private val history = ArrayDeque<Pair<String, String>>()

    suspend fun handle(command: String): Reply = withContext(Dispatchers.IO) {
        history.addLast("user" to command)
        while (history.size > MAX_TURNS) history.removeFirst()

        brain.ask(history.toList()).fold(
            onSuccess = { reply ->
                history.addLast("assistant" to reply.text)
                reply.actions.forEach { ActionNotifier.offer(context, it) }
                reply
            },
            onFailure = { error ->
                val text = if (error is NoKeyException) {
                    context.getString(R.string.no_key)
                } else {
                    "⚠ ${error.message ?: "Error"}"
                }
                Reply(text, null, emptyList())
            }
        )
    }

    companion object {
        private const val MAX_TURNS = 12
    }
}
