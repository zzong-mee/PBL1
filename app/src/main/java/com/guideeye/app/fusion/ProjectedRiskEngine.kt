package com.guideeye.app.fusion

/** Experimental proximity/closing risk, not a collision-path predictor.
 * Never suppress independent ToF warnings using these results.
 */
object ProjectedRiskEngine {
    enum class Level { CAUTION, DANGER }
    data class Risk(val estimate: ProjectedRangeTracker.Estimate,
        val mobility: ObjectMobility.Type, val level: Level,
        val conservativeDistanceMm: Double, val reason: String)

    fun evaluate(estimates: List<ProjectedRangeTracker.Estimate>, nowMs: Long): List<Risk> =
        estimates.mapNotNull { e ->
            val m = e.measurement
            if (m.observedAtMs !in 0..nowMs || nowMs-m.observedAtMs > 750 ||
                m.distanceMm !in 1.0..4000.0 || !e.filteredDistanceMm.isFinite() ||
                e.filteredDistanceMm < 0) return@mapNotNull null
            val distance = minOf(m.distanceMm,e.filteredDistanceMm)
            val mobility = ObjectMobility.classify(m.classId)
            val closing = e.closingMmPerSecond?.takeIf { e.samples >= 3 && it.isFinite() && it >= 0 }
            val ttc = e.ttcSeconds?.takeIf { closing != null && it.isFinite() && it >= 0 }
            val reason = when {
                distance <= 1000 -> "NEAR"
                closing != null && closing >= 800 -> "CLOSING"
                ttc != null && ttc <= 3.0 -> "SHORT_TTC"
                else -> null
            }
            Risk(e,mobility,if(reason != null) Level.DANGER else Level.CAUTION,
                distance,reason ?: if(closing == null) "MOTION_UNKNOWN" else "PROXIMITY")
        }.sortedWith(compareByDescending<Risk> { it.level == Level.DANGER }
            .thenBy { it.estimate.ttcSeconds?.takeIf { t -> t.isFinite() && t >= 0 } ?: Double.POSITIVE_INFINITY }
            .thenBy { it.conservativeDistanceMm }
            .thenBy { if(it.mobility == ObjectMobility.Type.TYPE_2) 1 else 0 }
            .thenBy { it.estimate.measurement.trackId })
}
