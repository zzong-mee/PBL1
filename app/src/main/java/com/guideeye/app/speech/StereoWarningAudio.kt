package com.guideeye.app.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

/** Preliminary stereo renderer, not HRTF. All playback methods are main-thread confined. */
class StereoWarningAudio : AutoCloseable {
    companion object {
        const val SAMPLE_RATE = 48000
        fun durationMs(danger: Boolean) = if (danger) 450 else 150

        /** Equal-power lateral panning. Null direction gives centered audio. */
        fun render(danger: Boolean, azimuthDegrees: Double?): ShortArray {
            val azimuth = azimuthDegrees?.takeIf { it.isFinite() } ?: 0.0
            val pan = sin(Math.toRadians(azimuth))
            val left = sqrt((1.0-pan)/2)
            val right = sqrt((1.0+pan)/2)
            val frames = SAMPLE_RATE * durationMs(danger) / 1000
            val fade = SAMPLE_RATE / 200
            return ShortArray(frames * 2).also { pcm ->
                for (i in 0 until frames) {
                    val t = i.toDouble()/SAMPLE_RATE
                    val envelope = minOf(1.0, i.toDouble()/fade, (frames-1-i).toDouble()/fade)
                    // Match the existing DTMF 9 frequency pair; avoid abrupt clicks.
                    val sample = 0.35 * 32767 * envelope *
                        (sin(2*PI*852*t)+sin(2*PI*1477*t))/2
                    pcm[2*i] = (sample*left).roundToInt().toShort()
                    pcm[2*i+1] = (sample*right).roundToInt().toShort()
                }
            }
        }
    }

    private var track: AudioTrack? = null
    private var closed = false

    fun play(danger: Boolean, azimuthDegrees: Double?): Boolean =
        playPcm(render(danger, azimuthDegrees))

    /** Interleaved 48 kHz signed 16-bit stereo; accepts future binaural renderer output. */
    fun playPcm(pcm: ShortArray): Boolean {
        if (closed || pcm.isEmpty() || pcm.size % 2 != 0) return false
        stop()
        return try {
            val next = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track = next
            check(next.state == AudioTrack.STATE_INITIALIZED)
            check(next.write(pcm, 0, pcm.size) == pcm.size)
            next.play()
            true
        } catch (e: Exception) {
            stop()
            android.util.Log.e("ThirdEyeAudio", "Stereo playback failed", e)
            false
        }
    }

    fun stop() {
        val previous = track
        track = null
        if (previous != null) {
            runCatching { previous.stop() }
            runCatching { previous.release() }
        }
    }

    override fun close() {
        closed = true
        stop()
    }
}
