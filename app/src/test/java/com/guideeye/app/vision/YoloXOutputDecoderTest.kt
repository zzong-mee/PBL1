package com.guideeye.app.vision

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ln

class YoloXOutputDecoderTest {
    @Test fun `raw grid maps through top-left letterbox back to original frame`() {
        val rows=Array(3549) { FloatArray(85) }
        // Original 640x480: scale .65; desired centre (320,240), size 100x100.
        val index=19*52+26
        rows[index][0]=0f;rows[index][1]=.5f
        rows[index][2]=ln(65f/8);rows[index][3]=ln(65f/8)
        rows[index][4]=.9f;rows[index][5]=.8f
        val d=YoloXOutputDecoder.decode(rows,640,480).single()
        assertEquals(.72f,d.confidence,.001f)
        assertEquals(270f/640,d.left,.001f);assertEquals(190f/480,d.top,.001f)
        assertEquals(370f/640,d.right,.001f);assertEquals(290f/480,d.bottom,.001f)
    }
    @Test fun `padding-only box and nonfinite scores cannot become objects`() {
        val rows=Array(3549) { FloatArray(85) }
        // First grid: centre at y=400 in the bottom padding of a 416x208 image.
        rows[0][0]=20f;rows[0][1]=50f;rows[0][4]=.9f;rows[0][5]=.9f
        rows[1][4]=Float.NaN;rows[1][5]=1f
        rows[2][4]=1f;rows[2][5]=1f;rows[2][2]=Float.POSITIVE_INFINITY
        assertTrue(YoloXOutputDecoder.decode(rows,640,320).isEmpty())
    }
}
