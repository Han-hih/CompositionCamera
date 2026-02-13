package com.app.compositioncamera.camera.data.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.OrientationEventListener
import com.app.compositioncamera.camera.domain.model.DeviceRotation
import com.app.compositioncamera.camera.domain.model.OrientationSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class OrientationSensorDataSource(
    context: Context
) {
    private val appContext = context.applicationContext

    fun observeOrientationSamples(): Flow<OrientationSample> = callbackFlow {
        val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        var deviceRotation = DeviceRotation.ROTATION_0
        val orientationListener = object : OrientationEventListener(appContext) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                deviceRotation = when {
                    orientation in 70..110 -> DeviceRotation.ROTATION_270
                    orientation in 160..200 -> DeviceRotation.ROTATION_180
                    orientation in 250..290 -> DeviceRotation.ROTATION_90
                    orientation >= 340 || orientation <= 20 -> DeviceRotation.ROTATION_0
                    else -> deviceRotation
                }
            }
        }

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor == null) {
            trySend(
                OrientationSample(
                    pitchDeg = 0f,
                    rawRollDeg = 0f,
                    deviceRotation = deviceRotation,
                    isSensorAvailable = false
                )
            )

            awaitClose {
                orientationListener.disable()
            }
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val rawRollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                trySend(
                    OrientationSample(
                        pitchDeg = pitchDeg,
                        rawRollDeg = rawRollDeg,
                        deviceRotation = deviceRotation,
                        isSensorAvailable = true
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = sensorManager.registerListener(
            listener,
            rotationSensor,
            SensorManager.SENSOR_DELAY_GAME
        )

        if (!registered) {
            trySend(
                OrientationSample(
                    pitchDeg = 0f,
                    rawRollDeg = 0f,
                    deviceRotation = deviceRotation,
                    isSensorAvailable = false
                )
            )
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
            orientationListener.disable()
        }
    }.conflate()
}
