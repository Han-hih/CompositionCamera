package com.app.compositioncamera.camera.domain.model

data class CompositionMetrics(
    val mode: String,
    val rollDeg: Float,
    val compositionScore: Int,
    val personCount: Int,
    val objectCount: Int,
    val primaryObjectLabel: String?
)
