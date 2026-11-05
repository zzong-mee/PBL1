package com.guideeye.app.fusion

import com.guideeye.app.vision.Detection
import kotlin.math.abs

/**
 * Experimental camera/ToF fusion. Camera and sensors must be mounted facing forward.
 * This uses a coarse horizontal sector association, NOT a calibrated pixel-to-ray transform.
 * Missing, stale or unmatched input never produces a fused warning.
 */
object ObstacleFusion {
    enum class Level { CAUTION, DANGER }
    data class Warning(val classId: Int, val distanceMm: Int, val sector: Int, val level: Level)

    fun evaluate(
        detections: List<Detection>,
        approaches: List<TofApproachTracker.Approach>,
        nowMs: Long,
        visionReceivedAtMs: Long,
        maxSkewMs: Long = 500
    ): Warning? {
        if (nowMs < visionReceivedAtMs || nowMs-visionReceivedAtMs > 1500) return null
        return detections.asSequence().filter {
            it.classId in 0..79 && it.confidence >= .55f &&
                it.left.isFinite() && it.right.isFinite() &&
                it.top.isFinite() && it.bottom.isFinite() &&
                it.left in 0f..1f && it.right in 0f..1f &&
                it.left < it.right && it.top < it.bottom &&
                it.right-it.left >= .08f
        }.mapNotNull { detection ->
            val center = (detection.left + detection.right) / 2f
            if (center !in .34f..66f) return@mapNotNull null
            val sector = when {
                center < .44f -> 0
                center > .56f -> 2
                else -> 1
            }
            // No calibrated extrinsics yet: only use central-facing cells.
            // Sensor-specific orientation must be calibrated before real-world use.
            val matches = approaches.filter { approach ->
                val col = approach.zone % 8
                val sectorMatch = when (sector) {
                    0 -> col in 2..4
                    1 -> col in 3..5
                    else -> col in 4..6
                }
                sectorMatch && approach.distanceMm in 1..3000 &&
                    nowMs >= approach.observedAtMs &&
                    nowMs-approach.observedAtMs <= 750 &&
                    abs(approach.observedAtMs-visionReceivedAtMs) <= maxSkewMs
            }
            val closest = matches.minByOrNull { it.distanceMm } ?: return@mapNotNull null
            Warning(detection.classId,closest.distanceMm,sector,Level.DANGER)
        }.minByOrNull { it.distanceMm }
    }
}

/** Consecutive observations and a cooldown reduce repetitive or transient speech. */
class ObstacleWarningGate(private val cooldownMs: Long = 5000L) {
    private var previous: ObstacleFusion.Warning? = null
    private var consecutive = 0
    private var lastAnnounced = Long.MIN_VALUE
    fun reset() { previous = null; consecutive = 0; lastAnnounced = Long.MIN_VALUE }
    fun offer(warning: ObstacleFusion.Warning?, nowMs: Long): ObstacleFusion.Warning? {
        if (warning == null) { previous = null; consecutive = 0; return null }
        consecutive = if (previous?.classId == warning.classId &&
            previous?.sector == warning.sector) consecutive + 1 else 1
        previous = warning
        if (consecutive < 2 || (lastAnnounced != Long.MIN_VALUE &&
                nowMs - lastAnnounced < cooldownMs)) return null
        lastAnnounced = nowMs
        return warning
    }
}
