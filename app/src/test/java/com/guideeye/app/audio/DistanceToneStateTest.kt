package com.guideeye.app.audio

import org.junit.Assert.*
import org.junit.Test

class DistanceToneStateTest {
    @Test fun `near equal regions keep selection but clearly closer region switches`() {
        val state = DistanceToneState()
        state.update(1000, 1100, 0)
        assertEquals(DistanceToneState.Side.LEFT, state.output(0)!!.side)
        state.update(1000, 900, 100)
        assertEquals(DistanceToneState.Side.LEFT, state.output(100)!!.side)
        state.update(1000, 800, 200)
        assertEquals(DistanceToneState.Side.RIGHT, state.output(200)!!.side)
        state.update(750, 800, 300)
        assertEquals(DistanceToneState.Side.RIGHT, state.output(300)!!.side)
        state.update(600, 800, 400)
        assertEquals(DistanceToneState.Side.LEFT, state.output(400)!!.side)
    }
    @Test fun `lost selected sensor switches immediately and old stale selection is forgotten`() {
        val state = DistanceToneState()
        state.update(300, 2000, 0)
        state.update(null, 2000, 100)
        assertEquals(DistanceToneState.Side.RIGHT, state.output(100)!!.side)
        state.update(1900, 2000, 2000)
        assertEquals(DistanceToneState.Side.LEFT, state.output(2000)!!.side)
        assertNull(state.output(3501))
        state.update(null, null, 3600)
        assertNull(state.output(3600))
    }
    @Test fun `distance interval and gain approach a new value continuously and faster when closer`() {
        val state = DistanceToneState()
        state.update(3000, null, 0)
        state.update(1000, null, 100)
        assertEquals(3000f, state.output(100)!!.smoothedMm, .01f)
        val approaching = (1..10).map { state.output(100 + 30L * it)!! }
        assertTrue(approaching.first().smoothedMm in 1000f..3000f)
        approaching.zipWithNext().forEach { (a,b) ->
            assertTrue(b.smoothedMm < a.smoothedMm)
            assertTrue(b.signal.intervalMs <= a.signal.intervalMs)
            assertTrue(b.signal.gain >= a.signal.gain)
        }
        val near = state.output(500)!!.smoothedMm
        state.update(3000, null, 500)
        val receding = state.output(530)!!
        assertTrue(receding.smoothedMm > near && receding.smoothedMm < 3000)
        assertTrue((3000 - approaching.first().smoothedMm) > (receding.smoothedMm - near))
    }
    @Test fun `phase keeps progress through interval change and reset produces a fresh first beep`() {
        val phase = BeepPhase()
        assertTrue(phase.due(0, 1000))
        assertFalse(phase.due(500, 1000)) // half a cycle
        assertFalse(phase.due(600, 400)) // three quarters, do not restart the cycle
        assertTrue(phase.due(700, 400))
        assertFalse(phase.due(700, 400))
        phase.reset()
        assertTrue(phase.due(710, 400))
    }
    @Test fun `delayed scheduling cannot create a burst of catch up beeps`() {
        val phase = BeepPhase()
        assertTrue(phase.due(0, 180))
        assertTrue(phase.due(10070, 180))
        assertFalse(phase.due(10100, 180))
        assertFalse(phase.due(10160, 180))
        assertTrue(phase.due(10250, 180))
    }
}
