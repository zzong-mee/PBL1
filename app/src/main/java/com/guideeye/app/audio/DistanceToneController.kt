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
class DistanceToneController(context: Context, private val status: (Int) -> Unit,
    private val target: (DistanceToneState.Output?) -> Unit = {}) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var track: AudioTrack? = null
    private val state = DistanceToneState()
    private val phase = BeepPhase()
    private var lastTarget: Pair<DistanceToneState.Side, Int>? = null
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
            // MODE_STATIC starts in NO_STATIC_DATA; a successful write initializes it.
            check(audio.state == AudioTrack.STATE_NO_STATIC_DATA || audio.state == AudioTrack.STATE_INITIALIZED)
            check(audio.write(samples, 0, samples.size) == samples.size)
            check(audio.state == AudioTrack.STATE_INITIALIZED)
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
            phase.reset()
            report(R.string.distance_tone_waiting)
            handler.post(tick)
        } catch (error: Exception) { android.util.Log.e("ThirdEyeAudio", "Distance tone initialization failed", error); stop(R.string.distance_tone_error) }
    }
    fun update(leftMm: Int?, rightMm: Int?) {
        val now = SystemClock.elapsedRealtime()
        state.update(leftMm, rightMm, now)
        if (state.output(now) == null) {
            silence(); phase.reset(); lastTarget = null; target(null)
        }
    }
    fun invalidate() {
        state.reset(); phase.reset(); lastTarget = null
        silence(); target(null)
        if (enabled) report(R.string.distance_tone_waiting)
    }
    private fun silence() { runCatching { track?.stop() } }
    private val tick = object : Runnable {
        override fun run() {
            if (!enabled) return
            val now = SystemClock.elapsedRealtime()
            val output = state.output(now)
            val signal = output?.signal
            if (signal == null) {
                silence(); phase.reset(); lastTarget = null; target(null)
                report(R.string.distance_tone_waiting)
            } else {
                try {
                    val audio = track ?: error("No audio track")
                    check(audio.setVolume(signal.gain) == AudioTrack.SUCCESS)
                    if (phase.due(now, signal.intervalMs)) {
                        audio.stop()
                        check(audio.reloadStaticData() == AudioTrack.SUCCESS)
                        audio.play()
                    }
                    val selection = output.side to output.measuredMm
                    if (selection != lastTarget) { lastTarget = selection; target(output) }
                    report(R.string.distance_tone_active)
                } catch (error: Exception) { android.util.Log.e("ThirdEyeAudio", "Distance tone playback failed", error); stop(R.string.distance_tone_error); return }
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
