package com.guideeye.app.usb

import org.json.JSONObject

/** Keep all 64 indices, including invalid cells. Never compact the grid. */
data class TofGrid(val mm: List<Int?>, val status: List<Int?>, val ageAtReceiptMs: Long)
data class TofGridFrame(val left: TofGrid, val right: TofGrid, val receivedAt: Long) {
    companion object {
        fun parse(json: JSONObject, receivedAt: Long): TofGridFrame? = runCatching {
            require(json.getInt("grid_size")==8 && json.getString("grid_order")=="st_uld_raw")
            fun integer(value: Any?, max: Int): Int? {
                val number=value as? Number ?: return null
                val d=number.toDouble()
                return d.takeIf { it.isFinite() && it%1.0==0.0 && it in 0.0..max.toDouble() }?.toInt()
            }
            fun grid(key: String): TofGrid {
                val distances=json.getJSONArray("${key}_zones_mm")
                val statuses=json.getJSONArray("${key}_status")
                require(distances.length()==64 && statuses.length()==64)
                val age=integer(json.get("${key}_age_ms"),Int.MAX_VALUE)?.toLong() ?: error("Missing age")
                val codes=List(64) { integer(statuses.opt(it),255) }
                val mm=List(64) { i -> integer(distances.opt(i),4000)?.takeIf {
                    it>0 && codes[i] in listOf(5,6,9) && age<=1000
                } }
                return TofGrid(mm,codes,age)
            }
            require(json.optString("tof_error").isEmpty())
            TofGridFrame(grid("left"),grid("right"),receivedAt)
        }.getOrNull()
    }
}
