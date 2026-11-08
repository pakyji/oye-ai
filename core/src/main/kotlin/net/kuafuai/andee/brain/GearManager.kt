package net.kuafuai.andee.brain

import net.kuafuai.andee.config.Notebook
import net.kuafuai.andee.config.GearNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages gear (installed tools/apps) knowledge.
 */
class GearManager(private val notebook: Notebook) {

    /** All gear notes */
    val gear: List<GearNote>
        get() = notebook.data.gear

    /** Upsert gear note */
    suspend fun upsertGear(note: GearNote) {
        withContext(Dispatchers.IO) {
            val current = notebook.data
            val idx = current.gear.indexOfFirst { it.packageName == note.packageName }
            val updatedGear = if (idx >= 0) {
                current.gear.toMutableList().apply { this[idx] = note }
            } else {
                current.gear + note
            }
            notebook.data = current.copy(gear = updatedGear)
        }
    }

    /** Remove gear */
    suspend fun removeGear(packageName: String) {
        withContext(Dispatchers.IO) {
            val current = notebook.data
            val updatedGear = current.gear.filter { it.packageName != packageName }
            notebook.data = current.copy(gear = updatedGear)
        }
    }

    /** Get gear by package name */
    fun getGear(packageName: String): GearNote? = gear.find { it.packageName == packageName }

    /** Get visible gear (not hidden) */
    fun visibleGear(): List<GearNote> = gear.filter { it.visibility != "hidden" }

    /** Gear index for prompt */
    fun gearIndex(): String {
        val visible = visibleGear()
        if (visible.isEmpty()) return ""
        val sb = StringBuilder("INSTALLED GEAR:\n")
        for (g in visible) {
            sb.append("  - ${g.name} (${g.packageName}): ${g.description}\n")
            if (g.capabilities.isNotBlank()) sb.append("    Capabilities: ${g.capabilities}\n")
            if (g.limit != null) sb.append("    Limit: ${g.limit}\n")
        }
        return sb.toString().trim()
    }
}