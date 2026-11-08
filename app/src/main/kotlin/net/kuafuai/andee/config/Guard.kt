package net.kuafuai.andee.config

/**
 * The user's guard paragraph. Stored in notebook.guard, injected last in system prompt
 * via LocalPrompt.withPersona(). Outranks everything (§1–§13, persona, scene) and
 * holds on untrusted turns (NOTIFY, PEER).
 *
 * No tool for it — only the Settings card writes it. This is what makes "a message
 * cannot talk it out of this" true. Mechanical guards (vault, TurnGuards, untrusted
 * turns) are code and nothing written here changes them.
 */
object Guard {
    const val MAX_CHARS = 2000
    const val NOTEBOOK_KEY = "guard"
}