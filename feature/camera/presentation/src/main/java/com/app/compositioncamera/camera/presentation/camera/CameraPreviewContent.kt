package com.app.compositioncamera.camera.presentation.camera

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.app.compositioncamera.camera.domain.model.DeviceRotation
import com.app.compositioncamera.camera.domain.model.HorizonGuideState
import com.app.compositioncamera.camera.domain.model.CompositionMetrics
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import java.util.concurrent.Executors
import android.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import com.app.compositioncamera.util.Logx
import kotlin.math.abs
import kotlin.math.min
import kotlin.system.*

@Composable
internal fun CameraPreviewContent(
    horizonGuideState: HorizonGuideState,
    subjectGuideMode: SubjectGuideMode,
    onSubjectGuideModeChanged: (SubjectGuideMode) -> Unit,
    aiCoachingText: String?,
    onRequestAiCoaching: (CompositionMetrics) -> Unit
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
    var faceDetectionSnapshot by remember {
        mutableStateOf(FaceDetectionSnapshot(0, emptyList()))
    }
    val objectDetectorAnalyzer = remember {
        ObjectDetectorAnalyzer { snapshot ->
            objectDetectionSnapshot = snapshot
        }
    }
    val faceDetectorAnalyzer = remember {
        FaceDetectorAnalyzer { snapshot ->
            faceDetectionSnapshot = snapshot
        }
    }
    var lastAiRequestAtMs by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            objectDetectorAnalyzer.close()
            faceDetectorAnalyzer.close()
            analysisExecutor.shutdown()
        }
    }

    val personFeedback = if (subjectGuideMode == SubjectGuideMode.PERSON) {
        buildPersonCompositionFeedback(faceDetectionSnapshot.boxes)
    } else {
        null
    }

    LaunchedEffect(
        subjectGuideMode,
        horizonGuideState.rollDeg,
        faceDetectionSnapshot.count,
        objectDetectionSnapshot.count,
        objectDetectionSnapshot.primaryLabel,
        personFeedback?.score
    ) {
        if (subjectGuideMode != SubjectGuideMode.PERSON) return@LaunchedEffect
        if (faceDetectionSnapshot.count == 0) return@LaunchedEffect

        val now = System.currentTimeMillis()
        if (now - lastAiRequestAtMs < 1_500L) return@LaunchedEffect
        lastAiRequestAtMs = now

        onRequestAiCoaching(
            CompositionMetrics(
                mode = subjectGuideMode.name,
                rollDeg = horizonGuideState.rollDeg,
                compositionScore = personFeedback?.score ?: 0,
                personCount = faceDetectionSnapshot.count,
                objectCount = objectDetectionSnapshot.count,
                primaryObjectLabel = objectDetectionSnapshot.primaryLabel
            )
        )
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
                                val activeAnalyzer = when (subjectGuideMode) {
                                    SubjectGuideMode.PERSON -> faceDetectorAnalyzer
                                    SubjectGuideMode.OBJECT -> objectDetectorAnalyzer
                                }
                                analysis.setAnalyzer(analysisExecutor, activeAnalyzer)
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
                        Logx.e(
                            throwable = e,
                            message = "Camera binding failed",
                            tag = "CameraX"
                        )
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
        } else {
            DetectedObjectOverlay(
                boxes = faceDetectionSnapshot.boxes,
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
            val detectionText = when (subjectGuideMode) {
                SubjectGuideMode.OBJECT -> if (objectDetectionSnapshot.count > 0) {
                    val label = objectDetectionSnapshot.primaryLabel?.let { " ($it)" }.orEmpty()
                    "사물 감지됨 ${objectDetectionSnapshot.count}개$label"
                } else {
                    "사물을 찾는 중..."
                }

                SubjectGuideMode.PERSON -> if (faceDetectionSnapshot.count > 0) {
                    val feedback = personFeedback ?: buildPersonCompositionFeedback(faceDetectionSnapshot.boxes)
                    "인물 ${faceDetectionSnapshot.count}명 · 구도 ${feedback.score}점\n${feedback.message}"
                } else {
                    "인물을 찾는 중..."
                }
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
            if (subjectGuideMode == SubjectGuideMode.PERSON && !aiCoachingText.isNullOrBlank()) {
                Text(
                    text = "AI 코칭: $aiCoachingText",
                    color = Color(0xFFB8E8FF),
                    fontWeight = FontWeight.SemiBold,
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

private data class PersonCompositionFeedback(
    val score: Int,
    val message: String
)

private fun buildPersonCompositionFeedback(boxes: List<DetectedObjectBox>): PersonCompositionFeedback {
    if (boxes.isEmpty()) {
        return PersonCompositionFeedback(
            score = 0,
            message = "인물을 프레임 안에 넣어 주세요"
        )
    }

    val targetCount = when {
        boxes.size >= 3 -> 3
        boxes.size == 2 -> 2
        else -> 1
    }
    val selected = boxes
        .sortedByDescending { it.height() }
        .take(targetCount)
        .sortedBy { it.centerX() }
    val edgeClipped = selected.any { it.isEdgeClipped() }

    if (targetCount == 2) {
        val left = selected[0]
        val right = selected[1]
        val leftEyeY = left.eyeLineY()
        val rightEyeY = right.eyeLineY()
        val groupCenter = (left.centerX() + right.centerX()) / 2f
        val eyeAlignGap = abs(leftEyeY - rightEyeY)
        val sizeGap = abs(left.height() - right.height())
        val spacing = right.centerX() - left.centerX()

        val score = (
            100
                - (abs(groupCenter - 0.5f) * 200f).toInt()
                - (eyeAlignGap * 260f).toInt()
                - (sizeGap * 200f).toInt()
                - (abs(spacing - 0.32f) * 180f).toInt()
                - if (edgeClipped) 22 else 0
        ).coerceIn(0, 100)

        val message = when {
            edgeClipped -> "두 인물이 잘리지 않게 프레임을 조금 넓혀보세요"
            abs(groupCenter - 0.5f) > 0.10f -> "두 사람 중심을 화면 가운데에 맞춰보세요"
            eyeAlignGap > 0.08f -> "두 사람 머리 높이를 비슷하게 맞춰보세요"
            sizeGap > 0.12f -> "두 사람 카메라 거리를 비슷하게 맞춰보세요"
            spacing < 0.18f -> "두 사람 사이를 조금 더 벌려보세요"
            spacing > 0.48f -> "두 사람 사이 간격을 조금 줄여보세요"
            else -> "좋아요, 2인 구도가 안정적이에요"
        }
        return PersonCompositionFeedback(score = score, message = message)
    }

    if (targetCount == 3) {
        val left = selected[0]
        val middle = selected[1]
        val right = selected[2]
        val groupCenter = (left.centerX() + right.centerX()) / 2f
        val span = right.centerX() - left.centerX()
        val middleOffset = abs(middle.centerX() - groupCenter)
        val eyeAlignGap = maxOf(
            abs(left.eyeLineY() - middle.eyeLineY()),
            abs(middle.eyeLineY() - right.eyeLineY()),
            abs(left.eyeLineY() - right.eyeLineY())
        )
        val sizeGap = maxOf(
            abs(left.height() - middle.height()),
            abs(middle.height() - right.height()),
            abs(left.height() - right.height())
        )

        val score = (
            100
                - (abs(groupCenter - 0.5f) * 180f).toInt()
                - (middleOffset * 220f).toInt()
                - (abs(span - 0.58f) * 170f).toInt()
                - (eyeAlignGap * 220f).toInt()
                - (sizeGap * 170f).toInt()
                - if (edgeClipped) 26 else 0
        ).coerceIn(0, 100)

        val message = when {
            edgeClipped -> "세 인물이 잘리지 않게 조금 더 멀리서 촬영해보세요"
            abs(groupCenter - 0.5f) > 0.11f -> "세 사람 중심을 화면 중앙으로 맞춰보세요"
            middleOffset > 0.10f -> "가운데 인물을 중앙에 오도록 위치를 조정해보세요"
            eyeAlignGap > 0.10f -> "세 사람의 머리 높이를 조금 더 맞춰보세요"
            sizeGap > 0.14f -> "카메라와의 거리를 비슷하게 맞춰보세요"
            span < 0.42f -> "세 사람이 조금 더 옆으로 퍼져 서보세요"
            span > 0.74f -> "세 사람이 너무 퍼졌어요. 조금 모여보세요"
            else -> "좋아요, 3인 구도가 균형 잡혀 있어요"
        }
        return PersonCompositionFeedback(score = score, message = message)
    }

    val mainFace = selected.first()
    val faceCenterX = mainFace.centerX()
    val faceHeight = mainFace.height()
    val eyeLineY = mainFace.eyeLineY()
    val topMargin = mainFace.topFraction
    val leftMargin = mainFace.leftFraction
    val rightMargin = 1f - mainFace.rightFraction
    val isFullBody = faceHeight < 0.18f
    val nearestThirdXDistance = min(abs(faceCenterX - (1f / 3f)), abs(faceCenterX - (2f / 3f)))

    val score = if (isFullBody) {
        val centerDistance = abs(faceCenterX - 0.5f)
        val eyeLineTargetY = 0.28f
        val sideBalanceDistance = abs(leftMargin - rightMargin)
        (
            100
                - (centerDistance * 170f).toInt()
                - (abs(eyeLineY - eyeLineTargetY) * 140f).toInt()
                - (abs(faceHeight - 0.12f) * 220f).toInt()
                - (abs(topMargin - 0.10f) * 160f).toInt()
                - (sideBalanceDistance * 100f).toInt()
                - if (edgeClipped) 16 else 0
        ).coerceIn(0, 100)
    } else {
        val eyeLineTargetY = 1f / 3f
        (
            100
                - (nearestThirdXDistance * 120f).toInt()
                - (abs(eyeLineY - eyeLineTargetY) * 140f).toInt()
                - (abs(faceHeight - 0.34f) * 130f).toInt()
                - (abs(topMargin - 0.12f) * 80f).toInt()
                - if (edgeClipped) 16 else 0
        ).coerceIn(0, 100)
    }

    val message = if (isFullBody) {
        when {
            edgeClipped -> "전신이 잘리지 않게 카메라를 조금 더 뒤로 물려보세요"
            faceHeight < 0.07f -> "인물이 너무 멀어요. 조금 가까이 가보세요"
            faceHeight > 0.20f -> "전신 구도치고 너무 가까워요. 조금 물러나 보세요"
            eyeLineY < 0.20f -> "전신 구도: 카메라를 조금 아래로 내려보세요"
            eyeLineY > 0.36f -> "전신 구도: 카메라를 조금 위로 올려보세요"
            abs(faceCenterX - 0.5f) > 0.16f -> "전신 구도는 인물을 중앙 쪽에 맞춰보세요"
            abs(leftMargin - rightMargin) > 0.14f -> "좌우 여백을 조금 더 균형 있게 맞춰보세요"
            else -> "좋아요, 전신 인물 구도가 안정적이에요"
        }
    } else {
        when {
            edgeClipped -> "얼굴이 잘리지 않게 프레임을 조금 넓혀보세요"
            faceHeight < 0.22f -> "인물이 작아요. 조금 더 가까이 가보세요"
            faceHeight > 0.55f -> "인물이 너무 커요. 조금 물러나 보세요"
            eyeLineY < 0.25f -> "카메라를 조금 아래로 내려보세요"
            eyeLineY > 0.42f -> "카메라를 조금 위로 올려보세요"
            nearestThirdXDistance > 0.12f -> "인물을 좌/우 3분할선 쪽으로 옮겨보세요"
            else -> "좋아요, 인물 구도가 안정적이에요"
        }
    }

    return PersonCompositionFeedback(score = score, message = message)
}

private fun DetectedObjectBox.centerX(): Float = (leftFraction + rightFraction) / 2f

private fun DetectedObjectBox.height(): Float = bottomFraction - topFraction

private fun DetectedObjectBox.eyeLineY(): Float = topFraction + (height() * 0.35f)

private fun DetectedObjectBox.isEdgeClipped(): Boolean {
    return leftFraction < 0.02f ||
        topFraction < 0.02f ||
        rightFraction > 0.98f ||
        bottomFraction > 0.98f
}
