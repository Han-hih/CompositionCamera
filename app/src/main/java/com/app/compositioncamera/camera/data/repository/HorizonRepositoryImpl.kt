package com.app.compositioncamera.camera.data.repository

import com.app.compositioncamera.camera.data.source.OrientationSensorDataSource
import com.app.compositioncamera.camera.domain.model.OrientationSample
import com.app.compositioncamera.camera.domain.repository.HorizonRepository
import kotlinx.coroutines.flow.Flow

class HorizonRepositoryImpl(
    private val orientationSensorDataSource: OrientationSensorDataSource
) : HorizonRepository {
    override fun observeOrientationSamples(): Flow<OrientationSample> {
        return orientationSensorDataSource.observeOrientationSamples()
    }
}
