package com.guideeye.app.fusion

import com.guideeye.app.usb.TofGrid
import com.guideeye.app.usb.TofGridFrame
import com.guideeye.app.vision.Detection
import com.guideeye.app.vision.TwoStageObjectTracker
import org.junit.Assert.*
import org.junit.Test

class TofObjectAssociatorTest {
    private fun grid(vararg cells: Pair<Int, Int>): TofGrid {
        val values = MutableList<Int?>(64) { null }
        val statuses = MutableList<Int?>(64) { null }
        for ((zone, distance) in cells) { values[zone] = distance; statuses[zone] = 5 }
        return TofGrid(values, statuses, 0)
    }
    private fun frame() = TofGridFrame(grid(27 to 1600, 28 to 1700, 35 to 1650), grid(), 1000)
    private fun track() = TwoStageObjectTracker.TrackedDetection(7,
        Detection(0, 0.9f, 0.35f, 0.1f, 0.65f, 0.9f))
    private fun calibrated() = TofObjectAssociator.Calibration(
        List(8) { 0.1f + it * 0.1f }, List(8) { 0.1f + it * 0.1f })

    @Test fun connectedCellsFormClusterWithMedianRange() {
        val clusters = TofObjectAssociator(null).clusters(frame(), 1100)
        assertEquals(1, clusters.size)
        assertEquals(3, clusters.single().zones.size)
        assertEquals(1650, clusters.single().distanceMm)
        assertNull(clusters.single().centerX)
    }

    @Test fun uncalibratedAssociationFailsClosed() {
        assertTrue(TofObjectAssociator(null).associate(listOf(track()), frame(), 1000, 1100).isEmpty())
    }

    @Test fun calibratedAndFreshAssociationReturnsTrackId() {
        val matches = TofObjectAssociator(calibrated()).associate(listOf(track()), frame(), 1000, 1100)
        assertEquals(1, matches.size)
        assertEquals(7L, matches.single().trackId)
        assertEquals(1650, matches.single().distanceMm)
    }

    @Test fun staleAndUnsynchronisedFramesAreRejected() {
        val matcher = TofObjectAssociator(calibrated())
        assertTrue(matcher.associate(listOf(track()), frame(), 1000, 2000).isEmpty())
        assertTrue(matcher.associate(listOf(track()), frame(), 1500, 1550).isEmpty())
    }

    @Test fun largeDepthDiscontinuitySeparatesClusters() {
        val f = TofGridFrame(grid(0 to 900, 1 to 2300), grid(), 1000)
        assertEquals(2, TofObjectAssociator(null).clusters(f, 1100).size)
    }
}
