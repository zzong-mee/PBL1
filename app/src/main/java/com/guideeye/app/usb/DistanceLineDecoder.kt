package com.guideeye.app.usb

import com.guideeye.app.data.DistanceReading
import org.json.JSONObject
import org.json.JSONTokener

/** Newline-delimited UTF-8 JSON. USB read boundaries need not match line boundaries. */
class DistanceLineDecoder {
    private val line = StringBuilder()
    private var discarding = false

    fun accept(bytes: ByteArray, count: Int = bytes.size): List<DistanceReading> {
        val readings = mutableListOf<DistanceReading>()
        for (i in 0 until count) {
            val c = bytes[i].toInt().and(0xff).toChar()
            if (c == '\n') {
                if (!discarding) parse(line.toString())?.let(readings::add)
                line.clear()
                discarding = false
            } else if (!discarding) {
                if (line.length >= 512) { line.clear(); discarding = true }
                else line.append(c)
            }
        }
        return readings
    }

    private fun parse(text: String): DistanceReading? = try {
        val tokens = JSONTokener(text.trim())
        val json = tokens.nextValue() as? JSONObject ?: error("Expected object")
        require(tokens.nextClean() == '\u0000')
        fun millimeters(key: String): Float {
            val n = json.get(key) as? Number ?: error("Not numeric")
            val d = n.toDouble()
            require(d.isFinite() && d in 1.0..100000.0 && d % 1.0 == 0.0)
            return (d / 1000).toFloat()
        }
        DistanceReading(millimeters("left_mm"), millimeters("right_mm"))
    } catch (_: Exception) { null }
}
