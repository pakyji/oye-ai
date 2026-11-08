package com.oyeai.ai

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.Context
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kuafuai.andee.brain.*
import net.kuafuai.andee.config.Notebook
import net.kuafuai.andee.net.ScreenTools
import net.kuafuai.andee.net.CoordinateProtocol
import net.kuafuai.andee.net.ToolSchemas
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android implementation of BrainHost.
 * Bridges the platform-agnostic LocalBrain to Android services.
 */
class AndeeBrainHost(
    private val application: Application,
    private val settings: AppSettings
) : BrainHost, CoroutineScope {

    override val coroutineContext: Job = SupervisorJob() + Dispatchers.Main.immediate

    private val notebook = Notebook(application)
    private val selfhoodManager = SelfhoodManager(notebook)
    private val planManager = PlanManager(notebook)
    private val sceneManager = SceneManager(notebook)
    private val todoManager = TodoManager(notebook)
    private val gearManager = GearManager(notebook)
    private val vaultManager = VaultManager(application)
    private val peerManager = PeerManager(notebook)
    private val topicManager = TopicManager(notebook)
    private val meetingManager = MeetingManager(application)

    private var brain: LocalBrain? = null
    private var screenTools: ScreenTools? = null
    private var accessibilityService: AccessibilityService? = null
    private var mediaProjection: MediaProjection? = null
    private var screenWidth = 1080
    private var screenHeight = 2400

    override val stopEpoch = AtomicInteger(0)

    init {
        brain = LocalBrain(this)
    }

    /** Initialize with accessibility service */
    fun setAccessibilityService(service: AccessibilityService) {
        accessibilityService = service
        screenTools = ScreenTools(this, service, mediaProjection)
    }

    /** Initialize with media projection */
    fun setMediaProjection(projection: MediaProjection) {
        mediaProjection = projection
        screenTools?.let { it } // ScreenTools will use it on next call
    }

    /** Update screen dimensions */
    fun updateScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        screenTools?.updateScreenSize(width, height)
    }

    /** Start a work turn */
    fun startWorkTurn(trigger: String = "heartbeat") {
        brain?.startWorkTurn(trigger)
    }

    /** Start a notify turn */
    fun startNotifyTurn(notification: String) {
        brain?.startNotifyTurn(notification)
    }

    /** Start a peer turn */
    fun startPeerTurn(peerId: String, message: String) {
        brain?.startPeerTurn(peerId, message)
    }

    /** Cancel current turn */
    fun cancelCurrentTurn() {
        brain?.cancelCurrentTurn()
    }

    // ========== BrainHost Implementation ==========

    override fun logVerbose(tag: String, msg: String) = Log.v(tag, msg)
    override fun logDebug(tag: String, msg: String) = Log.d(tag, msg)
    override fun logInfo(tag: String, msg: String) = Log.i(tag, msg)
    override fun logWarn(tag: String, msg: String, throwable: Throwable?) = Log.w(tag, msg, throwable)
    override fun logError(tag: String, msg: String, throwable: Throwable?) = Log.e(tag, msg, throwable)

    override fun locale(): Locale = application.resources.configuration.locales.get(0)

    override fun touchActivity() {
        // Update last activity timestamp for quiet hour logic
    }

    override fun scrollback(): List<ScrollbackRow> {
        // TODO: Load from persistent scrollback storage
        return emptyList()
    }

    override fun notebook(): Notebook = notebook

    override fun brainConfig(): BrainConfig {
        return BrainConfig(
            mode = "local",
            baseUrl = settings.deepseekBaseUrl ?: "https://api.deepseek.com/v1",
            apiKey = settings.deepseekApiKey ?: "",
            model = settings.deepseekModel ?: "deepseek-chat",
            thinking = false,
            reasoningEffort = "medium",
            maxSteps = 40
        )
    }

    override fun guard(): String = notebook.data.guard

    override suspend fun dispatch(method: String, params: String): Any {
        return dispatchTool(method, params)
    }

    override fun onTurnEnd() {
        // Turn ended - could persist state
    }

    override fun onSweepSpent(tokens: Int) {
        // Track token usage
    }

    override fun onQuietFinal() {
        // Quiet turn ended
    }

    override fun onStep(step: WorkStep) {
        // Step completed - could update UI
    }

    override fun activeScene(): String? = sceneManager.activeScene

    override fun friendsIndex(): String = peerManager.friendsIndex()

    override fun gearIndex(): String = gearManager.gearIndex()

    override fun notebookIndex(): Pair<String, String> {
        val memories = notebook.data.memories.map { "${it.key}: ${it.value.take(100)}" }.joinToString("\n")
        val wiki = notebook.data.wiki.keys.joinToString(", ")
        return memories to wiki
    }

    override fun planIndex(): String = planManager.planIndex()

    override fun selfhoodIndex(): String = selfhoodManager.selfhoodIndex()

    override fun onWorkResult(result: WorkResult) {
        // Work turn completed - could notify UI, persist result
        logInfo("AndeeBrain", "Work turn completed: ${result.totalToolsCalled} tools, ${result.totalImagesUsed} images, ${result.totalDurationMs}ms")
    }

    override fun note(text: String) {
        // Silent observation for next turn
        logDebug("AndeeBrain", "Note: $text")
    }

    override fun todoWithdrawn(text: String): Boolean = todoManager.todoWithdrawn(text)

    override fun onSceneChanged(name: String?) {
        sceneManager.setActiveScene(name)
        logInfo("AndeeBrain", "Scene changed to: ${name ?: "none"}")
    }

    // ========== Tool Dispatch ==========

    private suspend fun dispatchTool(method: String, params: String): Any {
        val json = try { JSONObject(params) } catch (e: Exception) { JSONObject() }

        return when (method) {
            // Screen tools
            "screen.describe" -> screenTools?.describe(json.optBoolean("screenshot", false))
            "screen.tap_eid" -> screenTools?.tapByEid(json.getString("eid"))
            "screen.tap_grid" -> screenTools?.tapGrid(json.getInt("x"), json.getInt("y"))
            "screen.tap_target" -> screenTools?.tapTarget(TapTarget(
                json.getString("description"),
                json.optInt("grid_x", -1).takeIf { it >= 0 },
                json.optInt("grid_y", -1).takeIf { it >= 0 },
                json.optInt("radius", 100)
            ))
            "screen.swipe" -> screenTools?.swipe(
                json.getInt("from_x"), json.getInt("from_y"),
                json.getInt("to_x"), json.getInt("to_y"),
                json.optInt("duration", 300)
            )
            "screen.type" -> screenTools?.typeText(json.getString("text"))
            "screen.scroll" -> screenTools?.scroll(json.getString("direction"), json.optString("eid", null))
            "screen.long_press" -> screenTools?.longPressByEid(json.getString("eid"), json.optInt("duration", 500))

            // Notebook tools
            "notebook.get" -> notebookGet(json.getString("key"))
            "notebook.set" -> notebookSet(json.getString("key"), json.getString("value"))
            "notebook.delete" -> notebookDelete(json.getString("key"))
            "notebook.list" -> notebookList()

            // Selfhood tools
            "selfhood.get" -> selfhoodGet()
            "selfhood.update_self" -> selfhoodUpdateSelf(json.getString("description"))
            "selfhood.upsert_care" -> selfhoodUpsertCare(json)
            "selfhood.remove_care" -> selfhoodRemoveCare(json.getString("name"))
            "selfhood.mark_care_done" -> selfhoodMarkCareDone(json.getString("name"))
            "selfhood.add_diary" -> selfhoodAddDiary(json.getString("text"))
            "selfhood.get_diary" -> selfhoodGetDiary(json.optString("from", null), json.optString("to", null))
            "selfhood.upsert_person" -> selfhoodUpsertPerson(json)
            "selfhood.remove_person" -> selfhoodRemovePerson(json.getString("name"))
            "selfhood.add_wiki_index" -> selfhoodAddWikiIndex(json)
            "selfhood.remove_wiki_index" -> selfhoodRemoveWikiIndex(json.getString("filename"))

            // Plan tools
            "plan.get" -> planGet()
            "plan.set_intent" -> planSetIntent(json.getString("intent"))
            "plan.add_item" -> planAddItem(json.getString("text"), json.getString("because"))
            "plan.mark_done" -> planMarkDone(json.getInt("index"))
            "plan.drop_item" -> planDropItem(json.getInt("index"))
            "plan.set_quiet" -> planSetQuiet(json.getString("items"))
            "plan.add_direction" -> planAddDirection(json)
            "plan.update_direction" -> planUpdateDirection(json.getString("name"), json)
            "plan.remove_direction" -> planRemoveDirection(json.getString("name"))
            "plan.toggle_direction" -> planToggleDirection(json.getString("name"))

            // Todo tools
            "todo.add" -> todoAdd(json)
            "todo.mark_run" -> todoMarkRun(json.getString("id"))
            "todo.withdraw" -> todoWithdraw(json.getString("id"))
            "todo.list" -> todoList()

            // Scene tools
            "scene.list" -> sceneList()
            "scene.get" -> sceneGet(json.getString("name"))
            "scene.learn" -> sceneLearn(json)
            "scene.forget" -> sceneForget(json.getString("name"))
            "scene.set_active" -> sceneSetActive(json.optString("name", null))

            // Gear tools
            "gear.list" -> gearList()
            "gear.upsert" -> gearUpsert(json)
            "gear.remove" -> gearRemove(json.getString("package_name"))

            // Vault tools
            "vault.put" -> vaultPut(json.getString("key"), json.getString("value"))
            "vault.get" -> vaultGet(json.getString("key"))
            "vault.delete" -> vaultDelete(json.getString("key"))
            "vault.list" -> vaultList()
            "vault.fill" -> vaultFill(json.getString("template"))

            // Peer tools
            "peer.invite" -> peerInvite()
            "peer.accept" -> peerAccept(json.getString("invite_json"))
            "peer.send" -> peerSend(json.getString("peer_id"), json.getString("message"))
            "peer.remove" -> peerRemove(json.getString("peer_id"))

            // Topic tools
            "topic.fold" -> topicFold(json)
            "topic.get" -> topicGet(json.getString("id"))
            "topic.search" -> topicSearch(json.getString("query"))
            "topic.list" -> topicList()
            "topic.delete" -> topicDelete(json.getString("id"))

            // Meeting tools
            "meeting.start" -> meetingStart(json.getString("title"))
            "meeting.stop" -> meetingStop()
            "meeting.transcribe" -> meetingTranscribe(json.getInt("chunk_index"))
            "meeting.fold" -> meetingFold()

            // System tools
            "system.sleep" -> systemSleep(json.getLong("ms"))
            "system.shell" -> systemShell(json.getString("command"))
            "system.notify" -> systemNotify(json.getString("title"), json.getString("body"))

            else -> throw IllegalArgumentException("Unknown method: $method")
        }
    }

    // ========== Tool Implementations ==========

    // Notebook
    private suspend fun notebookGet(key: String): String? = withContext(Dispatchers.IO) {
        notebook.data.memories[key]
    }

    private suspend fun notebookSet(key: String, value: String) = withContext(Dispatchers.IO) {
        val current = notebook.data
        val updated = current.memories.toMutableMap().apply { put(key, value) }
        notebook.data = current.copy(memories = updated)
        "OK"
    }

    private suspend fun notebookDelete(key: String) = withContext(Dispatchers.IO) {
        val current = notebook.data
        val updated = current.memories.toMutableMap().apply { remove(key) }
        notebook.data = current.copy(memories = updated)
        "OK"
    }

    private suspend fun notebookList(): Map<String, String> = withContext(Dispatchers.IO) {
        notebook.data.memories
    }

    // Selfhood
    private suspend fun selfhoodGet(): String = withContext(Dispatchers.IO) {
        com.google.gson.Gson().toJson(selfhoodManager.selfhood)
    }

    private suspend fun selfhoodUpdateSelf(description: String) = withContext(Dispatchers.IO) {
        selfhoodManager.updateSelf(description)
        "OK"
    }

    private suspend fun selfhoodUpsertCare(json: JSONObject) = withContext(Dispatchers.IO) {
        val care = net.kuafuai.andee.config.Care(
            name = json.getString("name"),
            description = json.getString("description"),
            intervalDays = json.getInt("interval_days"),
            lastDoneAt = json.optLong("last_done_at", 0),
            source = json.optString("source", "")
        )
        selfhoodManager.upsertCare(care)
        "OK"
    }

    private suspend fun selfhoodRemoveCare(name: String) = withContext(Dispatchers.IO) {
        selfhoodManager.removeCare(name)
        "OK"
    }

    private suspend fun selfhoodMarkCareDone(name: String) = withContext(Dispatchers.IO) {
        selfhoodManager.markCareDone(name)
        "OK"
    }

    private suspend fun selfhoodAddDiary(text: String) = withContext(Dispatchers.IO) {
        selfhoodManager.addDiaryEntry(text)
        "OK"
    }

    private suspend fun selfhoodGetDiary(from: String?, to: String?): List<net.kuafuai.andee.config.DiaryEntry> = withContext(Dispatchers.IO) {
        selfhoodManager.getDiaryEntries(from, to)
    }

    private suspend fun selfhoodUpsertPerson(json: JSONObject) = withContext(Dispatchers.IO) {
        val person = net.kuafuai.andee.config.Person(
            name = json.getString("name"),
            notes = json.optString("notes", ""),
            metAt = json.optLong("met_at", System.currentTimeMillis())
        )
        selfhoodManager.upsertPerson(person)
        "OK"
    }

    private suspend fun selfhoodRemovePerson(name: String) = withContext(Dispatchers.IO) {
        selfhoodManager.removePerson(name)
        "OK"
    }

    private suspend fun selfhoodAddWikiIndex(json: JSONObject) = withContext(Dispatchers.IO) {
        val entry = net.kuafuai.andee.config.WikiIndexEntry(
            title = json.getString("title"),
            filename = json.getString("filename"),
            secret = json.optBoolean("secret", false),
            updatedAt = json.optLong("updated_at", System.currentTimeMillis())
        )
        selfhoodManager.addWikiIndex(entry)
        "OK"
    }

    private suspend fun selfhoodRemoveWikiIndex(filename: String) = withContext(Dispatchers.IO) {
        selfhoodManager.removeWikiIndex(filename)
        "OK"
    }

    // Plan
    private suspend fun planGet(): String = withContext(Dispatchers.IO) {
        com.google.gson.Gson().toJson(planManager.plan)
    }

    private suspend fun planSetIntent(intent: String) = withContext(Dispatchers.IO) {
        planManager.setDayPlanIntent(intent)
        "OK"
    }

    private suspend fun planAddItem(text: String, because: String) = withContext(Dispatchers.IO) {
        planManager.addDayPlanItem(text, because)
        "OK"
    }

    private suspend fun planMarkDone(index: Int) = withContext(Dispatchers.IO) {
        planManager.markDayPlanItemDone(index)
        "OK"
    }

    private suspend fun planDropItem(index: Int) = withContext(Dispatchers.IO) {
        planManager.dropDayPlanItem(index)
        "OK"
    }

    private suspend fun planSetQuiet(itemsJson: String) = withContext(Dispatchers.IO) {
        val items = com.google.gson.Gson().fromJson(itemsJson, Array<String>::class.java).toList()
        planManager.setQuietItems(items)
        "OK"
    }

    private suspend fun planAddDirection(json: JSONObject) = withContext(Dispatchers.IO) {
        val dir = net.kuafuai.andee.config.Direction(
            name = json.getString("name"),
            description = json.getString("description"),
            intervalDays = json.getInt("interval_days"),
            source = json.optString("source", ""),
            active = json.optBoolean("active", true)
        )
        planManager.addDirection(dir)
        "OK"
    }

    private suspend fun planUpdateDirection(name: String, json: JSONObject) = withContext(Dispatchers.IO) {
        planManager.updateDirection(name) { dir ->
            dir.copy(
                description = json.optString("description", dir.description),
                intervalDays = json.optInt("interval_days", dir.intervalDays),
                active = json.optBoolean("active", dir.active)
            )
        }
        "OK"
    }

    private suspend fun planRemoveDirection(name: String) = withContext(Dispatchers.IO) {
        planManager.removeDirection(name)
        "OK"
    }

    private suspend fun planToggleDirection(name: String) = withContext(Dispatchers.IO) {
        planManager.toggleDirection(name)
        "OK"
    }

    // Todo
    private suspend fun todoAdd(json: JSONObject): String = withContext(Dispatchers.IO) {
        val todo = todoManager.addTodo(
            text = json.getString("text"),
            at = json.optLong("at", 0).takeIf { it > 0 },
            cron = json.optString("cron", null)?.takeIf { it.isNotBlank() },
            recurring = json.optBoolean("recurring", false),
            untrusted = json.optBoolean("untrusted", false)
        )
        todo.id
    }

    private suspend fun todoMarkRun(id: String) = withContext(Dispatchers.IO) {
        todoManager.markTodoRun(id)
        "OK"
    }

    private suspend fun todoWithdraw(id: String) = withContext(Dispatchers.IO) {
        todoManager.withdrawTodo(id)
        "OK"
    }

    private suspend fun todoList(): List<net.kuafuai.andee.config.Todo> = withContext(Dispatchers.IO) {
        todoManager.todos
    }

    // Scene
    private suspend fun sceneList(): List<net.kuafuai.andee.config.Scene> = withContext(Dispatchers.IO) {
        sceneManager.scenes
    }

    private suspend fun sceneGet(name: String): net.kuafuai.andee.config.Scene? = withContext(Dispatchers.IO) {
        sceneManager.getScene(name)
    }

    private suspend fun sceneLearn(json: JSONObject) = withContext(Dispatchers.IO) {
        val scene = net.kuafuai.andee.config.Scene(
            name = json.getString("name"),
            goal = json.getString("goal"),
            voice = json.getString("voice"),
            rules = json.getString("rules"),
            routine = json.getString("routine"),
            example = json.getString("example")
        )
        sceneManager.learnScene(scene)
        "OK"
    }

    private suspend fun sceneForget(name: String) = withContext(Dispatchers.IO) {
        sceneManager.forgetScene(name)
        "OK"
    }

    private suspend fun sceneSetActive(name: String?) = withContext(Dispatchers.IO) {
        sceneManager.setActiveScene(name)
        "OK"
    }

    // Gear
    private suspend fun gearList(): List<net.kuafuai.andee.config.GearNote> = withContext(Dispatchers.IO) {
        gearManager.gear
    }

    private suspend fun gearUpsert(json: JSONObject) = withContext(Dispatchers.IO) {
        val gear = net.kuafuai.andee.config.GearNote(
            packageName = json.getString("package_name"),
            name = json.getString("name"),
            description = json.getString("description"),
            capabilities = json.getString("capabilities"),
            limit = json.optString("limit", null),
            signedInAt = json.optLong("signed_in_at", 0).takeIf { it > 0 },
            visibility = json.optString("visibility", "default")
        )
        gearManager.upsertGear(gear)
        "OK"
    }

    private suspend fun gearRemove(packageName: String) = withContext(Dispatchers.IO) {
        gearManager.removeGear(packageName)
        "OK"
    }

    // Vault
    private suspend fun vaultPut(key: String, value: String) = withContext(Dispatchers.IO) {
        vaultManager.putSecret(key, value)
        "OK"
    }

    private suspend fun vaultGet(key: String): String? = withContext(Dispatchers.IO) {
        vaultManager.getSecret(key)
    }

    private suspend fun vaultDelete(key: String) = withContext(Dispatchers.IO) {
        vaultManager.deleteSecret(key)
        "OK"
    }

    private suspend fun vaultList(): List<String> = withContext(Dispatchers.IO) {
        vaultManager.listSecrets()
    }

    private suspend fun vaultFill(template: String): String = withContext(Dispatchers.IO) {
        vaultManager.fillSecret(template)
    }

    // Peer
    private suspend fun peerInvite(): String = withContext(Dispatchers.IO) {
        peerManager.inviteData()
    }

    private suspend fun peerAccept(inviteJson: String): String? = withContext(Dispatchers.IO) {
        peerManager.acceptInvite(inviteJson)?.id
    }

    private suspend fun peerSend(peerId: String, message: String): Boolean = withContext(Dispatchers.IO) {
        peerManager.sendMessage(peerId, message)
    }

    private suspend fun peerRemove(peerId: String) = withContext(Dispatchers.IO) {
        peerManager.removePeer(peerId)
        "OK"
    }

    // Topic
    private suspend fun topicFold(json: JSONObject): String = withContext(Dispatchers.IO) {
        val topic = topicManager.foldTopic(
            title = json.getString("title"),
            summary = json.getString("summary"),
            keywords = com.google.gson.Gson().fromJson(json.getString("keywords"), Array<String>::class.java).toList(),
            messageCount = json.getInt("message_count")
        )
        topic.id
    }

    private suspend fun topicGet(id: String): net.kuafuai.andee.brain.TopicManager.Topic? = withContext(Dispatchers.IO) {
        topicManager.getTopic(id)
    }

    private suspend fun topicSearch(query: String): List<net.kuafuai.andee.brain.TopicManager.Topic> = withContext(Dispatchers.IO) {
        topicManager.searchTopics(query)
    }

    private suspend fun topicList(): List<net.kuafuai.andee.brain.TopicManager.Topic> = withContext(Dispatchers.IO) {
        topicManager.topics
    }

    private suspend fun topicDelete(id: String) = withContext(Dispatchers.IO) {
        topicManager.deleteTopic(id)
        "OK"
    }

    // Meeting
    private suspend fun meetingStart(title: String): String = withContext(Dispatchers.IO) {
        meetingManager.startMeeting(title).id
    }

    private suspend fun meetingStop(): String? = withContext(Dispatchers.IO) {
        meetingManager.stopMeeting()?.id
    }

    private suspend fun meetingTranscribe(chunkIndex: Int): String = withContext(Dispatchers.IO) {
        val meeting = meetingManager.getCurrentMeeting() ?: return@withContext "No active meeting"
        val chunk = meeting.chunks.find { it.index == chunkIndex } ?: return@withContext "Chunk not found"
        meetingManager.transcribeChunk(chunk)
    }

    private suspend fun meetingFold(): String? = withContext(Dispatchers.IO) {
        val meeting = meetingManager.getCurrentMeeting() ?: return@withContext "No active meeting"
        meetingManager.foldMeeting(meeting, topicManager)
    }

    // System
    private suspend fun systemSleep(ms: Long) = withContext(Dispatchers.IO) {
        Thread.sleep(ms)
        "OK"
    }

    private suspend fun systemShell(command: String): String = withContext(Dispatchers.IO) {
        val process = Runtime.getRuntime().exec(command)
        val output = process.inputStream.readBytes().decodeToString()
        val error = process.errorStream.readBytes().decodeToString()
        process.waitFor()
        if (error.isNotBlank()) "Error: $error" else output
    }

    private suspend fun systemNotify(title: String, body: String) = withContext(Dispatchers.IO) {
        // TODO: Show Android notification
        "OK"
    }

    // TapTarget helper
    private data class TapTarget(
        val description: String,
        val gridX: Int? = null,
        val gridY: Int? = null,
        val gridRadius: Int = 100
    )

    /** Shutdown */
    fun shutdown() {
        brain?.shutdown()
        coroutineContext.cancel()
    }
}