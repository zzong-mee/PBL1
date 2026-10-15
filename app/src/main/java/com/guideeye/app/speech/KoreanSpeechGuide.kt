package com.guideeye.app.speech

import android.content.Context
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
            override fun onStart(id: String?) = report(R.string.tts_speaking)
            override fun onDone(id: String?) = report(R.string.tts_done)
            @Deprecated("Required by Android API")
            override fun onError(id: String?) = report(R.string.tts_error)
            override fun onError(id: String?, code: Int) = report(R.string.tts_error)
        })
    }

    private fun report(message: Int) {
        handler.post { if (!closed) onStatus(message, ready) }
    }

    fun speak(message: String): Boolean {
        if (!ready || closed) return false
        val accepted = engine?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "guideeye-test") == TextToSpeech.SUCCESS
        if (!accepted) report(R.string.tts_error)
        return accepted
    }

    fun stop() { engine?.stop() }
    fun close() {
        closed = true
        ready = false
        handler.removeCallbacksAndMessages(null)
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
