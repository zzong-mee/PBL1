package com.guideeye.app.usb

import org.junit.Assert.*
import org.junit.Test

class DistanceLineDecoderTest {
    private val packet = "{\"left_mm\":500,\"right_mm\":1200}\n"

    @Test fun `split and merged USB transfers preserve readings and units`() {
        val decoder = DistanceLineDecoder()
        assertTrue(decoder.accept(packet.take(15).toByteArray()).isEmpty())
        val result = decoder.accept((packet.drop(15) + packet.replace("\n", "\r\n")).toByteArray())
        assertEquals(2, result.size)
        assertEquals(0.5f, result[0].leftMeters, 0.001f)
        assertEquals(1.2f, result[1].rightMeters, 0.001f)
    }

    @Test fun `reject invalid values and logs without turning failures into zero metres`() {
        val decoder = DistanceLineDecoder()
        val invalid = listOf("booting", "{}", packet.trim() + "junk", "{\"left_mm\":null,\"right_mm\":100}",
            "{\"left_mm\":\"500\",\"right_mm\":100}", "{\"left_mm\":-1,\"right_mm\":100}",
            "{\"left_mm\":0,\"right_mm\":100}", "{\"left_mm\":500.5,\"right_mm\":100}",
            "{\"left_mm\":1,\"right_mm\":100001}")
        for (line in invalid) assertTrue(line, decoder.accept((line + "\n").toByteArray()).isEmpty())
        assertEquals(1, decoder.accept(packet.toByteArray()).size)
    }

    @Test fun `oversized line is discarded through newline then parser recovers`() {
        val decoder = DistanceLineDecoder()
        assertTrue(decoder.accept("x".repeat(600).toByteArray()).isEmpty())
        assertTrue(decoder.accept(packet.toByteArray()).isEmpty())
        assertEquals(1, decoder.accept(packet.toByteArray()).size)
    }
}
