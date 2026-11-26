package com.guideeye.app.fusion

import com.guideeye.app.vision.TwoStageObjectTracker
import kotlin.math.*

/** Experimental image-space risk heuristic. SAFE means low estimated risk, not verified safety. */
class ProjectedRiskEngine {
    enum class Level { SAFE, CAUTION, DANGER }
    data class Risk(val trackId: Long, val classId: Int,
        val estimate: ProjectedRangeTracker.Estimate?, val mobility: ObjectMobility.Type,
        val level: Level, val conservativeDistanceMm: Double?, val reason: String,
        val visualTtcSeconds: Double?, val expansionPerSecond: Double?,
        val pathOverlap: Double, val predictedPathOverlap: Double, val score: Int)
    private data class History(val x: Double, val scale: Double, val at: Long, val classId: Int)
    private val history = mutableMapOf<Long, History>()
    private val visual = VisualExpansionTtcEstimator()
    private var lastAt = -1L

    fun reset() { history.clear(); visual.reset(); lastAt = -1L }

    fun evaluate(tracks: List<TwoStageObjectTracker.TrackedDetection>,
                 estimates: List<ProjectedRangeTracker.Estimate>,
                 visionAtMs: Long, nowMs: Long): List<Risk> {
        if (visionAtMs !in 0..nowMs || nowMs-visionAtMs > 750 || visionAtMs <= lastAt)
            return emptyList()
        lastAt = visionAtMs
        history.keys.retainAll(tracks.map { it.trackId }.toSet())
        val visualTtc = visual.update(tracks,visionAtMs).associateBy { it.trackId }
        val ranges = estimates.filter {
            val m = it.measurement
            m.observedAtMs in 0..nowMs && nowMs-m.observedAtMs <= 750 &&
                abs(m.observedAtMs-visionAtMs)<=250 && m.distanceMm in 1.0..4000.0 &&
                it.filteredDistanceMm.isFinite() && it.filteredDistanceMm >= 0
        }.associateBy { it.measurement.trackId }
        return tracks.mapNotNull { track ->
            val d = track.detection
            if (d.left !in 0f..1f || d.right !in 0f..1f || d.top !in 0f..1f ||
                d.bottom !in 0f..1f || d.right<=d.left || d.bottom<=d.top) {
                history.remove(track.trackId)
                return@mapNotNull null
            }
            val width = (d.right-d.left).toDouble()
            val scale = sqrt(width*(d.bottom-d.top))
            val x = (d.left+d.right)/2.0
            val old = history[track.trackId]?.takeIf {
                it.classId==d.classId && visionAtMs-it.at in 80..700
            }
            history[track.trackId] = History(x,scale,visionAtMs,d.classId)
            val dt = old?.let { (visionAtMs-it.at)/1000.0 }
            val expansion = if(old!=null && dt!=null) (scale/old.scale-1)/dt else null
            val vx = if(old!=null && dt!=null) (x-old.x)/dt else 0.0
            // Central image corridor proxy, not a calibrated physical walking path.
            fun overlap(center: Double): Double =
                (min(center+width/2,.65)-max(center-width/2,.35)).coerceAtLeast(0.0)/width
            val path = overlap(x)
            val predicted = (0..4).maxOf { overlap(x+vx*it/4.0) }
            val e = ranges[track.trackId]?.takeIf { it.measurement.classId==d.classId }
            val distance = e?.let { min(it.measurement.distanceMm,it.filteredDistanceMm) }
            val mobility = ObjectMobility.classify(d.classId)
            val vt = visualTtc[track.trackId]?.ttcSeconds
            val closing = e?.closingMmPerSecond?.takeIf { e.samples>=3 && it.isFinite() && it>=0 }
            val mt = e?.ttcSeconds?.takeIf { closing!=null && it.isFinite() && it>=0 }
            val ttc = listOfNotNull(vt,mt).minOrNull()
            var score = if(mobility==ObjectMobility.Type.TYPE_2) 0 else 1
            if(predicted>=.25) score+=2
            if(scale>=.25) score++
            if(expansion!=null && expansion>=.08) score++
            if(ttc!=null && ttc<=6) score+=2
            if(ttc!=null && ttc<=3) score+=2
            if(distance!=null && distance<=2000) score++
            if(closing!=null && closing>=800) score+=2
            val immediate = distance!=null && mobility!=ObjectMobility.Type.TYPE_2
            val level = when {
                immediate -> Level.DANGER
                predicted>=.25 && (score>=6 || (distance!=null && distance<=1000) ||
                    (ttc!=null && ttc<=3)) -> Level.DANGER
                score>=2 || old==null || distance==null -> Level.CAUTION
                else -> Level.SAFE
            }
            Risk(track.trackId,d.classId,e,mobility,level,distance,
                if(immediate) "NEAR_MOBILE_OR_UNKNOWN" else "COMPOSITE",
                vt,expansion,path,predicted,score)
        }.sortedWith(compareByDescending<Risk> { it.level.ordinal }
            .thenByDescending { it.score }
            .thenBy { listOfNotNull(it.visualTtcSeconds,it.estimate?.ttcSeconds).minOrNull() ?: Double.POSITIVE_INFINITY }
            .thenBy { it.conservativeDistanceMm ?: Double.POSITIVE_INFINITY }
            .thenBy { it.trackId })
    }
}
