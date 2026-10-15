package com.guideeye.app

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.guideeye.app.accessibility.ButtonTouchFeedback
import com.guideeye.app.speech.KoreanSpeechGuide

/** User entry screen: no scrolling, Mock values, or USB debugging controls. */
class MainActivity : Activity() {
    private lateinit var speech: KoreanSpeechGuide
    private var foreground = false
    private var ready = false
    private var introAnnounced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.home_title), true)
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        findViewById<View>(R.id.root).requestApplyInsets()
        speech = KoreanSpeechGuide(this) { status, available ->
            ready = available
            findViewById<TextView>(R.id.home_voice_status).setText(when {
                status == R.string.tts_error -> R.string.home_voice_error
                available -> R.string.home_voice_ready
                else -> R.string.home_voice_unavailable
            })
            findViewById<Button>(R.id.listen_status).isEnabled = available
            findViewById<Button>(R.id.listen_help).isEnabled = available
            announceIntro()
        }
        findViewById<Button>(R.id.listen_status).setOnClickListener {
            speech.speak(getString(R.string.home_status_message))
        }
        findViewById<Button>(R.id.listen_help).setOnClickListener {
            speech.speak(getString(R.string.home_help_message))
        }
        findViewById<Button>(R.id.open_developer).setOnClickListener {
            startActivity(Intent(this, DeveloperActivity::class.java))
        }
        listOf(R.id.listen_status, R.id.listen_help, R.id.open_developer).forEach {
            ButtonTouchFeedback.install(findViewById(it)) { name -> speech.announceButton(name) }
        }
    }

    private fun announceIntro() {
        val exploration = getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
        if (foreground && ready && !introAnnounced) {
            introAnnounced = true
            if (!exploration) {
                val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                speech.announceButton(getString(if (landscape) R.string.home_intro_landscape else R.string.home_intro))
            }
        }
    }
    override fun onStart() { super.onStart(); foreground = true; announceIntro() }
    override fun onStop() { foreground = false; speech.stop(); super.onStop() }
    override fun onDestroy() { speech.close(); super.onDestroy() }
}
