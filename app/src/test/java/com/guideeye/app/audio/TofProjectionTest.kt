package com.guideeye.app.audio
import com.guideeye.app.usb.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TofProjectionTest {
    private fun mount(yaw:Double=0.0,turns:Int=0,mirror:Boolean=false)=TofMount(yaw,0.0,45.0,45.0,turns,mirror)
    private fun grid(vararg indices:Int)=TofGrid(List(64){if(it in indices)1000 else null},List(64){5},0)
    @Test fun `raw 64 zone positions survive nulls and are never compacted`() {
        val json=JSONObject().put("grid_size",8).put("grid_order","st_uld_raw").put("tof_error","")
        for(side in listOf("left","right")) {
            json.put("${side}_zones_mm",JSONArray(List(64){if(it==63)789 else JSONObject.NULL}))
            json.put("${side}_status",JSONArray(List(64){5})).put("${side}_age_ms",25)
        }
        val result=TofGridFrame.parse(json,100)!!
        assertEquals(64,result.left.mm.size); assertEquals(789,result.left.mm[63]); assertNull(result.left.mm[0])
        json.getJSONArray("left_status").put(63,0)
        assertNull(TofGridFrame.parse(json,100)!!.left.mm[63])
        json.put("left_zones_mm",JSONArray(listOf(789)))
        assertNull(TofGridFrame.parse(json,100))
    }
    @Test fun `grid has two axis rays not just one angle per sensor`() {
        val points=TofProjection.project(grid(0,7,56,63),mount(),0).associateBy{it.zone}
        assertTrue(points.getValue(0).azimuthDegrees>0 && points.getValue(0).elevationDegrees>0)
        assertTrue(points.getValue(7).azimuthDegrees<0 && points.getValue(7).elevationDegrees>0)
        assertTrue(points.getValue(56).azimuthDegrees>0 && points.getValue(56).elevationDegrees<0)
        assertTrue(points.getValue(63).azimuthDegrees<0 && points.getValue(63).elevationDegrees<0)
        val a=TofProjection.project(grid(0),mount(22.5),0).single()
        val b=TofProjection.project(grid(0),mount(-22.5),0).single()
        assertEquals(45.0,a.azimuthDegrees-b.azimuthDegrees,.0001)
    }
    @Test fun `rotation mirror and stale device samples affect geometry correctly`() {
        val raw=TofProjection.project(grid(0),mount(),0).single()
        val mirror=TofProjection.project(grid(0),mount(mirror=true),0).single()
        assertEquals(-raw.azimuthDegrees,mirror.azimuthDegrees,.0001)
        val turn=TofProjection.project(grid(0),mount(turns=2),0).single()
        assertEquals(-raw.elevationDegrees,turn.elevationDegrees,.0001)
        assertTrue(TofProjection.project(grid(0).copy(ageAtReceiptMs=950),mount(),51).isEmpty())
        assertTrue(TofProjection.project(grid(0),mount(),-1).isEmpty())
    }
    @Test fun `point selector retains nearby distance ties but changes to a clearly closer zone`() {
        fun target(zone:Int,mm:Int)=SpatialTarget("A",TofPoint(zone,mm,zone.toDouble(),0.0),1000)
        val selector=TofTargetSelector()
        assertEquals(10,selector.select(listOf(target(10,500),target(20,600)))!!.point.zone)
        assertEquals(10,selector.select(listOf(target(10,500),target(20,400)))!!.point.zone)
        assertEquals(20,selector.select(listOf(target(10,500),target(20,300)))!!.point.zone)
        assertNull(selector.select(emptyList()))
    }
    @Test fun `dense measured filters include elevation and preserve front left coordinate convention`() {
        val bytes=javaClass.getResourceAsStream("/kemar-spatial.bin")!!.use{it.readBytes()}
        val bank=SpatialHrirBank(bytes)
        assertEquals(710,bank.filters.size)
        val left=bank.filters[bank.nearest(22.5,20.0)]
        assertTrue(abs(left.azimuth-22.5)<5 && abs(left.elevation-20.0)<.01)
        val right=bank.filters[bank.nearest(-22.5,-20.0)]
        assertTrue(right.azimuth>330 && right.elevation<0)
        val pcm=bank.render(bank.nearest(20.0,20.0))
        assertTrue(pcm.maxOf{abs(it.toInt())} in 1..19661)
        assertFalse(pcm.filterIndexed { i,_->i%2==0 }.toShortArray().contentEquals(pcm.filterIndexed { i,_->i%2==1 }.toShortArray()))
    }
    @Test fun `actual firmware writer fixture keeps the invalid first cell and valid last cell`() {
        val json=javaClass.getResourceAsStream("/tof-grid-packet.json")!!.bufferedReader().use { JSONObject(it.readText()) }
        val parsed=TofGridFrame.parse(json,100)!!
        assertNull(parsed.left.mm[0]); assertEquals(1063,parsed.left.mm[63])
        assertTrue(parsed.right.mm.all { it==null })
        assertEquals((1..63).toList(),TofProjection.project(parsed.left,mount(),0).map { it.zone })
    }
}
