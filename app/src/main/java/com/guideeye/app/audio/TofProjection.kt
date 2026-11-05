package com.guideeye.app.audio

import com.guideeye.app.usb.TofGrid
import kotlin.math.*

/** Calibration must be supplied explicitly. No A/B-to-fixed-angle substitution. */
data class TofMount(val yawDegrees: Double, val pitchDegrees: Double,
    val horizontalFovDegrees: Double, val verticalFovDegrees: Double,
    val quarterTurns: Int, val mirrorColumns: Boolean) {
    init {
        require(yawDegrees.isFinite() && yawDegrees in -180.0..180.0)
        require(pitchDegrees.isFinite() && pitchDegrees in -90.0..90.0)
        require(horizontalFovDegrees in 1.0..120.0 && verticalFovDegrees in 1.0..120.0)
        require(quarterTurns in 0..3)
    }
}
data class TofPoint(val zone: Int, val rangeMm: Int, val azimuthDegrees: Double,
                    val elevationDegrees: Double)
object TofProjection {
    fun project(grid: TofGrid, mount: TofMount, elapsedSinceReceiptMs: Long): List<TofPoint> {
        require(grid.mm.size==64 && grid.status.size==64)
        if (elapsedSinceReceiptMs<0 || grid.ageAtReceiptMs !in 0..1000 ||
            elapsedSinceReceiptMs>1000-grid.ageAtReceiptMs) return emptyList()
        val yaw=Math.toRadians(mount.yawDegrees); val pitch=Math.toRadians(mount.pitchDegrees)
        return grid.mm.mapIndexedNotNull { index, distance ->
            if (distance==null || distance !in 1..4000 || grid.status[index] !in listOf(5,6,9)) return@mapIndexedNotNull null
            var row=index/8; var col=index%8
            repeat(mount.quarterTurns) { val old=row; row=col; col=7-old }
            if (mount.mirrorColumns) col=7-col
            // Rectilinear nominal-FoV model, zone center ray; not calibrated optical intrinsics.
            val y=(1.0-2.0*(col+.5)/8)*tan(Math.toRadians(mount.horizontalFovDegrees/2))
            val z=(1.0-2.0*(row+.5)/8)*tan(Math.toRadians(mount.verticalFovDegrees/2))
            val xp=cos(pitch)-z*sin(pitch)
            val zp=sin(pitch)+z*cos(pitch)
            val x=xp*cos(yaw)-y*sin(yaw)
            val rotatedY=xp*sin(yaw)+y*cos(yaw)
            TofPoint(index,distance,Math.toDegrees(atan2(rotatedY,x)),
                Math.toDegrees(atan2(zp,sqrt(x*x+rotatedY*rotatedY))))
        }
    }
}
