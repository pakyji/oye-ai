package net.kuafuai.andee.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Encrypted notebook storage. All persistent brain data lives here as a single JSON blob
 * in EncryptedSharedPreferences. Sections are merged on write so the whole notebook
 * is always one atomic write — no partial states survive a crash.
 *
 * Sections:
 * - guard: String — user's rules paragraph
 * - selfhood: Selfhood — self, cares, diary, people, wiki index
 * - plan: Plan — day plan + directions
 * - memories: Map<String, String> — key-value recalls
 * - todos: List<Todo> — scheduled wake-ups
 * - scenes: List<Scene> — learned scenes
 * - gear: List<GearNote> — notes on installed tools
 * - wiki: Map<String, WikiArticle> — article index (bodies in .md files)
 * - drive: DriveState — heartbeat/work turn counters
 */
class Notebook(private val context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        "notebook",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val gson = Gson()
    private val notebookKey = "notebook_blob"

    /** Current notebook data (never null, empty defaults) */
    var data: NotebookData
        get() = gson.fromJson(prefs.getString(notebookKey, "{}"), NotebookData::class.java)
        set(value) {
            prefs.edit().putString(notebookKey, gson.toJson(value)).apply()
        }

    /** Wipe everything — factory reset */
    fun wipe() {
        prefs.edit().clear().apply()
    }

    /** Export full notebook as JSON string */
    fun export(): String = gson.toJson(data)

    /** Import notebook from JSON string */
    fun importNotebook(json: String) {
        val imported = gson.fromJson(json, NotebookData::class.java)
        data = imported
    }
}

data class NotebookData(
    val guard: String = "",
    val selfhood: Selfhood = Selfhood(),
    val plan: Plan = Plan(),
    val memories: Map<String, String> = emptyMap(),
    val todos: List<Todo> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val gear: List<GearNote> = emptyList(),
    val wiki: Map<String, WikiArticle> = emptyMap(),
    val drive: DriveState = DriveState(),
    val lastSweepAt: Long = 0,
    val notifyRule: String = ""
) {
    fun copy(
        guard: String? = null,
        selfhood: Selfhood? = null,
        plan: Plan? = null,
        memories: Map<String, String>? = null,
        todos: List<Todo>? = null,
        scenes: List<Scene>? = null,
        gear: List<GearNote>? = null,
        wiki: Map<String, WikiArticle>? = null,
        drive: DriveState? = null,
        lastSweepAt: Long? = null,
        notifyRule: String? = null
    ): NotebookData = NotebookData(
        guard = guard ?: this.guard,
        selfhood = selfhood ?: this.selfhood,
        plan = plan ?: this.plan,
        memories = memories ?: this.memories,
        todos = todos ?: this.todos,
        scenes = scenes ?: this.scenes,
        gear = gear ?: this.gear,
        wiki = wiki ?: this.wiki,
        drive = drive ?: this.drive,
        lastSweepAt = lastSweepAt ?: this.lastSweepAt,
        notifyRule = notifyRule ?: this.notifyRule
    )
}

data class Selfhood(
    val self: String = "",
    val cares: List<Care> = emptyList(),
    val diary: List<DiaryEntry> = emptyList(),
    val people: List<Person> = emptyList(),
    val wikiIndex: List<WikiIndexEntry> = emptyList()
) {
    fun copy(
        self: String? = null,
        cares: List<Care>? = null,
        diary: List<DiaryEntry>? = null,
        people: List<Person>? = null,
        wikiIndex: List<WikiIndexEntry>? = null
    ): Selfhood = Selfhood(
        self = self ?: this.self,
        cares = cares ?: this.cares,
        diary = diary ?: this.diary,
        people = people ?: this.people,
        wikiIndex = wikiIndex ?: this.wikiIndex
    )
}

data class Care(
    val name: String,
    val description: String,
    val intervalDays: Int,
    val lastDoneAt: Long = 0,
    val source: String = ""
) {
    val overdue: Boolean get() = System.currentTimeMillis() - lastDoneAt > intervalDays * 86400000L
}

data class DiaryEntry(
    val date: String, // yyyy-MM-dd
    val text: String
)

data class Person(
    val name: String,
    val notes: String = "",
    val metAt: Long = System.currentTimeMillis()
)

data class WikiIndexEntry(
    val title: String,
    val filename: String,
    val secret: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class WikiArticle(
    val title: String,
    val body: String,
    val secret: Boolean = false
)

data class Plan(
    val directions: List<Direction> = emptyList(),
    val dayPlan: DayPlan = DayPlan(),
    val morningTodoId: String? = null,
    val nightlyPictureTodoId: String? = null
) {
    fun copy(
        directions: List<Direction>? = null,
        dayPlan: DayPlan? = null,
        morningTodoId: String? = null,
        nightlyPictureTodoId: String? = null
    ): Plan = Plan(
        directions = directions ?: this.directions,
        dayPlan = dayPlan ?: this.dayPlan,
        morningTodoId = morningTodoId ?: this.morningTodoId,
        nightlyPictureTodoId = nightlyPictureTodoId ?: this.nightlyPictureTodoId
    )
}

data class Direction(
    val name: String,
    val description: String,
    val intervalDays: Int,
    val source: String,
    val active: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

data class DayPlan(
    val intent: String = "",
    val items: List<DayPlanItem> = emptyList(),
    val quiet: List<String> = emptyList(),
    val date: String = "" // yyyy-MM-dd
) {
    fun copy(
        intent: String? = null,
        items: List<DayPlanItem>? = null,
        quiet: List<String>? = null,
        date: String? = null
    ): DayPlan = DayPlan(
        intent = intent ?: this.intent,
        items = items ?: this.items,
        quiet = quiet ?: this.quiet,
        date = date ?: this.date
    )
}

data class DayPlanItem(
    val text: String,
    val because: String, // links to direction or todo
    val done: Boolean = false,
    val dropped: Boolean = false
)

data class Todo(
    val id: String,
    val text: String,
    val at: Long?, // one-off timestamp
    val cron: String?, // cron expression for recurring
    val recurring: Boolean = false,
    val untrusted: Boolean = false, // set on NOTIFY turn
    val createdAt: Long = System.currentTimeMillis(),
    var lastRunAt: Long = 0
)

data class Scene(
    val name: String,
    val goal: String,
    val voice: String,
    val rules: String,
    val routine: String,
    val example: String,
    val learnedAt: Long = System.currentTimeMillis()
)

data class GearNote(
    val packageName: String,
    val name: String,
    val description: String,
    val capabilities: String,
    val limit: String?,
    val signedInAt: Long?,
    val visibility: String = "default" // default, hidden, shown
)

data class DriveState(
    val looksToday: Int = 0,
    val workTurnsToday: Int = 0,
    val lastWorkTurnAt: Long = 0,
    val todayCallIds: List<String> = emptyList(),
    val thoughts: List<String> = emptyList()
)