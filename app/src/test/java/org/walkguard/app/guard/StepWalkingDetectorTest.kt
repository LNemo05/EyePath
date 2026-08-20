package org.walkguard.app.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepWalkingDetectorTest {
    @Test fun detectorStepReportsWalkingImmediately() {
        val stateMachine = StepWalkingStateMachine()

        assertTrue(stateMachine.onStepDetected(nowMs = 1_000L))

        assertEquals(
            StepWalkingDetector.State(isWalking = true, lastStepElapsedMs = 0L),
            stateMachine.stateAt(nowMs = 1_000L)
        )
    }

    @Test fun walkingStopsAtExactOneSecondBoundary() {
        val stateMachine = StepWalkingStateMachine()
        stateMachine.onStepDetected(nowMs = 1_000L)

        assertTrue(stateMachine.stateAt(nowMs = 1_999L).isWalking)
        assertFalse(stateMachine.stateAt(nowMs = 2_000L).isWalking)
    }

    @Test fun initialAndUnchangedCounterValuesAreNotValidSteps() {
        val stateMachine = StepWalkingStateMachine()

        assertFalse(stateMachine.onStepCounter(value = 10f, nowMs = 1_000L))
        assertFalse(stateMachine.onStepCounter(value = 10f, nowMs = 1_500L))
        assertFalse(stateMachine.stateAt(nowMs = 1_500L).isWalking)
    }

    @Test fun increasedCounterValueIsAValidStep() {
        val stateMachine = StepWalkingStateMachine()
        stateMachine.onStepCounter(value = 10f, nowMs = 1_000L)

        assertTrue(stateMachine.onStepCounter(value = 11f, nowMs = 1_200L))
        assertTrue(stateMachine.stateAt(nowMs = 1_200L).isWalking)
    }

    @Test fun newStepInvalidatesStaleStopCallback() {
        val clock = MutableClock()
        val scheduler = RecordingStopScheduler()
        val emitted = mutableListOf<StepWalkingDetector.State>()
        val monitor = StepWalkingMonitor(
            nowMs = clock::now,
            stopScheduler = scheduler,
            onStateChanged = emitted::add
        )

        monitor.onStepDetected()
        clock.value = 500L
        monitor.onStepDetected()
        clock.value = 1_000L
        scheduler.tasks.first().runEvenIfCancelled()

        assertEquals(listOf(true, true), emitted.map { it.isWalking })

        clock.value = 1_500L
        scheduler.tasks.last().runIfActive()
        assertFalse(emitted.last().isWalking)
    }

    @Test fun unchangedCounterDoesNotReplaceStopTask() {
        val clock = MutableClock(value = 1_000L)
        val scheduler = RecordingStopScheduler()
        val monitor = StepWalkingMonitor(
            nowMs = clock::now,
            stopScheduler = scheduler
        )

        monitor.onStepCounter(10f)
        monitor.onStepCounter(11f)
        monitor.onStepCounter(11f)

        assertEquals(1, scheduler.tasks.size)
    }

    @Test fun stopPreventsPendingStoppedCallback() {
        val clock = MutableClock()
        val scheduler = RecordingStopScheduler()
        val emitted = mutableListOf<StepWalkingDetector.State>()
        val monitor = StepWalkingMonitor(
            nowMs = clock::now,
            stopScheduler = scheduler,
            onStateChanged = emitted::add
        )

        monitor.onStepDetected()
        monitor.stop()
        clock.value = 1_000L
        scheduler.tasks.single().runEvenIfCancelled()

        assertEquals(listOf(true), emitted.map { it.isWalking })
    }

    private class MutableClock(var value: Long = 0L) {
        fun now(): Long = value
    }

    private class RecordingStopScheduler : WalkingStopScheduler {
        val tasks = mutableListOf<Task>()

        override fun schedule(delayMs: Long, action: () -> Unit): WalkingStopHandle {
            return Task(delayMs, action).also(tasks::add)
        }

        class Task(
            val delayMs: Long,
            private val action: () -> Unit
        ) : WalkingStopHandle {
            private var cancelled = false

            override fun cancel() {
                cancelled = true
            }

            fun runIfActive() {
                if (!cancelled) action()
            }

            fun runEvenIfCancelled() {
                action()
            }
        }
    }
}
