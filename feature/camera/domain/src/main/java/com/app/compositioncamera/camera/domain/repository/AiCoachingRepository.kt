package com.app.compositioncamera.camera.domain.repository

import com.app.compositioncamera.camera.domain.model.CompositionMetrics

interface AiCoachingRepository {
    suspend fun getCoaching(metrics: CompositionMetrics): String?
}
