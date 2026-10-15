package com.guideeye.app.accessibility

import org.junit.Assert.*
import org.junit.Test

class ReleaseGestureTest {
    @Test fun `touch down does not execute and release inside executes only once`() {
        val gesture = ReleaseGesture()
        gesture.begin(2)
        assertTrue(gesture.release(2, true))
        assertFalse(gesture.release(2, true))
    }
    @Test fun `sliding outside then back inside stays cancelled`() {
        val gesture = ReleaseGesture()
        gesture.begin(0)
        gesture.move(false)
        gesture.move(true)
        assertFalse(gesture.release(0, true))
    }
    @Test fun `release outside cannot click even without a move event`() {
        val gesture = ReleaseGesture()
        gesture.begin(0)
        assertFalse(gesture.release(0, false))
    }
    @Test fun `system or multitouch cancellation prevents activation and feedback repeats`() {
        val gesture = ReleaseGesture()
        gesture.begin(0)
        assertTrue(gesture.cancel())
        assertFalse(gesture.cancel())
        assertFalse(gesture.release(0, true))
        gesture.begin(1)
        assertTrue(gesture.release(1, true))
    }
    @Test fun `another finger cannot release the original button`() {
        val gesture = ReleaseGesture()
        gesture.begin(3)
        assertFalse(gesture.release(4, true))
    }
}
