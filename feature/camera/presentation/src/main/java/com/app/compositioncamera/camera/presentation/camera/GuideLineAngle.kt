package com.app.compositioncamera.camera.presentation.camera

import com.app.compositioncamera.camera.domain.model.DeviceRotation
import com.app.compositioncamera.camera.domain.model.HorizonGuideState

internal fun calculateGuideLineAngleDeg(horizonGuideState: HorizonGuideState): Float {
    val maxDisplayRollDegrees = 35f
    val displayRollDeg = horizonGuideState.rollDeg.coerceIn(-maxDisplayRollDegrees, maxDisplayRollDegrees)
    return when (horizonGuideState.deviceRotation) {
        DeviceRotation.ROTATION_90,
        DeviceRotation.ROTATION_270 -> 90f

        else -> displayRollDeg
    }
}
