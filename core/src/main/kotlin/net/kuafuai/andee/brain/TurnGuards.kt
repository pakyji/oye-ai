package net.kuafuai.andee.brain

import net.kuafuai.andee.config.Notebook

/**
 * Turn validation guards. These are mechanical (code-enforced) and cannot be
 * overridden by prompt or user message. They enforce the untrusted turn contract.
 */
object TurnGuards {

    /** Maximum steps per work turn */
    const val MAX_STEPS = 40

    /** Maximum images per turn (vision calls) */
    const val MAX_IMAGES_PER_TURN = 3

    /** Maximum tool calls per turn */
    const val MAX_TOOLS_PER_TURN = 20

    /** Deadline for a single tool call (ms) */
    const val TOOL_DEADLINE_MS = 30_000

    /** Deadline for entire work turn (ms) */
    const val TURN_DEADLINE_MS = 180_000

    /** Minimum interval between heartbeat work turns (ms) */
    const val HEARTBEAT_MIN_INTERVAL_MS = 5 * 60 * 1000 // 5 minutes

    /** Maximum quiet turns before forced work turn */
    const val MAX_QUIET_TURNS = 12

    /**
     * Check if a turn is "untrusted" (NOTIFY or PEER).
     * Untrusted turns: guard holds, no vault access, no peer writes, no todo writes.
     */
    fun isUntrusted(turnType: String): Boolean = turnType == "NOTIFY" || turnType == "PEER"

    /**
     * Validate a work turn request before execution.
     * Returns null if valid, error message if invalid.
     */
    fun validateWorkTurn(
        config: BrainConfig,
        notebook: Notebook,
        stepCount: Int,
        imageCount: Int,
        toolCount: Int
    ): String? {
        if (stepCount > MAX_STEPS) return "Step limit exceeded ($MAX_STEPS)"
        if (imageCount > MAX_IMAGES_PER_TURN) return "Image limit exceeded ($MAX_IMAGES_PER_TURN)"
        if (toolCount > MAX_TOOLS_PER_TURN) return "Tool call limit exceeded ($MAX_TOOLS_PER_TURN)"
        return null
    }

    /**
     * Validate a tool call for the current turn context.
     * Returns null if allowed, error message if blocked.
     */
    fun validateToolCall(
        method: String,
        turnType: String,
        notebook: Notebook
    ): String? {
        val untrusted = isUntrusted(turnType)

        // Vault operations blocked on untrusted turns
        if (untrusted && method.startsWith("vault.")) {
            return "Vault access blocked on untrusted turn"
        }

        // Peer write operations blocked on untrusted turns
        if (untrusted && (method == "peer.send" || method == "peer.invite")) {
            return "Peer write blocked on untrusted turn"
        }

        // Todo writes blocked on untrusted turns
        if (untrusted && method == "todo.add") {
            return "Todo write blocked on untrusted turn"
        }

        // Scene learn blocked on untrusted turns
        if (untrusted && method == "scene.learn") {
            return "Scene learn blocked on untrusted turn"
        }

        return null
    }

    /** Check if heartbeat should trigger a work turn */
    fun shouldHeartbeatWork(
        lastWorkTurnAt: Long,
        quietTurnCount: Int
    ): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceWork = now - lastWorkTurnAt
        return timeSinceWork > HEARTBEAT_MIN_INTERVAL_MS || quietTurnCount >= MAX_QUIET_TURNS
    }
}