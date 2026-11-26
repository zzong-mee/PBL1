package com.guideeye.app.fusion

import com.guideeye.app.usb.TofGrid
import com.guideeye.app.usb.TofGridFrame
import com.guideeye.app.vision.Detection
import com.guideeye.app.vision.TwoStageObjectTracker
import org.junit.Assert.*
import org.junit.Test

class TrackedObjectRangeEstimatorTest {
    private val calibration = TofObjectAssociator.Calibration(
        List(8) { 0.1f + it * 0.1f }, List(8) { 0.1f + it * 0.1f })
    private fun track(id: Long) = TwoStageObjectTracker.TrackedDetection(
        id, Detection(0, .9f, .35f, .1f, .65f, .9f))
    private fun frame(distance: Int, at: Long): TofGridFrame {
        val mm = MutableList<Int?>(64) { null }
        val status = MutableList<Int?>(64) { null }
        mm[27] = distance; mm[28] = distance; status[27] = 5; status[28] = 5
        val empty = TofGrid(List(64) { null }, List(64) { null }, 0)
        return TofGridFrame(TofGrid(mm, status, 0), empty, at)
    }

    @Test fun refusesUncalibratedObjectRanges() {
        val estimator = TrackedObjectRangeEstimator(null)
        assertTrue(estimator.update(listOf(track(1)), frame(2500, 1000), 1000, 1050).isEmpty())
    }

    @Test fun keepsTrackSpecificStateAndEstimatesClosing() {
        val estimator = TrackedObjectRangeEstimator(calibration)
        val first = estimator.update(listOf(track(1)), frame(3000, 1000), 1000, 1050)
        assertEquals(1L, first.single().trackId)
        val second = estimator.update(listOf(track(1)), frame(2700, 1200), 1200, 1250)
        assertEquals(1L, second.single().trackId)
        assertTrue(second.single().closingMmPerSecond > 0)
        val third = estimator.update(listOf(track(2)), frame(2400, 1400), 1400, 1450)
        assertEquals(2L, third.single().trackId)
        assertEquals(0.0, third.single().closingMmPerSecond, 0.001)
    }

    @Test fun missingAndRepeatedMeasurementsDoNotEmitOldRanges() {
        val estimator = TrackedObjectRangeEstimator(calibration)
        assertEquals(1, estimator.update(listOf(track(1)), frame(3000, 1000), 1000, 1050).size)
        assertTrue(estimator.update(listOf(track(1)), null, 1100, 1150).isEmpty())
        assertTrue(estimator.update(listOf(track(1)), frame(3000, 1000), 1200, 1250).isEmpty())
        assertTrue(estimator.update(listOf(track(1)), frame(3000, 1300), 1300, 1350).isNotEmpty())
    }

    @Test fun resetClearsVelocity() {
        val estimator = TrackedObjectRangeEstimator(calibration)
        estimator.update(listOf(track(1)), frame(3000, 1000), 1000, 1050)
        estimator.update(listOf(track(1)), frame(2600, 1200), 1200, 1250)
        estimator.reset()
        val result = estimator.update(listOf(track(1)), frame(2400, 1400), 1400, 1450)
        assertEquals(0.0, result.single().closingMmPerSecond, 0.001)
    }
}
