package com.guideeye.app.fusion

import org.junit.Assert.*
import org.junit.Test

class IndependentTofClusterTest {
    private val engine = IndependentTofRiskEngine()
    private fun point(zone: Int, distance: Int, speed: Double = 1000.0,
                      sensor: Int = 0, time: Long = 1000) =
        TofApproachTracker.Approach(sensor, zone, distance, speed, time)

    @Test fun separatedObjectsKeepTheirOwnSpeeds() {
        val risks = engine.evaluate(listOf(
            point(9, 1000), point(10, 1100),
            point(45, 2400, 2000.0), point(46, 2500, 2000.0)
        ), 1100)
        assertEquals(2, risks.size)
        assertEquals(1.0, risks[0].ttcSeconds, 0.001)
        assertEquals(1.2, risks[1].ttcSeconds, 0.001)
    }

    @Test fun adjacentCellsWithLargeDepthGapStaySeparate() {
        assertEquals(2, engine.evaluate(listOf(
            point(9, 1000), point(10, 2000)
        ), 1100).size)
    }

    @Test fun connectedCellsMergeTransitively() {
        assertEquals(1, engine.evaluate(listOf(
            point(9, 1000), point(10, 1100), point(11, 1200)
        ), 1100).size)
    }

    @Test fun sensorsAndObservationTimesStaySeparate() {
        assertEquals(3, engine.evaluate(listOf(
            point(9, 1000), point(9, 1000, sensor = 1),
            point(10, 1000, time = 1050)
        ), 1100).size)
    }

    @Test fun rowBoundaryDoesNotCreateFalseAdjacency() {
        assertEquals(2, engine.evaluate(listOf(
            point(7, 1000), point(8, 1000)
        ), 1100).size)
    }
}
