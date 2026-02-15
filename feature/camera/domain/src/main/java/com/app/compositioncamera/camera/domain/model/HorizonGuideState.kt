package com.app.compositioncamera.camera.domain.model

data class HorizonGuideState(
    val rollDeg: Float = 0f,
    val deviceRotation: DeviceRotation = DeviceRotation.ROTATION_0,
    val isSensorAvailable: Boolean = true
)
