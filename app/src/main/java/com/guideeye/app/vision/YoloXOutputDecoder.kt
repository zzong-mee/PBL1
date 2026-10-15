package com.guideeye.app.vision

import kotlin.math.*

/** Raw YOLOX grid decoding; letterbox is anchored at the top-left, not centred. */
object YoloXOutputDecoder {
    fun decode(rows: Array<FloatArray>, width: Int, height: Int): List<Detection> {
        require(width > 0 && height > 0 && rows.size == 3549 && rows.all { it.size == 85 })
        val scale = min(416f/width,416f/height)
        val found=mutableListOf<Detection>()
        var index=0
        for(stride in intArrayOf(8,16,32)) for(y in 0 until 416/stride) for(x in 0 until 416/stride) {
            val row=rows[index++]; var cls=0
            for(c in 1 until 80) if(row[c+5]>row[cls+5]) cls=c
            val confidence=row[4]*row[cls+5]
            if(!confidence.isFinite() || confidence < .35f) continue
            val cx=(row[0]+x)*stride;val cy=(row[1]+y)*stride
            val w=exp(row[2])*stride;val h=exp(row[3])*stride
            if (!cx.isFinite() || !cy.isFinite() || !w.isFinite() || !h.isFinite() || w<=0 || h<=0) continue
            val l=((cx-w/2)/scale/width).coerceIn(0f,1f)
            val t=((cy-h/2)/scale/height).coerceIn(0f,1f)
            val r=((cx+w/2)/scale/width).coerceIn(0f,1f)
            val b=((cy+h/2)/scale/height).coerceIn(0f,1f)
            if(listOf(l,t,r,b).all { it.isFinite() } && r>l && b>t) found.add(Detection(cls,confidence,l,t,r,b))
        }
        return DetectionRules.suppress(found)
    }
}
