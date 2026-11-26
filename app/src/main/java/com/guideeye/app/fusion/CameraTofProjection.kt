package com.guideeye.app.fusion

import kotlin.math.*

/** Provisional geometry for the corrected 2:1 camera image, not optical calibration. */
object CameraTofProjection {
    data class Point(val x: Float, val y: Float)
    fun orientedZone(index: Int, turns: Int, mirror: Boolean): Pair<Int, Int> {
        require(index in 0..63 && turns in 0..3)
        var row = index / 8
        var col = index % 8
        repeat(turns) { val old = row; row = col; col = 7 - old }
        if (mirror) col = 7 - col
        return row to col
    }
    fun project(sensor: Int, swap: Boolean, row: Double, col: Double,
                distanceMm: Double, aspect: Double = 2.0): Point? {
        require(sensor in 0..1)
        if (!distanceMm.isFinite() || distanceMm !in 1.0..4000.0 ||
            !aspect.isFinite() || aspect <= 0) return null
        val m = if ((sensor == 0) != swap) MeasuredMount.left else MeasuredMount.right
        val yaw = Math.toRadians(m.yawDegrees)
        val t = tan(Math.toRadians(22.5))
        val x = (2*col/8-1)*t
        val y = (1-2*row/8)*t
        val k = distanceMm / sqrt(x*x+y*y+1)
        val px = k*(x*cos(yaw)+sin(yaw))+m.xMm
        val py = k*y+m.yMm
        val pz = k*(cos(yaw)-x*sin(yaw))+m.zMm
        if (pz <= 0) return null
        val focal = 1.0/(2*tan(Math.toRadians(30.0)))
        return Point((.5+focal*px/pz).toFloat(),(.5-aspect*focal*py/pz).toFloat())
    }
}
