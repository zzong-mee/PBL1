package com.guideeye.app.data

/** Distances in metres; absent hardware readings must never be represented as zero. */
data class DistanceReading(val leftMeters: Float, val rightMeters: Float)

/** Replace with a transport-backed source when the sensor protocol is decided. */
interface DistanceSource {
    fun start(onReading: (DistanceReading) -> Unit)
    fun stop()
}
