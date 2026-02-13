package com.app.compositioncamera.camera.di

import com.app.compositioncamera.camera.data.repository.HorizonRepositoryImpl
import com.app.compositioncamera.camera.domain.repository.HorizonRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CameraDataModule {
    @Binds
    @Singleton
    abstract fun bindHorizonRepository(
        impl: HorizonRepositoryImpl
    ): HorizonRepository
}
