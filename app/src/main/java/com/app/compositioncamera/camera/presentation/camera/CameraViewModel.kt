package com.app.compositioncamera.camera.presentation.camera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.compositioncamera.camera.data.repository.HorizonRepositoryImpl
import com.app.compositioncamera.camera.data.source.OrientationSensorDataSource
import com.app.compositioncamera.camera.domain.usecase.ObserveHorizonGuideUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CameraViewModel(
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

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val dataSource = OrientationSensorDataSource(appContext)
                    val repository = HorizonRepositoryImpl(dataSource)
                    val useCase = ObserveHorizonGuideUseCase(repository)

                    @Suppress("UNCHECKED_CAST")
                    return CameraViewModel(useCase) as T
                }
            }
        }
    }
}
