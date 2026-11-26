package com.guideeye.app.vision

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

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
        // Assumed horizontal FOV of the upright image: 60 degrees, square pixels.
        // Coordinates already refer to the corrected upright, mirrored, cropped preview.
        val focal = bounds.width() / (2.0 * kotlin.math.tan(Math.toRadians(30.0)))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f; textSize = 10f * resources.displayMetrics.scaledDensity }
        canvas.save()
        canvas.clipRect(bounds)
        for ((s,g) in listOf(grid.left,grid.right).withIndex()) {
            if (g.ageAtReceiptMs !in 0..1000 || elapsed > 1000-g.ageAtReceiptMs || g.mm.size != 64 || g.status.size != 64) continue
            val m = if ((s == 0) != swapSensors) com.guideeye.app.fusion.MeasuredMount.left else com.guideeye.app.fusion.MeasuredMount.right
            val yaw = Math.toRadians(m.yawDegrees)
            val t = kotlin.math.tan(Math.toRadians(22.5))
            val color = if (s == 0) Color.rgb(70,160,255) else Color.rgb(60,230,120)
            fun project(row: Double, col: Double, distance: Double): PointF? {
                val x = (2*col/8-1)*t
                val y = (1-2*row/8)*t
                val z = 1.0
                val k = distance / kotlin.math.sqrt(x*x+y*y+z*z)
                val px = k*(x*kotlin.math.cos(yaw)+z*kotlin.math.sin(yaw))+m.xMm
                val py = k*y+m.yMm
                val pz = k*(z*kotlin.math.cos(yaw)-x*kotlin.math.sin(yaw))+m.zMm
                if (pz <= 0) return null
                return PointF((bounds.centerX()+focal*px/pz).toFloat(), (bounds.centerY()-focal*py/pz).toFloat())
            }
            for (i in 0..63) {
                var row=i/8; var col=i%8
                repeat(orientations[s].first) { val old=row; row=col; col=7-old }
                if (orientations[s].second) col=7-col
                val d=g.mm[i]
                val valid=d != null && d in 1..4000 && g.status[i] in listOf(5,6,9)
                val range=if(valid) d!!.toDouble() else 2000.0
                val corners=listOf(project(row.toDouble(),col.toDouble(),range),project(row.toDouble(),col+1.0,range),project(row+1.0,col+1.0,range),project(row+1.0,col.toDouble(),range)).filterNotNull()
                if(corners.size!=4) continue
                val path=Path().apply { moveTo(corners[0].x,corners[0].y); corners.drop(1).forEach { lineTo(it.x,it.y) }; close() }
                p.color=if(valid) color else Color.GRAY
                p.alpha=if(valid) 45 else 15; p.style=Paint.Style.FILL; canvas.drawPath(path,p)
                p.alpha=180; p.style=Paint.Style.STROKE; canvas.drawPath(path,p)
                val center=project(row+.5,col+.5,range) ?: continue
                p.style=Paint.Style.FILL; p.color=Color.WHITE; p.alpha=255
                canvas.drawText(if(valid) "${if(s==0) "A" else "B"} ${d}mm" else "—",center.x,center.y,p)
            }
        }
        p.style=Paint.Style.FILL; p.color=Color.BLACK; p.alpha=210
        canvas.drawRect(bounds.left,bounds.top,bounds.right,bounds.top+p.textSize*1.5f,p)
        p.color=Color.WHITE
        canvas.drawText("예상 투영 · 미보정 · A/B 확인 필요",bounds.left+4,bounds.top+p.textSize,p)
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
