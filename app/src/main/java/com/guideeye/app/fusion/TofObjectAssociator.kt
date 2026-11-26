package com.guideeye.app.fusion

import com.guideeye.app.usb.TofGrid
import com.guideeye.app.usb.TofGridFrame
import com.guideeye.app.vision.TwoStageObjectTracker
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Conservative, calibration-gated ToF clustering and camera association.
 * Column projection MUST be measured on the mounted glasses; no guessed
 * sensor-to-camera mapping is used for object-specific distances.
 */
class TofObjectAssociator(
    private val calibration: Calibration?,
    private val maxSkewMs: Long = 250,
    private val maxFrameAgeMs: Long = 500,
    private val neighborDepthMm: Int = 450
) {
    /** Per-sensor column centres, in normalized camera image X coordinates. */
    data class Calibration(val leftColumnsX: List<Float>, val rightColumnsX: List<Float>) {
        init {
            require(leftColumnsX.size == 8 && rightColumnsX.size == 8)
            require((leftColumnsX + rightColumnsX).all { it.isFinite() && it in 0f..1f })
        }
        fun x(sensor: Int, col: Int) =
            if (sensor == 0) leftColumnsX[col] else rightColumnsX[col]
    }

    data class Cluster(
        val sensor: Int,
        val zones: List<Int>,
        val distanceMm: Int,
        val centerX: Float?
    )

    data class Match(
        val trackId: Long,
        val classId: Int,
        val distanceMm: Int,
        val observedAtMs: Long,
        val cluster: Cluster
    )

    fun clusters(frame: TofGridFrame, nowMs: Long): List<Cluster> {
        if (nowMs < frame.receivedAt || nowMs - frame.receivedAt > maxFrameAgeMs) return emptyList()
        return listOf(frame.left, frame.right).flatMapIndexed { sensor, grid ->
            clusterGrid(grid, sensor)
        }
    }

    private fun clusterGrid(grid: TofGrid, sensor: Int): List<Cluster> {
        if (grid.mm.size != 64 || grid.status.size != 64 || grid.ageAtReceiptMs !in 0..1000) return emptyList()
        val visited = BooleanArray(64)
        val result = mutableListOf<Cluster>()
        for (start in 0 until 64) {
            if (visited[start] || !valid(grid, start)) continue
            val queue = ArrayDeque<Int>()
            val zones = mutableListOf<Int>()
            queue.add(start)
            visited[start] = true
            while (queue.isNotEmpty()) {
                val zone = queue.removeFirst()
                zones.add(zone)
                val row = zone / 8
                val col = zone % 8
                for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    val r = row + dr
                    val c = col + dc
                    if (r !in 0..7 || c !in 0..7) continue
                    val other = r * 8 + c
                    if (visited[other] || !valid(grid, other)) continue
                    if (abs(grid.mm[zone]!! - grid.mm[other]!!) > neighborDepthMm) continue
                    visited[other] = true
                    queue.add(other)
                }
            }
            val distances = zones.map { grid.mm[it]!! }.sorted()
            val median = distances[distances.size / 2]
            val x = calibration?.let { c -> zones.map { c.x(sensor, it % 8) }.average().toFloat() }
            result.add(Cluster(sensor, zones.sorted(), median, x))
        }
        return result
    }

    private fun valid(grid: TofGrid, index: Int) =
        grid.mm[index] in 1..4000 && grid.status[index] in listOf(5, 6, 9)

    fun associate(
        tracks: List<TwoStageObjectTracker.TrackedDetection>,
        frame: TofGridFrame?,
        visionAtMs: Long,
        nowMs: Long
    ): List<Match> {
        if (calibration == null || frame == null || visionAtMs > nowMs ||
            nowMs - visionAtMs > maxFrameAgeMs ||
            abs(frame.receivedAt - visionAtMs) > maxSkewMs) return emptyList()

        val clusters = clusters(frame, nowMs).filter { it.centerX != null && it.zones.size >= 2 }
        val candidates = mutableListOf<Triple<Double, TwoStageObjectTracker.TrackedDetection, Cluster>>()
        for (track in tracks) {
            val d = track.detection
            if (d.left !in 0f..1f || d.right !in 0f..1f || d.left >= d.right) continue
            for (cluster in clusters) {
                val x = cluster.centerX!!
                // A point inside the bounding box alone is not sufficient:
                // require proximity to the box centre to avoid ambiguous matches.
                val center = (d.left + d.right) / 2f
                val tolerance = min(0.12f, max(0.04f, (d.right - d.left) * 0.35f))
                val error = abs(x - center)
                if (x in d.left..d.right && error <= tolerance)
                    candidates.add(Triple(error.toDouble(), track, cluster))
            }
        }
        val usedTracks = mutableSetOf<Long>()
        val usedClusters = mutableSetOf<Cluster>()
        val result = mutableListOf<Match>()
        for ((_, track, cluster) in candidates.sortedBy { it.first }) {
            if (!usedTracks.add(track.trackId)) continue
            if (!usedClusters.add(cluster)) { usedTracks.remove(track.trackId); continue }
            result.add(Match(track.trackId, track.detection.classId,
                cluster.distanceMm, frame.receivedAt, cluster))
        }
        return result
    }
}
