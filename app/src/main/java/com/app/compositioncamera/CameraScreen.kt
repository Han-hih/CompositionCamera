package com.app.compositioncamera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.OrientationEventListener
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.app.compositioncamera.util.Logx
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current

    var hasPermissionResult by remember { mutableStateOf(false) }
    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA,
        onPermissionResult = { isGranted ->
            hasPermissionResult = true
        }
    )

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        } else {
            hasPermissionResult = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreviewContent()
        } else {
            if (hasPermissionResult) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "카메라 권한이 필요합니다",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "권한이 거부되었습니다.\n아래 경로에서 직접 허용해주세요.\n\n[설정] > [애플리케이션] > [권한]\n> [카메라] > [앱 사용 중에만 허용]",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("설정으로 이동하기")
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreviewContent() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val horizonGuideState = rememberHorizonGuideState()
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

                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )
                        }
                    } catch (e: Exception) {
                        Logx.e(throwable = e, message = "Camera binding failed", tag = "CameraX")
                    }
                }, executor)
            }
        )

        CompositionGuideOverlay(
            horizonGuideState = horizonGuideState,
            deviceWidthPx = deviceWidthPx
        )
    }
}

@Composable
private fun CompositionGuideOverlay(
    horizonGuideState: HorizonGuideState,
    deviceWidthPx: Float
) {
    val maxDisplayRollDegrees = 35f
    val displayRollDeg = horizonGuideState.rollDeg.coerceIn(-maxDisplayRollDegrees, maxDisplayRollDegrees)
    val targetLineAngleDeg = when (horizonGuideState.deviceRotation) {
        Surface.ROTATION_90, Surface.ROTATION_270 -> 90f + displayRollDeg
        else -> displayRollDeg
    }
    val animatedLineAngleDeg by animateFloatAsState(
        targetValue = targetLineAngleDeg,
        animationSpec = tween(durationMillis = 70),
        label = "horizon-line-angle"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val thirdX = width / 3f
        val thirdY = height / 3f

        val gridLineColor = Color.White.copy(alpha = 0.28f)
        val centerY = height / 2f
        val centerX = width / 2f
        val horizonLineWidth = deviceWidthPx * 0.6f
        val halfLineWidth = horizonLineWidth / 2f
        val lineAngleRad = Math.toRadians(animatedLineAngleDeg.toDouble()).toFloat()
        val dx = cos(lineAngleRad) * halfLineWidth
        val dy = sin(lineAngleRad) * halfLineWidth
        val lineStart = Offset(centerX - dx, centerY - dy)
        val lineEnd = Offset(centerX + dx, centerY + dy)

        val levelThresholdDegrees = 2.5f
        val isLevel = abs(horizonGuideState.rollDeg) <= levelThresholdDegrees
        val horizonLineColor = when {
            !horizonGuideState.isSensorAvailable -> Color.White.copy(alpha = 0.72f)
            isLevel -> Color(0xFF2DDF7A)
            else -> Color(0xFFFF4D4D)
        }

        drawLine(
            color = gridLineColor,
            start = Offset(thirdX, 0f),
            end = Offset(thirdX, height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridLineColor,
            start = Offset(thirdX * 2f, 0f),
            end = Offset(thirdX * 2f, height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridLineColor,
            start = Offset(0f, thirdY),
            end = Offset(width, thirdY),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridLineColor,
            start = Offset(0f, thirdY * 2f),
            end = Offset(width, thirdY * 2f),
            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = horizonLineColor,
            start = lineStart,
            end = lineEnd,
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private data class HorizonGuideState(
    val rollDeg: Float,
    val deviceRotation: Int,
    val isSensorAvailable: Boolean
)

@Composable
private fun rememberHorizonGuideState(): HorizonGuideState {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    var rollDeg by remember { mutableFloatStateOf(0f) }
    var isSensorAvailable by remember { mutableStateOf(true) }
    var deviceRotation by remember { mutableStateOf(Surface.ROTATION_0) }
    var smoothedRollDeg by remember { mutableFloatStateOf(0f) }

    DisposableEffect(sensorManager) {
        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // Change quadrant only near the target right-angle orientation.
                // Intermediate angles keep the previous state to avoid early snapping.
                deviceRotation = when {
                    orientation in 70..110 -> Surface.ROTATION_270
                    orientation in 160..200 -> Surface.ROTATION_180
                    orientation in 250..290 -> Surface.ROTATION_90
                    orientation >= 340 || orientation <= 20 -> Surface.ROTATION_0
                    else -> deviceRotation
                }
            }
        }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor == null) {
            isSensorAvailable = false
            onDispose {
                orientationListener.disable()
            }
        } else {
            val rotationMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)

                    val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                    val rawRollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                    val screenRollDeg = when (deviceRotation) {
                        Surface.ROTATION_90 -> -pitchDeg
                        Surface.ROTATION_180 -> -rawRollDeg
                        Surface.ROTATION_270 -> pitchDeg
                        else -> rawRollDeg
                    }
                    val calibratedRollDeg = when (deviceRotation) {
                        Surface.ROTATION_0, Surface.ROTATION_180 -> screenRollDeg * 0.62f
                        else -> screenRollDeg
                    }

                    val smoothingFactor = 0.5f
                    smoothedRollDeg += (calibratedRollDeg - smoothedRollDeg) * smoothingFactor
                    rollDeg = smoothedRollDeg
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            isSensorAvailable = sensorManager.registerListener(
                listener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_GAME
            )

            onDispose {
                sensorManager.unregisterListener(listener)
                orientationListener.disable()
            }
        }
    }

    return HorizonGuideState(
        rollDeg = rollDeg,
        deviceRotation = deviceRotation,
        isSensorAvailable = isSensorAvailable
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionState.isInitialRequest(): Boolean {
    return !status.isGranted && !status.shouldShowRationale
}
