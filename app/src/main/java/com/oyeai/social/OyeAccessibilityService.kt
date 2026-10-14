package com.oyeai.social

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional accessibility service that scrolls Instagram and Facebook when the user asks.
 *
 * It only scrolls. It reads the package name of the active window and whether a view can scroll.
 * It never reads, stores or sends text, and it never taps, likes, follows, comments or types.
 * The user turns it on in Android settings and can turn it off at any time.
 */
class OyeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are not used. The service looks at the window only when a scroll is requested.
    }

    override fun onInterrupt() {
        // Nothing to interrupt: scrolls are short and self-contained.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * Scrolls the visible Instagram or Facebook screen. It tries the scrollable view's
     * accessibility action first, then a swipe gesture. [onResult] reports what actually happened.
     */
    fun scroll(direction: Direction, onResult: (ScrollResult) -> Unit) {
        val root = rootInActiveWindow
        if (root == null) {
            onResult(ScrollResult.NoActiveWindow)
            return
        }
        val supported = root.packageName?.toString() in SUPPORTED_PACKAGES
        if (!supported) {
            root.recycleCompat()
            onResult(ScrollResult.UnsupportedApp)
            return
        }

        val action = if (direction == Direction.Forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val target = findScrollable(root, action)
        val performed = target?.performAction(action) == true
        target?.recycleCompat()
        root.recycleCompat()

        if (performed) {
            onResult(ScrollResult.Done("accessibility"))
        } else {
            swipe(direction, onResult)
        }
    }

    /** Depth-first search for a scrollable view that supports [action]. Recycles the nodes it skips. */
    private fun findScrollable(node: AccessibilityNodeInfo, action: Int, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > MAX_DEPTH) return null
        if (node.isScrollable && node.actionList.any { it.id == action }) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollable(child, action, depth + 1)
            if (found != null) {
                if (found !== child) child.recycleCompat()
                return found
            }
            child.recycleCompat()
        }
        return null
    }

    private fun swipe(direction: Direction, onResult: (ScrollResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onResult(ScrollResult.Failed)
            return
        }
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val upper = metrics.heightPixels * 0.25f
        val lower = metrics.heightPixels * 0.75f
        val (fromY, toY) = if (direction == Direction.Forward) lower to upper else upper to lower
        val path = Path().apply {
            moveTo(x, fromY)
            lineTo(x, toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_MS))
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onResult(ScrollResult.Done("gesture"))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onResult(ScrollResult.Failed)
            }
        }, null)
        if (!dispatched) onResult(ScrollResult.Failed)
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.recycleCompat() {
        // recycle() is deprecated from Android 13, where it does nothing. Older versions need it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recycle()
    }

    companion object {
        /** The only apps OYE will scroll. */
        val SUPPORTED_PACKAGES = setOf(
            SocialApp.INSTAGRAM.packageName,
            SocialApp.FACEBOOK.packageName
        )

        private const val MAX_DEPTH = 12
        private const val SWIPE_MS = 250L

        @Volatile
        private var instance: OyeAccessibilityService? = null

        /** The connected service, or null when the user has not turned it on. */
        fun current(): OyeAccessibilityService? = instance
    }
}
