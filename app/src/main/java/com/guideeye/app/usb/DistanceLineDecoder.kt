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
        fun zoneMinimum(key: String): Float {
            val values = json.getJSONArray(key)
            require(values.length() > 0)
            var minimum = 100000.0
            for (i in 0 until values.length()) {
                val n = values.get(i) as? Number ?: continue
                val d = n.toDouble()
                if (d.isFinite() && d in 1.0..100000.0 && d % 1.0 == 0.0) minimum = minOf(minimum, d)
            }
            require(minimum < 100000.0)
            return (minimum / 1000).toFloat()
        }
        val left = if (json.has("left_mm")) millimeters("left_mm") else zoneMinimum("left_zones_mm")
        val right = if (json.has("right_mm")) millimeters("right_mm") else zoneMinimum("right_zones_mm")
        DistanceReading(left, right)
    } catch (_: Exception) { null }
}
