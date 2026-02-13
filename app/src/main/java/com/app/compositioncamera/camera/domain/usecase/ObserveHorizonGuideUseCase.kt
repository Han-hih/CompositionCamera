package com.app.compositioncamera.camera.domain.usecase

import com.app.compositioncamera.camera.domain.model.DeviceRotation
import com.app.compositioncamera.camera.domain.model.HorizonGuideState
import com.app.compositioncamera.camera.domain.repository.HorizonRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

class ObserveHorizonGuideUseCase @Inject constructor(
    private val horizonRepository: HorizonRepository
) {
    operator fun invoke(): Flow<HorizonGuideState> {
        return horizonRepository.observeOrientationSamples()
            .map { sample ->
                val screenRollDeg = when (sample.deviceRotation) {
                    DeviceRotation.ROTATION_90 -> -sample.pitchDeg
                    DeviceRotation.ROTATION_180 -> -sample.rawRollDeg
                    DeviceRotation.ROTATION_270 -> sample.pitchDeg
                    DeviceRotation.ROTATION_0 -> sample.rawRollDeg
                }

                val calibratedRollDeg = when (sample.deviceRotation) {
                    DeviceRotation.ROTATION_0,
                    DeviceRotation.ROTATION_180 -> screenRollDeg * 0.62f

                    else -> screenRollDeg
                }

                HorizonGuideState(
                    rollDeg = calibratedRollDeg,
                    deviceRotation = sample.deviceRotation,
                    isSensorAvailable = sample.isSensorAvailable
                )
            }
            .runningFold(HorizonGuideState()) { previous, current ->
                val smoothingFactor = 0.5f
                previous.copy(
                    rollDeg = previous.rollDeg + (current.rollDeg - previous.rollDeg) * smoothingFactor,
                    deviceRotation = current.deviceRotation,
                    isSensorAvailable = current.isSensorAvailable
                )
            }
            .drop(1)
    }
}
