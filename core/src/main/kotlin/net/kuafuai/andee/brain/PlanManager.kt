package net.kuafuai.andee.brain

import net.kuafuai.andee.config.Notebook
import net.kuafuai.andee.config.Plan
import net.kuafuai.andee.config.Direction
import net.kuafuai.andee.config.DayPlan
import net.kuafuai.andee.config.DayPlanItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Manages plans: day plan, directions, todos integration.
 */
class PlanManager(private val notebook: Notebook) {

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    /** Current plan snapshot */
    val plan: Plan
        get() = notebook.data.plan

    /** Get today's day plan (creates empty if not exists) */
    fun todayPlan(): DayPlan {
        val today = LocalDate.now().format(dateFormat)
        val current = plan.dayPlan
        return if (current.date == today) current else DayPlan(date = today)
    }

    /** Set day plan intent */
    suspend fun setDayPlanIntent(intent: String) {
        val today = todayPlan().copy(intent = intent)
        updatePlan { it.copy(dayPlan = today) }
    }

    /** Add day plan item */
    suspend fun addDayPlanItem(text: String, because: String) {
        val today = todayPlan()
        val item = DayPlanItem(text = text, because = because)
        val updated = today.copy(items = today.items + item)
        updatePlan { it.copy(dayPlan = updated) }
    }

    /** Mark day plan item done */
    suspend fun markDayPlanItemDone(index: Int) {
        val today = todayPlan()
        if (index in today.items.indices) {
            val items = today.items.toMutableList()
            items[index] = items[index].copy(done = true)
            val updated = today.copy(items = items)
            updatePlan { it.copy(dayPlan = updated) }
        }
    }

    /** Drop day plan item */
    suspend fun dropDayPlanItem(index: Int) {
        val today = todayPlan()
        if (index in today.items.indices) {
            val items = today.items.toMutableList()
            items[index] = items[index].copy(dropped = true)
            val updated = today.copy(items = items)
            updatePlan { it.copy(dayPlan = updated) }
        }
    }

    /** Set quiet items for today */
    suspend fun setQuietItems(items: List<String>) {
        val today = todayPlan().copy(quiet = items)
        updatePlan { it.copy(dayPlan = today) }
    }

    /** Add direction */
    suspend fun addDirection(direction: Direction) {
        val directions = plan.directions + direction
        updatePlan { it.copy(directions = directions) }
    }

    /** Update direction */
    suspend fun updateDirection(name: String, transform: (Direction) -> Direction) {
        val directions = plan.directions.map { d ->
            if (d.name == name) transform(d) else d
        }
        updatePlan { it.copy(directions = directions) }
    }

    /** Remove direction */
    suspend fun removeDirection(name: String) {
        val directions = plan.directions.filter { it.name != name }
        updatePlan { it.copy(directions = directions) }
    }

    /** Toggle direction active */
    suspend fun toggleDirection(name: String) {
        updateDirection(name) { it.copy(active = !it.active) }
    }

    /** Get active directions */
    fun activeDirections(): List<Direction> = plan.directions.filter { it.active }

    /** Get plan index text for prompt injection */
    fun planIndex(): String {
        val sb = StringBuilder()
        val today = todayPlan()

        if (today.intent.isNotBlank()) {
            sb.append("TODAY'S INTENT: ${today.intent}\n\n")
        }

        val pendingItems = today.items.filter { !it.done && !it.dropped }
        if (pendingItems.isNotEmpty()) {
            sb.append("TODAY'S PLAN:\n")
            for ((i, item) in pendingItems.withIndex()) {
                sb.append("  ${i + 1}. ${item.text} (because: ${item.because})\n")
            }
            sb.append("\n")
        }

        val doneItems = today.items.filter { it.done }
        if (doneItems.isNotEmpty()) {
            sb.append("DONE TODAY:\n")
            for (item in doneItems) {
                sb.append("  - ${item.text}\n")
            }
            sb.append("\n")
        }

        if (today.quiet.isNotEmpty()) {
            sb.append("QUIET HOURS:\n")
            for (q in today.quiet) sb.append("  - $q\n")
            sb.append("\n")
        }

        val activeDirs = activeDirections()
        if (activeDirs.isNotEmpty()) {
            sb.append("ACTIVE DIRECTIONS:\n")
            for (dir in activeDirs) {
                sb.append("  - ${dir.name}: ${dir.description} (every ${dir.intervalDays}d)\n")
            }
            sb.append("\n")
        }

        return sb.toString().trim()
    }

    /** Check and advance day plan (call on date change) */
    suspend fun advanceDayIfNeeded() {
        val today = LocalDate.now().format(dateFormat)
        if (plan.dayPlan.date != today) {
            // Archive yesterday's plan (could move to diary)
            val newPlan = DayPlan(date = today)
            updatePlan { it.copy(dayPlan = newPlan) }
        }
    }

    private suspend fun updatePlan(transform: (Plan) -> Plan) {
        withContext(Dispatchers.IO) {
            val current = notebook.data
            val updatedPlan = transform(current.plan)
            notebook.data = current.copy(plan = updatedPlan)
        }
    }
}