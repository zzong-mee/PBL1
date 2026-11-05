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
import com.guideeye.app.vision.ExternalVisionController
import com.guideeye.app.vision.YoloXDetector

/** User entry screen: no scrolling, Mock values, or USB debugging controls. */
class MainActivity : Activity() {
    private lateinit var speech: KoreanSpeechGuide
    private var foreground = false
    private var ready = false
    private var voiceUnavailable = false
    private var introAnnounced = false
    private var paused = false
    private lateinit var vision: ExternalVisionController
    private var usbState = R.string.usb_idle
    private var cameraStatus = R.string.external_disconnected

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        introAnnounced = savedInstanceState?.getBoolean("introAnnounced", false) ?: false
        paused = savedInstanceState?.getBoolean("paused", false) ?: false
        updatePauseUi()
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
        vision = ExternalVisionController(this, ::updateExternalStatus)
        speech = KoreanSpeechGuide(this) { status, available ->
            ready = available
            voiceUnavailable = !available
            findViewById<TextView>(R.id.home_voice_status).setText(when {
                status == R.string.tts_error -> R.string.home_voice_error
                available -> R.string.home_voice_ready
                else -> R.string.home_voice_unavailable
            })
            findViewById<Button>(R.id.listen_status).isEnabled = available
            updateAccessibilityState()
            announceIntro()
        }
        findViewById<Button>(R.id.listen_status).setOnClickListener {
            val message = when {
                paused -> getString(R.string.central_object_paused)
                usbState == R.string.external_camera_error -> getString(R.string.external_camera_error)
                !vision.isConnected -> {
                    if (usbState != R.string.usb_permission && usbState != R.string.usb_opening) vision.connect()
                    getString(R.string.external_connect_message)
                }
                !vision.hasFreshFrame() -> getString(R.string.external_wait_message)
                else -> vision.central()?.let { getString(R.string.central_object_result, YoloXDetector.labels[it.classId]) }
                    ?: getString(R.string.central_object_uncertain)
            }
            speech.speak(message)
        }
        findViewById<Button>(R.id.listen_help).setOnClickListener {
            paused = !paused
            // Stop speech already playing/queued; explicit button feedback remains available when paused.
            speech.stop()
            if (paused) vision.stop() else { vision.start(); vision.connect() }
            updatePauseUi()
            speech.speak(getString(if (paused) R.string.operation_paused_message else R.string.operation_resumed_message))
        }
        findViewById<Button>(R.id.listen_howto).setOnClickListener {
            speech.speak(getString(R.string.home_howto_message))
        }
        findViewById<Button>(R.id.open_developer).setOnClickListener {
            startActivity(Intent(this, DeveloperActivity::class.java))
        }
        listOf(R.id.listen_status, R.id.listen_help, R.id.listen_howto, R.id.open_developer).forEach {
            ButtonTouchFeedback.install(findViewById(it)) { name -> speech.announceButton(name) }
        }
    }

    private fun updatePauseUi() {
        findViewById<Button>(R.id.listen_help).setText(if (paused) R.string.resume_operation else R.string.pause_operation)
        findViewById<TextView>(R.id.home_operation_status).setText(if (paused) R.string.operation_paused else cameraStatus)
        val compact = resources.configuration.fontScale > 1.3f || resources.configuration.screenHeightDp < 480
        findViewById<Button>(R.id.listen_status).setCompoundDrawablesWithIntrinsicBounds(
            0, if (compact) 0 else R.drawable.ic_center_object, 0, 0)
        findViewById<Button>(R.id.listen_help).setCompoundDrawablesWithIntrinsicBounds(
            0, if (compact) 0 else if (paused) R.drawable.ic_resume else R.drawable.ic_pause, 0, 0)
        updateAccessibilityState()
    }

    private fun updateAccessibilityState() {
        val centralState = when {
            voiceUnavailable -> R.string.voice_unavailable_state
            !ready -> R.string.voice_preparing_state
            paused -> R.string.central_object_paused_state
            else -> cameraStatus
        }
        ViewCompat.setStateDescription(findViewById(R.id.listen_status), getString(centralState))
        val toggle = findViewById<Button>(R.id.listen_help)
        toggle.contentDescription = getString(if (paused) R.string.resume_description else R.string.pause_description)
        ViewCompat.setStateDescription(toggle,
            getString(if (paused) R.string.pause_state_paused else R.string.pause_state_active))
    }

    private fun updateExternalStatus(code: Int) {
        usbState = code
        cameraStatus = when (code) {
            R.string.usb_waiting, R.string.usb_receiving ->
                if (::vision.isInitialized && vision.hasFreshFrame()) R.string.external_vision_ready else R.string.external_waiting
            else -> code
        }
        if (!paused) findViewById<TextView>(R.id.home_operation_status).setText(cameraStatus)
        updateAccessibilityState()
    }

    private fun announceIntro() {
        val exploration = getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
        if (foreground && ready && !introAnnounced) {
            introAnnounced = true
            if (!exploration) {
                val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val pauseLabel = getString(if (paused) R.string.resume_operation else R.string.pause_operation)
                val intro = getString(if (landscape) R.string.home_intro_landscape else R.string.home_intro, pauseLabel)
                val message = (if (paused) getString(R.string.operation_paused_message) + " " + intro else intro) +
                    " " + getString(R.string.home_touch_help)
                speech.announceButton(message)
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("paused", paused)
        outState.putBoolean("introAnnounced", introAnnounced)
        super.onSaveInstanceState(outState)
    }
    override fun onStart() { super.onStart(); foreground = true; if (!paused) { vision.start(); vision.connect() }; announceIntro() }
    override fun onStop() { foreground = false; vision.stop(); speech.stop(); super.onStop() }
    override fun onDestroy() { vision.close(); speech.close(); super.onDestroy() }
}
