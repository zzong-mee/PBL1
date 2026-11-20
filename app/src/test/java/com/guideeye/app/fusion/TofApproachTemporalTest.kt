package com.guideeye.app.fusion

import com.guideeye.app.usb.TofGrid
import com.guideeye.app.usb.TofGridFrame
import org.junit.Assert.*
import org.junit.Test

class TofApproachTemporalTest {
    private fun grid(distance: Int): TofGrid {
        val mm = MutableList<Int?>(64) { null }
        val status = MutableList<Int?>(64) { null }
        for (zone in listOf(27, 28)) { mm[zone] = distance; status[zone] = 5 }
        return TofGrid(mm, status, 0)
    }
    private fun frame(distance: Int, time: Long) =
        TofGridFrame(grid(distance), grid(4000), time)

    @Test fun consistentApproachAcrossThreeFramesIsDetected() {
        val tracker = TofApproachTracker()
        assertTrue(tracker.update(frame(2500, 1000)).isEmpty())
        assertTrue(tracker.update(frame(2200, 1200)).isEmpty())
        assertFalse(tracker.update(frame(1900, 1400)).isEmpty())
    }

    @Test fun oneFrameJumpAfterStableDistanceIsIgnored() {
        val tracker = TofApproachTracker()
        tracker.update(frame(2500, 1000))
        tracker.update(frame(2500, 1200))
        assertTrue(tracker.update(frame(1700, 1400)).isEmpty())
    }

    @Test fun missingMeasurementBreaksConfirmation() {
        val tracker = TofApproachTracker()
        tracker.update(frame(2500, 1000))
        tracker.update(frame(2200, 1200))
        tracker.update(frame(1900, 1400))
        tracker.update(frame(1600, 3000))
        assertTrue(tracker.update(frame(1300, 3200)).isEmpty())
    }
}
