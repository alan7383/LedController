package com.example.ledcontroller.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ColorWheel(modifier: Modifier = Modifier, brightness: Float = 1f, currentHue: Float, currentSat: Float, onColorChanged: (hue: Float, saturation: Float) -> Unit) {
    var inputSize by remember { mutableStateOf(IntSize.Zero) }
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    val view = LocalView.current

    LaunchedEffect(inputSize, currentHue, currentSat) {
        if (inputSize != IntSize.Zero) {
            val center = Offset(inputSize.width / 2f, inputSize.height / 2f)
            val radius = min(inputSize.width, inputSize.height) / 2f
            if (currentSat == 0f) touchPosition = center
            else {
                val angleRad = Math.toRadians(currentHue.toDouble())
                val dist = currentSat * radius
                touchPosition = Offset(x = (center.x + dist * cos(angleRad)).toFloat(), y = (center.y + dist * sin(angleRad)).toFloat())
            }
        }
    }

    Canvas(modifier = modifier.aspectRatio(1f).onSizeChanged { inputSize = it }
        .pointerInput(Unit) {
            detectTapGestures(onPress = { offset ->
                val center = Offset(inputSize.width / 2f, inputSize.height / 2f)
                val radius = min(inputSize.width, inputSize.height) / 2f
                val dx = offset.x - center.x
                val dy = offset.y - center.y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance <= radius + 50) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    val rawSaturation = (distance / radius).coerceIn(0f, 1f)
                    val saturation = if (rawSaturation > 0.95f) 1f else rawSaturation
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onColorChanged(angle, saturation)
                }
            })
        }
        .pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val center = Offset(inputSize.width / 2f, inputSize.height / 2f)
                val radius = min(inputSize.width, inputSize.height) / 2f
                val position = change.position
                val dx = position.x - center.x
                val dy = position.y - center.y
                val distance = sqrt(dx * dx + dy * dy)
                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (angle < 0) angle += 360f
                val rawSaturation = (distance / radius).coerceIn(0f, 1f)
                val saturation = if (rawSaturation > 0.95f) 1f else rawSaturation
                onColorChanged(angle, saturation)
            }
        }) {
        val center = center
        val radius = size.minDimension / 2
        val sweepGradient = Brush.sweepGradient(colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red), center = center)
        drawCircle(brush = sweepGradient, radius = radius)
        val radialGradient = Brush.radialGradient(colors = listOf(Color.White, Color.Transparent), center = center, radius = radius)
        drawCircle(brush = radialGradient, radius = radius)
        drawCircle(color = Color.Black.copy(alpha = 1f - brightness), radius = radius)
        touchPosition?.let { pos ->
            drawCircle(color = Color.White, radius = 12.dp.toPx(), center = pos, style = Stroke(width = 3.dp.toPx()))
            drawCircle(color = Color.Black.copy(alpha = 0.3f), radius = 14.dp.toPx(), center = pos, style = Stroke(width = 1.dp.toPx()))
        }
    }
}