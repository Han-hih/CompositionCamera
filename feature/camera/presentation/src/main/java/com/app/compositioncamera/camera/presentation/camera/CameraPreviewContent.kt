package com.app.compositioncamera.camera.presentation.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.app.compositioncamera.camera.domain.model.DeviceRotation
import com.app.compositioncamera.camera.domain.model.HorizonGuideState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import java.util.concurrent.Executors
import android.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas

@Composable
internal fun CameraPreviewContent(
    horizonGuideState: HorizonGuideState,
    subjectGuideMode: SubjectGuideMode,
    onSubjectGuideModeChanged: (SubjectGuideMode) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val deviceWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var objectDetectionSnapshot by remember {
        mutableStateOf(ObjectDetectionSnapshot(0, null, emptyList()))
    }
    val objectDetectorAnalyzer = remember {
        ObjectDetectorAnalyzer { snapshot ->
            objectDetectionSnapshot = snapshot
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            objectDetectorAnalyzer.close()
            analysisExecutor.shutdown()
        }
    }

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
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor, objectDetectorAnalyzer)
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()

                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
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
        if (subjectGuideMode == SubjectGuideMode.OBJECT) {
            DetectedObjectOverlay(
                boxes = objectDetectionSnapshot.boxes,
                modifier = Modifier.fillMaxSize()
            )
        }

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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        ) {
            if (subjectGuideMode == SubjectGuideMode.OBJECT) {
                val detectionText = if (objectDetectionSnapshot.count > 0) {
                    val label = objectDetectionSnapshot.primaryLabel?.let { " ($it)" }.orEmpty()
                    "사물 감지됨 ${objectDetectionSnapshot.count}개$label"
                } else {
                    "사물을 찾는 중..."
                }

                Text(
                    text = detectionText,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row {
            FilterChip(
                selected = subjectGuideMode == SubjectGuideMode.PERSON,
                onClick = { onSubjectGuideModeChanged(SubjectGuideMode.PERSON) },
                label = { Text("인물") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = subjectGuideMode == SubjectGuideMode.OBJECT,
                onClick = { onSubjectGuideModeChanged(SubjectGuideMode.OBJECT) },
                label = { Text("사물") }
            )
            }
        }
    }
}

@Composable
private fun DetectedObjectOverlay(
    boxes: List<DetectedObjectBox>,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            isAntiAlias = true
            style = Paint.Style.FILL
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }
    }

    Canvas(modifier = modifier) {
        boxes.forEach { box ->
            val left = box.leftFraction * size.width
            val top = box.topFraction * size.height
            val right = box.rightFraction * size.width
            val bottom = box.bottomFraction * size.height

            drawRoundRect(
                color = Color(0xFFFFC857),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            box.label?.let { label ->
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    left,
                    (top - 10.dp.toPx()).coerceAtLeast(24.dp.toPx()),
                    labelPaint
                )
            }
        }
    }
}
