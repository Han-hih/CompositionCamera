package com.app.compositioncamera.camera.domain.repository

import com.app.compositioncamera.camera.domain.model.OrientationSample
import kotlinx.coroutines.flow.Flow

interface HorizonRepository {
    fun observeOrientationSamples(): Flow<OrientationSample>
}
