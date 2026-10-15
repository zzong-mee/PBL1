package com.guideeye.app.data

import android.os.Handler
import android.os.Looper

/** Main-thread callbacks; start is idempotent and stop removes pending work. */
class MockDistanceSource : DistanceSource {
    private val handler = Handler(Looper.getMainLooper())
    private val sequence = MockSequence()
    private var listener: ((DistanceReading) -> Unit)? = null
    private val tick = object : Runnable {
        override fun run() {
            listener?.invoke(sequence.next())
            if (listener != null) handler.postDelayed(this, 2_000)
        }
    }
    override fun start(onReading: (DistanceReading) -> Unit) {
        stop()
        listener = onReading
        handler.post(tick)
    }
    override fun stop() {
        listener = null
        handler.removeCallbacks(tick)
    }
}
