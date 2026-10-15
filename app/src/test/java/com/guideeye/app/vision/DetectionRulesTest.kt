package com.guideeye.app.vision

import org.junit.Assert.*
import org.junit.Test

class DetectionRulesTest {
    @Test fun `class-aware suppression removes duplicate boxes but keeps different objects`() {
        val a=Detection(0,.9f,.2f,.2f,.8f,.8f)
        val duplicate=a.copy(confidence=.8f)
        val different=a.copy(classId=1)
        assertEquals(listOf(a,different),DetectionRules.suppress(listOf(a,duplicate,different)))
    }
    @Test fun `off-centre and uncertain boxes are not announced`() {
        assertNull(DetectionRules.central(listOf(Detection(0,.9f,0f,0f,.2f,.2f),Detection(0,.2f,.3f,.3f,.7f,.7f))))
    }
    @Test fun `box containing centre has priority over one intersecting central region`() {
        val containing=Detection(0,.6f,.3f,.3f,.9f,.9f)
        val nearby=Detection(1,.9f,.4f,.4f,.49f,.49f)
        assertEquals(containing,DetectionRules.central(listOf(nearby,containing)))
    }
}
