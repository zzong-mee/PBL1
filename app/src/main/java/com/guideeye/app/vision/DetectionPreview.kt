package com.guideeye.app.vision

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.guideeye.app.fusion.CameraTofProjection
import com.guideeye.app.usb.TofGridColorPalette

/** Shows the exact analyzed image with fit-center mapping, not a separately cropped preview. */
class DetectionPreview(context: Context, attrs: AttributeSet? = null) : View(context,attrs) {
    private var frame: Bitmap?=null
    private var detections=emptyList<Detection>()
    private var tracked=emptyList<TwoStageObjectTracker.TrackedDetection>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth=3f;textSize=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,16f,resources.displayMetrics) }
    private val target=RectF()
    private var tof: com.guideeye.app.usb.TofGridFrame? = null
    private var orientations = listOf(0 to false, 0 to false)
    private var swapSensors = false
    fun setTofOverlay(grid: com.guideeye.app.usb.TofGridFrame?, a: Pair<Int, Boolean>, b: Pair<Int, Boolean>, swap: Boolean) {
        tof = grid; orientations = listOf(a,b); swapSensors = swap; invalidate()
    }
    private fun drawTof(canvas: Canvas, bounds: RectF) {
        val grid = tof ?: return
        val elapsed = android.os.SystemClock.elapsedRealtime() - grid.receivedAt
        if (elapsed !in 0..1000) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f; textSize = 10f * resources.displayMetrics.scaledDensity }
        canvas.save()
        canvas.clipRect(bounds)
        for ((s,g) in listOf(grid.left,grid.right).withIndex()) {
            if (g.ageAtReceiptMs !in 0..1000 || elapsed > 1000-g.ageAtReceiptMs || g.mm.size != 64 || g.status.size != 64) continue
            fun project(row: Double, col: Double, distance: Double): PointF? {
                val point = CameraTofProjection.project(s, swapSensors, row, col, distance,
                    bounds.width().toDouble()/bounds.height()) ?: return null
                return PointF(bounds.left+point.x*bounds.width(), bounds.top+point.y*bounds.height())
            }
            for (i in 0..63) {
                val (row,col) = CameraTofProjection.orientedZone(i, orientations[s].first, orientations[s].second)
                val d=g.mm[i]
                val valid=d != null && d in 1..4000 && g.status[i] in listOf(5,6,9)
                val range=if(valid) d!!.toDouble() else 2000.0
                val corners=listOf(project(row.toDouble(),col.toDouble(),range),project(row.toDouble(),col+1.0,range),project(row+1.0,col+1.0,range),project(row+1.0,col.toDouble(),range)).filterNotNull()
                if(corners.size!=4) continue
                val path=Path().apply { moveTo(corners[0].x,corners[0].y); corners.drop(1).forEach { lineTo(it.x,it.y) }; close() }
                p.color=TofGridColorPalette.backgroundColor(d.takeIf { valid })
                p.alpha=if(valid) 100 else 25; p.style=Paint.Style.FILL; canvas.drawPath(path,p)
                p.alpha=220; p.style=Paint.Style.STROKE; canvas.drawPath(path,p)
                val center=project(row+.5,col+.5,range) ?: continue
                p.style=Paint.Style.FILL; p.color=Color.WHITE; p.alpha=255
                val label = "${if(s==0) "A" else "B"} ${if(valid) "${d}mm" else "—"}"
                p.setShadowLayer(2f,0f,1f,Color.BLACK)
                canvas.drawText(label,center.x,center.y,p)
                p.clearShadowLayer()
            }
        }
        p.style=Paint.Style.FILL; p.color=Color.BLACK; p.alpha=210
        canvas.drawRect(bounds.left,bounds.top,bounds.right,bounds.top+p.textSize*1.5f,p)
        p.color=Color.WHITE
        canvas.drawText("거리 히트맵 투영 · 미보정",bounds.left+4,bounds.top+p.textSize,p)
        canvas.restore()
        postInvalidateDelayed(250)
    }
    fun show(bitmap: Bitmap, items: List<Detection>, tracks: List<TwoStageObjectTracker.TrackedDetection> = emptyList()) { frame?.recycle();frame=bitmap;detections=items;tracked=tracks;invalidate() }
    fun clear() { tof=null; frame?.recycle();frame=null;detections=emptyList();tracked=emptyList();invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas);val bitmap=frame ?: return
        val scale=minOf(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
        val w=bitmap.width*scale;val h=bitmap.height*scale;val left=(width-w)/2;val top=(height-h)/2
        paint.style=Paint.Style.FILL;target.set(left,top,left+w,top+h);canvas.drawBitmap(bitmap,null,target,paint)
        drawTof(canvas,target)
        paint.style=Paint.Style.STROKE;paint.color=Color.YELLOW
        canvas.drawRect(left+.4f*w,top+.4f*h,left+.6f*w,top+.6f*h,paint)
        // Contrast ring keeps the exact image centre visible on light and dark objects.
        val radius = 5f * resources.displayMetrics.density
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        canvas.drawCircle(left + w / 2, top + h / 2, radius + 2f * resources.displayMetrics.density, paint)
        paint.color = Color.YELLOW
        canvas.drawCircle(left + w / 2, top + h / 2, radius, paint)
        for(d in detections) {
            paint.color=Color.GREEN;paint.style=Paint.Style.STROKE
            canvas.drawRect(left+d.left*w,top+d.top*h,left+d.right*w,top+d.bottom*h,paint)
            paint.style=Paint.Style.FILL
            val id = tracked.firstOrNull { it.detection == d }?.trackId
            val label = YoloXDetector.labels.getOrNull(d.classId) ?: "객체"
            canvas.drawText(if (id == null) label else "$label #$id",left+d.left*w, maxOf(top+paint.textSize,top+d.top*h),paint)
        }
    }
    override fun onDetachedFromWindow() { clear();super.onDetachedFromWindow() }
}
