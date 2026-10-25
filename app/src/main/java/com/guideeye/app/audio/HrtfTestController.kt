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
import java.util.concurrent.Executors

/** Synthetic horizontal sound sources, independent of USB/YOLO and real obstacles. */
class HrtfTestController(context: Context, private val status: (Int, Int?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(AudioManager::class.java)
    private val worker = Executors.newSingleThreadExecutor()
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var buffers: Map<Int, ShortArray>? = null
    private var track: AudioTrack? = null
    private var angle = 0
    private var sweep = false
    private var nextDirectionAt = 0L
    private var closed = false
    private val phase = BeepPhase()
    private var distanceMm = 2000
    var enabled = false
        private set
    private val focus: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ change ->
            if (enabled && change != AudioManager.AUDIOFOCUS_GAIN) stop(R.string.hrtf_focus_lost)
        }, main).build()
    init {
        worker.execute {
            try {
                val bank = HrirBank.parse(context.assets.open("hrtf/kemar-horizontal.bin").use { it.readBytes() })
                val rendered = HrtfRenderer.render(bank)
                main.post { if (!closed) { buffers = rendered; status(R.string.hrtf_ready, null) } }
            } catch (_: Exception) {
                main.post { if (!closed) status(R.string.hrtf_error, null) }
            }
        }
    }
    fun setDistance(mm: Int) {
        distanceMm = mm.coerceIn(200,4000)
        if (enabled) status(R.string.hrtf_playing, angle)
    }
    private fun replaceTrack(direction: Int) {
        val samples = buffers?.get(direction) ?: error("Unknown HRTF direction")
        runCatching { track?.stop() }; track?.release(); track = null
        val audio = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        try {
            // MODE_STATIC starts in NO_STATIC_DATA; a successful write initializes it.
            check(audio.state == AudioTrack.STATE_NO_STATIC_DATA || audio.state == AudioTrack.STATE_INITIALIZED)
            check(audio.write(samples,0,samples.size) == samples.size)
            check(audio.state == AudioTrack.STATE_INITIALIZED)
            track = audio
        } catch (error: Exception) { audio.release(); throw error }
        angle = direction; phase.reset()
        status(R.string.hrtf_playing, angle)
    }
    fun play(direction: Int, circle: Boolean = false) {
        if (closed) return
        if (buffers == null) { status(R.string.hrtf_preparing,null); return }
        try {
            if (!enabled && manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                status(R.string.hrtf_focus_lost,null); return
            }
            main.removeCallbacks(tick)
            replaceTrack(direction)
            enabled = true; sweep = circle
            nextDirectionAt = SystemClock.elapsedRealtime() + 1800
            main.post(tick)
        } catch (error: Exception) { android.util.Log.e("ThirdEyeAudio", "HRTF playback failed", error); stop(R.string.hrtf_error) }
    }
    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!enabled || closed) return
            try {
                val now = SystemClock.elapsedRealtime()
                if (sweep && now >= nextDirectionAt) {
                    replaceTrack((angle + 45) % 360)
                    nextDirectionAt = now + 1800
                }
                val signal = DistanceBeepPolicy.signal(distanceMm,null,0)!!
                val audio = track ?: error("No track")
                check(audio.setVolume(signal.gain) == AudioTrack.SUCCESS)
                if (phase.due(now,signal.intervalMs)) {
                    audio.stop(); check(audio.reloadStaticData() == AudioTrack.SUCCESS); audio.play()
                }
            } catch (error: Exception) { android.util.Log.e("ThirdEyeAudio", "HRTF playback failed", error); stop(R.string.hrtf_error); return }
            main.postDelayed(this,30)
        }
    }
    fun stop(code: Int = R.string.hrtf_stopped) {
        enabled = false; sweep = false
        main.removeCallbacks(tick)
        runCatching { track?.stop() }; runCatching { track?.release() }; track = null
        manager.abandonAudioFocusRequest(focus)
        if (!closed) status(code,null)
    }
    fun close() {
        stop(); closed = true; buffers = null
        worker.shutdown()
        main.removeCallbacksAndMessages(null)
    }
}
