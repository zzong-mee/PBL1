package com.guideeye.app.fusion

import com.guideeye.app.vision.Detection
import com.guideeye.app.vision.TwoStageObjectTracker
import org.junit.Assert.*
import org.junit.Test

class CalibrationFreeRiskTest {
    private fun detection(id: Long, width: Float) =
        TwoStageObjectTracker.TrackedDetection(id, Detection(0, .9f,
            .5f - width / 2, .5f - width / 2, .5f + width / 2, .5f + width / 2))

    @Test fun tofRiskNeedsFreshApproachWithinThreeMetres() {
        val engine = IndependentTofRiskEngine()
        val approach = TofApproachTracker.Approach(0, 27, 2000, 1000.0, 1000)
        assertEquals(2.0, engine.evaluate(listOf(approach), 1100).single().ttcSeconds, 0.001)
        assertTrue(engine.evaluate(listOf(approach), 1800).isEmpty())
        assertTrue(engine.evaluate(listOf(approach.copy(distanceMm = 3200)), 1100).isEmpty())
    }

    @Test fun imageTtcNeedsThreeGrowingFrames() {
        val engine = VisualExpansionTtcEstimator()
        assertTrue(engine.update(listOf(detection(1, .2f)), 1000).isEmpty())
        assertTrue(engine.update(listOf(detection(1, .25f)), 1200).isEmpty())
        val result = engine.update(listOf(detection(1, .3f)), 1400)
        assertEquals(1L, result.single().trackId)
        assertTrue(result.single().ttcSeconds > 0)
    }

    @Test fun staticOrNewIdDoesNotInheritTtc() {
        val engine = VisualExpansionTtcEstimator()
        engine.update(listOf(detection(1, .2f)), 1000)
        engine.update(listOf(detection(1, .3f)), 1200)
        assertTrue(engine.update(listOf(detection(2, .4f)), 1400).isEmpty())
        assertTrue(engine.update(listOf(detection(2, .4f)), 1600).isEmpty())
        assertTrue(engine.update(listOf(detection(2, .4f)), 1800).isEmpty())
    }
}
