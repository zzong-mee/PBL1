package com.guideeye.app.audio

import kotlin.math.abs
import kotlin.math.floor

/** 90=left edge, 45=front, 0=right edge. HRTF uses +45..-45 instead. */
object RadarScanPolicy {
    const val PERIOD_MS=1000L
    const val BINS=16
    fun bin(azimuth: Double): Int? {
        if(!azimuth.isFinite() || azimuth !in -45.0..45.0) return null
        return floor((45.0-azimuth)/90.0*BINS).toInt().coerceIn(0,BINS-1)
    }
    fun candidates(points: List<SpatialTarget>,now:Long): Map<Int,SpatialTarget> = points
        .filter { it.validUntil>=now && it.point.rangeMm in 1..4000 && bin(it.point.azimuthDegrees)!=null }
        .groupBy { bin(it.point.azimuthDegrees)!! }
        .mapValues { (_,values) -> values.minWith(compareBy<SpatialTarget>{it.point.rangeMm}
            .thenBy{abs(it.point.elevationDegrees)}.thenBy{it.sensor}.thenBy{it.point.zone}) }
    fun plan(points: List<SpatialTarget>,now:Long,limit:Int): Set<Int> {
        require(limit in 1..BINS)
        val bins=candidates(points,now)
        val ordered=bins.entries.sortedWith(compareBy<Map.Entry<Int,SpatialTarget>>{it.value.point.rangeMm}
            .thenBy { abs(it.value.point.azimuthDegrees) }.thenBy { it.key })
        val selected=mutableListOf<Int>()
        fun add(bin:Int) {
            if(bin in selected || selected.size>=limit)return
            if(limit<BINS && selected.any {
                abs(bins.getValue(it).point.azimuthDegrees-bins.getValue(bin).point.azimuthDegrees)<15.0
            })return
            selected.add(bin)
        }
        // A near wall on one side must not monopolize the default three-direction summary.
        if(limit in 3..5) {
            fun sector(angle:Double)=if(angle>15)0 else if(angle < -15)2 else 1
            ordered.groupBy { sector(it.value.point.azimuthDegrees) }.values
                .map { it.first() }.sortedBy { it.value.point.rangeMm }.forEach { add(it.key) }
        }
        for((bin,_) in ordered) add(bin)
        return selected.toSortedSet()
    }
    data class Position(val cycle:Long,val slot:Int,val lateMs:Long)
    fun position(origin:Long,now:Long):Position? {
        if(now<origin)return null
        val elapsed=now-origin
        val cycle=elapsed/PERIOD_MS
        val phase=elapsed%PERIOD_MS
        val slot=(phase*BINS/PERIOD_MS).toInt()
        return Position(cycle,slot,phase-slot*PERIOD_MS/BINS)
    }
    fun current(plan:Set<Int>,latest:List<SpatialTarget>,position:Position,now:Long):SpatialTarget? {
        if(position.slot !in plan || position.lateMs !in 0..20)return null
        return candidates(latest,now)[position.slot]
    }
    fun nextBoundary(origin:Long,now:Long):Long {
        if(now<origin)return origin
        val elapsed=now-origin
        // ceil for half-millisecond boundaries: 63,125,188,...,1000.
        val tick=elapsed*BINS/PERIOD_MS+1
        return origin+(tick*PERIOD_MS+BINS-1)/BINS
    }
}
