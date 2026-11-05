package com.guideeye.app.fusion

import com.guideeye.app.usb.TofGridFrame
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
        detections: List<Detection>, frame: TofGridFrame?,
        nowMs: Long, visionReceivedAtMs: Long,
        maxSkewMs: Long = 500
    ): Warning? {
        if (frame == null || nowMs < frame.receivedAt ||
            nowMs - frame.receivedAt > 1000 ||
            nowMs < visionReceivedAtMs || nowMs - visionReceivedAtMs > 1500 ||
            abs(frame.receivedAt - visionReceivedAtMs) > maxSkewMs) return null

        val candidates = detections.asSequence().filter {
            it.classId >= 0 && it.confidence >= .55f &&
                it.left.isFinite() && it.right.isFinite() &&
                it.top.isFinite() && it.bottom.isFinite() &&
                it.left in 0f..1f && it.right in 0f..1f &&
                it.left < it.right && it.top < it.bottom &&
                (it.right-it.left) >= .08f
        }.mapNotNull { detection ->
            val center = (detection.left + detection.right) / 2f
            // Three coarse frontal sectors. Outer sectors are deliberately excluded
            // until the camera-to-ToF extrinsics have been measured.
            if (center !in .34f..66f) return@mapNotNull null
            val sector = when {
                center < .44f -> 0
                center > .56f -> 2
                else -> 1
            }
            // Each sensor contributes its center-facing columns only. We require
            // multiple valid nearby zones to reject isolated spurious returns.
            val values = listOf(frame.left, frame.right).flatMap { grid ->
                val age = grid.ageAtReceiptMs + (nowMs - frame.receivedAt)
                if (age !in 0..1000 || grid.mm.size != 64 || grid.status.size != 64) emptyList()
                else grid.mm.mapIndexedNotNull { index, mm ->
                    val row = index / 8
                    val col = index % 8
                    val inSector = when (sector) {
                        0 -> col in 2..4
                        1 -> col in 3..5
                        else -> col in 4..6
                    }
                    if (row in 2..6 && inSector && mm != null &&
                        mm in 1..4000 && grid.status[index] in listOf(5,6,9)) mm else null
                }
            }.sorted()
            if (values.size < 3) return@mapNotNull null
            val distance = values[2] // third nearest valid return, not a single minimum
            val level = when {
                distance <= 700 -> Level.DANGER
                distance <= 1400 -> Level.CAUTION
                else -> return@mapNotNull null
            }
            Warning(detection.classId, distance, sector, level)
        }.toList()
        return candidates.minWithOrNull(compareBy<Warning> { it.distanceMm }.thenByDescending { it.level })
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
