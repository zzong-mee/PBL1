package com.guideeye.app.usb

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class UsbStreamDecoderTest {
    private fun frame(type: Int, payload: ByteArray): ByteArray {
        val body=ByteBuffer.allocate(14+payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(1.toByte()).put(type.toByte()).putInt(payload.size).putInt(4).putInt(100).put(payload).array()
        return "GEYE".toByteArray()+body+ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(CRC32().apply { update(body) }.value.toInt()).array()
    }
    @Test fun `every USB split boundary preserves JPEG and does not parse JSON inside image`() {
        val data=byteArrayOf(0xff.toByte(),0xd8.toByte())+"{\"left_mm\":500,\"right_mm\":1200}\nGEYE".toByteArray()
        val bytes=frame(2,data)
        for(chunk in 1..bytes.size) {
            val packets=mutableListOf<ByteArray>();var legacy=0
            val decoder=UsbStreamDecoder({type,p->assertEquals(2,type);packets.add(p)},{legacy++})
            bytes.toList().chunked(chunk).forEach { decoder.accept(it.toByteArray()) }
            assertEquals(1,packets.size);assertArrayEquals(data,packets.single());assertEquals(0,legacy)
        }
    }
    @Test fun `bad CRC is discarded and next packet recovers`() {
        val invalid=frame(2,byteArrayOf(1,2,3));invalid[invalid.lastIndex]=0
        var count=0;val decoder=UsbStreamDecoder({_,_->count++},{})
        decoder.accept(invalid+frame(2,byteArrayOf(4,5)))
        assertEquals(1,count)
    }
    @Test fun `oversized length is rejected before allocation and stream recovers`() {
        val invalid="GEYE".toByteArray()+ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
            .put(1.toByte()).put(2.toByte()).putInt(Int.MAX_VALUE).putLong(0).array()
        var count=0;val decoder=UsbStreamDecoder({_,_->count++},{})
        decoder.accept(invalid+frame(2,byteArrayOf(4,5)))
        assertEquals(1,count)
    }
    @Test fun `original JSON and binary frames coexist`() {
        var distance=0;var image=0
        val decoder=UsbStreamDecoder({_,_->image++},{distance++})
        decoder.accept("{\"left_mm\":500,\"right_mm\":1200}\r\n".toByteArray()+frame(2,byteArrayOf(1,2)))
        assertEquals(1,distance);assertEquals(1,image)
    }
}
