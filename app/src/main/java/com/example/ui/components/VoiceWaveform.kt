package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.domain.model.RingState
import com.example.ui.theme.TagoreCyanBright
import com.example.ui.theme.TagoreCyanPrimary
import com.example.ui.theme.TagoreErrorRed
import com.example.ui.theme.TagoreSuccessGreen
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Voice-reactive cyan waveform rendered directly below the TAGORE Holographic Ring.
 */
@Composable
fun VoiceWaveform(
    ringState: RingState,
    audioLevel: Float,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "voice_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val waveColor = when (ringState) {
        RingState.ERROR, RingState.EMERGENCY -> TagoreErrorRed
        RingState.SUCCESS -> TagoreSuccessGreen
        else -> if (isDarkMode) TagoreCyanPrimary else MaterialTheme.colorScheme.primary
    }
    val highlight = if (isDarkMode) TagoreCyanBright else waveColor

    val baseEnergy = when (ringState) {
        RingState.SPEAKING -> 0.72f + audioLevel * 0.28f
        RingState.LISTENING -> 0.42f + audioLevel * 0.58f
        RingState.WAKE, RingState.WAKE_UP, RingState.WORKING -> 0.48f
        RingState.THINKING, RingState.SEARCHING -> 0.34f
        RingState.SLEEP -> 0.04f
        else -> 0.12f
    }.coerceIn(0.03f, 1.0f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        val barCount = 35
        val centerIdx = barCount / 2
        val totalWidth = size.width
        val step = totalWidth / (barCount + 1)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val x = (i + 1) * step
            val distFromCenter = abs(i - centerIdx).toFloat() / centerIdx.toFloat()
            val envelope = (1f - distFromCenter * distFromCenter).coerceIn(0.08f, 1f)
            val harmonic = abs(sin(phase + i * 0.48f))
            val barHalfHeight = (size.height * 0.44f) * envelope * baseEnergy * (0.35f + 0.65f * harmonic)

            val barColor: Color = if (distFromCenter < 0.35f) highlight else waveColor
            drawLine(
                color = barColor.copy(alpha = (0.35f + 0.65f * envelope)),
                start = Offset(x, centerY - barHalfHeight.coerceAtLeast(1.5f)),
                end = Offset(x, centerY + barHalfHeight.coerceAtLeast(1.5f)),
                strokeWidth = (step * 0.44f).coerceIn(2f, 5f),
                cap = StrokeCap.Round
            )
        }
    }
}
