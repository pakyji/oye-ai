package net.kuafuai.andee.brain

import net.kuafuai.andee.config.Notebook
import net.kuafuai.andee.config.Scene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages learned scenes. Scenes are reusable contexts (voice, rules, routine, example).
 */
class SceneManager(private val notebook: Notebook) {

    /** All scenes */
    val scenes: List<Scene>
        get() = notebook.data.scenes

    /** Get scene by name */
    fun getScene(name: String): Scene? = scenes.find { it.name == name }

    /** Active scene (set by host) */
    var activeScene: String? = null
        private set

    /** Set active scene */
    fun setActiveScene(name: String?) {
        activeScene = name
    }

    /** Learn a new scene */
    suspend fun learnScene(scene: Scene) {
        withContext(Dispatchers.IO) {
            val current = notebook.data
            val existingIdx = current.scenes.indexOfFirst { it.name == scene.name }
            val updatedScenes = if (existingIdx >= 0) {
                current.scenes.toMutableList().apply { this[existingIdx] = scene }
            } else {
                current.scenes + scene
            }
            notebook.data = current.copy(scenes = updatedScenes)
        }
    }

    /** Forget a scene */
    suspend fun forgetScene(name: String) {
        withContext(Dispatchers.IO) {
            val current = notebook.data
            val updatedScenes = current.scenes.filter { it.name != name }
            notebook.data = current.copy(scenes = updatedScenes)
            if (activeScene == name) activeScene = null
        }
    }

    /** Get scene index for prompt */
    fun sceneIndex(): String {
        if (scenes.isEmpty()) return ""
        val sb = StringBuilder("AVAILABLE SCENES:\n")
        for (scene in scenes) {
            val active = if (scene.name == activeScene) " [ACTIVE]" else ""
            sb.append("  - ${scene.name}$active: ${scene.goal}\n")
        }
        return sb.toString().trim()
    }

    /** Get full scene prompt for active scene */
    fun activeScenePrompt(): String? {
        val scene = getScene(activeScene ?: "") ?: return null
        return """
            SCENE: ${scene.name}
            GOAL: ${scene.goal}
            VOICE: ${scene.voice}
            RULES: ${scene.rules}
            ROUTINE: ${scene.routine}
            EXAMPLE: ${scene.example}
        """.trimIndent()
    }
}