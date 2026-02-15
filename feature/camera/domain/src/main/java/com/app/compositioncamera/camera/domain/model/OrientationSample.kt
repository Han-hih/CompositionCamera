package com.app.compositioncamera.camera.domain.model

data class OrientationSample(
    val pitchDeg: Float,
    val rawRollDeg: Float,
    val deviceRotation: DeviceRotation,
    val isSensorAvailable: Boolean
)
