package com.guideeye.app.audio
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RadarScanPolicyTest {
    private fun point(bin:Int,range:Int=1000,elevation:Double=0.0,until:Long=5000)=SpatialTarget("A",
        TofPoint(bin,range,45-(bin+.5)*90/16,elevation),until)
    @Test fun `one second sweeps left to right without shortening empty directions`() {
        assertEquals(0,RadarScanPolicy.bin(45.0));assertEquals(15,RadarScanPolicy.bin(-45.0))
        assertNull(RadarScanPolicy.bin(45.1));assertNull(RadarScanPolicy.bin(Double.NaN))
        assertNull(RadarScanPolicy.position(1000,999))
        assertEquals(0,RadarScanPolicy.position(1000,1000)!!.slot)
        assertEquals(8,RadarScanPolicy.position(1000,1500)!!.slot)
        assertEquals(15,RadarScanPolicy.position(1000,1999)!!.slot)
        assertEquals(1L,RadarScanPolicy.position(1000,2000)!!.cycle)
        val boundaries=mutableListOf<Long>();var now=1000L
        repeat(16){now=RadarScanPolicy.nextBoundary(1000,now);boundaries.add(now)}
        assertEquals(2000L,boundaries.last())
        assertEquals((1..15).toList(),boundaries.dropLast(1).map{RadarScanPolicy.position(1000,it)!!.slot})
    }
    @Test fun `each horizontal bin keeps its nearest valid zone including elevation`() {
        val low=point(3,1200,-15.0)
        val high=point(3,600,15.0)
        val bins=RadarScanPolicy.candidates(listOf(low,high,point(5,1000,until=5)),10)
        assertEquals(1,bins.size);assertEquals(high,bins.getValue(3))
    }
    @Test fun `limits cap sounds and diversity avoids filling only adjacent wall zones`() {
        val all=(0..15).map{point(it,500+it)}
        for(limit in listOf(1,3,5,16)) {
            val selected=RadarScanPolicy.plan(all,0,limit)
            assertTrue(selected.size<=limit)
            if(limit<16) for(a in selected)for(b in selected)if(a!=b)assertTrue(abs(a-b)*90.0/16>=15)
        }
        assertEquals(setOf(0,5,11),RadarScanPolicy.plan(all,0,3))
        assertEquals(16,RadarScanPolicy.plan(all,0,16).size)
        assertTrue(RadarScanPolicy.plan(emptyList(),0,3).isEmpty())
    }
    @Test fun `slot plays fresh measurement rather than an expired cycle start snapshot`() {
        val initial=listOf(point(15,1000,until=500))
        val plan=RadarScanPolicy.plan(initial,0,3)
        val right=RadarScanPolicy.position(0,938)!!
        assertNull(RadarScanPolicy.current(plan,initial,right,938))
        val refreshed=listOf(point(15,700,until=1500))
        assertEquals(700,RadarScanPolicy.current(plan,refreshed,right,938)!!.point.rangeMm)
        assertNull(RadarScanPolicy.current(plan,emptyList(),right,938))
    }
    @Test fun `delayed callbacks skip missed slots without a catch up burst`() {
        val all=(0..15).map{point(it)}; val plan=RadarScanPolicy.plan(all,0,16)
        val late=RadarScanPolicy.position(0,550)!!
        assertEquals(8,late.slot);assertNull(RadarScanPolicy.current(plan,all,late,550))
        assertEquals(563,RadarScanPolicy.nextBoundary(0,550).toInt())
    }
    @Test fun `short measured HRTF pulses fit slots and retain stereo cues`() {
        val bank=SpatialHrirBank(javaClass.getResourceAsStream("/kemar-spatial.bin")!!.use{it.readBytes()})
        val pcm=bank.render(bank.nearest(30.0,10.0),20)
        assertEquals((44100*20/1000+511)*2,pcm.size)
        assertTrue(pcm.size/2.0/44100*1000<62.5)
        assertTrue(pcm.maxOf{abs(it.toInt())} in 1..19661)
        assertFalse(pcm.filterIndexed{i,_->i%2==0}.toShortArray().contentEquals(pcm.filterIndexed{i,_->i%2==1}.toShortArray()))
    }
    @Test fun `diversity uses actual zone bearings rather than only bin centers`() {
        val a=SpatialTarget("A",TofPoint(1,500,39.5,0.0),5000)
        val b=SpatialTarget("A",TofPoint(2,600,28.0,0.0),5000)
        assertTrue(abs(RadarScanPolicy.bin(a.point.azimuthDegrees)!!-RadarScanPolicy.bin(b.point.azimuthDegrees)!!)>=3)
        assertEquals(1,RadarScanPolicy.plan(listOf(a,b),0,3).size)
    }
    @Test fun `default three directions retain far opposite side instead of only nearest wall pieces`() {
        val points=listOf(point(0,300),point(3,350),point(8,1500),point(14,3000))
        assertEquals(setOf(0,8,14),RadarScanPolicy.plan(points,0,3))
        assertEquals(setOf(0),RadarScanPolicy.plan(points,0,1))
    }
}
