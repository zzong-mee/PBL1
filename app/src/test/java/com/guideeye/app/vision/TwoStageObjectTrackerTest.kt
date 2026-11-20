package com.guideeye.app.vision

import org.junit.Assert.*
import org.junit.Test

class TwoStageObjectTrackerTest {
    private fun detection(left: Float, confidence: Float = 0.9f, classId: Int = 0) =
        Detection(classId, confidence, left, 0.2f, left + 0.2f, 0.6f)

    @Test fun retainsIdForOverlappingObject() {
        val tracker = TwoStageObjectTracker()
        val first = tracker.update(listOf(detection(0.2f))).single().trackId
        val second = tracker.update(listOf(detection(0.22f))).single().trackId
        assertEquals(first, second)
    }

    @Test fun lowConfidenceDetectionRecoversTrackWithoutStartingNewOne() {
        val tracker = TwoStageObjectTracker()
        val id = tracker.update(listOf(detection(0.2f))).single().trackId
        assertEquals(id, tracker.update(listOf(detection(0.21f, 0.4f))).single().trackId)
        assertTrue(tracker.update(listOf(detection(0.7f, 0.4f))).isEmpty())
    }

    @Test fun doesNotMatchDifferentClasses() {
        val tracker = TwoStageObjectTracker()
        val first = tracker.update(listOf(detection(0.2f))).single().trackId
        val second = tracker.update(listOf(detection(0.2f, classId = 2))).single().trackId
        assertNotEquals(first, second)
    }

    @Test fun expiresAfterMissingFrames() {
        val tracker = TwoStageObjectTracker(maxMissingFrames = 1)
        val first = tracker.update(listOf(detection(0.2f))).single().trackId
        tracker.update(emptyList())
        tracker.update(emptyList())
        assertNotEquals(first, tracker.update(listOf(detection(0.2f))).single().trackId)
    }
}
