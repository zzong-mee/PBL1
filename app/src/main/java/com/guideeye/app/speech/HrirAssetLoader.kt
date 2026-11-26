package com.guideeye.app.speech

import android.content.Context
import java.io.DataInputStream

object HrirAssetLoader {
    fun load(context: Context): HrtfRenderer? = try {
        DataInputStream(context.assets.open("hrir.bin").buffered()).use { input ->
            require(input.readInt() == 0x48524952)
            require(input.readInt() == StereoWarningAudio.SAMPLE_RATE)
            val count = input.readInt()
            val taps = input.readInt()
            require(count in 1..10000 && taps in 1..2048)
            require(count.toLong()*taps <= 4000000)
            val responses = List(count) {
                val az = input.readFloat().toDouble()
                val el = input.readFloat().toDouble()
                HrtfRenderer.Response(az, el,
                    FloatArray(taps) { input.readFloat() },
                    FloatArray(taps) { input.readFloat() })
            }
            require(input.read() == -1)
            HrtfRenderer(responses)
        }
    } catch (e: Exception) {
        android.util.Log.w("ThirdEyeAudio", "Measured HRIR asset unavailable", e)
        null
    }
}
