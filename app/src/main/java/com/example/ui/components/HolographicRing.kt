package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.domain.model.RingState
import com.example.ui.theme.TagoreCyanBright
import com.example.ui.theme.TagoreCyanPrimary
import com.example.ui.theme.TagoreErrorRed
import com.example.ui.theme.TagoreOfflineAmber
import com.example.ui.theme.TagoreSuccessGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Centerpiece TAGORE Holographic Ring implementing all 15 visual movement states,
 * opposite rotational speeds, luminous orbital nodes, confirmation tika/checkmark,
 * and interactive gesture rotation/zoom.
 */
@Composable
fun HolographicRing(
    ringState: RingState,
    isOfflineMode: Boolean,
    audioLevel: Float,
    brightness: Float,
    animationSpeed: Float,
    isDarkMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speedFactor = animationSpeed.coerceIn(0.4f, 2.0f)
    val baseDurationMs = when (ringState) {
        RingState.SLEEP -> 24000
        RingState.IDLE, RingState.WAITING -> 14000
        RingState.LISTENING, RingState.SPEAKING -> 7500
        RingState.THINKING, RingState.SEARCHING, RingState.CAMERA_VISION -> 4800
        RingState.WORKING -> 3200
        RingState.WAKE, RingState.WAKE_UP -> 4200
        RingState.SUCCESS, RingState.ERROR, RingState.EMERGENCY -> 5500
        RingState.GESTURE_MODE -> 9000
    }
    val adjustedDuration = (baseDurationMs / speedFactor).toInt().coerceAtLeast(1200)

    val infiniteTransition = rememberInfiniteTransition(label = "tagore_ring")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = adjustedDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_angle"
    )
    // Inner rings rotate in opposite direction and slower than outer ring per spec
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (adjustedDuration * 1.45f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_angle"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (ringState) {
                    RingState.EMERGENCY, RingState.ERROR -> 650
                    RingState.WAKE, RingState.WAKE_UP, RingState.WORKING -> 900
                    RingState.SLEEP -> 3200
                    else -> 1800
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val animatedAudio by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "audio_level"
    )

    var gestureRotationOffset by remember { mutableFloatStateOf(0f) }
    var gestureZoom by remember { mutableFloatStateOf(1f) }

    val primaryColor = when (ringState) {
        RingState.ERROR, RingState.EMERGENCY -> TagoreErrorRed
        RingState.SUCCESS -> TagoreSuccessGreen
        else -> if (isDarkMode) TagoreCyanPrimary else MaterialTheme.colorScheme.primary
    }
    val highlightColor = when (ringState) {
        RingState.ERROR, RingState.EMERGENCY -> Color(0xFFFF8A98)
        RingState.SUCCESS -> Color(0xFFB9F6CA)
        else -> if (isDarkMode) TagoreCyanBright else Color(0xFF4DD0E1)
    }

    val intensityAlpha = (
        when (ringState) {
            RingState.SLEEP -> 0.35f
            RingState.IDLE, RingState.WAITING -> 0.72f + pulse * 0.14f
            RingState.LISTENING, RingState.SPEAKING -> 0.85f + animatedAudio * 0.15f
            RingState.WAKE, RingState.WAKE_UP, RingState.SUCCESS -> 1.0f
            else -> 0.88f + pulse * 0.12f
        } * brightness.coerceIn(0.25f, 1.0f)
    ).coerceIn(0.2f, 1f)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("tagore_holographic_ring")
            .semantics {
                contentDescription = "TAGORE Holographic Ring, state ${ringState.displayTitle}"
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(ringState) {
                if (ringState == RingState.GESTURE_MODE) {
                    detectTransformGestures { _, _, zoom, rotation ->
                        gestureRotationOffset = (gestureRotationOffset + rotation) % 360f
                        gestureZoom = (gestureZoom * zoom).coerceIn(0.75f, 1.25f)
                    }
                } else {
                    gestureZoom = 1f
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val expandFactor = when (ringState) {
                RingState.WAKE, RingState.WAKE_UP -> 1.0f + pulse * 0.045f
                RingState.LISTENING, RingState.SPEAKING -> 1.0f + animatedAudio * 0.055f
                RingState.GESTURE_MODE -> gestureZoom
                else -> 1.0f
            }
            val maxR = (minDim * 0.45f) * expandFactor

            // Ambient radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.22f * intensityAlpha),
                        primaryColor.copy(alpha = 0.06f * intensityAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR * 1.15f
                ),
                radius = maxR * 1.15f,
                center = center
            )

            // 1. Largest Outer Segmented Ring (clockwise)
            rotate(degrees = outerAngle + gestureRotationOffset, pivot = center) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.26f * intensityAlpha),
                    radius = maxR,
                    center = center,
                    style = Stroke(width = minDim * 0.006f)
                )
                val arcCount = 4
                for (i in 0 until arcCount) {
                    val startAngle = i * (360f / arcCount) + 10f
                    drawArc(
                        color = primaryColor.copy(alpha = 0.92f * intensityAlpha),
                        startAngle = startAngle,
                        sweepAngle = 58f,
                        useCenter = false,
                        topLeft = Offset(center.x - maxR, center.y - maxR),
                        size = Size(maxR * 2f, maxR * 2f),
                        style = Stroke(width = minDim * 0.016f, cap = StrokeCap.Round)
                    )
                }
                // Fine telemetry tick marks on outer ring
                val tickCount = 48
                for (t in 0 until tickCount) {
                    val rad = Math.toRadians((t * (360.0 / tickCount)))
                    val innerTickR = maxR * 0.94f
                    val outerTickR = if (t % 4 == 0) maxR * 1.035f else maxR * 0.99f
                    val tickAlpha = if (ringState == RingState.WORKING && (t % 8) == ((outerAngle / 45f).toInt() % 8)) {
                        1f
                    } else {
                        0.45f * intensityAlpha
                    }
                    drawLine(
                        color = highlightColor.copy(alpha = tickAlpha),
                        start = Offset(
                            center.x + (innerTickR * cos(rad)).toFloat(),
                            center.y + (innerTickR * sin(rad)).toFloat()
                        ),
                        end = Offset(
                            center.x + (outerTickR * cos(rad)).toFloat(),
                            center.y + (outerTickR * sin(rad)).toFloat()
                        ),
                        strokeWidth = if (t % 4 == 0) minDim * 0.007f else minDim * 0.0035f
                    )
                }
            }

            // 2. Second Orbital Ring (counter-clockwise, slightly slower)
            val r2 = maxR * 0.82f
            rotate(degrees = innerAngle + gestureRotationOffset * 0.6f, pivot = center) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f * intensityAlpha),
                    radius = r2,
                    center = center,
                    style = Stroke(width = minDim * 0.008f)
                )
                for (i in 0 until 6) {
                    drawArc(
                        color = highlightColor.copy(alpha = 0.85f * intensityAlpha),
                        startAngle = i * 60f + 8f,
                        sweepAngle = 36f,
                        useCenter = false,
                        topLeft = Offset(center.x - r2, center.y - r2),
                        size = Size(r2 * 2f, r2 * 2f),
                        style = Stroke(width = minDim * 0.013f, cap = StrokeCap.Round)
                    )
                }
            }

            // 3. Bright Orbital Dots between Outer and Second Ring (hidden in SLEEP)
            if (ringState != RingState.SLEEP) {
                val nodeCount = 8
                val orbitR = maxR * 0.90f
                for (n in 0 until nodeCount) {
                    val dir = if (ringState == RingState.THINKING) -1f else 1f
                    val deg = (outerAngle * 0.7f * dir) + (n * (360f / nodeCount))
                    val rad = Math.toRadians(deg.toDouble())
                    val dotPos = Offset(
                        center.x + (orbitR * cos(rad)).toFloat(),
                        center.y + (orbitR * sin(rad)).toFloat()
                    )
                    val dotColor = if (isOfflineMode && n % 2 == 0) TagoreOfflineAmber else highlightColor
                    drawCircle(
                        color = dotColor.copy(alpha = 0.9f * intensityAlpha),
                        radius = minDim * (if (n % 2 == 0) 0.011f else 0.007f),
                        center = dotPos
                    )
                }
            }

            // 4. Third Inner Precision Ring (slower opposite rotation)
            val r3 = maxR * 0.63f
            rotate(degrees = (outerAngle * 0.5f), pivot = center) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.55f * intensityAlpha),
                    radius = r3,
                    center = center,
                    style = Stroke(width = minDim * 0.01f)
                )
                for (i in 0 until 3) {
                    drawArc(
                        color = primaryColor.copy(alpha = 0.95f * intensityAlpha),
                        startAngle = i * 120f + 15f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(center.x - r3, center.y - r3),
                        size = Size(r3 * 2f, r3 * 2f),
                        style = Stroke(width = minDim * 0.014f, cap = StrokeCap.Round)
                    )
                }
            }

            // 5. Innermost Core Halo Ring
            val r4 = maxR * 0.42f
            rotate(degrees = innerAngle * 0.45f, pivot = center) {
                drawCircle(
                    color = highlightColor.copy(alpha = (0.4f + pulse * 0.25f) * intensityAlpha),
                    radius = r4,
                    center = center,
                    style = Stroke(width = minDim * 0.008f)
                )
            }

            // 6. State-Specific Luminous Signals
            when (ringState) {
                RingState.SEARCHING -> {
                    // Luminous radial beam traveling outward and back
                    val beamLength = r4 + (maxR - r4) * pulse
                    val rad = Math.toRadians(outerAngle.toDouble())
                    val endPt = Offset(
                        center.x + (beamLength * cos(rad)).toFloat(),
                        center.y + (beamLength * sin(rad)).toFloat()
                    )
                    drawLine(
                        color = highlightColor,
                        start = center,
                        end = endPt,
                        strokeWidth = minDim * 0.01f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = Color.White, radius = minDim * 0.018f, center = endPt)
                }
                RingState.CAMERA_VISION -> {
                    // Horizontal holographic scanner sweep
                    val scanY = center.y + ((pulse - 0.5f) * 2f) * (r3 * 0.8f)
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, highlightColor, Color.Transparent),
                            startX = center.x - r3,
                            endX = center.x + r3
                        ),
                        start = Offset(center.x - r3, scanY),
                        end = Offset(center.x + r3, scanY),
                        strokeWidth = minDim * 0.01f
                    )
                }
                RingState.THINKING -> {
                    // Inward-flowing luminous particles
                    for (p in 0 until 6) {
                        val frac = ((pulse + p / 6f) % 1f)
                        val dist = maxR * 0.78f * (1f - frac)
                        val rad = Math.toRadians((p * 60f + outerAngle).toDouble())
                        val pt = Offset(
                            center.x + (dist * cos(rad)).toFloat(),
                            center.y + (dist * sin(rad)).toFloat()
                        )
                        drawCircle(
                            color = highlightColor.copy(alpha = (1f - frac) * intensityAlpha),
                            radius = minDim * 0.01f,
                            center = pt
                        )
                    }
                }
                else -> Unit
            }

            // 7. Center Core / Tika Confirmation / Error / Emergency Glyph
            drawCenterNucleus(
                ringState = ringState,
                center = center,
                minDim = minDim,
                coreRadius = r4 * 0.52f,
                primaryColor = primaryColor,
                highlightColor = highlightColor,
                intensityAlpha = intensityAlpha,
                pulse = pulse,
                audioLevel = animatedAudio
            )

            // Offline subtle outer amber arc indicator when offline
            if (isOfflineMode) {
                val offlineR = maxR * 1.07f
                drawArc(
                    color = TagoreOfflineAmber.copy(alpha = 0.78f),
                    startAngle = 245f,
                    sweepAngle = 50f,
                    useCenter = false,
                    topLeft = Offset(center.x - offlineR, center.y - offlineR),
                    size = Size(offlineR * 2f, offlineR * 2f),
                    style = Stroke(width = minDim * 0.01f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

private fun DrawScope.drawCenterNucleus(
    ringState: RingState,
    center: Offset,
    minDim: Float,
    coreRadius: Float,
    primaryColor: Color,
    highlightColor: Color,
    intensityAlpha: Float,
    pulse: Float,
    audioLevel: Float
) {
    // Glowing center disc
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                highlightColor.copy(alpha = 0.85f * intensityAlpha),
                primaryColor.copy(alpha = 0.32f * intensityAlpha),
                Color.Transparent
            ),
            center = center,
            radius = coreRadius * (1.25f + audioLevel * 0.35f)
        ),
        radius = coreRadius * (1.25f + audioLevel * 0.35f),
        center = center
    )

    when (ringState) {
        RingState.SUCCESS -> {
            // Tika-like confirmation checkmark in the center
            val s = coreRadius * 0.65f
            val path = Path().apply {
                moveTo(center.x - s * 0.7f, center.y + s * 0.05f)
                lineTo(center.x - s * 0.15f, center.y + s * 0.6f)
                lineTo(center.x + s * 0.8f, center.y - s * 0.55f)
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = minDim * 0.022f, cap = StrokeCap.Round)
            )
        }
        RingState.ERROR -> {
            // Minimal crimson X indicator
            val s = coreRadius * 0.52f
            drawLine(
                color = Color.White,
                start = Offset(center.x - s, center.y - s),
                end = Offset(center.x + s, center.y + s),
                strokeWidth = minDim * 0.02f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(center.x + s, center.y - s),
                end = Offset(center.x - s, center.y + s),
                strokeWidth = minDim * 0.02f,
                cap = StrokeCap.Round
            )
        }
        RingState.EMERGENCY -> {
            // Controlled warning triangle
            val s = coreRadius * 0.72f
            val tri = Path().apply {
                moveTo(center.x, center.y - s * 0.8f)
                lineTo(center.x + s * 0.78f, center.y + s * 0.62f)
                lineTo(center.x - s * 0.78f, center.y + s * 0.62f)
                close()
            }
            drawPath(
                path = tri,
                color = Color.White,
                style = Stroke(width = minDim * 0.016f, cap = StrokeCap.Round)
            )
        }
        else -> {
            val dotR = (minDim * 0.032f) * (1f + pulse * 0.14f + audioLevel * 0.25f)
            drawCircle(
                color = highlightColor.copy(alpha = 0.95f * intensityAlpha),
                radius = dotR,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * intensityAlpha),
                radius = dotR * 0.48f,
                center = center
            )
        }
    }
}
