package com.guideeye.app.audio

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class HrtfRendererTest {
    private fun bytes() = javaClass.getResourceAsStream("/kemar-horizontal.bin")!!.use { it.readBytes() }
    private fun bank() = HrirBank.parse(bytes())
    private fun energy(samples: FloatArray) = samples.sumOf { it.toDouble() * it }
    private fun arrival(samples: FloatArray): Int {
        val threshold = samples.maxOf { abs(it) } * .1f
        return samples.indexOfFirst { abs(it) > threshold }
    }
    @Test fun `actual bank contains complete measured horizontal directions and citation`() {
        val bank = bank()
        assertEquals(44100,bank.rate)
        assertEquals((0..315 step 45).toSet(),bank.filters.keys)
        assertTrue(bank.filters.values.all { it.size == 2 && it.all { ear -> ear.size == 512 } })
        val license = javaClass.getResourceAsStream("/MIT_KEMAR_LICENSE.txt")!!.bufferedReader().use { it.readText() }
        assertTrue(license.contains("provided the authors"))
    }
    @Test fun `measured left and right directions preserve arrival and energy cues`() {
        val left = bank().filters.getValue(90)
        assertTrue(arrival(left[0]) < arrival(left[1]))
        assertTrue(energy(left[0]) > energy(left[1]))
        val right = bank().filters.getValue(270)
        assertTrue(arrival(right[1]) < arrival(right[0]))
        assertTrue(energy(right[1]) > energy(right[0]))
        val front = bank().filters.getValue(0)
        val back = bank().filters.getValue(180)
        assertFalse(front[0].contentEquals(back[0]))
    }
    @Test fun `FIR convolution preserves delay channel order and full filter tail`() {
        assertArrayEquals(floatArrayOf(0f,1f,1f,2f,2f,0f),
            HrtfRenderer.convolve(floatArrayOf(1f,2f),floatArrayOf(0f,1f),floatArrayOf(1f,0f)),0f)
    }
    @Test fun `rendered actual stimuli are distinct stereo unclipped and preserve near ear energy`() {
        val bank = bank()
        val rendered = HrtfRenderer.render(bank)
        assertEquals(8,rendered.values.map { it.toList() }.toSet().size)
        assertTrue(rendered.values.all { it.size == (bank.rate*60/1000+511)*2 })
        assertTrue(rendered.values.all { pcm -> pcm.maxOf { abs(it.toInt()) } in 1..19661 })
        fun earEnergy(angle: Int,ear: Int): Double = rendered.getValue(angle).filterIndexed { i,_ -> i%2==ear }
            .sumOf { it.toDouble()*it }
        assertTrue(earEnergy(90,0) > earEnergy(90,1))
        assertTrue(earEnergy(270,1) > earEnergy(270,0))
        assertTrue(HrtfRenderer.noise(44100).contentEquals(HrtfRenderer.noise(44100)))
    }
    @Test fun `corrupted or truncated HRIR assets fail instead of silently becoming panning`() {
        val original = bytes()
        for (bad in listOf(original.copyOf(20),original.copyOf().also { it[0]=0 },
            original.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putFloat(24,Float.NaN) })) {
            try { HrirBank.parse(bad); fail("Invalid bank accepted") } catch (_: IllegalArgumentException) {}
        }
    }
}
