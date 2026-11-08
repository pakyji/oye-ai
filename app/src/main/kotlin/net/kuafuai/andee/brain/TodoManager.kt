package net.kuafuai.andee.brain

import net.kuafuai.andee.config.Notebook
import net.kuafuai.andee.config.Todo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages todos: one-off, recurring (cron), and untrusted (from NOTIFY turns).
 */
class TodoManager(private val notebook: Notebook) {

    /** All todos */
    val todos: List<Todo>
        get() = notebook.data.todos

    /** Pending (not run yet) todos */
    val pendingTodos: List<Todo>
        get() = todos.filter { it.at != null && it.at! > System.currentTimeMillis() || it.cron != null }

    /** Due todos (one-off past due, or recurring due now) */
    fun dueTodos(): List<Todo> {
        val now = System.currentTimeMillis()
        return todos.filter { todo ->
            if (todo.recurring && todo.cron != null) {
                // Simple cron check - in reality would use a cron parser
                isCronDue(todo.cron!!, todo.lastRunAt)
            } else if (todo.at != null) {
                todo.at! <= now && todo.lastRunAt == 0
            } else false
        }
    }

    /** Add a new todo */
    suspend fun addTodo(text: String, at: Long? = null, cron: String? = null, recurring: Boolean = false, untrusted: Boolean = false): Todo {
        val todo = Todo(
            id = UUID.randomUUID().toString(),
            text = text,
            at = at,
            cron = cron,
            recurring = recurring,
            untrusted = untrusted
        )
        return withContext(Dispatchers.IO) {
            val current = notebook.data
            notebook.data = current.copy(todos = current.todos + todo)
            todo
        }
    }

    /** Mark todo as run */
    suspend fun markTodoRun(id: String) {
        withContext(Dispatchers.IO) {
            val current = notebook.data
            val updatedTodos = current.todos.map { todo ->
                if (todo.id == id) todo.copy(lastRunAt = System.currentTimeMillis()) else todo
            }
            notebook.data = current.copy(todos = updatedTodos)
        }
    }

    /** Withdraw (delete) a todo */
    suspend fun withdrawTodo(id: String) {
        withContext(Dispatchers.IO) {
            val current = notebook.data
            val updatedTodos = current.todos.filter { it.id != id }
            notebook.data = current.copy(todos = updatedTodos)
        }
    }

    /** Check if a todo was withdrawn (for NOTIFY turn deduplication) */
    fun todoWithdrawn(text: String): Boolean {
        return todos.any { it.text == text && it.untrusted }
    }

    /** Get todo by id */
    fun getTodo(id: String): Todo? = todos.find { it.id == id }

    /** Simple cron due check (placeholder - use real cron lib in production) */
    private fun isCronDue(cron: String, lastRunAt: Long): Boolean {
        // TODO: Implement proper cron parsing
        // For now, check if it's been more than 24h since last run for daily crons
        if (cron.contains("0 0 * * *")) { // daily at midnight
            return System.currentTimeMillis() - lastRunAt > 24 * 60 * 60 * 1000L
        }
        return false
    }

    /** Todo index for prompt */
    fun todoIndex(): String {
        val due = dueTodos()
        val pending = pendingTodos.filter { it !in due }
        if (due.isEmpty() && pending.isEmpty()) return ""

        val sb = StringBuilder("TODOS:\n")
        if (due.isNotEmpty()) {
            sb.append("  DUE NOW:\n")
            for (todo in due) {
                val recur = if (todo.recurring) " [recurring]" else ""
                val untrusted = if (todo.untrusted) " [untrusted]" else ""
                sb.append("    - ${todo.text}$recur$untrusted\n")
            }
        }
        if (pending.isNotEmpty()) {
            sb.append("  UPCOMING:\n")
            for (todo in pending.take(5)) {
                val time = todo.at?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: todo.cron ?: ""
                sb.append("    - ${todo.text} @ $time\n")
            }
        }
        return sb.toString().trim()
    }
}