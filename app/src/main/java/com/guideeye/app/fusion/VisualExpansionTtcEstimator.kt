package com.guideeye.app.fusion

import com.guideeye.app.vision.TwoStageObjectTracker
import kotlin.math.sqrt

/**
 * Image-only apparent expansion estimate; not metric distance or a reliable
 * collision time. Ego-motion, rotations and object articulation affect scale.
 * Requires three consecutive expanding observations of the same tracked ID.
 */
class VisualExpansionTtcEstimator(
    private val minExpansionPerSecond: Double = 0.08,
    private val maxGapMs: Long = 700,
    private val maxTtcSeconds: Double = 15.0
) {
    data class Estimate(val trackId: Long, val classId: Int, val ttcSeconds: Double,
                        val observedAtMs: Long)
    private data class State(val scale: Double, val timeMs: Long, val streak: Int)
    private val states = mutableMapOf<Long, State>()
    private var lastFrameMs = -1L

    fun reset() { states.clear(); lastFrameMs = -1L }

    fun update(tracks: List<TwoStageObjectTracker.TrackedDetection>, atMs: Long): List<Estimate> {
        if (atMs < 0 || atMs <= lastFrameMs) return emptyList()
        lastFrameMs = atMs
        val active = tracks.map { it.trackId }.toSet()
        states.keys.retainAll(active)
        val output = mutableListOf<Estimate>()
        for (track in tracks) {
            val d = track.detection
            if (!d.left.isFinite() || !d.right.isFinite() ||
                !d.top.isFinite() || !d.bottom.isFinite() ||
                d.left < 0f || d.top < 0f || d.right > 1f || d.bottom > 1f ||
                d.right <= d.left || d.bottom <= d.top) {
                states.remove(track.trackId)
                continue
            }
            val scale = sqrt(((d.right - d.left) * (d.bottom - d.top)).toDouble())
            val old = states[track.trackId]
            if (old == null || atMs - old.timeMs > maxGapMs) {
                states[track.trackId] = State(scale, atMs, 0)
                continue
            }
            val dt = (atMs - old.timeMs) / 1000.0
            val expansion = (scale / old.scale - 1.0) / dt
            val streak = if (expansion >= minExpansionPerSecond) old.streak + 1 else 0
            states[track.trackId] = State(scale, atMs, streak)
            if (streak >= 2) {
                // d(log apparent size)/dt approximation to inverse time-to-contact.
                val ttc = 1.0 / expansion
                if (ttc.isFinite() && ttc in 0.0..maxTtcSeconds)
                    output.add(Estimate(track.trackId, d.classId, ttc, atMs))
            }
        }
        return output.sortedBy { it.ttcSeconds }
    }
}
