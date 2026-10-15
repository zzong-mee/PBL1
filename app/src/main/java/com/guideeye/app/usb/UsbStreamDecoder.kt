package com.guideeye.app.usb

import com.guideeye.app.data.DistanceReading
import java.util.zip.CRC32

/** GEYE v1 frames plus the original newline distance JSON, never parsing JPEG bytes as text. */
class UsbStreamDecoder(private val packet: (Int, ByteArray) -> Unit, private val legacy: (DistanceReading) -> Unit) {
    private val magic = byteArrayOf(71,69,89,69)
    private var matched=0
    private var header=ByteArray(14)
    private var headerSize=0
    private var payload: ByteArray?=null
    private var payloadSize=0
    private val crcBytes=ByteArray(4)
    private var crcSize=0
    private var inFrame=false
    private var lines=DistanceLineDecoder()
    fun accept(bytes: ByteArray, count: Int=bytes.size) {
        for(i in 0 until count) {
            val b=bytes[i]
            if(!inFrame) {
                lines.accept(byteArrayOf(b)).forEach(legacy)
                matched=if(b==magic[matched]) matched+1 else if(b==magic[0]) 1 else 0
                if(matched==4) { inFrame=true;matched=0;headerSize=0;payload=null;payloadSize=0;crcSize=0;lines=DistanceLineDecoder() }
            } else if(headerSize<14) {
                header[headerSize++]=b
                if(headerSize==14) {
                    val length=unsigned(header,2)
                    val type=header[1].toInt()
                    val max=when(type) { 1->4096;2->262144;3->1024;else->0 }
                    if(header[0].toInt()!=1 || length !in 1..max.toLong()) reset()
                    else payload=ByteArray(length.toInt())
                }
            } else if(payloadSize<payload!!.size) payload!![payloadSize++]=b
            else {
                crcBytes[crcSize++]=b
                if(crcSize==4) {
                    val crc=CRC32().apply { update(header);update(payload!!) }.value
                    if(crc==unsigned(crcBytes,0)) packet(header[1].toInt(),payload!!)
                    reset()
                }
            }
        }
    }
    private fun reset() { inFrame=false;matched=0;payload=null;lines=DistanceLineDecoder() }
    private fun unsigned(a: ByteArray, offset: Int): Long = (0..3).fold(0L) { n,i -> n or ((a[offset+i].toLong() and 255) shl (8*i)) }
}
