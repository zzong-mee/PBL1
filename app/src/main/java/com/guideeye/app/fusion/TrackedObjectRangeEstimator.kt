package com.guideeye.app.fusion

import com.guideeye.app.usb.TofGridFrame
import com.guideeye.app.vision.TwoStageObjectTracker

/**
 * Owns one independent metric Kalman filter per YOLO track ID.
 * No calibrated ToF-to-camera mapping => no object-specific estimate.
 * Unmatched or stale objects never inherit another object's distance.
 *
 * Call update() serially with camera frames; timestamps use elapsedRealtime().
 */
class TrackedObjectRangeEstimator(
    calibration: TofObjectAssociator.Calibration?,
    private val maxMissingMs: Long = 1500L,
    private val maxMeasurementsGapMs: Long = 600L
) {
    data class ObjectRange(
        val trackId: Long,
        val classId: Int,
        val distanceMm: Double,
        val closingMmPerSecond: Double,
        val ttcSeconds: Double?,
        val measuredAtMs: Long
    )

    private data class State(
        val filter: ObjectRangeKalmanFilter = ObjectRangeKalmanFilter(),
        var lastMatchedAtMs: Long,
        var lastObservedAtMs: Long
    )

    private val associator = TofObjectAssociator(calibration)
    private val states = mutableMapOf<Long, State>()
    private var lastVisionAtMs = -1L

    init {
        require(maxMissingMs > 0 && maxMeasurementsGapMs > 0)
    }

    fun reset() {
        states.clear()
        lastVisionAtMs = -1L
    }

    fun update(
        tracks: List<TwoStageObjectTracker.TrackedDetection>,
        frame: TofGridFrame?,
        visionAtMs: Long,
        nowMs: Long
    ): List<ObjectRange> {
        if (visionAtMs < 0 || nowMs < visionAtMs || visionAtMs <= lastVisionAtMs) return emptyList()
        lastVisionAtMs = visionAtMs
        val active = tracks.map { it.trackId }.toSet()
        states.entries.removeAll { (id, state) ->
            id !in active && (nowMs < state.lastMatchedAtMs ||
                nowMs - state.lastMatchedAtMs > maxMissingMs)
        }

        val matches = associator.associate(tracks, frame, visionAtMs, nowMs)
        val output = mutableListOf<ObjectRange>()
        for (match in matches) {
            val previous = states[match.trackId]
            val state = if (previous == null ||
                match.observedAtMs <= previous.lastObservedAtMs ||
                match.observedAtMs - previous.lastObservedAtMs > maxMeasurementsGapMs
            ) State(lastMatchedAtMs = match.observedAtMs, lastObservedAtMs = -1L)
            else previous

            // Duplicate ToF timestamps must not be fed twice into the filter.
            if (previous != null && match.observedAtMs == previous.lastObservedAtMs) continue
            val estimate = state.filter.update(match.distanceMm, match.observedAtMs) ?: continue
            state.lastMatchedAtMs = match.observedAtMs
            state.lastObservedAtMs = match.observedAtMs
            states[match.trackId] = state
            output.add(ObjectRange(match.trackId, match.classId, estimate.distanceMm,
                estimate.closingMmPerSecond, estimate.ttcSeconds, match.observedAtMs))
        }
        return output
    }
}
