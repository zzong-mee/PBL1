package com.guideeye.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Displays the supplied flowchart without redrawing its contents.
 * Put the original PNG at app/src/main/assets/algorithm_flow.png.
 * Coordinates below are in the 1024x1472 preview coordinate system.
 */
class AlgorithmFlowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val bitmap: Bitmap? = runCatching {
        context.assets.open("algorithm_flow.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
    private val coordinates = mapOf(
        "startup" to RectF(96f,430f,350f,494f),
        "usb_init" to RectF(96f,430f,350f,494f),
        "device_check" to RectF(138f,535f,308f,601f),
        "receive" to RectF(456f,956f,666f,1021f),
        "tof" to RectF(214f,1062f,454f,1127f),
        "validate" to RectF(226f,1167f,443f,1233f),
        "candidate" to RectF(238f,1273f,431f,1339f),
        "jpeg" to RectF(496f,1062f,695f,1127f),
        "yolo" to RectF(507f,1167f,682f,1233f),
        "tts" to RectF(460f,535f,691f,601f),
        "audio" to RectF(466f,640f,681f,705f),
        "central" to RectF(527f,218f,690f,283f),
        "description" to RectF(527f,324f,690f,389f)
    )
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(39, 188, 255)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        setShadowLayer(17f, 0f, 0f, Color.CYAN)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 34, 199, 255)
        style = Paint.Style.FILL
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    var activeStages: Set<String> = emptySet()
        set(value) { field = value; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (width * 1472f / 1024f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sx = width / 1024f
        val sy = height / 1472f
        canvas.save()
        canvas.scale(sx, sy)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, 1024f, 1472f), bitmapPaint)
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawColor(Color.WHITE)
            canvas.drawText("원본 알고리즘 이미지 파일을 추가하세요", 512f, 720f, p)
            canvas.drawText("assets/algorithm_flow.png", 512f, 760f, p)
        }
        if (bitmap != null) activeStages.forEach { key ->
            coordinates[key]?.let { rect ->
                canvas.drawRoundRect(rect, 18f, 18f, fill)
                canvas.drawRoundRect(rect, 18f, 18f, glow)
            }
        }
        canvas.restore()
    }
}
