package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GitAccent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Material Design 3 Expressive UI Snake Squiggle Loading Indicator.
 *
 * Replaces standard CircularProgressIndicator with a vibrant, undulating sinusoidal
 * "snake" squiggle path that slithers, expands, contracts, and undulates smoothly
 * around an orbital track with rounded head and tapered tail.
 */
@Composable
fun ExpressiveSnakeLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = GitAccent,
    trackColor: Color = Color.Transparent,
    strokeWidth: Dp = 3.dp,
    waveCount: Int = 6,
    amplitudeDp: Dp = 2.5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "snake_loading")

    // Rotation of the snake around the center
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snake_rotation"
    )

    // Dynamic wave phase producing the slithering snake ripple
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snake_wave_phase"
    )

    // Indeterminate sweep length (snake expanding and contracting as it crawls)
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 70f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "snake_sweep"
    )

    Box(
        modifier = modifier
            .progressSemantics()
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = strokeWidth.toPx()
            val ampPx = min(amplitudeDp.toPx(), size.minDimension * 0.12f)
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) - strokePx - ampPx

            if (baseRadius <= 0f) return@Canvas

            // Optional faint background track
            if (trackColor != Color.Transparent && trackColor.alpha > 0f) {
                drawCircle(
                    color = trackColor,
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = strokePx * 0.7f)
                )
            }

            // Draw the undulating snake squiggle
            val path = Path()
            val startDeg = rotation
            val totalSweep = sweepAngle
            val steps = 72
            val degStep = totalSweep / steps

            var headPoint = Offset.Zero
            var tailPoint = Offset.Zero

            for (i in 0..steps) {
                val currentDeg = startDeg + (i * degStep)
                val rad = Math.toRadians(currentDeg.toDouble())
                // Radial sinusoidal ripple creates the authentic expressive snake squiggle
                val waveOffset = sin(waveCount * rad + wavePhase).toFloat() * ampPx
                val r = baseRadius + waveOffset

                val x = center.x + r * cos(rad).toFloat()
                val y = center.y + r * sin(rad).toFloat()

                val point = Offset(x, y)
                if (i == 0) {
                    path.moveTo(x, y)
                    tailPoint = point
                } else {
                    path.lineTo(x, y)
                }
                if (i == steps) {
                    headPoint = point
                }
            }

            // Draw snake body with smooth rounded joints
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw expressive snake head accent (slightly larger eye/head dot)
            if (headPoint != Offset.Zero && strokePx >= 3f) {
                drawCircle(
                    color = color,
                    radius = strokePx * 0.7f,
                    center = headPoint
                )
            }
        }
    }
}

/**
 * Material Design 3 Expressive UI Linear Snake Squiggle Progress Bar.
 *
 * Replaces standard LinearProgressIndicator with a slithering sinusoidal snake
 * crawling horizontally with expressive waves and rounded caps.
 */
@Composable
fun ExpressiveLinearSnakeIndicator(
    modifier: Modifier = Modifier,
    color: Color = GitAccent,
    trackColor: Color = color.copy(alpha = 0.15f),
    strokeWidth: Dp = 3.5.dp,
    amplitudeDp: Dp = 3.5.dp,
    wavelengthDp: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "linear_snake")

    val headFraction by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "linear_head"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "linear_wave_phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .progressSemantics(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = strokeWidth.toPx()
            val ampPx = amplitudeDp.toPx()
            val waveLenPx = wavelengthDp.toPx().coerceAtLeast(16f)
            val centerY = size.height / 2f
            val width = size.width

            // Subtle base track line
            if (trackColor.alpha > 0f) {
                drawLine(
                    color = trackColor,
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = strokePx * 0.5f,
                    cap = StrokeCap.Round
                )
            }

            // Snake segment boundaries (slithering length ~ 35% of total width)
            val snakeLengthPx = (width * 0.4f).coerceAtLeast(60f)
            val headX = headFraction * width
            val tailX = headX - snakeLengthPx

            val visibleStartX = tailX.coerceIn(0f, width)
            val visibleEndX = headX.coerceIn(0f, width)

            if (visibleEndX > visibleStartX) {
                val path = Path()
                val stepPx = 3f
                var isFirst = true
                var currX = visibleStartX

                while (currX <= visibleEndX) {
                    val angle = (2 * PI * (currX / waveLenPx)) - wavePhase
                    // Taper amplitude at the snake head and tail
                    val progressInSnake = ((currX - tailX) / snakeLengthPx).coerceIn(0f, 1f)
                    val envelope = sin(progressInSnake * PI).toFloat()
                    val y = centerY + (sin(angle).toFloat() * ampPx * envelope)

                    if (isFirst) {
                        path.moveTo(currX, y)
                        isFirst = false
                    } else {
                        path.lineTo(currX, y)
                    }
                    currX += stepPx
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Leading snake head dot
                if (headX in 0f..width) {
                    val headAngle = (2 * PI * (headX / waveLenPx)) - wavePhase
                    val headY = centerY + (sin(headAngle).toFloat() * ampPx * 0.2f)
                    drawCircle(
                        color = color,
                        radius = strokePx * 0.75f,
                        center = Offset(headX, headY)
                    )
                }
            }
        }
    }
}
