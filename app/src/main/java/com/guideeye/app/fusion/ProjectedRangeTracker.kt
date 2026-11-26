package com.guideeye.app.fusion

import kotlin.math.roundToInt

/** Diagnostic radial range tracker. TTC is not a predicted collision path. */
class ProjectedRangeTracker {
    data class Estimate(val measurement: ProjectedObjectDistance.Measurement,
        val filteredDistanceMm: Double, val closingMmPerSecond: Double?,
        val ttcSeconds: Double?, val samples: Int)
    private data class State(val sensor: Int, val classId: Int,
        val filter: ObjectRangeKalmanFilter = ObjectRangeKalmanFilter(),
        var lastAt: Long = -1, var samples: Int = 0)
    private val states = mutableMapOf<Long, State>()
    private val lastSensorAt = longArrayOf(-1,-1)
    private var lastVisionAt = -1L

    fun reset() {
        states.clear()
        lastSensorAt.fill(-1)
        lastVisionAt = -1
    }

    fun update(measurements: List<ProjectedObjectDistance.Measurement>,
               activeIds: Set<Long>, visionAtMs: Long, nowMs: Long): List<Estimate> {
        if (visionAtMs !in 0..nowMs || visionAtMs <= lastVisionAt ||
            nowMs-visionAtMs > 750) return emptyList()
        lastVisionAt = visionAtMs
        states.entries.removeAll { (id,s) -> id !in activeIds || nowMs-s.lastAt > 1500 }
        val fresh = measurements.filter {
            it.trackId in activeIds && it.sensor in 0..1 &&
                it.distanceMm in 1.0..4000.0 && it.observedAtMs in 0..nowMs &&
                nowMs-it.observedAtMs <= 750 &&
                kotlin.math.abs(it.observedAtMs-visionAtMs)<=250 &&
                it.observedAtMs > lastSensorAt[it.sensor]
        }.groupBy { it.sensor }.values.flatMap { group ->
            val newest = group.maxOf { it.observedAtMs }
            group.filter { it.observedAtMs == newest }
        }
        val output = mutableListOf<Estimate>()
        for (m in fresh.distinctBy { it.trackId }) {
            val previous = states[m.trackId]
            if (previous != null && m.observedAtMs <= previous.lastAt) continue
            val state = if (previous == null || previous.sensor != m.sensor ||
                previous.classId != m.classId || m.observedAtMs-previous.lastAt > 1000)
                State(m.sensor,m.classId) else previous
            if (state.lastAt >= 0 && m.observedAtMs-state.lastAt < 80) continue
            val estimate = state.filter.update(m.distanceMm.roundToInt(),m.observedAtMs) ?: continue
            state.lastAt = m.observedAtMs
            state.samples++
            states[m.trackId] = state
            val closing = estimate.closingMmPerSecond.takeIf { state.samples >= 3 }
            val ttc = if (closing != null && closing >= 100 && estimate.distanceMm > 0)
                estimate.distanceMm / closing else null
            output.add(Estimate(m,estimate.distanceMm,closing,ttc,state.samples))
        }
        for (m in fresh) lastSensorAt[m.sensor] = maxOf(lastSensorAt[m.sensor],m.observedAtMs)
        return output.sortedBy { it.measurement.trackId }
    }
}
