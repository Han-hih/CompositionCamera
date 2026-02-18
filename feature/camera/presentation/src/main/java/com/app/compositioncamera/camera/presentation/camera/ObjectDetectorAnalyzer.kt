package com.app.compositioncamera.camera.presentation.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

internal data class ObjectDetectionSnapshot(
    val count: Int,
    val primaryLabel: String?,
    val boxes: List<DetectedObjectBox>
)

internal data class DetectedObjectBox(
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float,
    val label: String?
)

internal class ObjectDetectorAnalyzer(
    private val onResult: (ObjectDetectionSnapshot) -> Unit
) : ImageAnalysis.Analyzer, Closeable {

    private val isProcessing = AtomicBoolean(false)
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .enableMultipleObjects()
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
            .addOnSuccessListener { detectedObjects ->
                val primaryLabel = detectedObjects.firstOrNull()
                    ?.labels
                    ?.maxByOrNull { it.confidence }
                    ?.text

                onResult(
                    ObjectDetectionSnapshot(
                        count = detectedObjects.size,
                        primaryLabel = primaryLabel,
                        boxes = detectedObjects.mapNotNull { detectedObject ->
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
                            if (imageWidth <= 0f || imageHeight <= 0f) return@mapNotNull null

                            val box = detectedObject.boundingBox
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
                                label = detectedObject.labels.maxByOrNull { it.confidence }?.text
                            )
                        }
                    )
                )
            }
            .addOnFailureListener {
                onResult(
                    ObjectDetectionSnapshot(
                        count = 0,
                        primaryLabel = null,
                        boxes = emptyList()
                    )
                )
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
