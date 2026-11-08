package net.kuafuai.andee.brain

import kotlinx.serialization.Serializable

/**
 * Represents a single step in a work turn.
 * Serialized for trace/logging.
 */
@Serializable
data class WorkStep(
    val step: Int,
    val method: String,
    val params: String,
    val result: String?,
    val error: String?,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val imagesUsed: Int = 0
) {
    val success: Boolean get() = error == null
    val isToolCall: Boolean get() = method.isNotBlank()
}

/** Result of a completed work turn */
@Serializable
data class WorkResult(
    val steps: List<WorkStep>,
    val totalDurationMs: Long,
    val totalImagesUsed: Int,
    val totalToolsCalled: Int,
    val finalOutput: String?,
    val stoppedEarly: Boolean = false,
    val stopReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val success: Boolean get() = steps.all { it.success } && !stoppedEarly
}