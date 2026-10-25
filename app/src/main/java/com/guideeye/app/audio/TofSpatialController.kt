package com.guideeye.app.audio

import android.content.Context
import android.media.*
import android.os.*
import com.guideeye.app.R
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.roundToInt

/** Renders measured HRIR closest to a calibrated zone ray. One active measured point. */
class TofSpatialController(context: Context,private val status: (Int)->Unit) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val manager=context.getSystemService(AudioManager::class.java)
    private val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var bank: SpatialHrirBank?=null
    private var closed=false
    private var generation=0
    private var requested: Int?=null
    private var pcm: ShortArray?=null
    private var trackPcm: ShortArray?=null
    private var track: AudioTrack?=null
    private var target: SpatialTarget?=null
    private var filteredRange: Float?=null
    private var steppedAt: Long?=null
    private val phase=BeepPhase()
    var enabled=false; private set
    private var lastCode=0
    private fun report(code: Int) { if(code!=lastCode) { lastCode=code; status(code) } }
    private val focus: AudioFocusRequest=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs).setOnAudioFocusChangeListener({ c -> if(enabled && c!=AudioManager.AUDIOFOCUS_GAIN) stop() },main).build()
    init {
        worker.execute {
            try {
                val loaded=SpatialHrirBank(context.assets.open("hrtf/kemar-spatial.bin").use { it.readBytes() })
                main.post { if(!closed) { bank=loaded; report(R.string.tof_spatial_ready) } }
            } catch(e: Exception) {
                android.util.Log.e("ThirdEyeAudio","Spatial bank failed",e)
                main.post { if(!closed) report(R.string.hrtf_error) }
            }
        }
    }
    fun start() {
        if (closed || enabled) return
        if (bank==null) { report(R.string.hrtf_preparing); return }
        if (manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { report(R.string.hrtf_focus_lost); return }
        enabled=true; report(R.string.tof_spatial_waiting); main.post(tick)
    }
    fun offer(value: SpatialTarget?) {
        if (!enabled) return
        if (value==null || value.validUntil<SystemClock.elapsedRealtime()) { invalidate(); return }
        target=value
        val engine=bank ?: return
        val index=engine.nearest(value.point.azimuthDegrees,value.point.elevationDegrees)
        if(requested==index) return
        requested=index; pcm=null; silence()
        val token=++generation
        worker.execute {
            try {
                val rendered=engine.render(index)
                main.post { if(!closed && enabled && token==generation) pcm=rendered }
            } catch(e: Exception) {
                android.util.Log.e("ThirdEyeAudio","Spatial rendering failed",e)
                main.post { if(!closed && token==generation) stop(R.string.hrtf_error) }
            }
        }
    }
    private fun silence() { runCatching { track?.stop() } }
    fun invalidate() {
        generation++; target=null; filteredRange=null; steppedAt=null; requested=null; pcm=null; phase.reset(); silence()
        if(enabled) report(R.string.tof_spatial_waiting)
    }
    private fun use(samples: ShortArray): AudioTrack {
        if(trackPcm===samples) return track!!
        silence(); track?.release(); track=null; trackPcm=null
        val audio=AudioTrack.Builder().setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size*2).build()
        try {
            check(audio.state==AudioTrack.STATE_NO_STATIC_DATA || audio.state==AudioTrack.STATE_INITIALIZED)
            check(audio.write(samples,0,samples.size)==samples.size)
            check(audio.state==AudioTrack.STATE_INITIALIZED)
            track=audio; trackPcm=samples; return audio
        } catch(e: Exception) { audio.release(); throw e }
    }
    private val tick: Runnable=object:Runnable {
        override fun run() {
            if(!enabled || closed) return
            val now=SystemClock.elapsedRealtime(); val current=target; val samples=pcm
            if(current==null || now>current.validUntil) invalidate()
            else if(samples!=null) {
                try {
                    val desired=current.point.rangeMm.toFloat()
                    val old=filteredRange ?: desired
                    val elapsed=steppedAt?.let { (now-it).coerceAtLeast(0).toFloat() } ?: 0f
                    val tau=if(desired<old)80f else 350f
                    filteredRange=old+(desired-old)*(1f-exp(-elapsed/tau)); steppedAt=now
                    val signal=DistanceBeepPolicy.signal(filteredRange!!.roundToInt(),null,0)!!
                    val audio=use(samples)
                    check(audio.setVolume(signal.gain)==AudioTrack.SUCCESS)
                    if(phase.due(now,signal.intervalMs)) {
                        audio.stop(); check(audio.reloadStaticData()==AudioTrack.SUCCESS); audio.play()
                    }
                    report(R.string.tof_spatial_active)
                } catch(e: Exception) {
                    android.util.Log.e("ThirdEyeAudio","Spatial playback failed",e); stop(R.string.hrtf_error); return
                }
            }
            main.postDelayed(this,30)
        }
    }
    fun stop(code: Int=R.string.hrtf_stopped) {
        enabled=false; main.removeCallbacks(tick); invalidate(); silence()
        runCatching { track?.release() }; track=null; trackPcm=null
        manager.abandonAudioFocusRequest(focus)
        if(!closed) report(code)
    }
    fun close() { stop(); closed=true; worker.shutdown(); main.removeCallbacksAndMessages(null) }
}
