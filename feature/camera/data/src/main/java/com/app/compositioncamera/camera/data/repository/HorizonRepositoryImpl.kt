package com.app.compositioncamera.camera.data.repository

import com.app.compositioncamera.camera.data.source.OrientationSensorDataSource
import com.app.compositioncamera.camera.domain.model.OrientationSample
import com.app.compositioncamera.camera.domain.repository.HorizonRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class HorizonRepositoryImpl @Inject constructor(
    private val orientationSensorDataSource: OrientationSensorDataSource
) : HorizonRepository {
    override fun observeOrientationSamples(): Flow<OrientationSample> {
        return orientationSensorDataSource.observeOrientationSamples()
    }
}
