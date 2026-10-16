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
import com.oyeai.control.PhoneCommand
import com.oyeai.control.PhoneCommandParser
import com.oyeai.control.PhoneCommandRunner
import com.oyeai.voice.SpeechInput
import com.oyeai.voice.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    /** Hands-free: the mic keeps listening after each answer, so the user never presses it again. */
    private val _handsFree = MutableStateFlow(false)
    /** Sentences said while the AI was still answering the previous one. */
    private val queued = ArrayDeque<String>()
    /** True while OYE is talking. The mic is off then. */
    private var speaking = false
    val handsFree: StateFlow<Boolean> = _handsFree.asStateFlow()

    /** False while the chat screen is in the background. Listening stops then. */
    private var visible = true

    fun clearNotice() {
        _notice.value = null
    }

    fun micDenied() {
        _notice.value = getApplication<Application>().getString(R.string.mic_needed)
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        // Phone and social commands such as "stop" must work even while the AI is busy.
        PhoneCommandParser.parse(clean)?.let { command ->
            runPhone(clean, command)
            return
        }
        // Still thinking about the last message: keep this one and send it when the answer is done.
        if (_busy.value) {
            queued.addLast(clean)
            if (_handsFree.value) listenLoop()
            return
        }
        append(Role.User, clean)
        _busy.value = true
        // Hands-free: keep listening while the AI thinks, so a new sentence is not lost.
        if (_handsFree.value) listenLoop()

        viewModelScope.launch {
            val history = _messages.value.takeLast(12).map { m ->
                (if (m.role == Role.User) "user" else "assistant") to m.text
            }
            val result = brain.ask(history)
            result.onSuccess { reply ->
                val items = reply.actions.map { ActionItem(it) }
                val msgId = append(Role.Assistant, reply.text, items)
                // Safe actions run right away. Risky ones wait for the user to tap Confirm.
                reply.actions.forEachIndexed { index, action ->
                    if (!action.needsConfirmation) runAction(msgId, index)
                }
                if (settings.speakReplies) {
                    val lang = reply.lang ?: settings.language.takeIf { it != "auto" } ?: "en"
                    sayReply(reply.text, lang)
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
            afterAnswer()
        }
    }

    /**
     * Speaks a reply. In hands-free mode it waits until the voice has finished, so the mic
     * does not hear Oye's own voice.
     */
    private suspend fun sayReply(text: String, lang: String) {
        if (_handsFree.value) {
            // Stop the mic first, so OYE does not hear its own voice.
            speech.stop()
            speaking = true
            try {
                speaker.speakAndWait(text, localeFor(lang), SPEAK_TIMEOUT_MS)
            } finally {
                speaking = false
            }
        } else {
            speaker.speak(text, localeFor(lang))
        }
    }

    /** Hands-free: start listening again once the answer is done and any voice has faded. */
    private fun afterAnswer() {
        viewModelScope.launch {
            delay(ECHO_PAUSE_MS)
            val next = queued.removeFirstOrNull()
            if (next != null) send(next) else if (_handsFree.value) listenLoop()
        }
    }

    private fun runPhone(text: String, command: PhoneCommand) {
        append(Role.User, text)
        viewModelScope.launch {
            val reply = PhoneCommandRunner.run(getApplication(), command)
            append(Role.Assistant, reply)
            if (settings.speakReplies) {
                val lang = settings.language.takeIf { it != "auto" } ?: "en"
                sayReply(reply, lang)
            }
            afterAnswer()
        }
    }

    /** Actions already started, so a double tap on Confirm cannot call or message twice. */
    private val started = mutableSetOf<Pair<Long, Int>>()

    fun runAction(messageId: Long, index: Int) {
        val item = _messages.value.firstOrNull { it.id == messageId }?.actions?.getOrNull(index) ?: return
        if (item.state != ActionState.Pending) return
        if (!synchronized(started) { started.add(messageId to index) }) return
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

    /** The mic button: turns hands-free listening on, or off if it is already on. */
    fun toggleHandsFree() {
        if (_handsFree.value) stopHandsFree() else startHandsFree()
    }

    fun startHandsFree() {
        _handsFree.value = true
        _notice.value = getApplication<Application>().getString(R.string.hands_free_on)
        listenLoop()
    }

    fun stopHandsFree() {
        _handsFree.value = false
        speech.stop()
    }

    /** Called when the chat screen appears or leaves. The mic is released in the background. */
    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (!isVisible) {
            speech.stop()
        } else if (_handsFree.value) {
            listenLoop()
        }
    }

    private fun listenLoop() {
        if (!_handsFree.value || !visible || speaking) return
        speech.listen(
            languageTag = speechTag(settings.language),
            onText = { text ->
                viewModelScope.launch {
                    if (_handsFree.value) send(text)
                }
            },
            onSilence = { retryLater() },
            onFail = { message ->
                _notice.value = message
                stopHandsFree()
            }
        )
    }

    private fun retryLater() {
        viewModelScope.launch {
            delay(RETRY_MS)
            listenLoop()
        }
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
        _handsFree.value = false
        speaker.shutdown()
        speech.destroy()
        super.onCleared()
    }

    companion object {
        private const val SPEAK_TIMEOUT_MS = 30_000L
        private const val ECHO_PAUSE_MS = 400L
        private const val RETRY_MS = 800L

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
