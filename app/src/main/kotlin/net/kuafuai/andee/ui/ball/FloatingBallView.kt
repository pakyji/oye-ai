package net.kuafuai.andee.ui.ball

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import net.kuafuai.andee.ui.ball.FloatingBallService.Look
import net.kuafuai.andee.ui.ball.FloatingBallService.Mood
import net.kuafuai.andee.ui.ball.FloatingBallService.VoiceState

/**
 * Floating ball view with 6 looks × 19 moods animations.
 * Uses vector drawables for scalable animations.
 */
class FloatingBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ballSize = resources.getDimensionPixelSize(R.dimen.floating_ball_size)
    private var currentLook: Look = Look.DEFAULT
    private var currentMood: Mood = Mood.NEUTRAL
    private var voiceState: VoiceState = VoiceState.IDLE
    private var notificationCount = 0
    private var batteryLevel = 100
    private var isCharging = false
    var isBeingDragged = false

    // Animation state
    private var idleAnimator: ValueAnimator? = null
    private var breatheAnimator: ValueAnimator? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var voiceAnimator: ValueAnimator? = null
    private var lookAnimator: ValueAnimator? = null

    // Layers for composition
    private val baseLayer = BitmapLayer()
    private val faceLayer = BitmapLayer()
    private val effectLayer = BitmapLayer()
    private val overlayLayer = BitmapLayer()

    // Paint objects
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(20f, 0f, 0f, Color.WHITE)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    init {
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        initializeLayers()
        startBreathing()
    }

    private fun initializeLayers() {
        // Base layer - ball body
        baseLayer.draw { canvas ->
            val rect = RectF(0f, 0f, ballSize.toFloat(), ballSize.toFloat())
            val moodColor = getMoodColor(currentMood)
            basePaint.color = moodColor
            canvas.drawRoundRect(rect, ballSize * 0.5f, ballSize * 0.5f, basePaint)

            // Subtle gradient overlay
            val gradient = RadialGradient(
                ballSize * 0.3f, ballSize * 0.3f,
                ballSize * 0.7f,
                intArrayOf(Color.parseColor("#FFFFFFFF"), moodColor, moodColor),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            basePaint.shader = gradient
            canvas.drawRoundRect(rect, ballSize * 0.5f, ballSize * 0.5f, basePaint)
            basePaint.shader = null
        }

        // Face layer - eyes, mouth based on look
        faceLayer.draw { canvas ->
            drawFace(canvas)
        }

        // Effect layer - particles, sparkles, etc.
        effectLayer.draw { canvas ->
            drawEffects(canvas)
        }

        // Overlay layer - notifications, battery
        overlayLayer.draw { canvas ->
            drawOverlays(canvas)
        }
    }

    private fun drawFace(canvas: Canvas) {
        val centerX = ballSize / 2f
        val centerY = ballSize / 2f
        val eyeY = centerY - ballSize * 0.15f
        val eyeOffset = ballSize * 0.18f
        val eyeSize = ballSize * 0.12f

        when (currentLook) {
            Look.DEFAULT -> {
                // Normal eyes
                drawEye(canvas, centerX - eyeOffset, eyeY, eyeSize, false)
                drawEye(canvas, centerX + eyeOffset, eyeY, eyeSize, false)
                drawMouth(canvas, centerX, centerY + ballSize * 0.1f, MouthShape.NEUTRAL)
            }
            Look.SLEEPY -> {
                // Half-closed eyes
                drawEye(canvas, centerX - eyeOffset, eyeY, eyeSize, true)
                drawEye(canvas, centerX + eyeOffset, eyeY, eyeSize, true)
                drawMouth(canvas, centerX, centerY + ballSize * 0.1f, MouthShape.SMALL_SMILE)
            }
            Look.CURIOUS -> {
                // Wide eyes, one raised
                drawEye(canvas, centerX - eyeOffset, eyeY - ballSize * 0.03f, eyeSize * 1.2f, false)
                drawEye(canvas, centerX + eyeOffset, eyeY + ballSize * 0.02f, eyeSize * 0.9f, false)
                drawMouth(canvas, centerX, centerY + ballSize * 0.1f, MouthShape.O_SHAPE)
            }
            Look.EXCITED -> {
                // Sparkle eyes
                drawSparkleEye(canvas, centerX - eyeOffset, eyeY, eyeSize)
                drawSparkleEye(canvas, centerX + eyeOffset, eyeY, eyeSize)
                drawMouth(canvas, centerX, centerY + ballSize * 0.1f, MouthShape.BIG_SMILE)
            }
            Look.CONCERNED -> {
                // Furrowed brows
                drawConcernedEye(canvas, centerX - eyeOffset, eyeY, eyeSize)
                drawConcernedEye(canvas, centerX + eyeOffset, eyeY, eyeSize)
                drawMouth(canvas, centerX, centerY + ballSize * 0.1f, MouthShape.FROWN)
            }
            Look.PLAYFUL -> {
                // Tongue out, wobble
                drawEye(canvas, centerX - eyeOffset, eyeY, eyeSize, false)
                drawEye(canvas, centerX + eyeOffset, eyeY, eyeSize, false)
                drawMouth(canvas, centerX, centerY + ballSize * 0.1f, MouthShape.TONGUE_OUT)
            }
        }
    }

    private fun drawEye(canvas: Canvas, x: Float, y: Float, size: Float, halfClosed: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        if (halfClosed) {
            canvas.drawOval(RectF(x - size, y - size * 0.3f, x + size, y + size * 0.3f), paint)
        } else {
            canvas.drawCircle(x, y, size, paint)
            // Highlight
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(x - size * 0.3f, y - size * 0.3f, size * 0.25f, highlightPaint)
        }
    }

    private fun drawSparkleEye(canvas: Canvas, x: Float, y: Float, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        canvas.drawCircle(x, y, size, paint)
        // Sparkle
        val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW }
        canvas.drawCircle(x - size * 0.2f, y - size * 0.2f, size * 0.15f, sparklePaint)
        canvas.drawCircle(x + size * 0.3f, y - size * 0.1f, size * 0.1f, sparklePaint)
    }

    private fun drawConcernedEye(canvas: Canvas, x: Float, y: Float, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        // Eye with furrowed brow line above
        canvas.drawCircle(x, y, size, paint)
        val browPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(x - size, y - size * 1.3f, x + size, y - size * 1.1f, browPaint)
    }

    private enum class MouthShape { NEUTRAL, SMALL_SMILE, BIG_SMILE, FROWN, O_SHAPE, TONGUE_OUT }

    private fun drawMouth(canvas: Canvas, centerX: Float, centerY: Float, shape: MouthShape) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        val width = ballSize * 0.25f

        when (shape) {
            MouthShape.NEUTRAL -> canvas.drawLine(centerX - width, centerY, centerX + width, centerY, paint)
            MouthShape.SMALL_SMILE -> {
                val path = Path().apply { moveTo(centerX - width, centerY); quadTo(centerX, centerY - width * 0.5f, centerX + width, centerY) }
                canvas.drawPath(path, paint)
            }
            MouthShape.BIG_SMILE -> {
                val path = Path().apply { moveTo(centerX - width, centerY); quadTo(centerX, centerY - width, centerX + width, centerY) }
                canvas.drawPath(path, paint)
            }
            MouthShape.FROWN -> {
                val path = Path().apply { moveTo(centerX - width, centerY); quadTo(centerX, centerY + width * 0.5f, centerX + width, centerY) }
                canvas.drawPath(path, paint)
            }
            MouthShape.O_SHAPE -> canvas.drawCircle(centerX, centerY, width * 0.4f, paint)
            MouthShape.TONGUE_OUT -> {
                val path = Path().apply {
                    moveTo(centerX - width * 0.5f, centerY)
                    quadTo(centerX, centerY + width * 0.8f, centerX + width * 0.5f, centerY)
                }
                canvas.drawPath(path, paint)
                // Tongue
                val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6B6B") }
                canvas.drawOval(RectF(centerX - width * 0.3f, centerY, centerX + width * 0.3f, centerY + width * 0.6f), tonguePaint)
            }
        }
    }

    private fun drawEffects(canvas: Canvas) {
        when (currentMood) {
            Mood.EXCITED, Mood.HAPPY -> drawParticles(canvas)
            Mood.SAD -> drawTears(canvas)
            Mood.ANGRY -> drawSteam(canvas)
            Mood.SLEEPY -> drawZzz(canvas)
            Mood.AFFECTIONATE -> drawHearts(canvas)
            Mood.SURPRISED -> drawPopLines(canvas)
            Mood.CONFUSED -> drawSpiral(canvas)
            Mood.ANXIOUS -> drawJitterLines(canvas)
            else -> {}
        }

        // Voice state effects
        when (voiceState) {
            VoiceState.LISTENING -> drawListeningWaves(canvas)
            VoiceState.SPEAKING -> drawSpeakingWaves(canvas)
            VoiceState.THINKING -> drawThinkingSpinner(canvas)
            else -> {}
        }
    }

    private fun drawParticles(canvas: Canvas) {
        // Floating particles around ball
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = getMoodColor(currentMood) }
        // Simplified - in real impl would animate particles
        for (i in 0..5) {
            val angle = (System.currentTimeMillis() / 500 + i * 60) % 360
            val radius = ballSize * (0.6f + 0.2f * (Math.sin(Math.toRadians(angle.toDouble())).toFloat()))
            val x = ballSize / 2f + radius * Math.cos(Math.toRadians(angle.toDouble())).toFloat()
            val y = ballSize / 2f + radius * Math.sin(Math.toRadians(angle.toDouble())).toFloat()
            canvas.drawCircle(x, y, 3f, paint)
        }
    }

    private fun drawTears(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLUE }
        val centerX = ballSize / 2f
        val centerY = ballSize / 2f
        val eyeOffset = ballSize * 0.18f
        val eyeY = centerY - ballSize * 0.15f
        val tearY = eyeY + ballSize * 0.15f + (System.currentTimeMillis() % 1000) / 1000f * ballSize * 0.3f
        canvas.drawOval(RectF(centerX - eyeOffset - 3f, tearY - 6f, centerX - eyeOffset + 3f, tearY + 6f), paint)
        canvas.drawOval(RectF(centerX + eyeOffset - 3f, tearY - 6f, centerX + eyeOffset + 3f, tearY + 6f), paint)
    }

    private fun drawSteam(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 2f }
        val centerX = ballSize / 2f
        val topY = 0f
        for (i in 0..2) {
            val offset = (System.currentTimeMillis() / 300 + i * 300) % 1000
            val y = topY - offset / 1000f * ballSize * 0.5f
            val path = Path().apply {
                moveTo(centerX - 10f, y)
                quadTo(centerX - 15f, y - 10f, centerX, y - 20f)
                quadTo(centerX + 15f, y - 10f, centerX + 10f, y)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawZzz(canvas: Canvas) {
        textPaint.color = Color.BLUE
        val centerX = ballSize / 2f
        val topY = -ballSize * 0.3f
        val offset = (System.currentTimeMillis() / 500) % 3
        for (i in 0..2) {
            val alpha = if (i == offset) 255 else 128
            textPaint.alpha = alpha
            canvas.drawText("Z", centerX + (i - 1) * 15f, topY - i * 20f, textPaint)
        }
        textPaint.alpha = 255
    }

    private fun drawHearts(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6B9D") }
        val centerX = ballSize / 2f
        val centerY = ballSize / 2f
        for (i in 0..3) {
            val angle = (System.currentTimeMillis() / 800 + i * 90) % 360
            val radius = ballSize * 0.7f
            val x = centerX + radius * Math.cos(Math.toRadians(angle.toDouble())).toFloat()
            val y = centerY + radius * Math.sin(Math.toRadians(angle.toDouble())).toFloat()
            drawHeart(canvas, x, y, 12f, paint)
        }
    }

    private fun drawHeart(canvas: Canvas, x: Float, y: Float, size: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(x, y + size * 0.3f)
            quadTo(x - size, y - size, x, y - size * 0.5f)
            quadTo(x + size, y - size, x, y + size * 0.3f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPopLines(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 3f }
        val centerX = ballSize / 2f
        val centerY = ballSize / 2f
        for (i in 0..7) {
            val angle = i * 45.0
            val len = ballSize * 0.6f
            val x1 = centerX + len * 0.7f * Math.cos(Math.toRadians(angle)).toFloat()
            val y1 = centerY + len * 0.7f * Math.sin(Math.toRadians(angle)).toFloat()
            val x2 = centerX + len * Math.cos(Math.toRadians(angle)).toFloat()
            val y2 = centerY + len * Math.sin(Math.toRadians(angle)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
    }

    private fun drawSpiral(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.MAGENTA; style = Paint.Style.STROKE; strokeWidth = 2f }
        val centerX = ballSize / 2f
        val centerY = ballSize / 2f
        val path = Path()
        val turns = 2
        val maxRadius = ballSize * 0.4f
        for (i in 0..360 * turns) {
            val angle = Math.toRadians(i.toDouble())
            val radius = maxRadius * (i / (360f * turns))
            val x = centerX + radius * Math.cos(angle).toFloat()
            val y = centerY + radius * Math.sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawJitterLines(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 1f }
        val centerX = ballSize / 2f
        val centerY = ballSize / 2f
        for (i in 0..5) {
            val angle = (System.currentTimeMillis() / 100 + i * 60) % 360
            val radius = ballSize * 0.5f
            val x = centerX + radius * Math.cos(Math.toRadians(angle.toDouble())).toFloat()
            val y = centerY + radius * Math.sin(Math.toRadians(angle.toDouble())).toFloat()
            val len = 10f + (Math.random() * 10).toFloat()
            val x2 = x + len * Math.cos(Math.toRadians(angle.toDouble())).toFloat()
            val y2 = y + len * Math.sin(Math.toRadians(angle.toDouble())).toFloat()
            canvas.drawLine(x, y, x2, y2, paint)
        }
    }

    private fun drawListeningWaves(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 2f }
        val centerX = ballSize / 2f
        val centerY = ballSize / 2f
        for (i in 0..3) {
            val radius = ballSize * 0.5f + (System.currentTimeMillis() / 200 + i * 200) % 1000 / 1000f * ballSize * 0.4f
            val alpha = (1f - ((System.currentTimeMillis() / 200 + i * 200) % 1000) / 1000f) * 255
            paint.alpha = alpha.toInt()
            canvas.drawCircle(centerX, centerY, radius, paint)
        }
        paint.alpha = 255
    }

    private fun drawSpeakingWaves(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 2f }
        val centerX = ballSize / 2f
        val centerY = ballSize / 2f
        // Waveform bars
        for (i in -5..5) {
            val height = (Math.abs(Math.sin((System.currentTimeMillis() / 100 + i * 100).toDouble())) * ballSize * 0.3f).toFloat()
            val x = centerX + i * 8f
            canvas.drawLine(x, centerY - height, x, centerY + height, paint)
        }
    }

    private fun drawThinkingSpinner(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
        val centerX = ballSize / 2f
        val centerY = ballSize / 2f
        val radius = ballSize * 0.35f
        val startAngle = (System.currentTimeMillis() / 10) % 360
        canvas.drawArc(RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius), startAngle.toFloat(), 270f, false, paint)
    }

    private fun drawOverlays(canvas: Canvas) {
        // Notification badge
        if (notificationCount > 0) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
            val badgeRadius = 14f
            val x = ballSize - badgeRadius
            val y = badgeRadius
            canvas.drawCircle(x, y, badgeRadius, badgePaint)
            textPaint.color = Color.WHITE
            textPaint.textSize = 12f
            canvas.drawText(notificationCount.toString(), x, y + 4f, textPaint)
        }

        // Battery indicator
        if (batteryLevel < 20 || isCharging) {
            val batW = 24f
            val batH = 12f
            val x = ballSize - batW - 4f
            val y = 4f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // Battery outline
            paint.style = Paint.Style.STROKE
            paint.color = if (isCharging) Color.GREEN else (if (batteryLevel < 20) Color.RED else Color.WHITE)
            paint.strokeWidth = 1.5f
            canvas.drawRoundRect(RectF(x, y, x + batW, y + batH), 2f, 2f, paint)
            // Battery tip
            canvas.drawRect(RectF(x + batW, y + batH * 0.3f, x + batW + 2f, y + batH * 0.7f), paint)
            // Battery fill
            paint.style = Paint.Style.FILL
            val fillW = batW * 0.9f * batteryLevel / 100f
            canvas.drawRoundRect(RectF(x + 1f, y + 1f, x + 1f + fillW, y + batH - 1f), 1f, 1f, paint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw composed layers
        baseLayer.bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        faceLayer.bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        effectLayer.bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        overlayLayer.bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            recreateLayers(w, h)
        }
    }

    private fun recreateLayers(w: Int, h: Int) {
        baseLayer.resize(w, h)
        faceLayer.resize(w, h)
        effectLayer.resize(w, h)
        overlayLayer.resize(w, h)
        initializeLayers()
        invalidate()
    }

    fun onTouchDown() {
        isBeingDragged = true
        stopIdleAnimations()
        // Scale down slightly
        animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
    }

    fun onTouchMove(dx: Float, dy: Float) {
        // Wobble effect while dragging
        rotation = (rotation + dx * 0.1f) % 360
    }

    fun onTouchUp() {
        isBeingDragged = false
        animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(OvershootInterpolator(1.2f)).start()
        startIdleAnimations()
    }

    fun onTap() {
        // Double-tap or single-tap action
        triggerTapAnimation()
    }

    private fun triggerTapAnimation() {
        // Quick scale bounce
        animate().scaleX(1.2f).scaleY(1.2f).setDuration(80).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(OvershootInterpolator(1.5f)).start()
        }.start()
        // Change mood briefly
        val prevMood = currentMood
        setMood(Mood.SURPRISED)
        postDelayed({ setMood(prevMood) }, 1000)
    }

    fun triggerIdleAnimation() {
        // Random idle behavior based on mood
        when (currentMood) {
            Mood.HAPPY, Mood.EXCITED, Mood.PLAYFUL -> {
                // Bounce
                animate().translationY(-10f).setDuration(200).withEndAction {
                    animate().translationY(0f).setDuration(300).setInterpolator(OvershootInterpolator(1.2f)).start()
                }.start()
            }
            Mood.SLEEPY -> {
                // Slow blink
                setLook(Look.SLEEPY)
                postDelayed({ setLook(Look.DEFAULT) }, 2000)
            }
            Mood.CURIOUS -> {
                // Look around
                val prevLook = currentLook
                setLook(Look.CURIOUS)
                postDelayed({ setLook(prevLook) }, 1500)
            }
            else -> {
                // Subtle breathe
            }
        }
    }

    fun setMood(mood: Mood) {
        currentMood = mood
        initializeLayers() // Redraw with new mood color
        invalidate()
    }

    fun setLook(look: Look) {
        currentLook = look
        faceLayer.draw { drawFace(it) }
        invalidate()
    }

    fun setVoiceState(state: VoiceState) {
        voiceState = state
        // Start/stop voice animations
        when (state) {
            VoiceState.LISTENING, VoiceState.SPEAKING, VoiceState.THINKING -> {
                // Voice animations handled in drawEffects
            }
            VoiceState.IDLE -> {}
        }
        invalidate()
    }

    fun showNotification(count: Int) {
        notificationCount = count
        overlayLayer.draw { drawOverlays(it) }
        invalidate()
    }

    fun setBatteryLevel(level: Int, charging: Boolean) {
        batteryLevel = level
        isCharging = charging
        overlayLayer.draw { drawOverlays(it) }
        invalidate()
    }

    private fun startBreathing() {
        breatheAnimator = ValueAnimator.ofFloat(1f, 1.02f, 1f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                scaleX = scale
                scaleY = scale
            }
        }.apply { start() }
    }

    private fun stopIdleAnimations() {
        idleAnimator?.cancel()
    }

    private fun startIdleAnimations() {
        idleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { _ -> triggerIdleAnimation() }
        }.apply { start() }
    }

    private fun getMoodColor(mood: Mood): Int = when (mood) {
        Mood.HAPPY, Mood.EXCITED, Mood.PROUD, Mood.AFFECTIONATE -> Color.parseColor("#FFD93D")
        Mood.SAD, Mood.SLEEPY, Mood.RELAXED -> Color.parseColor("#6BCBFF")
        Mood.ANGRY, Mood.ANXIOUS, Mood.DETERMINED, Mood.ALERT -> Color.parseColor("#FF6B6B")
        Mood.SURPRISED, Mood.CURIOUS -> Color.parseColor("#FF9F1C")
        Mood.CONFUSED, Mood.EMBARRASSED -> Color.parseColor("#C77DFF")
        Mood.FOCUSED -> Color.parseColor("#4ECDC4")
        Mood.BORED -> Color.parseColor("#95A5A6")
        else -> Color.parseColor("#FFFFFF")
    }

    private class BitmapLayer {
        var bitmap: Bitmap? = null
        private var canvas: Canvas? = null

        fun resize(width: Int, height: Int) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            canvas = Canvas(bitmap!!)
        }

        fun draw(block: (Canvas) -> Unit) {
            canvas?.let { c ->
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                block(c)
            }
        }
    }
}