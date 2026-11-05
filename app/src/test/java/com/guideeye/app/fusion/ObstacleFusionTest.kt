package com.guideeye.app.fusion

import com.guideeye.app.usb.TofGrid
import com.guideeye.app.usb.TofGridFrame
import com.guideeye.app.vision.Detection
import org.junit.Assert.*
import org.junit.Test

class ObstacleFusionTest {
    private fun frame(mm: Int, at: Long): TofGridFrame {
        val grid = TofGrid(List(64) { mm }, List(64) { 5 }, 0L)
        return TofGridFrame(grid, grid, at)
    }
    private val central = Detection(0, .9f, .4f, .2f, .6f, .9f)
    private fun approach(): List<TofApproachTracker.Approach> {
        val tracker = TofApproachTracker()
        tracker.update(frame(2500,1000))
        assertTrue(tracker.update(frame(2200,1200)).isEmpty())
        return tracker.update(frame(1900,1400))
    }
    @Test fun rapidApproachWithinThreeMetersTriggers() {
        assertEquals(ObstacleFusion.Level.DANGER,
            ObstacleFusion.evaluate(listOf(central),approach(),1450,1400)?.level)
    }
    @Test fun stationaryOrRecedingObjectDoesNotTrigger() {
        val tracker = TofApproachTracker()
        tracker.update(frame(2000,1000))
        tracker.update(frame(2000,1200))
        assertTrue(tracker.update(frame(2100,1400)).isEmpty())
    }
    @Test fun outsideThreeMetersDoesNotTrigger() {
        val tracker = TofApproachTracker()
        tracker.update(frame(3800,1000))
        tracker.update(frame(3500,1200))
        assertTrue(tracker.update(frame(3200,1400)).isEmpty())
    }
    @Test fun staleOrLowConfidenceDoesNotTrigger() {
        assertNull(ObstacleFusion.evaluate(listOf(central),approach(),2500,1400))
        assertNull(ObstacleFusion.evaluate(listOf(central.copy(confidence=.2f)),approach(),1450,1400))
    }
    @Test fun singleCellNoiseDoesNotTrigger() {
        val tracker = TofApproachTracker()
        val baseline = frame(2000,1000)
        tracker.update(baseline)
        fun changed(at: Long, value: Int): TofGridFrame {
            val mm = List<Int?>(64) { if(it==27) value else 2000 }
            val grid = TofGrid(mm,List(64){5},0L)
            return TofGridFrame(grid,grid,at)
        }
        tracker.update(changed(1200,1700))
        assertTrue(tracker.update(changed(1400,1400)).isEmpty())
    }
    @Test fun consecutiveAndCooldown() {
        val gate = ObstacleWarningGate(5000)
        val warning = ObstacleFusion.Warning(0,1900,1,ObstacleFusion.Level.DANGER)
        assertNull(gate.offer(warning,1000))
        assertNotNull(gate.offer(warning,1200))
        assertNull(gate.offer(warning,1400))
        assertNotNull(gate.offer(warning,6300))
    }
}
