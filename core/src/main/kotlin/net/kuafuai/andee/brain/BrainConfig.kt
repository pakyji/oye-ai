package net.kuafuai.andee.brain

import net.kuafuai.andee.config.Notebook

/**
 * Configuration for the on-device brain. Read from voice_prefs (user settings).
 * The brain can be "local" (on-device, default) or "hub" (remote WebSocket).
 */
data class BrainConfig(
    val mode: String = "local", // "local" | "hub"
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val thinking: Boolean = false,
    val reasoningEffort: String = "medium", // low | medium | high
    val maxSteps: Int = 40,
    val hubUrl: String = "", // for hub mode
    val deviceName: String = ""
) {
    /** True if this config change is only about how the same backend is asked (model, thinking, effort) */
    fun sameBackend(other: BrainConfig): Boolean =
        mode == other.mode &&
        baseUrl == other.baseUrl &&
        apiKey == other.apiKey &&
        hubUrl == other.hubUrl

    companion object {
        fun fromVoicePrefs(prefs: Notebook): BrainConfig {
            // Read from voice_prefs via the host
            return BrainConfig()
        }
    }
}

/**
 * Contract between the brain and the device body. Both Android and Desktop implement this.
 * The brain never sees platform types — only this interface.
 */
interface BrainHost {
    /** Log a message at VERBOSE level */
    fun logVerbose(tag: String, msg: String)

    /** Log a message at DEBUG level */
    fun logDebug(tag: String, msg: String)

    /** Log a message at INFO level */
    fun logInfo(tag: String, msg: String)

    /** Log a message at WARN level */
    fun logWarn(tag: String, msg: String, throwable: Throwable? = null)

    /** Log a message at ERROR level */
    fun logError(tag: String, msg: String, throwable: Throwable? = null)

    /** Current locale for stamping messages */
    fun locale(): java.util.Locale

    /** Touch activity timestamp (for quiet-hour timer) */
    fun touchActivity()

    /** Get scrollback rows for sweep rebuild */
    fun scrollback(): List<ScrollbackRow>

    /** Get notebook instance */
    fun notebook(): Notebook

    /** Get current BrainConfig */
    fun brainConfig(): BrainConfig

    /** Get guard paragraph */
    fun guard(): String

    /** Dispatch a tool call to the device */
    suspend fun dispatch(method: String, params: String): Any

    /** Called when a turn ends (any kind) */
    fun onTurnEnd()

    /** Called when sweep spent tokens */
    fun onSweepSpent(tokens: Int)

    /** Called when a quiet turn ends */
    fun onQuietFinal()

    /** Called for each step of a working turn */
    fun onStep(step: WorkStep)

    /** Current active scene name (or null) */
    fun activeScene(): String?

    /** Friends index line for prompt */
    fun friendsIndex(): String

    /** Gear index line for prompt */
    fun gearIndex(): String

    /** Notebook index lines for prompt */
    fun notebookIndex(): Pair<String, String> // (memories, wiki)

    /** Plan index line for prompt */
    fun planIndex(): String

    /** Selfhood index line for prompt */
    fun selfhoodIndex(): String

    /** Called when a work turn produces a result */
    fun onWorkResult(result: LocalBrain.WorkResult)

    /** Note a silent observation for the next turn */
    fun note(text: String)

    /** Stop epoch for turn cancellation */
    val stopEpoch: java.util.concurrent.atomic.AtomicInteger
        get() = TODO("implement in body")

    /** Whether a todo with this text was withdrawn */
    fun todoWithdrawn(text: String): Boolean

    /** Called when scene changes */
    fun onSceneChanged(name: String?)
}

data class ScrollbackRow(
    val role: Role,
    val text: String,
    val timestamp: Long,
    val isEvent: Boolean = false
) {
    enum class Role { USER, ASSISTANT, EVENT }
    val isUser: Boolean get() = role == Role.USER
    val isAssistant: Boolean get() = role == Role.ASSISTANT
}