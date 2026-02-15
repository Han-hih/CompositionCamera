package com.app.compositioncamera.camera.presentation.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.app.compositioncamera.camera.domain.model.DeviceRotation
import com.app.compositioncamera.camera.domain.model.HorizonGuideState

@Composable
internal fun CameraPreviewContent(horizonGuideState: HorizonGuideState) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val deviceWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val executor = ContextCompat.getMainExecutor(context)

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()

                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("CameraX", "Camera binding failed", e)
                    }
                }, executor)
            }
        )

        CompositionGuideOverlay(
            horizonGuideState = horizonGuideState,
            deviceWidthPx = deviceWidthPx
        )

        val feedbackPositionModifier = when (horizonGuideState.deviceRotation) {
            DeviceRotation.ROTATION_90 -> Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (80).dp)

            DeviceRotation.ROTATION_180 -> Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)

            DeviceRotation.ROTATION_270 -> Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-80).dp)

            DeviceRotation.ROTATION_0 -> Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        }
        val feedbackRotationDeg = when (horizonGuideState.deviceRotation) {
            DeviceRotation.ROTATION_90 -> 90f
            DeviceRotation.ROTATION_180 -> 180f
            DeviceRotation.ROTATION_270 -> -90f
            DeviceRotation.ROTATION_0 -> 0f
        }
        val feedbackTransformOrigin = when (horizonGuideState.deviceRotation) {
            DeviceRotation.ROTATION_90 -> TransformOrigin(0.5f, 0.5f)
            DeviceRotation.ROTATION_270 -> TransformOrigin(0.5f, 0.5f)
            else -> TransformOrigin.Center
        }

        HorizonFeedbackText(
            horizonGuideState = horizonGuideState,
            modifier = feedbackPositionModifier.graphicsLayer {
                rotationZ = feedbackRotationDeg
                transformOrigin = feedbackTransformOrigin
            }
        )
    }
}
