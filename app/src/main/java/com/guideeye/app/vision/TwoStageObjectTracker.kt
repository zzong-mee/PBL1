package com.guideeye.app.vision

import kotlin.math.max
import kotlin.math.min

/**
 * Two-stage, ByteTrack-inspired IoU association for existing YOLOX detections.
 * This is NOT the full ByteTrack implementation (no motion Kalman filter yet).
 * Track IDs are local to this instance. Feed consecutive frames in timestamp order.
 */
class TwoStageObjectTracker(
    private val highConfidence: Float = 0.55f,
    private val lowConfidence: Float = 0.35f,
    private val matchIou: Float = 0.30f,
    private val maxMissingFrames: Int = 5
) {
    data class TrackedDetection(val trackId: Long, val detection: Detection)
    private data class Track(var detection: Detection, var missing: Int = 0)
    private val tracks = linkedMapOf<Long, Track>()
    private var nextId = 1L

    fun reset() {
        tracks.clear()
        nextId = 1L
    }

    fun update(detections: List<Detection>): List<TrackedDetection> {
        val valid = detections.filter {
            it.confidence.isFinite() && it.confidence >= lowConfidence &&
                it.left.isFinite() && it.top.isFinite() &&
                it.right.isFinite() && it.bottom.isFinite() &&
                it.left >= 0f && it.top >= 0f && it.right <= 1f && it.bottom <= 1f &&
                it.right > it.left && it.bottom > it.top
        }
        val high = valid.filter { it.confidence >= highConfidence }
        val low = valid.filter { it.confidence < highConfidence }
        val unmatched = tracks.keys.toMutableSet()
        val output = mutableListOf<TrackedDetection>()
        val matchedDetections = mutableSetOf<Detection>()

        fun associate(candidates: List<Detection>, allowNew: Boolean) {
            val available = candidates.toMutableList()
            while (available.isNotEmpty()) {
                var bestId: Long? = null
                var bestDetection: Detection? = null
                var bestIou = matchIou
                for (id in unmatched) {
                    val previous = tracks.getValue(id).detection
                    for (candidate in available) {
                        if (candidate.classId != previous.classId) continue
                        val overlap = iou(previous, candidate)
                        if (overlap > bestIou) {
                            bestIou = overlap
                            bestId = id
                            bestDetection = candidate
                        }
                    }
                }
                if (bestId == null || bestDetection == null) break
                val track = tracks.getValue(bestId)
                track.detection = bestDetection
                track.missing = 0
                unmatched.remove(bestId)
                available.remove(bestDetection)
                output.add(TrackedDetection(bestId, bestDetection))
                matchedDetections.add(bestDetection)
            }
            if (allowNew) for (detection in available) {
                val id = nextId++
                tracks[id] = Track(detection)
                output.add(TrackedDetection(id, detection))
            }
        }

        // Associate existing tracks in two passes BEFORE creating new tracks.
        // Otherwise a new high-score track could consume a low-score detection in the same frame.
        associate(high, allowNew = false)
        associate(low, allowNew = false)
        for (detection in high) if (detection !in matchedDetections) {
            val id = nextId++
            tracks[id] = Track(detection)
            output.add(TrackedDetection(id, detection))
        }
        for (id in unmatched) {
            val track = tracks.getValue(id)
            track.missing++
            if (track.missing > maxMissingFrames) tracks.remove(id)
        }
        return output.sortedBy { it.trackId }
    }

    private fun iou(a: Detection, b: Detection): Float {
        val w = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val h = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val intersection = w * h
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val union = areaA + areaB - intersection
        return if (union > 0f) intersection / union else 0f
    }
}
