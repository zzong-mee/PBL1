package com.guideeye.app.audio
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class SpatialHrirBank(bytes: ByteArray) {
    data class Filter(val azimuth: Float,val elevation: Float,val ears: Array<FloatArray>)
    val filters: List<Filter>
    private val scale: Float
    private val source=HrtfRenderer.noise(44100)
    init {
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size>=20)
        val magic=ByteArray(8); b.get(magic)
        require(String(magic,Charsets.US_ASCII)=="TEHRIR2\n")
        require(b.int==44100 && b.int==710 && b.int==512)
        require(b.remaining()==710*(8+4096))
        filters=List(710) {
            val az=b.float; val el=b.float
            require(az.isFinite() && az in 0f..360f && el.isFinite() && el in -90f..90f)
            val ears=Array(2) { FloatArray(512) { b.float.also { require(it.isFinite()) } } }
            Filter(az,el,ears)
        }
        // Common bound across every direction/ear: |convolution| <= |source|max * sum(|HRIR|).
        val bound=filters.maxOf { it.ears.maxOf { ear -> ear.sumOf { abs(it).toDouble() } } } * source.maxOf { abs(it) }
        require(bound.isFinite() && bound>0)
        scale=(.6 * 32767 / bound).toFloat()
    }
    fun nearest(azimuth: Double,elevation: Double): Int {
        require(azimuth.isFinite() && elevation.isFinite() && elevation in -90.0..90.0)
        val az=Math.toRadians(azimuth); val el=Math.toRadians(elevation)
        return filters.indices.maxBy { i ->
            val f=filters[i]; val fa=Math.toRadians(f.azimuth.toDouble()); val fe=Math.toRadians(f.elevation.toDouble())
            sin(el)*sin(fe)+cos(el)*cos(fe)*cos(az-fa)
        }
    }
    fun render(index: Int): ShortArray {
        val ears=filters[index].ears
        val result=HrtfRenderer.convolve(source,ears[0],ears[1])
        return ShortArray(result.size) { (result[it]*scale).toInt().coerceIn(-32767,32767).toShort() }
    }
}

data class SpatialTarget(val sensor: String,val point: TofPoint,val validUntil: Long)
class TofTargetSelector {
    private var key: Pair<String,Int>?=null
    fun reset() { key=null }
    fun select(candidates: List<SpatialTarget>): SpatialTarget? {
        if (candidates.isEmpty()) { reset(); return null }
        val nearest=candidates.minWith(compareBy<SpatialTarget> { it.point.rangeMm }
            .thenBy { abs(it.point.azimuthDegrees)+abs(it.point.elevationDegrees) })
        val old=candidates.find { (it.sensor to it.point.zone)==key }
        val chosen=if (old!=null && nearest.point.rangeMm+150>=old.point.rangeMm) old else nearest
        key=chosen.sensor to chosen.point.zone
        return chosen
    }
}
