package com.guideeye.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.guideeye.app.R
import com.guideeye.app.usb.UsbDistanceController
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** USB JPEG is an input adapter; detector accepts Bitmap so future frame sources can reuse it. */
class ExternalVisionController(context: Context, private val status: (Int) -> Unit,
    private val result: (Bitmap,List<Detection>,Long) -> Unit = { bitmap,_,_ -> bitmap.recycle() }) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var generation = 0
    private var detector: YoloXDetector? = null
    private var latest: Pair<Long,List<Detection>>? = null
    private var closed = false
    private val usb = UsbDistanceController(context, { code ->
        if (code != R.string.usb_receiving) { generation++; latest = null }
        status(code)
    }, {}, { bytes, received -> acceptJpeg(bytes,received) })

    fun start() { usb.start() }
    fun connect() { generation++;latest=null;usb.connect() }
    val isConnected get() = usb.isConnected
    fun central(now: Long=SystemClock.elapsedRealtime()): Detection? =
        latest?.takeIf { now-it.first in 0..2000 }?.let { DetectionRules.central(it.second) }
    fun hasFreshFrame(now: Long=SystemClock.elapsedRealtime()): Boolean = latest?.let { now-it.first in 0..2000 } == true

    fun acceptJpeg(bytes: ByteArray, received: Long) {
        if (closed || !busy.compareAndSet(false,true)) return
        val token=generation
        worker.execute {
            var decoded: Bitmap? = null
            try {
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth in 1..1280 && bounds.outHeight in 1..1280)
                val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("Invalid JPEG")
                decoded=bitmap
                val start=SystemClock.elapsedRealtime()
                val engine=detector ?: YoloXDetector(app).also { detector=it }
                val detections=engine.detect(bitmap)
                val elapsed=SystemClock.elapsedRealtime()-start
                main.post {
                    if(!closed && token==generation) {
                        latest=received to detections
                        status(R.string.external_vision_ready)
                        result(bitmap,detections,elapsed)
                    } else bitmap.recycle()
                }
            } catch (_: Exception) {
                decoded?.recycle()
                main.post { if(!closed && token==generation) { latest=null;status(R.string.external_vision_error) } }
            } finally { busy.set(false) }
        }
    }
    fun stop() { generation++;latest=null;usb.stop() }
    fun close() {
        closed=true;stop()
        worker.execute { detector?.close();detector=null }
        worker.shutdown()
    }
}
