package com.guideeye.app.audio

import org.junit.Assert.*
import org.junit.Test

class DistanceBeepPolicyTest {
    @Test fun `closer distance shortens interval and raises gain with bounded extremes`() {
        val near = DistanceBeepPolicy.signal(200, 1200, 0)!!
        val far = DistanceBeepPolicy.signal(4000, null, 0)!!
        assertEquals(180L, near.intervalMs)
        assertEquals(1200L, far.intervalMs)
        assertEquals(.55f, near.gain, .0001f)
        assertEquals(.12f, far.gain, .0001f)
        assertEquals(near.copy(distanceMm = 1), DistanceBeepPolicy.signal(1, null, 0))
        var previous = near
        for (distance in 201..4000) {
            val current = DistanceBeepPolicy.signal(distance, null, 0)!!
            assertTrue(current.intervalMs >= previous.intervalMs)
            assertTrue(current.gain <= previous.gain)
            previous = current
        }
    }
    @Test fun `one valid sensor may sound but invalid or out of range pair stays silent`() {
        assertEquals(700, DistanceBeepPolicy.signal(null, 700, 0)!!.distanceMm)
        assertEquals(700, DistanceBeepPolicy.signal(0, 700, 0)!!.distanceMm)
        assertNull(DistanceBeepPolicy.signal(null, null, 0))
        assertNull(DistanceBeepPolicy.signal(-1, 0, 0))
        assertNull(DistanceBeepPolicy.signal(4001, 9000, 0))
    }
    @Test fun `stale samples cannot keep sounding even when JPEG traffic continues`() {
        assertNotNull(DistanceBeepPolicy.signal(300, 500, 1500))
        assertNull(DistanceBeepPolicy.signal(300, 500, 1501))
        assertNull(DistanceBeepPolicy.signal(300, 500, -1))
    }
}
