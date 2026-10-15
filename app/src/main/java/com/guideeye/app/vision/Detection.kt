package com.guideeye.app.vision

import kotlin.math.*

/** Normalized coordinates in the complete, unmirrored input JPEG. */
data class Detection(val classId: Int, val confidence: Float, val left: Float, val top: Float, val right: Float, val bottom: Float)

object DetectionRules {
    fun iou(a: Detection, b: Detection): Float {
        val intersection = max(0f, min(a.right,b.right)-max(a.left,b.left)) * max(0f,min(a.bottom,b.bottom)-max(a.top,b.top))
        val area = (a.right-a.left)*(a.bottom-a.top)+(b.right-b.left)*(b.bottom-b.top)-intersection
        return if (area > 0) intersection/area else 0f
    }
    fun suppress(items: List<Detection>): List<Detection> {
        val kept = mutableListOf<Detection>()
        for (item in items.sortedByDescending { it.confidence }) {
            if (kept.none { it.classId == item.classId && iou(it,item) > 0.45f }) kept.add(item)
            if (kept.size >= 50) break
        }
        return kept
    }
    fun central(items: List<Detection>): Detection? = items.filter {
        it.confidence >= 0.35f && it.left < 0.6f && it.right > 0.4f && it.top < 0.6f && it.bottom > 0.4f
    }.minWithOrNull(compareBy<Detection> {
        if (it.left <= .5f && it.right >= .5f && it.top <= .5f && it.bottom >= .5f) 0 else 1
    }.thenBy { ((it.left+it.right)/2-.5f).pow(2)+((it.top+it.bottom)/2-.5f).pow(2) }.thenByDescending { it.confidence })
}
