package com.guideeye.app.fusion

import com.guideeye.app.usb.TofGridFrame
import com.guideeye.app.vision.TwoStageObjectTracker
import kotlin.math.abs

/** Experimental association only; never drives user warnings. */
object ProjectedCellAssociator {
    data class CellMatch(val trackId: Long, val classId: Int, val sensor: Int,
                         val zone: Int, val distanceMm: Int, val observedAtMs: Long,
                         val overlap: Double)
    private data class P(val x: Double, val y: Double)
    private fun area(p: List<P>): Double =
        if (p.size < 3) 0.0 else abs(p.indices.sumOf { i ->
            val a=p[i]; val b=p[(i+1)%p.size]; a.x*b.y-b.x*a.y
        })/2
    private fun clip(p: List<P>, axis: Int, limit: Double, greater: Boolean): List<P> {
        if (p.isEmpty()) return p
        fun value(a: P) = if(axis==0) a.x else a.y
        fun inside(a: P) = if(greater) value(a)>=limit else value(a)<=limit
        val out=mutableListOf<P>()
        var a=p.last()
        for(b in p) {
            if(inside(a)!=inside(b)) {
                val t=(limit-value(a))/(value(b)-value(a))
                out.add(P(a.x+t*(b.x-a.x),a.y+t*(b.y-a.y)))
            }
            if(inside(b)) out.add(b)
            a=b
        }
        return out
    }
    private fun rect(p: List<P>, l: Double,t: Double,r: Double,b: Double) =
        clip(clip(clip(clip(p,0,l,true),0,r,false),1,t,true),1,b,false)

    fun associate(tracks: List<TwoStageObjectTracker.TrackedDetection>,
                  frame: TofGridFrame?, visionAtMs: Long, nowMs: Long,
                  orientations: List<Pair<Int,Boolean>>, swap: Boolean): List<CellMatch> {
        if(frame==null || orientations.size!=2 || orientations.any { it.first !in 0..3 } ||
            visionAtMs !in 0..nowMs || nowMs-visionAtMs>750 ||
            frame.receivedAt !in 0..nowMs || nowMs-frame.receivedAt>750) return emptyList()
        val result=mutableListOf<CellMatch>()
        for((s,g) in listOf(frame.left,frame.right).withIndex()) {
            if(g.mm.size!=64 || g.status.size!=64 || g.ageAtReceiptMs !in 0..750) continue
            val observed=frame.receivedAt-g.ageAtReceiptMs
            if(observed !in 0..nowMs || nowMs-observed>750 || abs(observed-visionAtMs)>250) continue
            for(i in 0..63) {
                val d=g.mm[i] ?: continue
                if(d !in 1..4000 || g.status[i] !in listOf(5,6,9)) continue
                val (row,col)=CameraTofProjection.orientedZone(i,orientations[s].first,orientations[s].second)
                val polygon=listOf(row to col,row to col+1,row+1 to col+1,row+1 to col).mapNotNull { (r,c) ->
                    CameraTofProjection.project(s,swap,r.toDouble(),c.toDouble(),d.toDouble())?.let { P(it.x.toDouble(),it.y.toDouble()) }
                }
                if(polygon.size!=4) continue
                val visible=rect(polygon,0.0,0.0,1.0,1.0)
                val total=area(polygon)
                if(total<=1e-9 || area(visible)<total*.5) continue
                val candidates=tracks.mapNotNull { track ->
                    val b=track.detection
                    if(b.left !in 0f..1f || b.top !in 0f..1f || b.right !in 0f..1f || b.bottom !in 0f..1f || b.left>=b.right || b.top>=b.bottom) null
                    else track to (area(rect(visible,b.left.toDouble(),b.top.toDouble(),b.right.toDouble(),b.bottom.toDouble()))/total)
                }.sortedByDescending { it.second }
                val best=candidates.firstOrNull() ?: continue
                if(best.second<.5 || (candidates.getOrNull(1)?.second ?: 0.0) >= .25) continue
                result.add(CellMatch(best.first.trackId,best.first.detection.classId,s,i,d,observed,best.second))
            }
        }
        return result
    }
}
