package net.kuafuai.andee.brain

import android.media.MediaRecorder
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Meeting recorder with rotating ASR sessions.
 * Records audio in chunks, runs ASR on each chunk, folds into topic.
 */
class MeetingManager(private val context: Context) {

    data class Meeting(
        val id: String,
        val title: String,
        val startedAt: Long,
        val endedAt: Long? = null,
        val chunks: List<Chunk> = emptyList(),
        val topicId: String? = null
    ) {
        val isActive: Boolean get() = endedAt == null
        val duration: Long get() = (endedAt ?: System.currentTimeMillis()) - startedAt
    }

    data class Chunk(
        val index: Int,
        val audioPath: String,
        val transcript: String? = null,
        val startedAt: Long,
        val endedAt: Long
    )

    private var currentMeeting: Meeting? = null
    private var currentRecorder: MediaRecorder? = null
    private var currentChunkFile: File? = null
    private var chunkIndex = 0

    private val recordingsDir = File(context.filesDir, "meetings").apply { mkdirs() }

    /** Start a new meeting recording */
    suspend fun startMeeting(title: String): Meeting = withContext(Dispatchers.IO) {
        val meeting = Meeting(
            id = UUID.randomUUID().toString(),
            title = title,
            startedAt = System.currentTimeMillis()
        )
        currentMeeting = meeting
        chunkIndex = 0
        startNextChunk()
        meeting
    }

    /** Stop current meeting */
    suspend fun stopMeeting(): Meeting? = withContext(Dispatchers.IO) {
        val meeting = currentMeeting ?: return@withContext null
        stopCurrentChunk()
        val ended = meeting.copy(endedAt = System.currentTimeMillis())
        currentMeeting = null
        return@withContext ended
    }

    /** Get current meeting */
    fun getCurrentMeeting(): Meeting? = currentMeeting

    /** Start ASR on a chunk (called after chunk recorded) */
    suspend fun transcribeChunk(chunk: Chunk): String = withContext(Dispatchers.IO) {
        // TODO: Call ASR (Volcengine/Google/Whisper) on chunk.audioPath
        // For now, return placeholder
        "[Transcript for chunk ${chunk.index}]"
    }

    /** Fold meeting into a topic */
    suspend fun foldMeeting(meeting: Meeting, topicManager: TopicManager): String? = withContext(Dispatchers.IO) {
        val fullTranscript = meeting.chunks.map { it.transcript ?: "" }.joinToString("\n")
        val topic = topicManager.foldTopic(
            title = meeting.title,
            summary = fullTranscript.take(500),
            keywords = extractKeywords(fullTranscript),
            messageCount = meeting.chunks.size
        )
        // Update meeting with topicId
        // (would need to persist meeting to notebook)
        topic.id
    }

    private fun extractKeywords(text: String): List<String> {
        // Simple keyword extraction - in production use TF-IDF or similar
        val words = text.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length > 4 }
            .groupBy { it }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .map { it.first }
        return words
    }

    private fun startNextChunk() {
        stopCurrentChunk()
        chunkIndex++
        val chunkFile = File(recordingsDir, "${currentMeeting?.id}_chunk$chunkIndex.m4a")
        currentChunkFile = chunkFile

        currentRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(chunkFile.absolutePath)
            try {
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopCurrentChunk() {
        currentRecorder?.apply {
            try { stop() } catch (e: Exception) {}
            try { release() } catch (e: Exception) {}
        }
        currentRecorder = null

        // Create chunk record
        val chunk = Chunk(
            index = chunkIndex,
            audioPath = currentChunkFile?.absolutePath ?: "",
            startedAt = System.currentTimeMillis() - 30000, // ~30s chunks
            endedAt = System.currentTimeMillis()
        )
        currentMeeting?.let { m ->
            currentMeeting = m.copy(chunks = m.chunks + chunk)
        }
    }

    /** Rotate chunk (call periodically, e.g. every 30s) */
    suspend fun rotateChunk() = withContext(Dispatchers.IO) {
        startNextChunk()
    }
}