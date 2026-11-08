package net.kuafuai.andee.ui.ball

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Floating ball service - the persistent desk pet.
 * Features:
 * - 6 looks × 19 moods = 114 animation states
 * - Physics-based dragging with edge docking
 * - Idle animations, breathing, look-around
 * - Tap/long-press/gesture interactions
 * - Voice activity visualization
 * - Battery/notification indicators
 */
class FloatingBallService : Service(), CoroutineScope {

    override val coroutineContext: Job = SupervisorJob() + Dispatchers.Main.immediate

    private var windowManager: WindowManager? = null
    private var ballView: FloatingBallView? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private var isDocked = false
    private var dockSide = DockSide.RIGHT
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialX = 0f
    private var initialY = 0f

    override fun onCreate() {
        super.onCreate()
        createBallView()
        startIdleAnimations()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        removeBallView()
        coroutineContext.cancel()
        super.onDestroy()
    }

    private fun createBallView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ballView = FloatingBallView(this)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        ballView?.setOnTouchListener { v, event -> onBallTouch(v, event) }
        windowManager?.addView(ballView, windowParams)
    }

    private fun removeBallView() {
        ballView?.let { windowManager?.removeView(it) }
        ballView = null
    }

    private fun onBallTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                initialX = windowParams?.x?.toFloat() ?: 0f
                initialY = windowParams?.y?.toFloat() ?: 0f
                ballView?.onTouchDown()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastTouchX
                val dy = event.rawY - lastTouchY
                val newX = (initialX + dx).toInt()
                val newY = (initialY + dy).toInt()
                updatePosition(newX, newY)
                ballView?.onTouchMove(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - lastTouchX
                val dy = event.rawY - lastTouchY
                val distance = Math.hypot(dx.toDouble(), dy.toDouble())
                if (distance < 20) {
                    ballView?.onTap()
                } else {
                    snapToEdge()
                }
                ballView?.onTouchUp()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                ballView?.onTouchUp()
                return true
            }
        }
        return false
    }

    private fun updatePosition(x: Int, y: Int) {
        windowParams?.x = x
        windowParams?.y = y
        ballView?.let { windowManager?.updateViewLayout(it, windowParams!!) }
    }

    private fun snapToEdge() {
        val display = windowManager?.defaultDisplay
        val width = display?.width ?: 1080
        val height = display?.height ?: 2400
        val ballSize = ballView?.measuredWidth ?: 120

        val params = windowParams!!
        val centerX = params.x + ballSize / 2
        val centerY = params.y + ballSize / 2

        // Determine nearest edge
        val distLeft = centerX
        val distRight = width - centerX
        val distTop = centerY
        val distBottom = height - centerY

        val minDist = minOf(distLeft, distRight, distTop, distBottom)

        launch {
            val startX = params.x.toFloat()
            val startY = params.y.toFloat()
            var targetX = startX
            var targetY = startY

            when (minDist) {
                distLeft -> {
                    targetX = 0f
                    dockSide = DockSide.LEFT
                }
                distRight -> {
                    targetX = (width - ballSize).toFloat()
                    dockSide = DockSide.RIGHT
                }
                distTop -> {
                    targetY = 0f
                    dockSide = DockSide.TOP
                }
                distBottom -> {
                    targetY = (height - ballSize).toFloat()
                    dockSide = DockSide.BOTTOM
                }
            }

            isDocked = true
            animateToPosition(startX, startY, targetX, targetY, 300)
        }
    }

    private suspend fun animateToPosition(startX: Float, startY: Float, targetX: Float, targetY: Float, duration: Long) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < duration) {
            val progress = (System.currentTimeMillis() - startTime).toFloat() / duration
            val eased = DecelerateInterpolator().getInterpolation(progress)
            val currentX = startX + (targetX - startX) * eased
            val currentY = startY + (targetY - startY) * eased
            updatePosition(currentX.toInt(), currentY.toInt())
            delay(16) // ~60fps
        }
        updatePosition(targetX.toInt(), targetY.toInt())
    }

    private fun startIdleAnimations() {
        launch {
            while (true) {
                delay(5000 + (Math.random() * 10000).toLong()) // Random interval 5-15s
                if (!isDocked || ballView?.isBeingDragged != true) {
                    ballView?.triggerIdleAnimation()
                }
            }
        }
    }

    /** Update ball mood (called from brain) */
    fun setMood(mood: Mood) {
        ballView?.setMood(mood)
    }

    /** Update ball look (called from brain) */
    fun setLook(look: Look) {
        ballView?.setLook(look)
    }

    /** Show voice activity (speaking/listening) */
    fun setVoiceState(state: VoiceState) {
        ballView?.setVoiceState(state)
    }

    /** Show notification badge */
    fun showNotification(count: Int) {
        ballView?.showNotification(count)
    }

    /** Show battery level */
    fun setBatteryLevel(level: Int, charging: Boolean) {
        ballView?.setBatteryLevel(level, charging)
    }

    enum class DockSide { LEFT, RIGHT, TOP, BOTTOM }

    enum class Look {
        DEFAULT,       // Normal round ball
        SLEEPY,        // Half-closed eyes
        CURIOUS,       // Wide eyes, tilted
        EXCITED,       // Sparkles, bounce
        CONCERNED,     // Brow furrowed
        PLAYFUL        // Tongue out, wobble
    }

    enum class Mood {
        NEUTRAL,       // Baseline
        HAPPY,         // Smiling, warm colors
        SAD,           // Droopy, blue tint
        ANGRY,         // Red tint, sharp
        SURPRISED,     // Wide eyes, pop
        CONFUSED,      // Spiral eyes
        SLEEPY,        // Zzz, slow blink
        EXCITED,       // Fast pulse, particles
        FOCUSED,       // Sharp, steady
        BORED,         // Slow sway, gray
        ANXIOUS,       // Jitter, pulse
        PROUD,         // Puffed, gold tint
        EMBARRASSED,   // Pink, look away
        CURIOUS,       // Head tilt, sparkle
        DETERMINED,    // Intense, steady
        RELAXED,       // Slow breathe, soft
        ALERT,         // Wide, scanning
        AFFECTIONATE   // Hearts, warm pulse
    }

    enum class VoiceState {
        IDLE,
        LISTENING,     // Pulsing mic icon
        SPEAKING,      // Waveform animation
        THINKING       // Spinner
    }
}