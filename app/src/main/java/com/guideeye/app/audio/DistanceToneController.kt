package com.guideeye.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.guideeye.app.R
import kotlin.math.PI
import kotlin.math.sin

/** Foreground developer experiment: 880Hz, 60ms mono beeps; never changes media volume. */
class DistanceToneController(context: Context, private val status: (Int) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var track: AudioTrack? = null
    private var left: Int? = null
    private var right: Int? = null
    private var sampleAt: Long? = null
    private var lastBeep: Long? = null
    private var lastStatus = 0
    var enabled = false
        private set
    private val focus: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (enabled && change != AudioManager.AUDIOFOCUS_GAIN) stop(R.string.distance_tone_focus_lost)
        }, handler).build()

    private fun report(code: Int) {
        if (code != lastStatus) { lastStatus = code; status(code) }
    }
    private fun createTrack(): AudioTrack {
        val rate = 48000
        val samples = ShortArray(rate * 60 / 1000) { i ->
            val ramp = minOf(i / 240f, (rate * 60 / 1000 - 1 - i) / 240f, 1f).coerceAtLeast(0f)
            (sin(2 * PI * 880 * i / rate) * 32767 * ramp).toInt().toShort()
        }
        val audio = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        try {
            check(audio.state == AudioTrack.STATE_INITIALIZED)
            check(audio.write(samples, 0, samples.size) == samples.size)
            return audio
        } catch (error: Exception) { audio.release(); throw error }
    }
    fun start() {
        if (enabled) return
        try {
            if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                report(R.string.distance_tone_focus_lost); return
            }
            track = createTrack()
            enabled = true
            lastBeep = null
            report(R.string.distance_tone_waiting)
            handler.post(tick)
        } catch (_: Exception) { stop(R.string.distance_tone_error) }
    }
    fun update(leftMm: Int?, rightMm: Int?) {
        left = leftMm; right = rightMm; sampleAt = SystemClock.elapsedRealtime()
        // Stop the current beep immediately for an explicit null/out-of-range sample.
        if (DistanceBeepPolicy.signal(left, right, 0) == null) silence()
    }
    fun invalidate() {
        left = null; right = null; sampleAt = null; lastBeep = null
        silence()
        if (enabled) report(R.string.distance_tone_waiting)
    }
    private fun silence() { runCatching { track?.stop() } }
    private val tick = object : Runnable {
        override fun run() {
            if (!enabled) return
            val now = SystemClock.elapsedRealtime()
            val signal = sampleAt?.let { DistanceBeepPolicy.signal(left, right, now - it) }
            if (signal == null) {
                silence(); lastBeep = null
                report(R.string.distance_tone_waiting)
            } else {
                try {
                    if (lastBeep == null || now - lastBeep!! >= signal.intervalMs) {
                        val audio = track ?: error("No audio track")
                        audio.stop()
                        check(audio.reloadStaticData() == AudioTrack.SUCCESS)
                        check(audio.setVolume(signal.gain) == AudioTrack.SUCCESS)
                        audio.play()
                        lastBeep = now
                    }
                    report(R.string.distance_tone_active)
                } catch (_: Exception) { stop(R.string.distance_tone_error); return }
            }
            handler.postDelayed(this, 30)
        }
    }
    fun stop(code: Int = R.string.distance_tone_off) {
        enabled = false
        handler.removeCallbacks(tick)
        silence()
        runCatching { track?.release() }; track = null
        manager.abandonAudioFocusRequest(focus)
        invalidate()
        report(code)
    }
}
