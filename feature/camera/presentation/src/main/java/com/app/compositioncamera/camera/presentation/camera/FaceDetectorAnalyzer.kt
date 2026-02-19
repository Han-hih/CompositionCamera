package com.app.compositioncamera.camera.presentation.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

internal data class FaceDetectionSnapshot(
    val count: Int,
    val boxes: List<DetectedObjectBox>
)

internal class FaceDetectorAnalyzer(
    private val onResult: (FaceDetectionSnapshot) -> Unit
) : ImageAnalysis.Analyzer, Closeable {

    private val isProcessing = AtomicBoolean(false)
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                val imageWidth = if (imageProxy.imageInfo.rotationDegrees % 180 == 0) {
                    imageProxy.width.toFloat()
                } else {
                    imageProxy.height.toFloat()
                }
                val imageHeight = if (imageProxy.imageInfo.rotationDegrees % 180 == 0) {
                    imageProxy.height.toFloat()
                } else {
                    imageProxy.width.toFloat()
                }

                val boxes = if (imageWidth <= 0f || imageHeight <= 0f) {
                    emptyList()
                } else {
                    faces.mapNotNull { face ->
                        val box = face.boundingBox
                        val left = (box.left / imageWidth).coerceIn(0f, 1f)
                        val top = (box.top / imageHeight).coerceIn(0f, 1f)
                        val right = (box.right / imageWidth).coerceIn(0f, 1f)
                        val bottom = (box.bottom / imageHeight).coerceIn(0f, 1f)
                        if (right <= left || bottom <= top) return@mapNotNull null

                        DetectedObjectBox(
                            leftFraction = min(left, right),
                            topFraction = min(top, bottom),
                            rightFraction = max(left, right),
                            bottomFraction = max(top, bottom),
                            label = "인물"
                        )
                    }
                }

                onResult(FaceDetectionSnapshot(count = boxes.size, boxes = boxes))
            }
            .addOnFailureListener {
                onResult(FaceDetectionSnapshot(count = 0, boxes = emptyList()))
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }

    override fun close() {
        detector.close()
    }
}
