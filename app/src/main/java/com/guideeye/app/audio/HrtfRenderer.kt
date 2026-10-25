package com.guideeye.app.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** Measured, full HRIRs. SOFA positive azimuth points left, channels are L/R. */
class HrirBank private constructor(val rate: Int, val filters: Map<Int, Array<FloatArray>>) {
    companion object {
        fun parse(bytes: ByteArray): HrirBank {
            require(bytes.size >= 20)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(8); buffer.get(magic)
            require(String(magic, Charsets.US_ASCII) == "TEHRIR1\n")
            val rate = buffer.int; val count = buffer.int; val taps = buffer.int
            require(rate == 44100 && count == 8 && taps == 512)
            require(buffer.remaining() == count * (4 + 2 * taps * 4))
            val filters = linkedMapOf<Int, Array<FloatArray>>()
            repeat(count) {
                val angle = buffer.int
                require(angle in 0..315 && angle % 45 == 0 && !filters.containsKey(angle))
                filters[angle] = Array(2) { FloatArray(taps) { buffer.float.also { value -> require(value.isFinite()) } } }
                require(filters[angle]!!.all { ear -> ear.any { it != 0f } })
            }
            return HrirBank(rate, filters)
        }
    }
}

object HrtfRenderer {
    fun noise(rate: Int): FloatArray {
        val random = Random(0x5448495244L)
        val size = rate * 60 / 1000
        return FloatArray(size) { i ->
            val envelope = (.5 - .5 * cos(2 * PI * i / (size - 1))).toFloat()
            (random.nextFloat() * 2 - 1) * envelope
        }
    }
    fun convolve(source: FloatArray, left: FloatArray, right: FloatArray): FloatArray {
        require(source.isNotEmpty() && left.isNotEmpty() && left.size == right.size)
        val result = FloatArray((source.size + left.size - 1) * 2)
        for (i in source.indices) for (tap in left.indices) {
            result[2 * (i + tap)] += source[i] * left[tap]
            result[2 * (i + tap) + 1] += source[i] * right[tap]
        }
        return result
    }
    fun render(bank: HrirBank): Map<Int, ShortArray> {
        val source = noise(bank.rate)
        val floating = bank.filters.mapValues { (_, ears) -> convolve(source, ears[0], ears[1]) }
        // ONE scale for all directions and both ears preserves measured level differences.
        val peak = floating.values.maxOf { samples -> samples.maxOf { abs(it) } }
        require(peak.isFinite() && peak > 0)
        val scale = .6f * Short.MAX_VALUE / peak
        return floating.mapValues { (_, samples) -> ShortArray(samples.size) { i ->
            (samples[i] * scale).toInt().coerceIn(-32767,32767).toShort()
        } }
    }
}
