package com.guideeye.app.speech

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.guideeye.app.R
import java.util.Locale

class KoreanSpeechGuide(context: Context, private val onStatus: (Int, Boolean) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private val dangerTone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
    private var warningId: String? = null
    private var warningDanger = false
    private var sequence = 0L
    private var pendingManual: Pair<Long, String>? = null

    fun canSpeakWarning(danger: Boolean): Boolean =
        !closed && (if (danger) dangerTone != null || ready else ready) && (warningId == null || (danger && !warningDanger))

    private fun finishWarning(id: String?) {
        handler.post {
            if (!closed && id != null && id == warningId) {
                warningId = null
                warningDanger = false
                val pending = pendingManual
                pendingManual = null
                if (pending != null && android.os.SystemClock.elapsedRealtime()-pending.first in 0..2000)
                    speak(pending.second)
            }
        }
    }

    init {
        engine = TextToSpeech(context.applicationContext) { result ->
            // Post so the engine field is assigned even if initialization completes immediately.
            handler.post {
                if (!closed) {
                    val support = if (result == TextToSpeech.SUCCESS)
                        engine?.setLanguage(Locale.KOREAN) else null
                    ready = support != null && support >= TextToSpeech.LANG_AVAILABLE
                    onStatus(if (ready) R.string.tts_ready else R.string.tts_unavailable, ready)
                }
            }
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { if (id != "button-label") report(R.string.tts_speaking) }
            override fun onDone(id: String?) { finishWarning(id); if (id != "button-label") report(R.string.tts_done) }
            override fun onStop(id: String?, interrupted: Boolean) { finishWarning(id) }
            @Deprecated("Required by Android API")
            override fun onError(id: String?) { finishWarning(id); report(R.string.tts_error) }
            override fun onError(id: String?, code: Int) { finishWarning(id); report(R.string.tts_error) }
        })
    }

    private fun report(message: Int) {
        handler.post { if (!closed) onStatus(message, ready) }
    }

    fun speak(message: String): Boolean {
        if (!ready || closed || warningId != null) return false
        val accepted = engine?.speak(message, TextToSpeech.QUEUE_ADD, null, "guideeye-test") == TextToSpeech.SUCCESS
        if (!accepted) report(R.string.tts_error)
        return accepted
    }

    fun speakManual(message: String): Boolean {
        if (!ready || closed) return false
        if (warningId != null) {
            pendingManual = android.os.SystemClock.elapsedRealtime() to message
            return true
        }
        return speak(message)
    }

    /** Warnings preempt ordinary speech; only danger may interrupt caution. */
    fun speakWarning(message: String, danger: Boolean = true): Boolean {
        if (!canSpeakWarning(danger)) return false
        val id = "obstacle-warning-${++sequence}"
        warningId = id
        warningDanger = danger
        if (danger) {
            engine?.stop()
            val played = runCatching { dangerTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 300) == true }.getOrDefault(false)
            if (played) {
                handler.postDelayed({ finishWarning(id) }, 300)
                return true
            }
            // Fallback only if the tone cannot be played.
        }
        val accepted = engine?.speak(message, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
        if (!accepted) { finishWarning(id); report(R.string.tts_error) }
        return accepted
    }

    /** Each new contact replaces any previous label; it never executes the button action. */
    fun announceButton(name: String) {
        if (ready && !closed && warningId == null) {
            if (engine?.speak(name, TextToSpeech.QUEUE_FLUSH, null, "button-label") != TextToSpeech.SUCCESS) {
                report(R.string.tts_error)
            }
        }
    }

    fun stop() { warningId = null; warningDanger = false; pendingManual = null; dangerTone?.stopTone(); engine?.stop() }
    fun close() {
        closed = true
        ready = false
        handler.removeCallbacksAndMessages(null)
        engine?.stop()
        dangerTone?.release()
        engine?.shutdown()
        engine = null
    }
}
