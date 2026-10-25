package com.guideeye.app.audio

/** Nearest valid USB distance; no direction or collision-risk claim. */
object DistanceBeepPolicy {
    const val MAX_AGE_MS = 1500L
    data class Signal(val distanceMm: Int, val intervalMs: Long, val gain: Float)

    fun signal(leftMm: Int?, rightMm: Int?, ageMs: Long): Signal? {
        if (ageMs !in 0..MAX_AGE_MS) return null
        val distance = listOfNotNull(leftMm, rightMm).filter { it > 0 }.minOrNull() ?: return null
        if (distance > 4000) return null
        val proportion = ((distance - 200) / 3800f).coerceIn(0f, 1f)
        return Signal(distance, (180 + 1020 * proportion).toLong(), .55f - .43f * proportion)
    }
}
