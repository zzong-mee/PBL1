package com.guideeye.app.fusion

import org.junit.Assert.*
import org.junit.Test

class ObjectRangeKalmanFilterTest {
    @Test fun estimatesClosingAndTtcFromDecreasingRanges() {
        val filter = ObjectRangeKalmanFilter()
        val samples = listOf(3500, 3300, 3100, 2900, 2700, 2500)
        var result: ObjectRangeKalmanFilter.Estimate? = null
        for ((index, value) in samples.withIndex()) result = filter.update(value, index * 200L)
        assertNotNull(result)
        assertTrue(result!!.closingMmPerSecond > 0)
        assertNotNull(result.ttcSeconds)
        assertTrue(result.ttcSeconds!! > 0)
    }

    @Test fun staticObjectHasNoArtificialClosing() {
        val filter = ObjectRangeKalmanFilter()
        repeat(6) { filter.update(2000, it * 200L) }
        val result = filter.update(2000, 1200L)!!
        assertEquals(0.0, result.closingMmPerSecond, 0.001)
        assertNull(result.ttcSeconds)
    }

    @Test fun rejectsInvalidOrOutOfOrderMeasurement() {
        val filter = ObjectRangeKalmanFilter()
        assertNull(filter.update(0, 100))
        assertNotNull(filter.update(2000, 100))
        assertNull(filter.update(1900, 100))
        assertNull(filter.update(1900, 99))
        assertNull(filter.update(5000, 200))
        assertNotNull(filter.update(1900, 200))
    }

    @Test fun resetsVelocityAfterLongGap() {
        val filter = ObjectRangeKalmanFilter(maxGapMs = 500)
        filter.update(3000, 100)
        assertTrue(filter.update(2800, 300)!!.closingMmPerSecond > 0)
        val restarted = filter.update(2200, 1000)!!
        assertEquals(0.0, restarted.closingMmPerSecond, 0.001)
        assertNull(restarted.ttcSeconds)
    }
}
