package com.guideeye.app.fusion

import com.guideeye.app.usb.TofGrid
import com.guideeye.app.usb.TofGridFrame

/**
 * Tracks per-zone relative closing speed, independently for the two 8x8 sensors.
 * All thresholds are experimental and require physical calibration.
 */
class TofApproachTracker(
    private val maxDistanceMm: Int = 3000,
    private val minClosingMmPerSecond: Double = 800.0
) {
    data class Approach(val sensor: Int, val zone: Int, val distanceMm: Int,
                        val closingMmPerSecond: Double, val observedAtMs: Long)
    private var previous: TofGridFrame? = null
    private var previousCandidates = emptySet<Pair<Int,Int>>()

    fun reset() { previous = null; previousCandidates = emptySet() }

    fun update(frame: TofGridFrame?): List<Approach> {
        if (frame == null) { reset(); return emptyList() }
        val prior = previous
        previous = frame
        if (prior == null) return emptyList()
        val deltaMs = frame.receivedAt - prior.receivedAt
        if (deltaMs !in 80..1000) {
            previousCandidates = emptySet()
            return emptyList()
        }
        val candidates = mutableListOf<Approach>()
        fun collect(sensor: Int, old: TofGrid, new: TofGrid) {
            if (old.mm.size != 64 || new.mm.size != 64 ||
                old.status.size != 64 || new.status.size != 64 ||
                old.ageAtReceiptMs !in 0..1000 || new.ageAtReceiptMs !in 0..1000) return
            for (zone in 0 until 64) {
                val before = old.mm[zone] ?: continue
                val after = new.mm[zone] ?: continue
                if (old.status[zone] !in listOf(5,6,9) ||
                    new.status[zone] !in listOf(5,6,9) ||
                    before !in 1..4000 || after !in 1..maxDistanceMm) continue
                val actualDeltaMs = deltaMs + new.ageAtReceiptMs - old.ageAtReceiptMs
                if (actualDeltaMs !in 80..1000) continue
                val speed = (before-after)*1000.0/actualDeltaMs
                if (speed >= minClosingMmPerSecond) {
                    candidates.add(Approach(sensor,zone,after,speed,frame.receivedAt))
                }
            }
        }
        collect(0, prior.left, frame.left)
        collect(1, prior.right, frame.right)
        // At least two adjacent cells must agree, and the region must persist
        // across two consecutive speed estimates (three sensor frames).
        val candidateKeys = candidates.map { it.sensor to it.zone }.toSet()
        val confirmed = candidates.filter { point ->
            val row = point.zone / 8
            val col = point.zone % 8
            candidates.any { other ->
                other !== point && other.sensor == point.sensor &&
                    kotlin.math.abs(other.zone/8-row) <= 1 &&
                    kotlin.math.abs(other.zone%8-col) <= 1
            } && previousCandidates.any { (sensor, zone) ->
                sensor == point.sensor && kotlin.math.abs(zone/8-row) <= 1 &&
                    kotlin.math.abs(zone%8-col) <= 1
            }
        }
        previousCandidates = candidateKeys
        return confirmed
    }
}
