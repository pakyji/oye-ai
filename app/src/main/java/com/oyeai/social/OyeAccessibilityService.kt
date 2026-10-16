package com.oyeai.social

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/** What a label search found. Only the label the user asked for is ever matched. */
enum class LabelResult { Clicked, Found, NotClickable, NotFound, Unsupported, NoWindow }

/**
 * Optional accessibility service for Instagram and Facebook, used when the user asks.
 *
 * It scrolls, goes back or home, and can tap or find one label the user names. Screen text is
 * matched against that label only: nothing is read out, stored or sent, and no text is typed.
 * Likes, follows, comments and sending are never tapped. The user turns it on in Android settings
 * and can turn it off at any time.
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
        val pkg = root.packageName?.toString()
        val supported = isSupportedPackage(pkg)
        if (!supported) {
            root.recycleCompat()
            onResult(ScrollResult.UnsupportedApp)
            return
        }
        if (pkg != null && pkg in SUPPORTED_PACKAGES) lastSupported = pkg

        val action = if (direction == Direction.Forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val target = findScrollable(root, action)
        val performed = target?.performAction(action) == true
        // The root itself can be the scrollable view. Recycling it twice throws on Android 8 to 12.
        if (target != null && target !== root) target.recycleCompat()
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

    /** Package of the window on screen right now, or null when unknown. */
    fun activePackage(): String? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString()
        root.recycleCompat()
        return pkg
    }

    /** Performs a global action such as back or home. Returns what Android reported. */
    fun performGlobal(action: Int): Boolean = performGlobalAction(action)

    /**
     * Finds the first node whose text or description contains [label]. When [click] is true, it taps
     * that node or its nearest clickable parent. Only [label] is matched; the screen text is not returned.
     */
    fun findLabel(label: String, click: Boolean): LabelResult {
        val root = rootInActiveWindow ?: return LabelResult.NoWindow
        val pkg = root.packageName?.toString()
        val supported = isSupportedPackage(pkg)
        if (!supported) {
            root.recycleCompat()
            return LabelResult.Unsupported
        }
        if (pkg != null && pkg in SUPPORTED_PACKAGES) lastSupported = pkg
        val wanted = label.lowercase(Locale.ROOT)
        val match = findNode(root, wanted, 0)
        // When the root itself matched, it is recycled once below, as [match].
        if (match !== root) root.recycleCompat()
        if (match == null) return LabelResult.NotFound
        if (!click) {
            match.recycleCompat()
            return LabelResult.Found
        }
        val target = if (match.isClickable) match else clickableAncestor(match)
        val performed = target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (target !== match) target?.recycleCompat()
        match.recycleCompat()
        return if (performed) LabelResult.Clicked else LabelResult.NotClickable
    }

    /** Depth-first search for a node whose text or description contains [wanted]. The caller recycles the result. */
    private fun findNode(node: AccessibilityNodeInfo, wanted: String, depth: Int): AccessibilityNodeInfo? {
        if (depth > MAX_DEPTH) return null
        val text = node.text?.toString()?.lowercase(Locale.ROOT).orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
        if (wanted.isNotEmpty() && (text.contains(wanted) || desc.contains(wanted))) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNode(child, wanted, depth + 1)
            if (found != null) {
                if (found !== child) child.recycleCompat()
                return found
            }
            child.recycleCompat()
        }
        return null
    }

    /** The nearest clickable ancestor within a few levels. The caller recycles the result. */
    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        var levels = 0
        while (current != null && levels < 6) {
            if (current.isClickable) return current
            val next = current.parent
            current.recycleCompat()
            current = next
            levels++
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

    /** Instagram, Facebook, YouTube, and the movie app whose label is Castle. Other apps are never touched. */
    private fun isSupportedPackage(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        if (pkg in SUPPORTED_PACKAGES) return true
        val pm = packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0).any { info ->
            info.activityInfo.packageName == pkg &&
                info.loadLabel(pm).toString().equals(MOVIE_APP_LABEL, ignoreCase = true)
        }
    }

    companion object {
        const val MOVIE_APP_LABEL = "Castle"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        /** The apps OYE will scroll: Instagram, Facebook, YouTube (and Castle by label). */
        val SUPPORTED_PACKAGES = setOf(
            SocialApp.INSTAGRAM.packageName,
            SocialApp.FACEBOOK.packageName,
            YOUTUBE_PACKAGE
        )

        private const val MAX_DEPTH = 12
        private const val SWIPE_MS = 250L

        @Volatile
        private var instance: OyeAccessibilityService? = null

        /** The last supported app that was on screen, so OYE can bring it back for a scroll. */
        @Volatile
        private var lastSupported: String? = null

        fun lastSupportedPackage(): String? = lastSupported

        /** Package of the window on screen, or null when the service is off. */
        fun activePackage(): String? = instance?.activePackage()

        /** The connected service, or null when the user has not turned it on. */
        fun current(): OyeAccessibilityService? = instance
    }
}
