package com.guideeye.app.fusion

/** Mechanical measurements, not an optically validated calibration.
 * Camera origin: X wearer-right, Y up, Z forward; yaw positive right.
 * Physical left/right must be verified against USB A/B before association.
 */
object MeasuredMount {
    data class Extrinsics(val xMm: Double, val yMm: Double, val zMm: Double,
                          val yawDegrees: Double, val pitchDegrees: Double)
    val left = Extrinsics(-10.0, 20.0, -0.5, -27.5, 0.0)
    val right = Extrinsics(10.0, 20.0, -0.5, 27.5, 0.0)
    const val opticallyValidated = false
}
