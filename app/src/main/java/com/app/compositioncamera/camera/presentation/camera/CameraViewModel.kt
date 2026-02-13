package com.app.compositioncamera.camera.presentation.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.compositioncamera.camera.domain.usecase.ObserveHorizonGuideUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val observeHorizonGuideUseCase: ObserveHorizonGuideUseCase
) : ViewModel() {
    val uiState: StateFlow<CameraUiState> = observeHorizonGuideUseCase()
        .map { horizonState ->
            CameraUiState(horizonGuideState = horizonState)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CameraUiState()
        )
}
