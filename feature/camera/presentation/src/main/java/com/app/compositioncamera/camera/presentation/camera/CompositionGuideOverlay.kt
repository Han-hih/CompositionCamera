package com.app.compositioncamera.camera.presentation.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.app.compositioncamera.camera.domain.model.HorizonGuideState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun CompositionGuideOverlay(
    horizonGuideState: HorizonGuideState,
    deviceWidthPx: Float
) {
    val targetLineAngleDeg = calculateGuideLineAngleDeg(horizonGuideState)
    val animatedLineAngleDeg by animateFloatAsState(
        targetValue = targetLineAngleDeg,
        animationSpec = tween(durationMillis = 70),
        label = "horizon-line-angle"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val thirdX = width / 3f
        val thirdY = height / 3f

        val gridLineColor = Color.White.copy(alpha = 0.28f)
        val centerY = height / 2f
        val centerX = width / 2f
        val horizonLineWidth = deviceWidthPx * 0.6f
        val halfLineWidth = horizonLineWidth / 2f
        val lineAngleRad = Math.toRadians(animatedLineAngleDeg.toDouble()).toFloat()
        val dx = cos(lineAngleRad) * halfLineWidth
        val dy = sin(lineAngleRad) * halfLineWidth
        val lineStart = Offset(centerX - dx, centerY - dy)
        val lineEnd = Offset(centerX + dx, centerY + dy)

        val levelThresholdDegrees = 2.5f
        val isLevel = abs(horizonGuideState.rollDeg) <= levelThresholdDegrees
        val horizonLineColor = when {
            !horizonGuideState.isSensorAvailable -> Color.White.copy(alpha = 0.72f)
            isLevel -> Color(0xFF2DDF7A)
            else -> Color(0xFFFF4D4D)
        }

        drawLine(
            color = gridLineColor,
            start = Offset(thirdX, 0f),
            end = Offset(thirdX, height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridLineColor,
            start = Offset(thirdX * 2f, 0f),
            end = Offset(thirdX * 2f, height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridLineColor,
            start = Offset(0f, thirdY),
            end = Offset(width, thirdY),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridLineColor,
            start = Offset(0f, thirdY * 2f),
            end = Offset(width, thirdY * 2f),
            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = horizonLineColor,
            start = lineStart,
            end = lineEnd,
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

    }
}
