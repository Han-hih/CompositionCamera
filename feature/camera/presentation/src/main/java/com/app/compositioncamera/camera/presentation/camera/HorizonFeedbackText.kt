package com.app.compositioncamera.camera.presentation.camera

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.compositioncamera.camera.domain.model.HorizonGuideState
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun HorizonFeedbackText(
    horizonGuideState: HorizonGuideState,
    modifier: Modifier = Modifier
) {
    val message = buildHorizonFeedbackMessage(horizonGuideState)
    val textColor = when {
        !horizonGuideState.isSensorAvailable -> Color.White
        abs(horizonGuideState.rollDeg) <= LEVEL_THRESHOLD_DEGREES -> Color(0xFF2DDF7A)
        else -> Color(0xFFFFB3B3)
    }

    Text(
        text = message,
        color = textColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .width(260.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

private fun buildHorizonFeedbackMessage(state: HorizonGuideState): String {
    if (!state.isSensorAvailable) {
        return "센서를 사용할 수 없어요"
    }

    val rollAbs = abs(state.rollDeg)
    if (rollAbs <= LEVEL_THRESHOLD_DEGREES) {
        return "좋아요, 수평이 맞아요"
    }

    val degree = rollAbs.roundToInt().coerceAtLeast(1)
    return if (state.rollDeg > 0f) {
        "왼쪽으로 ${degree}°만큼 기울여 주세요"
    } else {
        "오른쪽으로 ${degree}°만큼 기울여 주세요"
    }
}

private const val LEVEL_THRESHOLD_DEGREES = 2.5f
