package com.guideeye.app.audio

import kotlin.math.exp
import kotlin.math.roundToInt

/** Selects a sensor region, not a YOLO object. Time arguments use monotonic milliseconds. */
class DistanceToneState {
    enum class Side { LEFT, RIGHT }
    data class Output(val side: Side, val measuredMm: Int, val smoothedMm: Float,
                      val signal: DistanceBeepPolicy.Signal)
    private var side: Side? = null
    private var measured: Int? = null
    private var filtered: Float? = null
    private var sampleAt: Long? = null
    private var steppedAt: Long? = null

    fun reset() {
        side = null; measured = null; filtered = null; sampleAt = null; steppedAt = null
    }
    fun update(left: Int?, right: Int?, now: Long) {
        if (sampleAt?.let { now - it !in 0..DistanceBeepPolicy.MAX_AGE_MS } == true) reset()
        val l = left?.takeIf { it in 1..4000 }
        val r = right?.takeIf { it in 1..4000 }
        if (l == null && r == null) { reset(); return }
        // Keep the selected region until the other is at least 150mm closer.
        // Invalid selected regions switch immediately; a clearly closer region always wins.
        side = when {
            l == null -> Side.RIGHT
            r == null -> Side.LEFT
            side == Side.LEFT && r + 150 >= l -> Side.LEFT
            side == Side.RIGHT && l + 150 >= r -> Side.RIGHT
            l <= r -> Side.LEFT
            else -> Side.RIGHT
        }
        // Advance old target to this timestamp before applying the new target.
        advance(now)
        measured = if (side == Side.LEFT) l else r
        if (filtered == null) filtered = measured!!.toFloat()
        sampleAt = now; steppedAt = now
    }
    private fun advance(now: Long) {
        val old = filtered ?: return
        val target = measured?.toFloat() ?: return
        val elapsed = steppedAt?.let { (now - it).coerceAtLeast(0).toFloat() } ?: 0f
        // Follow approaching values faster; receding values relax more slowly.
        val tau = if (target < old) 80f else 350f
        filtered = old + (target - old) * (1f - exp(-elapsed / tau))
        steppedAt = now
    }
    fun output(now: Long): Output? {
        val age = sampleAt?.let { now - it } ?: return null
        if (age !in 0..DistanceBeepPolicy.MAX_AGE_MS) { reset(); return null }
        advance(now)
        val smooth = filtered ?: return null
        return Output(side ?: return null, measured ?: return null, smooth,
            DistanceBeepPolicy.signal(smooth.roundToInt(), null, 0) ?: return null)
    }
}

/** Preserve fractional progress as interval changes; delayed ticks never emit a burst. */
class BeepPhase {
    private var phase = 1.0
    private var lastAt: Long? = null
    private var emittedAt: Long? = null
    fun reset() { phase = 1.0; lastAt = null; emittedAt = null }
    fun due(now: Long, intervalMs: Long): Boolean {
        require(intervalMs > 0)
        val elapsed = lastAt?.let { (now - it).coerceAtLeast(0) } ?: 0
        lastAt = now
        phase += elapsed.toDouble() / intervalMs
        if (phase < 1.0 || emittedAt?.let { now - it < 180 } == true) return false
        phase %= 1.0
        emittedAt = now
        return true
    }
}
