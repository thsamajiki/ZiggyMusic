package com.hero.ziggymusic.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs

/**
 * 디바이스 Rotation Vector 센서를 이용해 "Yaw(azimuth)"를 안정적으로 계산해
 * AudioProcessorChainController.setHeadTrackingYaw(yawDeg)로 주입
 *
 * 포인트:
 * - display rotation(0/90/180/270) 보정(remapCoordinateSystem)
 * - -180..180 wrap-around를 고려한 smoothing
 * - 너무 자주 갱신되는 경우(미세 노이즈) 임계값으로 컷
 */
class HeadTracker(
    context: Context,
    private var onYawUpdated: ((yawDeg: Float) -> Unit)? = null
) : SensorEventListener {
    enum class Mode(
        val samplingPeriodUs: Int,
        val maxReportLatencyUs: Int
    ) {
        // 전면: 부드러운 헤드트래킹(대략 50Hz)
        FOREGROUND(samplingPeriodUs = 20_000, maxReportLatencyUs = 0),

        // 백그라운드: 저전력(대략 5Hz) + batching(예: 2초)
        BACKGROUND_LOW_POWER(samplingPeriodUs = 200_000, maxReportLatencyUs = 2_000_000),
    }

    @Volatile
    private var currentMode: Mode? = null
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var isTracking = false

    private val rotationMatrix = FloatArray(9)
    private val adjustedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // Smoothing
    private var lastYaw = 0.0f
    private var hasLast = false

    // 0.0~1.0 (낮을수록 더 부드럽고 반응 느림)
    private val alpha = 0.15f

    // 작은 변화는 무시(센서 노이즈 억제)
    private val minDeltaDeg = 0.2f

    fun start(mode: Any? = null) {
        // 외부 호출 호환용: mode 미지정이면 FOREGROUND
        val targetMode = (mode as? Mode) ?: Mode.FOREGROUND

        val s = rotationSensor
        if (s == null) {
            Log.w(TAG, "Rotation sensor not available on this device.")
            return
        }

        // 같은 모드로 이미 구독 중이면 재등록 불필요
        if (isTracking && currentMode == targetMode) return

        // 모드 변경/재시작이면 한번 정리 후 등록
        if (isTracking) stop()

        val ok = sensorManager.registerListener(
            this,
            s,
            targetMode.samplingPeriodUs,
            targetMode.maxReportLatencyUs
        )
        if (!ok) {
            Log.w(TAG, "HeadTracker registerListener failed. mode=$targetMode")
            return
        }

        isTracking = true
        currentMode = targetMode
        Log.d(TAG, "HeadTracker started. mode=$targetMode")
    }


    fun stop() {
        if (!isTracking) return
        sensorManager.unregisterListener(this)
        isTracking = false
        hasLast = false
        Log.d(TAG, "HeadTracker stopped.")
        currentMode = null

    }

    // 외부에서 리스너를 등록할 수 있는 함수 추가
    fun setOnHeadPoseListener(listener: (Float) -> Unit) {
        this.onYawUpdated = listener
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // 1) Rotation vector -> Rotation matrix
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // 2) Display rotation 보정(remap)
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0 -> {
                // 그대로 사용
                System.arraycopy(rotationMatrix, 0, adjustedMatrix, 0, 9)
            }
            Surface.ROTATION_90 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X,
                    adjustedMatrix
                )
            }
            Surface.ROTATION_180 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y,
                    adjustedMatrix
                )
            }
            Surface.ROTATION_270 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X,
                    adjustedMatrix
                )
            }
            else -> {
                System.arraycopy(rotationMatrix, 0, adjustedMatrix, 0, 9)
            }
        }

        // 3) matrix -> orientation (azimuth, pitch, roll)
        SensorManager.getOrientation(adjustedMatrix, orientation)

        // orientation[0] = azimuth (-pi..pi)
        var yaw = Math.toDegrees(orientation[0].toDouble()).toFloat()

        // normalize to [-180, 180]
        yaw = normalizeDeg180(yaw)

        // 4) wrap-aware smoothing
        val smoothed = if (!hasLast) {
            hasLast = true
            yaw
        } else {
            val delta = shortestAngleDelta(lastYaw, yaw)
            if (abs(delta) < minDeltaDeg) {
                lastYaw // noise cut
            } else {
                val next = lastYaw + alpha * delta
                normalizeDeg180(next)
            }
        }

        lastYaw = smoothed

        onYawUpdated?.invoke(smoothed)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 필요시 캘리브레이션/상태 표시
    }

    private fun normalizeDeg180(deg: Float): Float {
        var d = deg
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    /**
     * from -> to 로 가는 최단 각도 변화량(delta, [-180..180])
     */
    private fun shortestAngleDelta(from: Float, to: Float): Float {
        var delta = to - from
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return delta
    }

    companion object {
        private const val TAG = "HeadTracker"
    }
}
