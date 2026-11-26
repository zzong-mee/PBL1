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
    @Test fun dangerWinsEvenWhenCautionHasShorterTtc() {
        val caution = risk(1000).copy(ttcSeconds = 0.5)
        val danger = risk(1000).copy(level = IndependentTofRiskEngine.Level.DANGER,
            distanceMm = 1200, ttcSeconds = 1.2)
        assertEquals(danger, IndependentTofWarningGate().offer(listOf(caution, danger), 1000))
    }

    @Test fun sameLevelPrefersShorterTtc() {
        val slower = risk(1000)
        val faster = slower.copy(ttcSeconds = 0.9)
        assertEquals(faster, IndependentTofWarningGate().offer(listOf(slower, faster), 1000))
    }

    @Test fun emptyFrameDoesNotAllowReplay() {
        val gate = IndependentTofWarningGate(cooldownMs = 0)
        assertNotNull(gate.offer(listOf(risk(1000)), 1000))
        assertNull(gate.offer(emptyList(), 1100))
        assertNull(gate.offer(listOf(risk(1000)), 1200))
    }

    @Test fun engineOrdersDangerBeforeShorterTtcCaution() {
        val points = listOf(
            TofApproachTracker.Approach(0, 9, 2000, 4000.0, 1000),
            TofApproachTracker.Approach(0, 45, 1200, 1000.0, 1000)
        )
        val risks = IndependentTofRiskEngine().evaluate(points, 1000)
        assertEquals(2, risks.size)
        assertEquals(IndependentTofRiskEngine.Level.DANGER, risks.first().level)
    }
}
