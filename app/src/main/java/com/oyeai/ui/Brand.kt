package com.oyeai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oyeai.R
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * The OYE mark: a glowing cyan-to-violet ring with a light point at its upper right.
 * [progress] draws the ring as it fills, for the splash animation.
 */
@Composable
fun OyeMark(modifier: Modifier = Modifier, progress: Float = 1f) {
    Canvas(modifier) {
        val side = size.minDimension
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = side * 0.34f
        val stroke = side * 0.07f
        val topLeft = Offset(centre.x - radius, centre.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        val sweep = 360f * progress.coerceIn(0f, 1f)
        val ring = Brush.sweepGradient(listOf(OyeCyan, OyeViolet, OyeCyan), centre)

        // Soft glow behind the ring.
        drawArc(
            color = OyeCyan.copy(alpha = 0.22f),
            startAngle = -90f, sweepAngle = sweep, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke * 2.6f, cap = StrokeCap.Round)
        )
        drawArc(
            brush = ring,
            startAngle = -90f, sweepAngle = sweep, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        if (progress >= 1f) {
            val angle = Math.toRadians(-45.0)
            val dot = Offset(
                centre.x + radius * cos(angle).toFloat(),
                centre.y + radius * sin(angle).toFloat()
            )
            drawCircle(color = OyeCyan.copy(alpha = 0.35f), radius = stroke * 1.1f, center = dot)
            drawCircle(color = Color.White, radius = stroke * 0.42f, center = dot)
        }
    }
}

/** The OYE wordmark with a cyan-to-violet gradient. */
@Composable
fun OyeWordmark(size: Int = 40, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.app_name),
        modifier = modifier,
        style = androidx.compose.ui.text.TextStyle(
            brush = Brush.linearGradient(listOf(OyeCyan, OyeViolet)),
            fontSize = size.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (size / 3).sp
        )
    )
}

/** "Developed by Professor", kept small and quiet wherever it appears. */
@Composable
fun CreatorCredit(modifier: Modifier = Modifier, alpha: Float = 1f) {
    Text(
        text = stringResource(R.string.creator_credit),
        modifier = modifier.alpha(alpha),
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        color = OyeMist,
        textAlign = TextAlign.Center
    )
}

/** Dark background with a soft violet light in the centre. */
@Composable
fun NightBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(OyeViolet.copy(alpha = 0.18f), OyeNight),
                    radius = 1100f
                )
            )
    ) { content() }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
        reveal.animateTo(1f, tween(700))
        delay(700)
        onFinished()
    }
    val pulse by rememberInfiniteTransition(label = "splash-glow").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulse"
    )
    NightBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            OyeMark(
                modifier = Modifier.size(150.dp).alpha(0.6f + 0.4f * pulse),
                progress = progress.value
            )
            Spacer(Modifier.height(20.dp))
            OyeWordmark(size = 40, modifier = Modifier.alpha(reveal.value))
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                modifier = Modifier.alpha(reveal.value),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = OyeMist
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 48.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            CreatorCredit(alpha = reveal.value)
        }
    }
}
