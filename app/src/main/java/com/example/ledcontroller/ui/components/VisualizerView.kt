package com.example.ledcontroller.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.coroutines.launch
import kotlin.math.pow

@Composable
fun VisualizerView(waveform: List<Float>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val barCount = 29
    val animations = remember { List(barCount) { Animatable(0f) } }

    LaunchedEffect(waveform) {
        val centerIndex = barCount / 2
        animations.forEachIndexed { index, animatable ->
            val distFromCenter = kotlin.math.abs(index - centerIndex)
            val waveIndex = (waveform.size - 1 - distFromCenter).coerceAtLeast(0)
            val rawAmplitude = waveform.getOrElse(waveIndex) { 0f }
            val scaleFactor = 1f - (distFromCenter.toFloat() / centerIndex.toFloat()).pow(1.5f)
            val targetValue = (rawAmplitude * scaleFactor).coerceIn(0.01f, 1f)

            launch {
                animatable.animateTo(targetValue, animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing))
            }
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val totalBarWidth = width / barCount
        val barWidth = totalBarWidth * 0.6f
        val maxHeight = height * 0.9f

        animations.forEachIndexed { index, animatable ->
            val x = (index * totalBarWidth) + (totalBarWidth / 2)
            val barHeight = (animatable.value * maxHeight).coerceAtLeast(6f)
            drawLine(
                color = barColor,
                start = Offset(x, (height / 2) - (barHeight / 2)),
                end = Offset(x, (height / 2) + (barHeight / 2)),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}