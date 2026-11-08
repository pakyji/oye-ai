package net.kuafuai.andee.net

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable

/**
 * Coordinate protocol for screen control.
 * - eids: element IDs from accessibility tree
 * - Grid: 0-1000 normalized coordinates (0,0 top-left, 1000,1000 bottom-right)
 * - tap_target: vision-based fallback when eid not available
 */
object CoordinateProtocol {

    /** Screen grid size */
    const val GRID_SIZE = 1000

    /** Convert screen pixels to grid coordinates */
    fun toGrid(x: Int, y: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        val gx = (x * GRID_SIZE / screenWidth).coerceIn(0, GRID_SIZE)
        val gy = (y * GRID_SIZE / screenHeight).coerceIn(0, GRID_SIZE)
        return gx to gy
    }

    /** Convert grid coordinates to screen pixels */
    fun toPixels(gx: Int, gy: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        val x = (gx * screenWidth / GRID_SIZE).coerceIn(0, screenWidth)
        val y = (gy * screenHeight / GRID_SIZE).coerceIn(0, screenHeight)
        return x to y
    }

    /** Get center grid coords from AccessibilityNodeInfo */
    fun nodeToGrid(node: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val cx = (bounds.left + bounds.right) / 2
        val cy = (bounds.top + bounds.bottom) / 2
        return toGrid(cx, cy, screenWidth, screenHeight)
    }

    /** Get grid rect from node */
    fun nodeToGridRect(node: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): GridRect {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val (gx1, gy1) = toGrid(bounds.left, bounds.top, screenWidth, screenHeight)
        val (gx2, gy2) = toGrid(bounds.right, bounds.bottom, screenWidth, screenHeight)
        return GridRect(gx1, gy1, gx2, gy2)
    }

    @Serializable
    data class GridRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /** Tap target for vision-based fallback */
    @Serializable
    data class TapTarget(
        val description: String,  // What to look for (e.g., "blue send button")
        val gridX: Int? = null,   // Optional hint region
        val gridY: Int? = null,
        val gridRadius: Int = 100
    )

    /** Screen description for model */
    @Serializable
    data class ScreenDescription(
        val width: Int,
        val height: Int,
        val orientation: String, // "portrait" | "landscape"
        val elements: List<ElementInfo>,
        val screenshotBase64: String? = null
    )

    @Serializable
    data class ElementInfo(
        val eid: String,           // Unique element ID (hash of path)
        val text: String?,         // Visible text
        val contentDesc: String?,  // Accessibility content description
        val className: String,     // View class
        val gridRect: GridRect,    // Normalized bounds
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val checked: Boolean?,
        val selected: Boolean?
    )

    /** Generate stable eid from node path */
    fun generateEid(node: AccessibilityNodeInfo): String {
        val path = StringBuilder()
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 10) {
            val clazz = current.className?.simpleName ?: "Unknown"
            val id = current.viewIdResourceName?.split("/").last() ?: ""
            val text = current.text?.toString()?.take(20) ?: ""
            path.append("/$clazz")
            if (id.isNotBlank()) path.append("[$id]")
            if (text.isNotBlank()) path.append("{$text}")
            current = current.parent
            depth++
        }
        return path.reverse().toString().let { java.util.UUID.nameUUIDFromBytes(it.toByteArray()).toString().take(12) }
    }

    /** Serialize element info for model */
    fun elementToInfo(node: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): ElementInfo {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val gridRect = nodeToGridRect(node, screenWidth, screenHeight)
        return ElementInfo(
            eid = generateEid(node),
            text = node.text?.toString(),
            contentDesc = node.contentDescription?.toString(),
            className = node.className?.simpleName ?: "Unknown",
            gridRect = gridRect,
            clickable = node.isClickable,
            editable = node.isEditable,
            scrollable = node.isScrollable,
            checked = if (node.isCheckable) node.isChecked else null,
            selected = node.isSelected
        )
    }
}