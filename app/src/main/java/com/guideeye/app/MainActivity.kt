package com.guideeye.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.guideeye.app.accessibility.ButtonTouchFeedback
import com.guideeye.app.data.DistanceSource
import com.guideeye.app.data.MockDistanceSource
import com.guideeye.app.speech.KoreanSpeechGuide
import com.guideeye.app.usb.UsbDistanceController

class MainActivity : Activity() {
    private val distances: DistanceSource = MockDistanceSource()
    private lateinit var speech: KoreanSpeechGuide
    private var mockEnabled = true
    private var usbMode = false
    private lateinit var usb: UsbDistanceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        listOf(R.id.title, R.id.heading_connection, R.id.heading_distance,
            R.id.heading_voice, R.id.heading_camera).forEach {
            androidx.core.view.ViewCompat.setAccessibilityHeading(findViewById(it), true)
        }
        // Android 16 enforces edge-to-edge: keep controls clear of system bars and cutouts.
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
        usbMode = savedInstanceState?.getBoolean("usbMode", false) ?: false
        mockEnabled = savedInstanceState?.getBoolean("mockEnabled", true) ?: true
        usb = UsbDistanceController(this, { status ->
            if (usbMode) {
                findViewById<TextView>(R.id.mock_status).setText(status)
                if (status != R.string.usb_receiving) clearUsbDistances()
            }
        }, { reading ->
            if (usbMode) {
                findViewById<TextView>(R.id.left_distance).text = getString(R.string.usb_left, reading.leftMeters)
                findViewById<TextView>(R.id.right_distance).text = getString(R.string.usb_right, reading.rightMeters)
            }
        })
        findViewById<Button>(R.id.connect_usb).setOnClickListener {
            usbMode = true
            updateMode()
            usb.connect()
        }
        findViewById<Button>(R.id.use_mock).setOnClickListener {
            usb.disconnect()
            usbMode = false
            updateMode()
        }
        val testButton = findViewById<Button>(R.id.speech_test)
        testButton.isEnabled = false
        speech = KoreanSpeechGuide(this) { status, ready ->
            findViewById<TextView>(R.id.tts_status).text = getString(R.string.tts_status, getString(status))
            testButton.isEnabled = ready
        }
        testButton.setOnClickListener {
            val message = getString(R.string.speech_message)
            if (speech.speak(message)) {
                findViewById<TextView>(R.id.recent_speech).text = getString(R.string.speech_recent, message)
            }
        }
        findViewById<Button>(R.id.toggle_mock).setOnClickListener {
            mockEnabled = !mockEnabled
            updateMock()
        }
        listOf(R.id.connect_usb, R.id.use_mock, R.id.toggle_mock, R.id.speech_test).forEach {
            ButtonTouchFeedback.install(findViewById(it)) { name -> speech.announceButton(name) }
        }
    }

    override fun onStart() {
        super.onStart()
        usb.start()
        updateMode()
    }
    private fun clearUsbDistances() {
        findViewById<TextView>(R.id.left_distance).setText(R.string.usb_left_waiting)
        findViewById<TextView>(R.id.right_distance).setText(R.string.usb_right_waiting)
    }
    private fun updateMode() {
        distances.stop()
        findViewById<TextView>(R.id.notice).setText(if (usbMode) R.string.usb_notice else R.string.mock_notice)
        findViewById<TextView>(R.id.sensor_status).setText(if (usbMode) R.string.usb_sensor_status else R.string.sensor_status)
        findViewById<Button>(R.id.toggle_mock).visibility = if (usbMode) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.use_mock).visibility = if (usbMode) View.VISIBLE else View.GONE
        if (usbMode) {
            clearUsbDistances()
            findViewById<TextView>(R.id.mock_status).setText(R.string.usb_idle)
        } else {
            findViewById<TextView>(R.id.left_distance).setText(R.string.left_waiting)
            findViewById<TextView>(R.id.right_distance).setText(R.string.right_waiting)
            updateMock()
        }
    }
    private fun updateMock() {
        findViewById<Button>(R.id.toggle_mock).setText(if (mockEnabled) R.string.pause_mock else R.string.resume_mock)
        findViewById<TextView>(R.id.mock_status).setText(if (mockEnabled) R.string.mock_running else R.string.mock_paused)
        if (mockEnabled) distances.start { reading ->
            findViewById<TextView>(R.id.left_distance).text = getString(R.string.left_distance, reading.leftMeters)
            findViewById<TextView>(R.id.right_distance).text = getString(R.string.right_distance, reading.rightMeters)
        } else distances.stop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("usbMode", usbMode)
        outState.putBoolean("mockEnabled", mockEnabled)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() { distances.stop(); usb.stop(); speech.stop(); super.onStop() }
    override fun onDestroy() { usb.close(); speech.close(); super.onDestroy() }
}
