package org.walkguard.app.guard

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

const val WALKING_STOP_DELAY_MS = 1_000L

class StepWalkingStateMachine(
    private val stopDelayMs: Long = WALKING_STOP_DELAY_MS
) {
    private var lastStepAtMs: Long? = null
    private var lastCounterValue: Float? = null

    fun onStepDetected(nowMs: Long): Boolean {
        lastStepAtMs = nowMs
        return true
    }

    fun onStepCounter(value: Float, nowMs: Long): Boolean {
        val previous = lastCounterValue
        lastCounterValue = value
        if (previous == null || value <= previous) return false

        lastStepAtMs = nowMs
        return true
    }

    fun isWalking(nowMs: Long): Boolean {
        val last = lastStepAtMs ?: return false
        return nowMs - last < stopDelayMs
    }

    fun stateAt(nowMs: Long): StepWalkingDetector.State {
        val last = lastStepAtMs
        return StepWalkingDetector.State(
            isWalking = last != null && nowMs - last < stopDelayMs,
            lastStepElapsedMs = last?.let { nowMs - it }
        )
    }
}

fun interface WalkingStopHandle {
    fun cancel()
}

fun interface WalkingStopScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): WalkingStopHandle
}

class StepWalkingMonitor(
    private val nowMs: () -> Long,
    private val stopScheduler: WalkingStopScheduler,
    private val onStateChanged: (StepWalkingDetector.State) -> Unit = {},
    private val stateMachine: StepWalkingStateMachine = StepWalkingStateMachine()
) {
    private var active = true
    private var generation = 0L
    private var stopHandle: WalkingStopHandle? = null

    fun onStepDetected() {
        if (stateMachine.onStepDetected(nowMs())) onValidStep()
    }

    fun onStepCounter(value: Float) {
        if (stateMachine.onStepCounter(value, nowMs())) onValidStep()
    }

    fun currentState(): StepWalkingDetector.State = stateMachine.stateAt(nowMs())

    fun stop() {
        active = false
        generation += 1
        stopHandle?.cancel()
        stopHandle = null
    }

    private fun onValidStep() {
        if (!active) return

        generation += 1
        val scheduledGeneration = generation
        stopHandle?.cancel()
        onStateChanged(stateMachine.stateAt(nowMs()))
        stopHandle = stopScheduler.schedule(WALKING_STOP_DELAY_MS) {
            if (!active || generation != scheduledGeneration) return@schedule
            onStateChanged(stateMachine.stateAt(nowMs()))
        }
    }
}

private class HandlerWalkingStopScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : WalkingStopScheduler {
    override fun schedule(delayMs: Long, action: () -> Unit): WalkingStopHandle {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        return WalkingStopHandle { handler.removeCallbacks(runnable) }
    }
}

class StepWalkingDetector(
    private val sensorManager: SensorManager,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
    stopScheduler: WalkingStopScheduler = HandlerWalkingStopScheduler(),
    private val onStateChanged: (State) -> Unit = {}
) : SensorEventListener {
    data class State(
        val isWalking: Boolean,
        val lastStepElapsedMs: Long?
    )

    private val monitor = StepWalkingMonitor(
        nowMs = nowMs,
        stopScheduler = stopScheduler,
        onStateChanged = onStateChanged
    )
    private var registeredSensor: Sensor? = null

    fun start(): Boolean {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: return false

        registeredSensor = sensor
        return sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        monitor.stop()
        sensorManager.unregisterListener(this)
        registeredSensor = null
    }

    fun currentState(): State = monitor.currentState()

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> monitor.onStepDetected()
            Sensor.TYPE_STEP_COUNTER -> event.values.firstOrNull()?.let(monitor::onStepCounter)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
