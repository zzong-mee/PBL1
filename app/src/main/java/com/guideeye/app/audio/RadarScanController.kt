package com.guideeye.app.audio

import android.content.Context
import android.media.*
import android.os.*
import com.guideeye.app.R
import java.util.concurrent.Executors

/** Fixed scan timeline. Missing/expired/render-late cells are silent, never replayed as a burst. */
class RadarScanController(context:Context,private val status:(Int,Int,Int)->Unit) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val manager=context.getSystemService(AudioManager::class.java)
    private val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var bank:SpatialHrirBank?=null
    private var closed=false
    private var token=0
    private var points=emptyList<SpatialTarget>()
    private val cache=linkedMapOf<Int,ShortArray>()
    private val requested=mutableSetOf<Int>()
    private var track:AudioTrack?=null
    private var origin=0L
    private var cycle=-1L
    private var slot=-1
    private var plan=emptySet<Int>()
    private var played=0
    private var lastPlayed=-1
    private var announced=-1L
    var limit=3; private set
    var enabled=false; private set
    private val focus:AudioFocusRequest=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs).setOnAudioFocusChangeListener({ c->if(enabled && c!=AudioManager.AUDIOFOCUS_GAIN)stop(R.string.hrtf_focus_lost) },main).build()
    init {
        worker.execute {
            try {
                val loaded=SpatialHrirBank(context.assets.open("hrtf/kemar-spatial.bin").use{it.readBytes()})
                main.post { if(!closed){bank=loaded;status(R.string.radar_ready,0,0)} }
            }catch(e:Exception){ main.post{if(!closed)status(R.string.hrtf_error,0,0)} }
        }
    }
    fun setLimit(value:Int){require(value in listOf(1,3,5,16));limit=value}
    fun offer(values:List<SpatialTarget>){
        if(!enabled)return
        points=values.filter{it.validUntil>=SystemClock.elapsedRealtime()}
        val engine=bank ?: return
        val wanted=RadarScanPolicy.candidates(points,SystemClock.elapsedRealtime()).values
            .map{engine.nearest(it.point.azimuthDegrees,it.point.elevationDegrees)}.toSet()
        val generation=token
        for(index in wanted) if(index !in cache && requested.add(index)) {
            worker.execute {
                try {
                    val rendered=engine.render(index,20)
                    main.post {
                        if(!closed && generation==token){
                            requested.remove(index)
                            if(cache.size>=64)cache.remove(cache.keys.first())
                            cache[index]=rendered
                        }
                    }
                }catch(e:Exception){main.post{if(!closed && generation==token)stop(R.string.hrtf_error)}}
            }
        }
    }
    fun invalidate(){
        points=emptyList();plan=emptySet();runCatching{track?.stop()}
        if(enabled)status(R.string.radar_waiting,0,0)
    }
    fun start(){
        if(enabled || closed)return
        if(bank==null){status(R.string.hrtf_preparing,0,0);return}
        try{
            if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){status(R.string.hrtf_focus_lost,0,0);return}
            enabled=true;cycle=-1;slot=-1;announced=-1;played=0
            origin=SystemClock.uptimeMillis()+200 // Warm-up only; subsequent cycles stay exactly 1s.
            status(R.string.radar_waiting,0,0);main.postAtTime(tick,origin)
        }catch(e:Exception){stop(R.string.hrtf_error)}
    }
    private fun play(samples:ShortArray,gain:Float){
        runCatching{track?.stop()};track?.release();track=null
        val audio=AudioTrack.Builder().setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size*2).build()
        try{
            check(audio.state==AudioTrack.STATE_NO_STATIC_DATA || audio.state==AudioTrack.STATE_INITIALIZED)
            check(audio.write(samples,0,samples.size)==samples.size)
            check(audio.state==AudioTrack.STATE_INITIALIZED)
            check(audio.setVolume(gain)==AudioTrack.SUCCESS)
            audio.play();track=audio
        }catch(e:Exception){audio.release();throw e}
    }
    private val tick:Runnable=object:Runnable{
        override fun run(){
            if(!enabled || closed)return
            val up=SystemClock.uptimeMillis();val now=SystemClock.elapsedRealtime()
            val position=RadarScanPolicy.position(origin,up) ?: return
            if(position.cycle!=cycle){
                cycle=position.cycle;slot=-1;played=0
                plan=RadarScanPolicy.plan(points,now,limit)
            }
            // At most the currently due slot; missed slots are not caught up.
            if(position.slot!=slot){
                slot=position.slot
                val value=RadarScanPolicy.current(plan,points,position,now)
                if(value!=null){
                    val index=bank!!.nearest(value.point.azimuthDegrees,value.point.elevationDegrees)
                    val samples=cache[index]
                    if(samples!=null){
                        try{play(samples,DistanceBeepPolicy.signal(value.point.rangeMm,null,0)!!.gain);played++}
                        catch(e:Exception){android.util.Log.e("ThirdEyeAudio","Radar playback failed",e);stop(R.string.hrtf_error);return}
                    }
                }
            }
            if(announced!=cycle || played!=lastPlayed || slot==15){
                status(if(plan.isEmpty())R.string.radar_waiting else R.string.radar_active,plan.size,played)
                announced=cycle;lastPlayed=played
            }
            main.postAtTime(this,RadarScanPolicy.nextBoundary(origin,SystemClock.uptimeMillis()))
        }
    }
    fun stop(code:Int=R.string.radar_stopped){
        enabled=false;token++;main.removeCallbacks(tick);requested.clear();invalidate()
        runCatching{track?.release()};track=null
        manager.abandonAudioFocusRequest(focus)
        if(!closed)status(code,0,0)
    }
    fun close(){stop();closed=true;cache.clear();worker.shutdown();main.removeCallbacksAndMessages(null)}
}
