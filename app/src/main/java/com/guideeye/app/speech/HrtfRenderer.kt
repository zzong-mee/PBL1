package com.guideeye.app.speech

import kotlin.math.*

/** HRIR coefficients must come from a measured, delay-preserving 48 kHz dataset.
 * Coordinates: positive azimuth right, positive elevation up, zero forward.
 * No synthetic HRTF is substituted for missing measurements.
 */
class HrtfRenderer(private val responses: List<Response>) {
    data class Response(val azimuth: Double, val elevation: Double,
                        val left: FloatArray, val right: FloatArray)

    init {
        require(responses.isNotEmpty())
        require(responses.all { r ->
            r.azimuth in -180.0..180.0 && r.elevation in -90.0..90.0 &&
                r.left.size in 1..2048 && r.right.size == r.left.size &&
                r.left.all { it.isFinite() } && r.right.all { it.isFinite() } &&
                r.left.any { it != 0f } && r.right.any { it != 0f }
        })
    }

    private fun nearest(azimuth: Double, elevation: Double): Response {
        val a = Math.toRadians(azimuth)
        val e = Math.toRadians(elevation)
        return responses.maxByOrNull { r ->
            val ra = Math.toRadians(r.azimuth)
            val re = Math.toRadians(r.elevation)
            sin(e)*sin(re)+cos(e)*cos(re)*cos(a-ra)
        }!!
    }

    /** Offline convolution for one short alert; retains both filter tails. */
    fun render(danger: Boolean, azimuth: Double, elevation: Double): ShortArray {
        require(azimuth.isFinite() && elevation in -90.0..90.0)
        val h = nearest(azimuth, elevation)
        val rate = StereoWarningAudio.SAMPLE_RATE
        val frames = rate * StereoWarningAudio.durationMs(danger) / 1000
        val fade = rate / 200
        val mono = FloatArray(frames) { i ->
            val t = i.toDouble()/rate
            val env = minOf(1.0, i.toDouble()/fade, (frames-1-i).toDouble()/fade)
            (0.35*env*(sin(2*PI*852*t)+sin(2*PI*1477*t))/2).toFloat()
        }
        val output = FloatArray((frames+h.left.size-1)*2)
        for (i in mono.indices) {
            val v = mono[i]
            for (k in h.left.indices) {
                output[2*(i+k)] += v*h.left[k]
                output[2*(i+k)+1] += v*h.right[k]
            }
        }
        // Shared peak limiting preserves interaural level differences.
        val peak = output.maxOf { abs(it) }
        val gain = if (peak > .9f) .9f/peak else 1f
        return ShortArray(output.size) { i ->
            (output[i]*gain*32767).roundToInt().coerceIn(-32768,32767).toShort()
        }
    }
}
