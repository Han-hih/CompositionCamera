package com.app.compositioncamera.camera.presentation.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.compositioncamera.camera.domain.usecase.ObserveHorizonGuideUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val observeHorizonGuideUseCase: ObserveHorizonGuideUseCase
) : ViewModel() {
    private val subjectGuideMode = MutableStateFlow(SubjectGuideMode.PERSON)

    val uiState: StateFlow<CameraUiState> = combine(
        observeHorizonGuideUseCase(),
        subjectGuideMode
    ) { horizonState, mode ->
        CameraUiState(
            horizonGuideState = horizonState,
            subjectGuideMode = mode
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CameraUiState()
        )

    fun setSubjectGuideMode(mode: SubjectGuideMode) {
        subjectGuideMode.value = mode
    }
}
