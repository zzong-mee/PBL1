package com.guideeye.app.fusion

import org.junit.Assert.*
import org.junit.Test

class IndependentTofWarningGateTest {
    private fun risk(at: Long) = IndependentTofRiskEngine.Risk(0, 1800, 1000.0, 1.8, at)
    @Test fun limitsRepeatedAnnouncements() {
        val gate = IndependentTofWarningGate()
        assertNotNull(gate.offer(listOf(risk(1000)), 1000))
        assertNull(gate.offer(listOf(risk(1100)), 1100))
        assertNull(gate.offer(listOf(risk(1100)), 6200))
        assertNotNull(gate.offer(listOf(risk(6200)), 6200))
    }
    @Test fun dangerEscalatesBeforeCooldownExpires() {
        val gate = IndependentTofWarningGate()
        assertNotNull(gate.offer(listOf(risk(1000)), 1000))
        val danger = risk(1200).copy(level = IndependentTofRiskEngine.Level.DANGER)
        assertEquals(IndependentTofRiskEngine.Level.DANGER,
            gate.offer(listOf(danger), 1200)?.level)
        assertNull(gate.offer(listOf(danger.copy(observedAtMs = 1400)), 1400))
    }

    @Test fun rejectsStaleAndResets() {
        val gate = IndependentTofWarningGate()
        assertNull(gate.offer(listOf(risk(1000)), 2000))
        assertNotNull(gate.offer(listOf(risk(2100)), 2100))
        gate.reset()
        assertNotNull(gate.offer(listOf(risk(2200)), 2200))
    }
}
