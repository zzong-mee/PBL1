package com.guideeye.app.fusion

/**
 * Calibration-free, sensor-local warning candidates. Direction and camera object
 * identity are intentionally unknown until mounting orientation is calibrated.
 * Requires spatial and temporal confirmation from TofApproachTracker.
 */
class IndependentTofRiskEngine(
    private val maxDistanceMm: Int = 3000,
    private val minClosingMmPerSecond: Double = 800.0,
    private val maxAgeMs: Long = 750
) {
    data class Risk(
        val sensor: Int,
        val distanceMm: Int,
        val closingMmPerSecond: Double,
        val ttcSeconds: Double,
        val observedAtMs: Long
    )
    fun evaluate(approaches: List<TofApproachTracker.Approach>, nowMs: Long): List<Risk> =
        approaches.asSequence().filter {
            it.sensor in 0..1 && it.zone in 0..63 &&
                it.distanceMm in 1..maxDistanceMm &&
                it.closingMmPerSecond.isFinite() &&
                it.closingMmPerSecond >= minClosingMmPerSecond &&
                nowMs >= it.observedAtMs && nowMs - it.observedAtMs <= maxAgeMs
        }.groupBy { it.sensor }.mapNotNull { (sensor, points) ->
            val nearest = points.minByOrNull { it.distanceMm } ?: return@mapNotNull null
            Risk(sensor, nearest.distanceMm, nearest.closingMmPerSecond,
                nearest.distanceMm / nearest.closingMmPerSecond, nearest.observedAtMs)
        }.sortedBy { it.ttcSeconds }.toList()
}
