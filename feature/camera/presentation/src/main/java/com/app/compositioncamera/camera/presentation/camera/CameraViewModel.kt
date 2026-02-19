package com.app.compositioncamera.camera.presentation.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.compositioncamera.util.Logx
import com.app.compositioncamera.camera.domain.model.CompositionMetrics
import com.app.compositioncamera.camera.domain.usecase.GetAiCoachingFeedbackUseCase
import com.app.compositioncamera.camera.domain.usecase.ObserveHorizonGuideUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val observeHorizonGuideUseCase: ObserveHorizonGuideUseCase,
    private val getAiCoachingFeedbackUseCase: GetAiCoachingFeedbackUseCase
) : ViewModel() {
    private val tag = "AiCoaching"
    private val subjectGuideMode = MutableStateFlow(SubjectGuideMode.PERSON)
    private val aiCoachingText = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CameraUiState> = combine(
        observeHorizonGuideUseCase(),
        subjectGuideMode,
        aiCoachingText
    ) { horizonState, mode, coachingText ->
        CameraUiState(
            horizonGuideState = horizonState,
            subjectGuideMode = mode,
            aiCoachingText = coachingText
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CameraUiState()
        )

    fun setSubjectGuideMode(mode: SubjectGuideMode) {
        subjectGuideMode.value = mode
        if (mode != SubjectGuideMode.PERSON) {
            aiCoachingText.value = null
        }
    }

    fun requestAiCoaching(metrics: CompositionMetrics) {
        viewModelScope.launch {
            val feedback = getAiCoachingFeedbackUseCase(metrics)
            Logx.d(message = "AI coaching response: ${feedback ?: "null"}", tag = tag)
            aiCoachingText.update { feedback ?: it }
        }
    }
}
