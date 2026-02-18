package com.app.compositioncamera.camera.presentation.camera

import com.app.compositioncamera.camera.domain.model.HorizonGuideState

data class CameraUiState(
    val horizonGuideState: HorizonGuideState = HorizonGuideState(),
    val subjectGuideMode: SubjectGuideMode = SubjectGuideMode.PERSON
)
