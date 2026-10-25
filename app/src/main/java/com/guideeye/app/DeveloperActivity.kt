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
import com.guideeye.app.vision.PhoneCameraController
import com.guideeye.app.vision.ExternalVisionController
import com.guideeye.app.vision.DetectionPreview
import com.guideeye.app.vision.YoloXDetector
import com.guideeye.app.usb.UsbDistanceController

class DeveloperActivity : Activity() {
    private val distances: DistanceSource = MockDistanceSource()
    private lateinit var speech: KoreanSpeechGuide
    private var mockEnabled = true
    private var usbMode = true
    private lateinit var usb: UsbDistanceController
    private lateinit var vision: ExternalVisionController
    private lateinit var phone: PhoneCameraController
    private var foreground = false
    private var lastSensorAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer)
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
        usbMode = savedInstanceState?.getBoolean("usbMode", true) ?: true
        mockEnabled = savedInstanceState?.getBoolean("mockEnabled", true) ?: true
        vision = ExternalVisionController(this, { code ->
            if (usbMode && foreground) {
                findViewById<TextView>(R.id.camera_status).setText(code)
                if (code != R.string.external_vision_ready) {
                    findViewById<DetectionPreview>(R.id.camera_preview).clear()
                    findViewById<TextView>(R.id.video).setText(R.string.video_placeholder)
                }
            }
        }, { bitmap, detections, elapsed ->
            if (usbMode && foreground) {
                findViewById<DetectionPreview>(R.id.camera_preview).show(bitmap,detections)
                val objects = detections.joinToString { "${YoloXDetector.labels[it.classId]} ${(it.confidence*100).toInt()}%" }
                findViewById<TextView>(R.id.video).text = getString(R.string.external_detection_info, elapsed,
                    objects.ifEmpty { getString(R.string.no_objects_detected) })
            } else bitmap.recycle()
        })
        phone = PhoneCameraController(this, { code ->
            if (!usbMode) {
                findViewById<TextView>(R.id.camera_status).setText(code)
                if (code != R.string.phone_ready) {
                    findViewById<DetectionPreview>(R.id.camera_preview).clear()
                    findViewById<TextView>(R.id.video).setText(R.string.phone_preview_help)
                }
            }
        }, { bitmap, detections, elapsed ->
            if (!usbMode && foreground) {
                findViewById<DetectionPreview>(R.id.camera_preview).show(bitmap, detections)
                val objects = detections.joinToString { "${YoloXDetector.labels[it.classId]} ${(it.confidence * 100).toInt()}%" }
                findViewById<TextView>(R.id.video).text = getString(R.string.external_detection_info, elapsed, objects)
            } else bitmap.recycle()
        })
        findViewById<Button>(R.id.use_phone_camera).setOnClickListener {
            usb.disconnect()
            vision.stop()
            usbMode = false
            updateMode()
            startPhoneCamera()
        }
        usb = UsbDistanceController(this, { status ->
            if (usbMode) {
                findViewById<TextView>(R.id.mock_status).setText(status)
                if (status == R.string.usb_receiving) {
                    if (android.os.SystemClock.elapsedRealtime()-lastSensorAt > 2000) clearUsbDistances()
                    if (!vision.hasFreshFrame()) {
                        findViewById<DetectionPreview>(R.id.camera_preview).clear()
                        findViewById<TextView>(R.id.camera_status).setText(R.string.external_waiting)
                        findViewById<TextView>(R.id.video).setText(R.string.video_placeholder)
                    }
                }
                if (status != R.string.usb_receiving) {
                    clearUsbDistances()
                    vision.stop()
                    findViewById<DetectionPreview>(R.id.camera_preview).clear()
                    findViewById<TextView>(R.id.camera_status).setText(status)
                    findViewById<TextView>(R.id.video).setText(R.string.video_placeholder)
                }
            }
        }, { reading ->
            if (usbMode) {
                findViewById<TextView>(R.id.left_distance).text = getString(R.string.usb_left, reading.leftMeters)
                findViewById<TextView>(R.id.right_distance).text = getString(R.string.usb_right, reading.rightMeters)
            }
        }, { bytes, received -> vision.acceptJpeg(bytes,received) }, { left,right ->
            lastSensorAt = android.os.SystemClock.elapsedRealtime()
            findViewById<TextView>(R.id.left_distance).text = left?.let { getString(R.string.usb_left,it/1000f) } ?: getString(R.string.usb_left_waiting)
            findViewById<TextView>(R.id.right_distance).text = right?.let { getString(R.string.usb_right,it/1000f) } ?: getString(R.string.usb_right_waiting)
        }, { failed ->
            findViewById<TextView>(R.id.sensor_status).setText(
                if (failed) R.string.tof_device_error else R.string.usb_sensor_status)
        })
        findViewById<Button>(R.id.connect_usb).setOnClickListener {
            usbMode = true
            phone.stop()
            vision.stop()
            findViewById<DetectionPreview>(R.id.camera_preview).clear()
            updateMode()
            usb.connect()
        }
        findViewById<Button>(R.id.use_mock).setOnClickListener {
            usb.disconnect()
            vision.stop()
            findViewById<DetectionPreview>(R.id.camera_preview).clear()
            findViewById<TextView>(R.id.camera_status).setText(R.string.camera_status)
            findViewById<TextView>(R.id.video).setText(R.string.video_placeholder)
            usbMode = false
            updateMode()
            startPhoneCamera()
        }
        findViewById<Button>(R.id.back_to_user).setOnClickListener { finish() }
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
        listOf(R.id.back_to_user, R.id.use_phone_camera, R.id.connect_usb, R.id.use_mock, R.id.toggle_mock, R.id.speech_test).forEach {
            ButtonTouchFeedback.install(findViewById(it)) { name -> speech.announceButton(name) }
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        usb.start()
        updateMode()
        if (usbMode) usb.connect() else startPhoneCamera()
    }
    private fun startPhoneCamera() {
        if (!foreground || usbMode) return
        phone.start()
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            findViewById<TextView>(R.id.camera_status).setText(R.string.phone_permission)
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        } else phone.connect()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && foreground && !usbMode) {
            if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) startPhoneCamera()
            else findViewById<TextView>(R.id.camera_status).setText(R.string.phone_permission)
        }
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
    override fun onStop() { foreground = false; phone.stop(); distances.stop(); usb.stop(); vision.stop(); findViewById<DetectionPreview>(R.id.camera_preview).clear(); findViewById<TextView>(R.id.camera_status).setText(R.string.external_disconnected); findViewById<TextView>(R.id.video).setText(R.string.video_placeholder); speech.stop(); super.onStop() }
    override fun onDestroy() { phone.close(); usb.close(); vision.close(); speech.close(); super.onDestroy() }
}
