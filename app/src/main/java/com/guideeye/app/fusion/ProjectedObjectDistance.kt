package com.guideeye.app.fusion

import kotlin.math.abs

/** Experimental foreground range selection; rejected cells remain available to independent ToF warnings. */
object ProjectedObjectDistance {
    data class Measurement(val trackId: Long, val classId: Int, val sensor: Int,
        val distanceMm: Double, val observedAtMs: Long, val supportingZones: List<Int>,
        val rejectedCount: Int)

    fun estimate(matches: List<ProjectedCellAssociator.CellMatch>): List<Measurement> =
        matches.filter { it.distanceMm in 1..4000 && it.zone in 0..63 &&
            it.sensor in 0..1 && it.observedAtMs >= 0 && it.overlap in .5..1.000001 }
            .groupBy { it.trackId }.values.mapNotNull { track ->
                if (track.map { it.classId }.distinct().size != 1) return@mapNotNull null
                val candidates = track.groupBy { it.sensor to it.observedAtMs }.values.mapNotNull { group ->
                    val cells = group.distinctBy { it.zone }.sortedBy { it.distanceMm }
                    // A bounded depth window avoids chaining foreground to background.
                    val supported = cells.indices.firstNotNullOfOrNull { start ->
                        val window = cells.drop(start).takeWhile {
                            it.distanceMm - cells[start].distanceMm <= 300
                        }
                        val connected = window.filter { a -> window.any { b ->
                            a.zone != b.zone && abs(a.zone/8-b.zone/8)<=1 &&
                                abs(a.zone%8-b.zone%8)<=1
                        } }
                        if (connected.size >= 2) connected else null
                    } ?: return@mapNotNull null
                    val sorted = supported.map { it.distanceMm }.sorted()
                    val median = (sorted[(sorted.size-1)/2]+sorted[sorted.size/2])/2.0
                    val first = supported.first()
                    Measurement(first.trackId, first.classId, first.sensor, median,
                        first.observedAtMs, supported.map { it.zone }.sorted(),
                        track.distinctBy { it.sensor to it.zone }.size-supported.size)
                }
                // Do not average asynchronous A/B samples. Select nearest supported range.
                candidates.minWithOrNull(compareBy<Measurement> { it.distanceMm }
                    .thenByDescending { it.supportingZones.size }.thenByDescending { it.observedAtMs })
            }.sortedBy { it.trackId }
}
