package com.oyeai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oyeai.AppSettings
import com.oyeai.R
import com.oyeai.actions.ActionExecutor
import com.oyeai.actions.Outcome
import com.oyeai.ai.Action
import com.oyeai.ai.Brain
import com.oyeai.ai.NoKeyException
import com.oyeai.voice.SpeechInput
import com.oyeai.voice.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class Role { User, Assistant }

enum class ActionState { Pending, Done, Failed }

data class ActionItem(
    val action: Action,
    val state: ActionState = ActionState.Pending,
    val detail: String = ""
)

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val actions: List<ActionItem> = emptyList()
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = AppSettings(app)
    private val brain = Brain(settings)
    private val executor = ActionExecutor(app)
    private val speaker = Speaker(app)
    private val speech = SpeechInput(app)

    private var nextId = 1L
    private val _messages = MutableStateFlow(
        listOf(ChatMessage(nextId++, Role.Assistant, app.getString(R.string.greeting)))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    fun micDenied() {
        _notice.value = getApplication<Application>().getString(R.string.mic_needed)
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _busy.value) return
        append(Role.User, clean)
        _busy.value = true

        viewModelScope.launch {
            val history = _messages.value.takeLast(12).map { m ->
                (if (m.role == Role.User) "user" else "assistant") to m.text
            }
            val result = brain.ask(history)
            result.onSuccess { reply ->
                val items = reply.actions.map { ActionItem(it) }
                val msgId = append(Role.Assistant, reply.text, items)
                if (settings.speakReplies) {
                    val lang = reply.lang ?: settings.language.takeIf { it != "auto" } ?: "en"
                    speaker.speak(reply.text, localeFor(lang))
                }
                // Safe actions run right away. Risky ones wait for the user to tap Confirm.
                reply.actions.forEachIndexed { index, action ->
                    if (!action.needsConfirmation) runAction(msgId, index)
                }
            }.onFailure { error ->
                val message = if (error is NoKeyException) {
                    getApplication<Application>().getString(R.string.no_key)
                } else {
                    "⚠ ${error.message ?: "Error"}"
                }
                append(Role.Assistant, message)
            }
            _busy.value = false
        }
    }

    fun runAction(messageId: Long, index: Int) {
        val item = _messages.value.firstOrNull { it.id == messageId }?.actions?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val outcome: Outcome = executor.execute(item.action)
            updateItem(messageId, index) {
                it.copy(
                    state = if (outcome.ok) ActionState.Done else ActionState.Failed,
                    detail = outcome.message
                )
            }
        }
    }

    fun listen(onText: (String) -> Unit) {
        speech.listen(
            languageTag = speechTag(settings.language),
            onText = { text -> viewModelScope.launch { onText(text) } },
            onFail = { message -> _notice.value = message }
        )
    }

    private fun append(role: Role, text: String, actions: List<ActionItem> = emptyList()): Long {
        val id = nextId++
        _messages.update { it + ChatMessage(id, role, text, actions) }
        return id
    }

    private fun updateItem(messageId: Long, index: Int, transform: (ActionItem) -> ActionItem) {
        _messages.update { list ->
            list.map { msg ->
                if (msg.id != messageId) msg
                else msg.copy(actions = msg.actions.mapIndexed { i, item -> if (i == index) transform(item) else item })
            }
        }
    }

    override fun onCleared() {
        speaker.shutdown()
        speech.destroy()
        super.onCleared()
    }

    companion object {
        fun localeFor(lang: String): Locale = when (lang) {
            "it" -> Locale.ITALY
            "ur" -> Locale("ur", "PK")
            else -> Locale.US
        }

        fun speechTag(setting: String): String {
            val lang = if (setting == "auto") Locale.getDefault().language else setting
            return when (lang) {
                "it" -> "it-IT"
                "ur" -> "ur-PK"
                else -> "en-US"
            }
        }
    }
}
