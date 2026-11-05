package com.guideeye.app.vision

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/** Shows the exact analyzed image with fit-center mapping, not a separately cropped preview. */
class DetectionPreview(context: Context, attrs: AttributeSet? = null) : View(context,attrs) {
    private var frame: Bitmap?=null
    private var detections=emptyList<Detection>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth=3f;textSize=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,16f,resources.displayMetrics) }
    private val target=RectF()
    fun show(bitmap: Bitmap, items: List<Detection>) { frame?.recycle();frame=bitmap;detections=items;invalidate() }
    fun clear() { frame?.recycle();frame=null;detections=emptyList();invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas);val bitmap=frame ?: return
        val scale=minOf(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
        val w=bitmap.width*scale;val h=bitmap.height*scale;val left=(width-w)/2;val top=(height-h)/2
        paint.style=Paint.Style.FILL;target.set(left,top,left+w,top+h);canvas.drawBitmap(bitmap,null,target,paint)
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
            canvas.drawText(YoloXDetector.labels[d.classId],left+d.left*w, maxOf(top+paint.textSize,top+d.top*h),paint)
        }
    }
    override fun onDetachedFromWindow() { clear();super.onDetachedFromWindow() }
}
