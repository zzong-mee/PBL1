package com.guideeye.app.fusion

import kotlin.math.*

/** Camera-origin approximation to head-relative direction; not optical calibration. */
object ObjectDirection {
    data class Direction(val azimuthDegrees: Double, val elevationDegrees: Double)

    private fun fromVector(x: Double, y: Double, z: Double): Direction? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || z <= 0) return null
        return Direction(Math.toDegrees(atan2(x,z)), Math.toDegrees(atan2(y,hypot(x,z))))
    }

    /** Normalized coordinates in the corrected 2:1 camera image. */
    fun fromCamera(centerX: Double, centerY: Double): Direction? {
        if (centerX !in 0.0..1.0 || centerY !in 0.0..1.0) return null
        val t = tan(Math.toRadians(30.0))
        return fromVector((2*centerX-1)*t, (1-2*centerY)*t/2, 1.0)
    }

    /** Raw ToF cluster cells; use the same orientation and mount geometry as projection. */
    fun fromTof(sensor: Int, zones: Set<Int>, distanceMm: Double,
                settings: TofOrientationSettings): Direction? {
        if (sensor !in 0..1 || zones.isEmpty() || zones.any { it !in 0..63 } ||
            distanceMm !in 1.0..4000.0) return null
        val (turns, mirror) = settings.orientations[sensor]
        if (turns !in 0..3) return null
        val mount = if ((sensor == 0) != settings.swap) MeasuredMount.left else MeasuredMount.right
        val yaw = Math.toRadians(mount.yawDegrees)
        val t = tan(Math.toRadians(22.5))
        val cells = zones.map { CameraTofProjection.orientedZone(it, turns, mirror) }
        val col = cells.map { it.second + .5 }.average()
        val row = cells.map { it.first + .5 }.average()
        val x = (2*col/8-1)*t
        val y = (1-2*row/8)*t
        val k = distanceMm / sqrt(x*x+y*y+1)
        return fromVector(k*(x*cos(yaw)+sin(yaw))+mount.xMm,
            k*y+mount.yMm, k*(cos(yaw)-x*sin(yaw))+mount.zMm)
    }
}
