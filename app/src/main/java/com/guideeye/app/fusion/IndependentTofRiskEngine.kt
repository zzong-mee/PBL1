package com.guideeye.app.fusion

/**
 * Calibration-free, sensor-local warning candidates. Direction and camera object
 * identity are intentionally unknown until mounting orientation is calibrated.
 * Requires spatial and temporal confirmation from TofApproachTracker.
 */
class IndependentTofRiskEngine(
    private val maxDistanceMm: Int = 3000,
    private val minClosingMmPerSecond: Double = 800.0,
    private val maxAgeMs: Long = 750,
    private val maxDepthGapMm: Int = 450
) {
    enum class Level { CAUTION, DANGER }
    data class Risk(
        val sensor: Int,
        val distanceMm: Int,
        val closingMmPerSecond: Double,
        val ttcSeconds: Double,
        val observedAtMs: Long,
        val level: Level = Level.CAUTION
    )
    fun evaluate(approaches: List<TofApproachTracker.Approach>, nowMs: Long): List<Risk> =
        approaches.asSequence().filter {
            it.sensor in 0..1 && it.zone in 0..63 &&
                it.distanceMm in 1..maxDistanceMm &&
                it.closingMmPerSecond.isFinite() &&
                it.closingMmPerSecond >= minClosingMmPerSecond &&
                nowMs >= it.observedAtMs && nowMs - it.observedAtMs <= maxAgeMs
        }.groupBy { it.sensor to it.observedAtMs }.values
            .flatMap { cluster(it) }.mapNotNull { points ->
            val sensor = points.first().sensor
            val nearest = points.minByOrNull { it.distanceMm } ?: return@mapNotNull null
            // Median speed across confirmed neighbouring cells suppresses one-cell spikes.
            val speeds = points.map { it.closingMmPerSecond }.sorted()
            val speed = speeds[speeds.size / 2]
            val ttc = nearest.distanceMm / speed
            val level = if (nearest.distanceMm <= 1500 && ttc <= 2.0) Level.DANGER else Level.CAUTION
            Risk(sensor, nearest.distanceMm, speed, ttc, nearest.observedAtMs, level)
        }.sortedBy { it.ttcSeconds }.toList()
    // Sensor-local connected components; no camera identity or persistent IDs.
    private fun cluster(points: List<TofApproachTracker.Approach>): List<List<TofApproachTracker.Approach>> {
        val remaining = points.toMutableList()
        val result = mutableListOf<List<TofApproachTracker.Approach>>()
        while (remaining.isNotEmpty()) {
            val component = mutableListOf(remaining.removeAt(0))
            var index = 0
            while (index < component.size) {
                val current = component[index++]
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    val adjacent = kotlin.math.abs(current.zone / 8 - next.zone / 8) <= 1 &&
                        kotlin.math.abs(current.zone % 8 - next.zone % 8) <= 1
                    if (adjacent && kotlin.math.abs(current.distanceMm - next.distanceMm) <= maxDepthGapMm) {
                        component.add(next)
                        iterator.remove()
                    }
                }
            }
            result.add(component)
        }
        return result
    }
}
