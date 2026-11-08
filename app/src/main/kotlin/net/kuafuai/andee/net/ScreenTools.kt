package net.kuafuai.andee.net

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kuafuai.andee.brain.BrainHost
import java.io.ByteArrayOutputStream

/**
 * Screen control tools implementation.
 * Uses AccessibilityService for eids/grid, MediaProjection for screenshots.
 */
class ScreenTools(
    private val host: BrainHost,
    private val accessibilityService: AccessibilityService?,
    private val mediaProjection: MediaProjection?
) {

    private var screenWidth = 1080
    private var screenHeight = 2400

    /** Update screen dimensions */
    fun updateScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    /** Describe screen: return element tree + optional screenshot */
    suspend fun describe(includeScreenshot: Boolean = false): ScreenDescription = withContext(Dispatchers.IO) {
        val root = accessibilityService?.rootInActiveWindow
        val elements = mutableListOf<ElementInfo>()

        root?.let { traverseNode(it, elements) }

        var screenshotB64: String? = null
        if (includeScreenshot) {
            screenshotB64 = takeScreenshot()
        }

        ScreenDescription(
            width = screenWidth,
            height = screenHeight,
            orientation = if (screenWidth > screenHeight) "landscape" else "portrait",
            elements = elements,
            screenshotBase64 = screenshotB64
        )
    }

    private fun traverseNode(node: AccessibilityNodeInfo, elements: MutableList<ElementInfo>) {
        // Only include interactive or text-bearing nodes
        if (node.isClickable || node.isEditable || node.isScrollable ||
            node.text?.isNotBlank() == true || node.contentDescription?.isNotBlank() == true) {
            elements.add(CoordinateProtocol.elementToInfo(node, screenWidth, screenHeight))
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { traverseNode(it, elements) }
        }
        node.recycle()
    }

    /** Tap by eid */
    suspend fun tapByEid(eid: String): Boolean = withContext(Dispatchers.IO) {
        val root = accessibilityService?.rootInActiveWindow ?: return@withContext false
        val target = findNodeByEid(root, eid)
        target?.let {
            val (gx, gy) = CoordinateProtocol.nodeToGrid(it, screenWidth, screenHeight)
            performTap(gx, gy)
            it.recycle()
            return@withContext true
        }
        false
    }

    /** Tap by grid coordinates */
    suspend fun tapGrid(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        performTap(x.coerceIn(0, CoordinateProtocol.GRID_SIZE), y.coerceIn(0, CoordinateProtocol.GRID_SIZE))
        true
    }

    /** Tap by vision target (tap_target) */
    suspend fun tapTarget(target: TapTarget): Boolean = withContext(Dispatchers.IO) {
        // TODO: Implement vision-based tap using screenshot + model
        // For now, fallback to grid if hint provided
        target.gridX?.let { gx ->
            target.gridY?.let { gy ->
                performTap(gx, gy)
                return@withContext true
            }
        }
        false
    }

    /** Swipe from grid to grid */
    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Int = 300): Boolean = withContext(Dispatchers.IO) {
        val service = accessibilityService ?: return@withContext false
        val (fx, fy) = CoordinateProtocol.toPixels(fromX, fromY, screenWidth, screenHeight)
        val (tx, ty) = CoordinateProtocol.toPixels(toX, toY, screenWidth, screenHeight)
        val path = android.graphics.Path().apply { moveTo(fx.toFloat(), fy.toFloat()); lineTo(tx.toFloat(), ty.toFloat()) }
        val stroke = android.graphics.Path().apply { moveTo(fx.toFloat(), fy.toFloat()); lineTo(tx.toFloat(), ty.toFloat()) }
        val builder = android.accessibilityservice.GestureDescription.Builder()
        builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs))
        service.dispatchGesture(builder.build(), null, null)
        true
    }

    /** Type text into focused editable field */
    suspend fun typeText(text: String): Boolean = withContext(Dispatchers.IO) {
        val service = accessibilityService ?: return@withContext false
        val root = service.rootInActiveWindow ?: return@withContext false
        val focused = findFocusedEditable(root)
        focused?.let {
            val args = android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            it.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT)
            it.recycle()
            return@withContext true
        }
        false
    }

    /** Scroll in direction */
    suspend fun scroll(direction: String, eid: String? = null): Boolean = withContext(Dispatchers.IO) {
        val service = accessibilityService ?: return@withContext false
        var target: AccessibilityNodeInfo? = null

        if (eid != null) {
            val root = service.rootInActiveWindow ?: return@withContext false
            target = findNodeByEid(root, eid)
        } else {
            target = service.rootInActiveWindow
        }

        target?.let {
            val action = when (direction.lowercase()) {
                "up" -> android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                "down" -> android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                "left" -> android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_LEFT
                "right" -> android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_RIGHT
                else -> -1
            }
            if (action != -1) {
                it.performAction(action)
                it.recycle()
                return@withContext true
            }
        }
        false
    }

    /** Long press by eid */
    suspend fun longPressByEid(eid: String, durationMs: Int = 500): Boolean = withContext(Dispatchers.IO) {
        val root = accessibilityService?.rootInActiveWindow ?: return@withContext false
        val target = findNodeByEid(root, eid)
        target?.let {
            val (gx, gy) = CoordinateProtocol.nodeToGrid(it, screenWidth, screenHeight)
            performLongPress(gx, gy, durationMs)
            it.recycle()
            return@withContext true
        }
        false
    }

    private fun findNodeByEid(root: AccessibilityNodeInfo, targetEid: String): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        fun search(node: AccessibilityNodeInfo) {
            if (found != null) return
            if (CoordinateProtocol.generateEid(node) == targetEid) {
                found = node
                return
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { search(it) }
            }
        }
        search(root)
        return found
    }

    private fun findFocusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        fun search(node: AccessibilityNodeInfo) {
            if (found != null) return
            if (node.isFocused && node.isEditable) {
                found = node
                return
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { search(it) }
            }
        }
        search(root)
        return found
    }

    private fun performTap(gridX: Int, gridY: Int) {
        val (px, py) = CoordinateProtocol.toPixels(gridX, gridY, screenWidth, screenHeight)
        val path = android.graphics.Path().apply { moveTo(px.toFloat(), py.toFloat()); lineTo(px.toFloat(), py.toFloat()) }
        val builder = android.accessibilityservice.GestureDescription.Builder()
        builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50))
        accessibilityService?.dispatchGesture(builder.build(), null, null)
    }

    private fun performLongPress(gridX: Int, gridY: Int, durationMs: Int) {
        val (px, py) = CoordinateProtocol.toPixels(gridX, gridY, screenWidth, screenHeight)
        val path = android.graphics.Path().apply { moveTo(px.toFloat(), py.toFloat()); lineTo(px.toFloat(), py.toFloat()) }
        val builder = android.accessibilityservice.GestureDescription.Builder()
        builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs))
        accessibilityService?.dispatchGesture(builder.build(), null, null)
    }

    private fun takeScreenshot(): String? {
        // Requires MediaProjection - placeholder
        // In real implementation: create virtual display, capture to bitmap, encode to base64
        return null
    }
}