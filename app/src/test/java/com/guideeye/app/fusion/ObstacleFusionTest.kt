package com.guideeye.app.fusion

import com.guideeye.app.usb.TofGrid
import com.guideeye.app.usb.TofGridFrame
import com.guideeye.app.vision.Detection
import org.junit.Assert.*
import org.junit.Test

class ObstacleFusionTest {
    private fun frame(mm: Int, time: Long = 1000L): TofGridFrame {
        val grid = TofGrid(List(64) { mm }, List(64) { 5 }, 0L)
        return TofGridFrame(grid, grid, time)
    }
    private val central = Detection(0, .9f, .4f, .2f, .6f, .9f)

    @Test fun nearCentralObjectTriggersDanger() {
        val result = ObstacleFusion.evaluate(listOf(central), frame(600), 1100L, 1000L)
        assertEquals(ObstacleFusion.Level.DANGER, result?.level)
    }
    @Test fun farObjectDoesNotTrigger() {
        assertNull(ObstacleFusion.evaluate(listOf(central), frame(2300), 1100L, 1000L))
    }
    @Test fun staleOrUnsynchronizedInputsDoNotTrigger() {
        assertNull(ObstacleFusion.evaluate(listOf(central), frame(500), 2100L, 1000L))
        assertNull(ObstacleFusion.evaluate(listOf(central), frame(500), 1200L, 2000L))
    }
    @Test fun noDetectionOrLowConfidenceDoesNotTrigger() {
        assertNull(ObstacleFusion.evaluate(emptyList(), frame(400), 1100L, 1000L))
        assertNull(ObstacleFusion.evaluate(listOf(central.copy(confidence=.2f)), frame(400), 1100L, 1000L))
    }
    @Test fun consecutiveAndCooldown() {
        val gate = ObstacleWarningGate(5000)
        val warning = ObstacleFusion.Warning(0, 600, 1, ObstacleFusion.Level.DANGER)
        assertNull(gate.offer(warning, 1000))
        assertNotNull(gate.offer(warning, 1200))
        assertNull(gate.offer(warning, 1400))
        assertNotNull(gate.offer(warning, 6300))
        gate.reset()
        assertNull(gate.offer(warning, 6500))
    }
}
