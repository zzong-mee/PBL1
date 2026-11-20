package com.guideeye.app.fusion

import kotlin.math.max

/**
 * Constant-velocity Kalman filter for an object's measured range (mm).
 * State: [rangeMm, radialVelocityMmPerSecond]; negative velocity means closing.
 * Feed only calibrated, time-aligned ToF measurements associated with ONE object.
 * Not a camera-motion compensation or object association algorithm.
 */
class ObjectRangeKalmanFilter(
    private val measurementVarianceMm2: Double = 40000.0,
    private val accelerationVarianceMm2PerSecond4: Double = 250000.0,
    private val maxGapMs: Long = 1500
) {
    data class Estimate(
        val distanceMm: Double,
        val radialVelocityMmPerSecond: Double,
        val observedAtMs: Long
    ) {
        val closingMmPerSecond: Double get() = max(0.0, -radialVelocityMmPerSecond)
        val ttcSeconds: Double?
            get() = if (closingMmPerSecond > 1.0) distanceMm / closingMmPerSecond else null
    }

    private var timeMs: Long? = null
    private var distance = 0.0
    private var velocity = 0.0
    private var p00 = 0.0
    private var p01 = 0.0
    private var p11 = 0.0

    init {
        require(measurementVarianceMm2 > 0 && accelerationVarianceMm2PerSecond4 >= 0)
        require(maxGapMs > 0)
    }

    fun reset() {
        timeMs = null
        distance = 0.0
        velocity = 0.0
        p00 = 0.0
        p01 = 0.0
        p11 = 0.0
    }

    fun update(measuredDistanceMm: Int, observedAtMs: Long): Estimate? {
        if (measuredDistanceMm !in 1..4000 || observedAtMs < 0) return null
        val previous = timeMs
        if (previous == null || observedAtMs - previous > maxGapMs) {
            distance = measuredDistanceMm.toDouble()
            velocity = 0.0
            p00 = measurementVarianceMm2
            p01 = 0.0
            p11 = 1000000.0
            timeMs = observedAtMs
            return estimate(observedAtMs)
        }
        if (observedAtMs <= previous) return null

        val dt = (observedAtMs - previous) / 1000.0
        val dt2 = dt * dt
        val dt3 = dt2 * dt
        val dt4 = dt2 * dt2
        val q = accelerationVarianceMm2PerSecond4

        distance += velocity * dt
        val prior00 = p00 + 2 * dt * p01 + dt2 * p11 + q * dt4 / 4
        val prior01 = p01 + dt * p11 + q * dt3 / 2
        val prior11 = p11 + q * dt2
        val innovation = measuredDistanceMm - distance
        val s = prior00 + measurementVarianceMm2
        val k0 = prior00 / s
        val k1 = prior01 / s

        distance += k0 * innovation
        velocity += k1 * innovation
        p00 = (1 - k0) * prior00
        p01 = (1 - k0) * prior01
        p11 = prior11 - k1 * prior01
        timeMs = observedAtMs
        return estimate(observedAtMs)
    }

    private fun estimate(atMs: Long) =
        Estimate(max(0.0, distance), velocity, atMs)
}
