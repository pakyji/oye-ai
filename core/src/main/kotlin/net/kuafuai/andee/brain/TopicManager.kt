package net.kuafuai.andee.brain

import net.kuafuai.andee.config.Notebook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Topic subsystem: fold long conversations into searchable topics.
 * Topics are stored in notebook.memories under "topics" key.
 */
class TopicManager(private val notebook: Notebook) {

    data class Topic(
        val id: String,
        val title: String,
        val summary: String,
        val keywords: List<String>,
        val messageCount: Int,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    private val topicsKey = "topics"

    /** All topics */
    val topics: List<Topic>
        get() = getTopicsFromNotebook()

    private fun getTopicsFromNotebook(): List<Topic> {
        val json = notebook.data.memories[topicsKey] ?: "[]"
        return com.google.gson.Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<List<Topic>>() {}.type)
    }

    private suspend fun saveTopics(topics: List<Topic>) = withContext(Dispatchers.IO) {
        val json = com.google.gson.Gson().toJson(topics)
        val current = notebook.data
        val updatedMemories = current.memories.toMutableMap().apply { put(topicsKey, json) }
        notebook.data = current.copy(memories = updatedMemories)
    }

    /** Fold a conversation range into a topic */
    suspend fun foldTopic(title: String, summary: String, keywords: List<String>, messageCount: Int): Topic {
        return withContext(Dispatchers.IO) {
            val topic = Topic(
                id = UUID.randomUUID().toString(),
                title = title,
                summary = summary,
                keywords = keywords,
                messageCount = messageCount
            )
            val updated = topics.toMutableList().apply { add(0, topic) } // newest first
            saveTopics(updated)
            topic
        }
    }

    /** Get topic by ID */
    fun getTopic(id: String): Topic? = topics.find { it.id == id }

    /** Search topics by keyword */
    fun searchTopics(query: String): List<Topic> {
        val q = query.lowercase()
        return topics.filter { t ->
            t.title.lowercase().contains(q) ||
            t.summary.lowercase().contains(q) ||
            t.keywords.any { it.lowercase().contains(q) }
        }
    }

    /** List recent topics */
    fun recentTopics(limit: Int = 10): List<Topic> = topics.take(limit)

    /** Delete topic */
    suspend fun deleteTopic(id: String) = withContext(Dispatchers.IO) {
        val updated = topics.filter { it.id != id }
        saveTopics(updated)
    }

    /** Topic index for prompt */
    fun topicIndex(): String {
        if (topics.isEmpty()) return ""
        val sb = StringBuilder("TOPICS (folded conversations):\n")
        for (t in topics.take(10)) {
            sb.append("  - ${t.title}: ${t.summary} [${t.keywords.joinToString(", ")}]\n")
        }
        return sb.toString().trim()
    }
}