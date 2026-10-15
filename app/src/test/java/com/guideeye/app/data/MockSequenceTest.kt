package com.guideeye.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockSequenceTest {
    @Test fun `mock distances vary and repeat after four readings`() {
        val source = MockSequence()
        val readings = List(4) { source.next() }
        assertEquals(4, readings.distinct().size)
        assertEquals(readings.first(), source.next())
        assertTrue(readings.all { it.leftMeters > 0 && it.rightMeters > 0 })
    }
}
