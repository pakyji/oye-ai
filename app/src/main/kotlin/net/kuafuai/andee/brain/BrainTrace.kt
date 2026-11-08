package net.kuafuai.andee.brain

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Brain trace for debugging and observability.
 * Emits structured events for each turn, step, tool call, and decision.
 */
object BrainTrace {

    @Serializable
    data class Event(
        val type: Type,
        val turnId: String,
        val step: Int? = null,
        val method: String? = null,
        val message: String,
        val data: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        enum class Type {
            TURN_START, TURN_END, STEP_START, STEP_END, TOOL_CALL, TOOL_RESULT,
            GUARD_CHECK, IMAGE_RATION, PREFIX_CACHE, ERROR, HEARTBEAT, SCENE_CHANGE
        }
    }

    private val channel = Channel<Event>(Channel.UNLIMITED)
    val events: ReceiveChannel<Event> = channel
    private val turnCounter = AtomicInteger(0)

    fun nextTurnId(): String = "turn-${turnCounter.incrementAndGet()}-${System.currentTimeMillis()}"

    suspend fun emit(event: Event) = channel.send(event)

    fun tryEmit(event: Event) = channel.trySend(event)

    // Convenience functions
    suspend fun turnStart(turnId: String, turnType: String, trigger: String) =
        emit(Event(Event.Type.TURN_START, turnId, message = "$turnType triggered by $trigger"))

    suspend fun turnEnd(turnId: String, result: WorkResult) =
        emit(Event(Event.Type.TURN_END, turnId, message = "Completed in ${result.totalDurationMs}ms, ${result.totalToolsCalled} tools, ${result.totalImagesUsed} images"))

    suspend fun stepStart(turnId: String, step: Int, method: String) =
        emit(Event(Event.Type.STEP_START, turnId, step, method, "Starting $method"))

    suspend fun stepEnd(turnId: String, step: WorkStep) =
        emit(Event(Event.Type.STEP_END, turnId, step.step, step.method,
            if (step.success) "Success (${step.durationMs}ms)" else "Failed: ${step.error}",
            step.result))

    suspend fun toolCall(turnId: String, step: Int, method: String, params: String) =
        emit(Event(Event.Type.TOOL_CALL, turnId, step, method, "Calling $method", params))

    suspend fun toolResult(turnId: String, step: Int, method: String, result: String, error: String?) =
        emit(Event(Event.Type.TOOL_RESULT, turnId, step, method, if (error != null) "Error: $error" else "OK", result))

    suspend fun guardCheck(turnId: String, passed: Boolean, reason: String?) =
        emit(Event(Event.Type.GUARD_CHECK, turnId, message = if (passed) "Guard passed" else "Guard blocked: $reason"))

    suspend fun imageRation(turnId: String, used: Int, limit: Int) =
        emit(Event(Event.Type.IMAGE_RATION, turnId, message = "Images: $used/$limit"))

    suspend fun prefixCache(turnId: String, preserved: Boolean, tokens: Int) =
        emit(Event(Event.Type.PREFIX_CACHE, turnId, message = if (preserved) "Prefix cache preserved ($tokens tokens)" else "Prefix cache broken"))

    suspend fun error(turnId: String, message: String, throwable: Throwable? = null) =
        emit(Event(Event.Type.ERROR, turnId, message = message, data = throwable?.message))

    suspend fun heartbeat(turnId: String, reason: String) =
        emit(Event(Event.Type.HEARTBEAT, turnId, message = "Heartbeat work turn: $reason"))

    suspend fun sceneChange(turnId: String, from: String?, to: String?) =
        emit(Event(Event.Type.SCENE_CHANGE, turnId, message = "Scene: ${from ?: "none"} → ${to ?: "none"}"))
}