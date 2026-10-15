package com.guideeye.app.vision

import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
import java.nio.FloatBuffer
import kotlin.math.*

/** Official YOLOX-Nano: BGR 0..255, top-left letterbox 416, raw grid output [1,3549,85]. */
class YoloXDetector(context: Context) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use {
        it.setIntraOpNumThreads(2)
        environment.createSession(context.assets.open("yolox_nano.onnx").use { f -> f.readBytes() }, it)
    }
    fun detect(bitmap: Bitmap): List<Detection> {
        val scale = min(416f/bitmap.width,416f/bitmap.height)
        val input = Bitmap.createBitmap(416,416,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(input)
        canvas.drawColor(Color.rgb(114,114,114))
        canvas.drawBitmap(bitmap,null,Rect(0,0,(bitmap.width*scale).toInt(),(bitmap.height*scale).toInt()),Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(416*416);input.getPixels(pixels,0,416,0,0,416,416);input.recycle()
        val values=FloatArray(3*416*416)
        for(i in pixels.indices) { values[i]=Color.blue(pixels[i]).toFloat();values[i+pixels.size]=Color.green(pixels[i]).toFloat();values[i+2*pixels.size]=Color.red(pixels[i]).toFloat() }
        val found=mutableListOf<Detection>()
        OnnxTensor.createTensor(environment,FloatBuffer.wrap(values),longArrayOf(1,3,416,416)).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val rows=(result[0].value as Array<Array<FloatArray>>)[0]
                found.addAll(YoloXOutputDecoder.decode(rows,bitmap.width,bitmap.height))
            }
        }
        return found
    }
    override fun close() { session.close() }
    companion object {
        val labels = listOf("사람","자전거","자동차","오토바이","비행기","버스","기차","트럭","보트","신호등","소화전","정지 표지판","주차 요금계","벤치","새","고양이","개","말","양","소","코끼리","곰","얼룩말","기린","배낭","우산","핸드백","넥타이","여행 가방","원반","스키","스노보드","공","연","야구 방망이","야구 글러브","스케이트보드","서핑보드","테니스 라켓","병","와인잔","컵","포크","칼","숟가락","그릇","바나나","사과","샌드위치","오렌지","브로콜리","당근","핫도그","피자","도넛","케이크","의자","소파","화분","침대","식탁","변기","텔레비전","노트북","마우스","리모컨","키보드","휴대전화","전자레인지","오븐","토스터","싱크대","냉장고","책","시계","꽃병","가위","곰 인형","헤어드라이어","칫솔")
    }
}
