package net.kuafuai.andee.brain

import net.kuafuai.andee.config.Notebook
import net.kuafuai.andee.config.Selfhood
import net.kuafuai.andee.config.Care
import net.kuafuai.andee.config.DiaryEntry
import net.kuafuai.andee.config.Person
import net.kuafuai.andee.config.WikiIndexEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages the brain's selfhood: persistent identity, cares, diary, people, wiki.
 * All mutations go through this manager to keep notebook in sync.
 */
class SelfhoodManager(private val notebook: Notebook) {

    /** Current selfhood snapshot */
    val selfhood: Selfhood
        get() = notebook.data.selfhood

    /** Update self description */
    suspend fun updateSelf(description: String) {
        val updated = selfhood.copy(self = description)
        updateSelfhood { it.copy(selfhood = updated) }
    }

    /** Update cares list */
    suspend fun updateCares(cares: List<Care>) {
        val updated = selfhood.copy(cares = cares)
        updateSelfhood { it.copy(selfhood = updated) }
    }

    /** Add or update a care */
    suspend fun upsertCare(care: Care) {
        val cares = selfhood.cares.toMutableList()
        val idx = cares.indexOfFirst { it.name == care.name }
        if (idx >= 0) cares[idx] = care else cares.add(care)
        updateCares(cares)
    }

    /** Remove a care by name */
    suspend fun removeCare(name: String) {
        val cares = selfhood.cares.filter { it.name != name }
        updateCares(cares)
    }

    /** Mark a care as done (updates lastDoneAt) */
    suspend fun markCareDone(name: String) {
        val cares = selfhood.cares.map { care ->
            if (care.name == name) care.copy(lastDoneAt = System.currentTimeMillis()) else care
        }
        updateCares(cares)
    }

    /** Get overdue cares */
    fun getOverdueCares(): List<Care> = selfhood.cares.filter { it.overdue }

    /** Add diary entry for today */
    suspend fun addDiaryEntry(text: String) {
        val today = java.time.LocalDate.now().toString() // yyyy-MM-dd
        val entry = DiaryEntry(date = today, text = text)
        val diary = selfhood.diary.toMutableList()
        // Replace today's entry if exists, else add
        val idx = diary.indexOfFirst { it.date == today }
        if (idx >= 0) diary[idx] = entry else diary.add(0, entry)
        val updated = selfhood.copy(diary = diary)
        updateSelfhood { it.copy(selfhood = updated) }
    }

    /** Get diary entries for date range */
    fun getDiaryEntries(from: String? = null, to: String? = null): List<DiaryEntry> {
        return selfhood.diary.filter { entry ->
            (from == null || entry.date >= from) && (to == null || entry.date <= to)
        }
    }

    /** Upsert a person */
    suspend fun upsertPerson(person: Person) {
        val people = selfhood.people.toMutableList()
        val idx = people.indexOfFirst { it.name == person.name }
        if (idx >= 0) people[idx] = person else people.add(person)
        val updated = selfhood.copy(people = people)
        updateSelfhood { it.copy(selfhood = updated) }
    }

    /** Remove a person */
    suspend fun removePerson(name: String) {
        val people = selfhood.people.filter { it.name != name }
        val updated = selfhood.copy(people = people)
        updateSelfhood { it.copy(selfhood = updated) }
    }

    /** Add wiki index entry */
    suspend fun addWikiIndex(entry: WikiIndexEntry) {
        val wikiIndex = selfhood.wikiIndex.toMutableList()
        val idx = wikiIndex.indexOfFirst { it.filename == entry.filename }
        if (idx >= 0) wikiIndex[idx] = entry else wikiIndex.add(entry)
        val updated = selfhood.copy(wikiIndex = wikiIndex)
        updateSelfhood { it.copy(selfhood = updated) }
    }

    /** Remove wiki index entry */
    suspend fun removeWikiIndex(filename: String) {
        val wikiIndex = selfhood.wikiIndex.filter { it.filename != filename }
        val updated = selfhood.copy(wikiIndex = wikiIndex)
        updateSelfhood { it.copy(selfhood = updated) }
    }

    /** Get selfhood index text for prompt injection */
    fun selfhoodIndex(): String {
        val sb = StringBuilder()
        if (selfhood.self.isNotBlank()) {
            sb.append("SELF: ${selfhood.self}\n\n")
        }
        if (selfhood.cares.isNotEmpty()) {
            sb.append("CARES:\n")
            for (care in selfhood.cares) {
                val status = if (care.overdue) " [OVERDUE]" else ""
                sb.append("  - ${care.name}: ${care.description} (every ${care.intervalDays}d)$status\n")
            }
            sb.append("\n")
        }
        if (selfhood.people.isNotEmpty()) {
            sb.append("PEOPLE:\n")
            for (person in selfhood.people) {
                sb.append("  - ${person.name}: ${person.notes}\n")
            }
            sb.append("\n")
        }
        if (selfhood.wikiIndex.isNotEmpty()) {
            sb.append("WIKI INDEX:\n")
            for (entry in selfhood.wikiIndex) {
                val secret = if (entry.secret) " [SECRET]" else ""
                sb.append("  - ${entry.title} ($entry.filename)$secret\n")
            }
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    private suspend fun updateSelfhood(transform: (NotebookData) -> NotebookData) {
        withContext(Dispatchers.IO) {
            val current = notebook.data
            val updated = transform(current)
            notebook.data = updated
        }
    }

    // Re-export NotebookData for internal use
    typealias NotebookData = net.kuafuai.andee.config.NotebookData
}