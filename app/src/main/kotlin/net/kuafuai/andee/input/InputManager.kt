package net.kuafuai.andee.input

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/**
 * Input manager: coordinates A11y IME (primary) and ADBKeyboard (fallback).
 * Provides unified text input and key events.
 */
class InputManager(
    private val context: Context,
    private val accessibilityService: AccessibilityService?
) : CoroutineScope {

    override val coroutineContext: Job = SupervisorJob() + Dispatchers.Main.immediate

    private val inputChannel = Channel<InputEvent>(Channel.UNLIMITED)
    val events: ReceiveChannel<InputEvent> = inputChannel

    private var useAdbKeyboard = false
    private var adbKeyboardEnabled = false

    sealed interface InputEvent {
        data class OnTextInput(val text: String, val source: InputSource) : InputEvent
        data class OnKeyEvent(val keyCode: Int, val action: Int) : InputEvent
        data class OnImeStateChanged(val active: Boolean) : InputEvent
    }

    enum class InputSource { A11Y_IME, ADB_KEYBOARD, SYSTEM }

    /** Initialize input channels */
    fun initialize() {
        checkAdbKeyboard()
        launch { monitorImeState() }
    }

    /** Check if ADBKeyboard is available and enabled */
    private fun checkAdbKeyboard() {
        // ADBKeyboard package: com.adbkeyboard
        val pm = context.packageManager
        try {
            val info = pm.getPackageInfo("com.adbkeyboard", 0)
            adbKeyboardEnabled = true
            // Check if it's the current IME
            val currentIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            useAdbKeyboard = currentIme?.contains("com.adbkeyboard") == true
        } catch (e: Exception) {
            adbKeyboardEnabled = false
        }
    }

    /** Send text via best available method */
    suspend fun sendText(text: String): Boolean {
        // Try A11y IME first (requires focused editable field)
        if (accessibilityService != null) {
            val success = sendTextViaA11y(text)
            if (success) {
                inputChannel.trySend(InputEvent.OnTextInput(text, InputSource.A11Y_IME))
                return true
            }
        }

        // Fallback to ADBKeyboard
        if (adbKeyboardEnabled) {
            val success = sendTextViaAdbKeyboard(text)
            if (success) {
                inputChannel.trySend(InputEvent.OnTextInput(text, InputSource.ADB_KEYBOARD))
                return true
            }
        }

        // Last resort: broadcast intent (works if app has INPUT_METHOD permission)
        sendTextViaBroadcast(text)
        inputChannel.trySend(InputEvent.OnTextInput(text, InputSource.SYSTEM))
        return true
    }

    /** Send key event */
    suspend fun sendKeyEvent(keyCode: Int, action: Int): Boolean {
        // Use accessibility service to inject key event
        accessibilityService?.let { service ->
            // Can't directly inject key events via A11y, use ADBKeyboard or shell
            return sendKeyViaShell(keyCode, action)
        }
        return sendKeyViaShell(keyCode, action)
    }

    /** Send text via A11y IME (set text on focused editable node) */
    private fun sendTextViaA11y(text: String): Boolean {
        val root = accessibilityService?.rootInActiveWindow ?: return false
        val focused = findFocusedEditable(root)
        focused?.let {
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val result = it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT)
            it.recycle()
            return result
        }
        return false
    }

    /** Send text via ADBKeyboard broadcast */
    private fun sendTextViaAdbKeyboard(text: String): Boolean {
        try {
            val intent = Intent("com.adbkeyboard.INPUT_TEXT")
            intent.putExtra("text", text)
            intent.package = "com.adbkeyboard"
            context.sendBroadcast(intent)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Send text via broadcast (generic) */
    private fun sendTextViaBroadcast(text: String) {
        val intent = Intent("android.intent.action.INSERT")
        intent.putExtra("text", text)
        context.sendBroadcast(intent)
    }

    /** Send key via shell (requires ADB or root) */
    private fun sendKeyViaShell(keyCode: Int, action: Int): Boolean {
        try {
            val actionStr = if (action == 0) "DOWN" else "UP"
            val cmd = "input keyevent $keyCode $actionStr"
            Runtime.getRuntime().exec(cmd)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Find focused editable node */
    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findFocusedEditable(child)
                if (found != null) return found
            }
        }
        return null
    }

    /** Monitor IME state changes */
    private suspend fun monitorImeState() {
        // TODO: Register IME state listener
    }

    /** Request user to enable ADBKeyboard */
    fun requestEnableAdbKeyboard() {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /** Check if A11y service is enabled */
    fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabledServices.contains(context.packageName)
    }

    /** Request user to enable accessibility service */
    fun requestAccessibility() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun shutdown() {
        coroutineContext.cancel()
    }
}