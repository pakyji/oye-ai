package net.kuafuai.andee.brain

import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.kuafuai.andee.config.Notebook
import net.kuafuai.andee.net.ToolSchemas
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * On-device brain: the autonomous agent loop.
 * Runs locally (DeepSeek) or via hub (WebSocket).
 *
 * Key properties:
 * - Serial tool execution (one at a time)
 * - Per-tool deadline (30s) and turn deadline (3min)
 * - Image rationing (max 3 per turn)
 * - Prefix cache preservation across turns
 * - Stop epoch for cancellation
 * - Turn guards enforce untrusted turn restrictions
 */
class LocalBrain(
    private val host: BrainHost
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopEpoch = AtomicInteger(0)
    private val currentTurn = AtomicReference<TurnState?>(null)
    private val prefixCache = AtomicReference<String>("")
    private val imageCountThisTurn = AtomicInteger(0)

    // Tool dispatcher - implemented by host (Android/Desktop)
    private suspend fun dispatchTool(method: String, params: String): Any = host.dispatch(method, params)

    /** Start a work turn (heartbeat or user-triggered) */
    fun startWorkTurn(trigger: String = "heartbeat") {
        scope.launch { runWorkTurn(trigger) }
    }

    /** Start a notify turn (untrusted) */
    fun startNotifyTurn(notification: String) {
        scope.launch { runNotifyTurn(notification) }
    }

    /** Start a peer turn (untrusted) */
    fun startPeerTurn(peerId: String, message: String) {
        scope.launch { runPeerTurn(peerId, message) }
    }

    /** Cancel current turn */
    fun cancelCurrentTurn() {
        stopEpoch.incrementAndGet()
        currentTurn.get()?.job?.cancel()
    }

    /** Get current stop epoch */
    fun getStopEpoch(): Int = stopEpoch.get()

    private data class TurnState(
        val turnId: String,
        val turnType: String, // "WORK", "NOTIFY", "PEER"
        val job: Job,
        val startTime: Long = System.currentTimeMillis()
    )

    /** Main work turn loop */
    private suspend fun runWorkTurn(trigger: String) {
        val turnId = BrainTrace.nextTurnId()
        val myEpoch = stopEpoch.get()
        val job = coroutineContext[Job]!!
        val state = TurnState(turnId, "WORK", job)
        currentTurn.set(state)

        imageCountThisTurn.set(0)
        val steps = mutableListOf<WorkStep>()
        var totalImages = 0
        var totalTools = 0
        var finalOutput: String? = null
        var stoppedEarly = false
        var stopReason: String? = null

        BrainTrace.turnStart(turnId, "WORK", trigger)

        try {
            val config = host.brainConfig()
            val notebook = host.notebook()
            val guard = host.guard()

            // Build system prompt
            val systemPrompt = LocalPrompt.withPersona(
                config = config,
                notebook = notebook,
                guard = guard,
                activeScene = host.activeScene(),
                friendsIndex = host.friendsIndex(),
                gearIndex = host.gearIndex(),
                notebookIndex = host.notebookIndex(),
                planIndex = host.planIndex(),
                selfhoodIndex = host.selfhoodIndex()
            )

            // Build messages: system + scrollback + current trigger
            val messages = buildMessages(systemPrompt, trigger)

            var step = 0
            while (step < TurnGuards.MAX_STEPS) {
                // Check cancellation
                if (stopEpoch.get() != myEpoch) {
                    stoppedEarly = true
                    stopReason = "Cancelled by new turn"
                    break
                }

                // Check turn deadline
                if (System.currentTimeMillis() - state.startTime > TurnGuards.TURN_DEADLINE_MS) {
                    stoppedEarly = true
                    stopReason = "Turn deadline exceeded"
                    break
                }

                step++

                // Call model
                val response = callModelWithDeadline(messages, config)

                // Parse response for tool calls or final output
                val parsed = parseModelResponse(response)

                if (parsed.toolCalls.isNotEmpty()) {
                    // Execute tools serially
                    for (toolCall in parsed.toolCalls) {
                        // Check cancellation
                        if (stopEpoch.get() != myEpoch) {
                            stoppedEarly = true
                            stopReason = "Cancelled during tool execution"
                            break
                        }

                        // Validate tool call
                        val guardCheck = TurnGuards.validateToolCall(
                            toolCall.method, "WORK", notebook
                        )
                        BrainTrace.guardCheck(turnId, guardCheck == null, guardCheck)

                        if (guardCheck != null) {
                            val errorStep = WorkStep(
                                step = step,
                                method = toolCall.method,
                                params = toolCall.params,
                                result = null,
                                error = guardCheck,
                                durationMs = 0
                            )
                            steps.add(errorStep)
                            host.onStep(errorStep)
                            continue
                        }

                        // Execute tool with deadline
                        val toolStart = System.currentTimeMillis()
                        BrainTrace.toolCall(turnId, step, toolCall.method, toolCall.params)

                        val toolResult = withTimeoutOrNull(TurnGuards.TOOL_DEADLINE_MS) {
                            dispatchTool(toolCall.method, toolCall.params)
                        }

                        val duration = System.currentTimeMillis() - toolStart

                        if (toolResult == null) {
                            val errorStep = WorkStep(
                                step = step,
                                method = toolCall.method,
                                params = toolCall.params,
                                result = null,
                                error = "Tool timeout (${TurnGuards.TOOL_DEADLINE_MS}ms)",
                                durationMs = duration
                            )
                            steps.add(errorStep)
                            host.onStep(errorStep)
                            BrainTrace.toolResult(turnId, step, toolCall.method, "", "Timeout")
                            continue
                        }

                        val resultStr = toolResult.toString()
                        val successStep = WorkStep(
                            step = step,
                            method = toolCall.method,
                            params = toolCall.params,
                            result = resultStr,
                            error = null,
                            durationMs = duration
                        )
                        steps.add(successStep)
                        host.onStep(successStep)
                        BrainTrace.toolResult(turnId, step, toolCall.method, resultStr, null)

                        // Add tool result to messages for next iteration
                        messages.add(ToolResultMessage(toolCall.id, resultStr))
                        totalTools++

                        // Check if this was a vision tool (counts toward image ration)
                        if (toolCall.method == "screen.tap_target" || toolCall.method == "screen.describe") {
                            totalImages++
                            imageCountThisTurn.incrementAndGet()
                            BrainTrace.imageRation(turnId, totalImages, TurnGuards.MAX_IMAGES_PER_TURN)
                        }

                        // Check image ration
                        if (totalImages > TurnGuards.MAX_IMAGES_PER_TURN) {
                            stoppedEarly = true
                            stopReason = "Image ration exceeded"
                            break
                        }
                    }

                    if (stoppedEarly) break
                } else if (parsed.finalOutput != null) {
                    finalOutput = parsed.finalOutput
                    break
                } else {
                    // No tool calls, no final output - model confused
                    stoppedEarly = true
                    stopReason = "Model produced no actionable output"
                    break
                }
            }

        } catch (e: Exception) {
            stoppedEarly = true
            stopReason = "Exception: ${e.message}"
            BrainTrace.error(turnId, stopReason!!, e)
        }

        val totalDuration = System.currentTimeMillis() - state.startTime
        val result = WorkResult(
            steps = steps,
            totalDurationMs = totalDuration,
            totalImagesUsed = totalImages,
            totalToolsCalled = totalTools,
            finalOutput = finalOutput,
            stoppedEarly = stoppedEarly,
            stopReason = stopReason
        )

        host.onWorkResult(result)
        host.onTurnEnd()
        BrainTrace.turnEnd(turnId, result)
        currentTurn.set(null)
    }

    /** Notify turn - untrusted, guard holds, limited tools */
    private suspend fun runNotifyTurn(notification: String) {
        val turnId = BrainTrace.nextTurnId()
        val myEpoch = stopEpoch.get()
        val job = coroutineContext[Job]!!
        val state = TurnState(turnId, "NOTIFY", job)
        currentTurn.set(state)

        val steps = mutableListOf<WorkStep>()
        var finalOutput: String? = null

        BrainTrace.turnStart(turnId, "NOTIFY", "notification")

        try {
            val config = host.brainConfig()
            val notebook = host.notebook()
            val guard = host.guard()

            // Build system prompt (guard injected, but limited tools)
            val systemPrompt = LocalPrompt.withPersona(
                config = config,
                notebook = notebook,
                guard = guard,
                activeScene = host.activeScene(),
                friendsIndex = host.friendsIndex(),
                gearIndex = host.gearIndex(),
                notebookIndex = host.notebookIndex(),
                planIndex = host.planIndex(),
                selfhoodIndex = host.selfhoodIndex()
            )

            val messages = buildMessages(systemPrompt, "NOTIFICATION: $notification")

            // Single model call for notify turns
            val response = callModelWithDeadline(messages, config)
            val parsed = parseModelResponse(response)

            if (parsed.finalOutput != null) {
                finalOutput = parsed.finalOutput
            }

            // Note: notify turns don't execute tools, just produce a note
            host.note(finalOutput ?: "Notification received")

        } catch (e: Exception) {
            BrainTrace.error(turnId, "Notify turn error: ${e.message}", e)
        }

        host.onTurnEnd()
        BrainTrace.turnEnd(turnId, WorkResult(
            steps = steps,
            totalDurationMs = System.currentTimeMillis() - state.startTime,
            totalImagesUsed = 0,
            totalToolsCalled = 0,
            finalOutput = finalOutput
        ))
        currentTurn.set(null)
    }

    /** Peer turn - untrusted, from QR-paired friend */
    private suspend fun runPeerTurn(peerId: String, message: String) {
        val turnId = BrainTrace.nextTurnId()
        val myEpoch = stopEpoch.get()
        val job = coroutineContext[Job]!!
        val state = TurnState(turnId, "PEER", job)
        currentTurn.set(state)

        val steps = mutableListOf<WorkStep>()
        var finalOutput: String? = null

        BrainTrace.turnStart(turnId, "PEER", "peer:$peerId")

        try {
            val config = host.brainConfig()
            val notebook = host.notebook()
            val guard = host.guard()

            val systemPrompt = LocalPrompt.withPersona(
                config = config,
                notebook = notebook,
                guard = guard,
                activeScene = host.activeScene(),
                friendsIndex = host.friendsIndex(),
                gearIndex = host.gearIndex(),
                notebookIndex = host.notebookIndex(),
                planIndex = host.planIndex(),
                selfhoodIndex = host.selfhoodIndex()
            )

            val messages = buildMessages(systemPrompt, "PEER MESSAGE from $peerId: $message")

            val response = callModelWithDeadline(messages, config)
            val parsed = parseModelResponse(response)

            if (parsed.finalOutput != null) {
                finalOutput = parsed.finalOutput
            }

            host.note(finalOutput ?: "Peer message received")

        } catch (e: Exception) {
            BrainTrace.error(turnId, "Peer turn error: ${e.message}", e)
        }

        host.onTurnEnd()
        BrainTrace.turnEnd(turnId, WorkResult(
            steps = steps,
            totalDurationMs = System.currentTimeMillis() - state.startTime,
            totalImagesUsed = 0,
            totalToolsCalled = 0,
            finalOutput = finalOutput
        ))
        currentTurn.set(null)
    }

    /** Build message list for model call */
    private fun buildMessages(systemPrompt: String, userContent: String): MutableList<Any> {
        val messages = mutableListOf<Any>()
        messages.add(SystemMessage(systemPrompt))

        // Add scrollback (last 20 rows)
        val scrollback = host.scrollback().takeLast(20)
        for (row in scrollback) {
            when (row.role) {
                ScrollbackRow.Role.USER -> messages.add(UserMessage(row.text))
                ScrollbackRow.Role.ASSISTANT -> messages.add(AssistantMessage(row.text))
                ScrollbackRow.Role.EVENT -> messages.add(EventMessage(row.text))
            }
        }

        messages.add(UserMessage(userContent))
        return messages
    }

    /** Call model with deadline and prefix cache preservation */
    private suspend fun callModelWithDeadline(messages: List<Any>, config: BrainConfig): String {
        val preset = when (config.mode) {
            "hub" -> callHubModel(messages, config)
            else -> callLocalModel(messages, config)
        }
        return withTimeoutOrNull(TurnGuards.TURN_DEADLINE_MS) { preset } ?: throw TimeoutCancellationException("Model call timeout")
    }

    /** Call local model (DeepSeek via OpenAI-compatible API) */
    private suspend fun callLocalModel(messages: List<Any>, config: BrainConfig): String {
        // This will be implemented with actual HTTP call to DeepSeek/OpenAI-compatible endpoint
        // For now, return a mock response structure
        return withContext(Dispatchers.IO) {
            // TODO: Implement actual HTTP call to config.baseUrl with config.apiKey
            // Using OkHttp with the messages serialized as OpenAI chat format
            // Preserve prefixCache: send cached prefix + new messages
            mockModelResponse(messages)
        }
    }

    /** Call hub model (WebSocket to remote brain) */
    private suspend fun callHubModel(messages: List<Any>, config: BrainConfig): String {
        return withContext(Dispatchers.IO) {
            // TODO: Implement WebSocket connection to config.hubUrl
            mockModelResponse(messages)
        }
    }

    /** Mock model response for development */
    private fun mockModelResponse(messages: List<Any>): String {
        // Find last user message
        val lastUser = messages.lastOrNull { it is UserMessage } as? UserMessage
        val content = lastUser?.content ?: ""

        // Simple heuristic responses for testing
        return if (content.contains("screen.describe")) {
            """{"tool_calls": [{"id": "call_1", "type": "function", "function": {"name": "screen.describe", "arguments": "{}"}}]}"""
        } else if (content.contains("NOTIFICATION")) {
            """{"final_output": "Notification noted."}"""
        } else if (content.contains("PEER MESSAGE")) {
            """{"final_output": "Peer message received."}"""
        } else {
            """{"final_output": "I'll help with that."}"""
        }
    }

    /** Parse model response for tool calls or final output */
    private fun parseModelResponse(response: String): ParsedResponse {
        try {
            val json = JSONObject(response)
            val toolCalls = mutableListOf<ToolCall>()

            if (json.has("tool_calls")) {
                val arr = json.getJSONArray("tool_calls")
                for (i in 0 until arr.length()) {
                    val tc = arr.getJSONObject(i)
                    val fn = tc.getJSONObject("function")
                    toolCalls.add(ToolCall(
                        id = tc.getString("id"),
                        method = ToolSchemas.methodOf(fn.getString("name")) ?: fn.getString("name"),
                        params = fn.getString("arguments")
                    ))
                }
            }

            val finalOutput = json.optString("final_output", null)
            return ParsedResponse(toolCalls, finalOutput)
        } catch (e: Exception) {
            return ParsedResponse(emptyList(), null)
        }
    }

    data class ToolCall(val id: String, val method: String, val params: String)
    data class ParsedResponse(val toolCalls: List<ToolCall>, val finalOutput: String?)

    // Message types
    sealed class Message
    data class SystemMessage(val content: String) : Message()
    data class UserMessage(val content: String) : Message()
    data class AssistantMessage(val content: String) : Message()
    data class EventMessage(val content: String) : Message()
    data class ToolResultMessage(val toolCallId: String, val content: String) : Message()

    /** Shutdown brain */
    fun shutdown() {
        scope.cancel()
    }
}