package com.app.compositioncamera.camera.domain.usecase

import com.app.compositioncamera.camera.domain.model.CompositionMetrics
import com.app.compositioncamera.camera.domain.repository.AiCoachingRepository
import javax.inject.Inject

class GetAiCoachingFeedbackUseCase @Inject constructor(
    private val aiCoachingRepository: AiCoachingRepository
) {
    suspend operator fun invoke(metrics: CompositionMetrics): String? {
        return aiCoachingRepository.getCoaching(metrics)
    }
}
