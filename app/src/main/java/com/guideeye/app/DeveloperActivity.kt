package com.guideeye.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.SeekBar
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import com.guideeye.app.audio.*
import com.guideeye.app.usb.TofGrid
import com.guideeye.app.usb.TofGridFrame
import com.guideeye.app.audio.HrtfTestController
import com.guideeye.app.accessibility.ButtonTouchFeedback
import com.guideeye.app.audio.DistanceToneController
import com.guideeye.app.audio.DistanceToneState
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
    private lateinit var radar: RadarScanController
    private lateinit var spatial: TofSpatialController
    private val spatialSelector=TofTargetSelector()
    private var gridFrame: TofGridFrame?=null
    private lateinit var hrtf: HrtfTestController
    private lateinit var distanceTone: DistanceToneController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer)
        distanceTone = DistanceToneController(this, { code ->
            findViewById<TextView>(R.id.distance_tone_status).setText(code)
            findViewById<Button>(R.id.toggle_distance_tone).setText(
                if (distanceTone.enabled) R.string.distance_tone_disable else R.string.distance_tone_enable)
        }, { output ->
            findViewById<TextView>(R.id.distance_tone_target).text = output?.let {
                getString(R.string.distance_tone_target,
                    getString(if (it.side == DistanceToneState.Side.LEFT) R.string.tone_left else R.string.tone_right),
                    it.measuredMm / 1000f)
            } ?: getString(R.string.distance_tone_no_target)
        })
        findViewById<Button>(R.id.toggle_distance_tone).setOnClickListener {
            when {
                distanceTone.enabled -> distanceTone.stop()
                foreground && usbMode -> { radar.stop(); spatial.stop(); hrtf.stop(); distanceTone.start() }
                else -> findViewById<TextView>(R.id.distance_tone_status).setText(R.string.distance_tone_usb_only)
            }
        }
        val hrtfDirections = mapOf(
            R.id.hrtf_front to (0 to R.string.hrtf_front),
            R.id.hrtf_front_left to (45 to R.string.hrtf_front_left),
            R.id.hrtf_left to (90 to R.string.hrtf_left),
            R.id.hrtf_back_left to (135 to R.string.hrtf_back_left),
            R.id.hrtf_back to (180 to R.string.hrtf_back),
            R.id.hrtf_back_right to (225 to R.string.hrtf_back_right),
            R.id.hrtf_right to (270 to R.string.hrtf_right),
            R.id.hrtf_front_right to (315 to R.string.hrtf_front_right))
        var virtualDistance = 2000
        val directionLabels = hrtfDirections.values.toMap()
        hrtf = HrtfTestController(this) { code, angle ->
            findViewById<TextView>(R.id.hrtf_status).text = if (code == R.string.hrtf_playing && angle != null)
                getString(code,getString(directionLabels.getValue(angle)),virtualDistance / 1000f)
            else getString(code)
        }
        hrtfDirections.forEach { (id, direction) ->
            findViewById<Button>(id).setOnClickListener {
                if (foreground) { radar.stop(); spatial.stop(); distanceTone.stop(); hrtf.play(direction.first) }
            }
        }
        findViewById<Button>(R.id.hrtf_circle).setOnClickListener {
            if (foreground) { radar.stop(); spatial.stop(); distanceTone.stop(); hrtf.play(0,true) }
        }
        findViewById<Button>(R.id.hrtf_stop).setOnClickListener { hrtf.stop() }
        findViewById<SeekBar>(R.id.hrtf_distance).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                virtualDistance = progress + 200
                findViewById<TextView>(R.id.hrtf_distance_label).text = getString(R.string.hrtf_distance,virtualDistance / 1000f)
                hrtf.setDistance(virtualDistance)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        spatial=TofSpatialController(this) { code ->
            findViewById<TextView>(R.id.tof_spatial_status).setText(code)
            if(!spatial.enabled) findViewById<TextView>(R.id.tof_spatial_target).setText(R.string.distance_tone_no_target)
            findViewById<Button>(R.id.tof_spatial_toggle).setText(
                if(spatial.enabled) R.string.tof_spatial_disable else R.string.tof_spatial_enable)
        }
        radar=RadarScanController(this) { code,selected,played ->
            findViewById<TextView>(R.id.radar_status).text=if(code==R.string.radar_active)
                getString(code,selected,played) else getString(code)
            findViewById<Button>(R.id.radar_toggle).setText(if(radar.enabled) R.string.radar_disable else R.string.radar_enable)
        }
        findViewById<Spinner>(R.id.radar_limit).apply {
            adapter=ArrayAdapter.createFromResource(this@DeveloperActivity,R.array.radar_limits,android.R.layout.simple_spinner_dropdown_item)
            setSelection(1)
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){radar.setLimit(listOf(1,3,5,16)[position])}
                override fun onNothingSelected(parent:AdapterView<*>?){}
            }
        }
        findViewById<Button>(R.id.radar_toggle).setOnClickListener {
            when {
                radar.enabled -> radar.stop()
                !foreground || !usbMode -> findViewById<TextView>(R.id.radar_status).setText(R.string.distance_tone_usb_only)
                !findViewById<CheckBox>(R.id.tof_mount_confirm).isChecked -> findViewById<TextView>(R.id.radar_status).setText(R.string.tof_calibration_required)
                else -> { spatial.stop(); hrtf.stop(); distanceTone.stop(); radar.start(); refreshGrid() }
            }
        }
        listOf(R.id.tof_rotation_a,R.id.tof_rotation_b).forEach { id ->
            findViewById<Spinner>(id).apply {
                adapter=ArrayAdapter.createFromResource(this@DeveloperActivity,R.array.tof_rotations,android.R.layout.simple_spinner_dropdown_item)
                onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent:AdapterView<*>?, view:View?, position:Int, itemId:Long) {
                        findViewById<CheckBox>(R.id.tof_mount_confirm).isChecked=false
                        radar.stop(); spatial.stop(); spatialSelector.reset(); refreshGrid()
                    }
                    override fun onNothingSelected(parent:AdapterView<*>?) {}
                }
            }
        }
        listOf(R.id.tof_mirror_a,R.id.tof_mirror_b).forEach { id ->
            findViewById<CheckBox>(id).setOnCheckedChangeListener { _,_ ->
                findViewById<CheckBox>(R.id.tof_mount_confirm).isChecked=false
                radar.stop(); spatial.stop(); spatialSelector.reset(); refreshGrid()
            }
        }
        findViewById<CheckBox>(R.id.tof_mount_confirm).setOnCheckedChangeListener { _, confirmed ->
            if(!confirmed) { radar.stop(); spatial.stop(); spatialSelector.reset() }
        }
        findViewById<Button>(R.id.tof_spatial_toggle).setOnClickListener {
            when {
                spatial.enabled -> spatial.stop()
                !foreground || !usbMode -> findViewById<TextView>(R.id.tof_spatial_status).setText(R.string.distance_tone_usb_only)
                !findViewById<CheckBox>(R.id.tof_mount_confirm).isChecked -> findViewById<TextView>(R.id.tof_spatial_status).setText(R.string.tof_calibration_required)
                else -> { radar.stop(); hrtf.stop(); distanceTone.stop(); spatialSelector.reset(); spatial.start(); refreshGrid() }
            }
        }
        listOf(R.id.title, R.id.heading_connection, R.id.heading_distance,
            R.id.heading_voice, R.id.heading_camera, R.id.heading_hrtf).forEach {
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
                    if (status != R.string.external_camera_error) clearUsbDistances()
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
            if (foreground && usbMode) distanceTone.update(left, right)
            findViewById<TextView>(R.id.left_distance).text = left?.let { getString(R.string.usb_left,it/1000f) } ?: getString(R.string.usb_left_waiting)
            findViewById<TextView>(R.id.right_distance).text = right?.let { getString(R.string.usb_right,it/1000f) } ?: getString(R.string.usb_right_waiting)
        }, { failed ->
            if (failed) distanceTone.invalidate()
            findViewById<TextView>(R.id.sensor_status).setText(
                if (failed) R.string.tof_device_error else R.string.usb_sensor_status)
        }, { frame ->
            gridFrame=frame
            refreshGrid()
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
            radar.stop(); spatial.stop(); hrtf.stop(); distanceTone.stop()
            val message = getString(R.string.speech_message)
            if (speech.speak(message)) {
                findViewById<TextView>(R.id.recent_speech).text = getString(R.string.speech_recent, message)
            }
        }
        findViewById<Button>(R.id.toggle_mock).setOnClickListener {
            mockEnabled = !mockEnabled
            updateMock()
        }
        listOf(R.id.back_to_user, R.id.use_phone_camera, R.id.connect_usb, R.id.use_mock, R.id.toggle_mock, R.id.toggle_distance_tone, R.id.speech_test, R.id.hrtf_front, R.id.hrtf_back,
            R.id.hrtf_left, R.id.hrtf_right, R.id.hrtf_front_left, R.id.hrtf_front_right,
            R.id.hrtf_back_left, R.id.hrtf_back_right, R.id.hrtf_circle, R.id.hrtf_stop, R.id.tof_spatial_toggle, R.id.radar_toggle).forEach {
            ButtonTouchFeedback.install(findViewById(it)) { name -> speech.announceButton(name) }
        }
    }

    private val gridFreshness=object:Runnable {
        override fun run() {
            if(!foreground)return
            if(gridFrame?.let { android.os.SystemClock.elapsedRealtime()-it.receivedAt>1000 }==true) {
                gridFrame=null; refreshGrid()
            } else if(gridFrame!=null) refreshGrid()
            window.decorView.postDelayed(this,200)
        }
    }
    override fun onStart() {
        super.onStart()
        foreground = true
        window.decorView.post(gridFreshness)
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
    private fun mount(left:Boolean)=TofMount(if(left)22.5 else -22.5,0.0,45.0,45.0,
        findViewById<Spinner>(if(left)R.id.tof_rotation_a else R.id.tof_rotation_b).selectedItemPosition.coerceAtLeast(0),
        findViewById<CheckBox>(if(left)R.id.tof_mirror_a else R.id.tof_mirror_b).isChecked)

    private fun refreshGrid() {
        val frame=gridFrame
        if(frame==null) {
            findViewById<TextView>(R.id.tof_grid).setText(R.string.tof_grid_waiting)
            findViewById<TextView>(R.id.tof_spatial_target).setText(R.string.distance_tone_no_target)
            radar.invalidate(); spatial.invalidate(); spatialSelector.reset(); return
        }
        val now=android.os.SystemClock.elapsedRealtime()
        val elapsed=now-frame.receivedAt
        fun format(grid:TofGrid,m:TofMount):String {
            val cells=Array<String>(64){"   -"}
            if(elapsed>=0 && grid.ageAtReceiptMs+elapsed<=1000) grid.mm.forEachIndexed { i,d ->
                var r=i/8; var c=i%8
                repeat(m.quarterTurns) { val old=r; r=c; c=7-old }
                if(m.mirrorColumns)c=7-c
                cells[r*8+c]=d?.let { "%4d".format(it) } ?: "   -"
            }
            return cells.toList().chunked(8).joinToString("\n"){it.joinToString(" ")}
        }
        val a=mount(true); val b=mount(false)
        findViewById<TextView>(R.id.tof_grid).text=getString(R.string.tof_grid_values,format(frame.left,a),format(frame.right,b))
        if(!findViewById<CheckBox>(R.id.tof_mount_confirm).isChecked || !foreground || !usbMode || (!spatial.enabled && !radar.enabled)) {
            radar.invalidate(); spatial.invalidate(); return
        }
        val candidates=TofProjection.project(frame.left,a,elapsed).map { SpatialTarget("A",it,frame.receivedAt+1000-frame.left.ageAtReceiptMs) } +
            TofProjection.project(frame.right,b,elapsed).map { SpatialTarget("B",it,frame.receivedAt+1000-frame.right.ageAtReceiptMs) }
        if(radar.enabled)radar.offer(candidates)
        val chosen=if(spatial.enabled)spatialSelector.select(candidates) else null
        spatial.offer(chosen)
        findViewById<TextView>(R.id.tof_spatial_target).text=chosen?.let {
            getString(R.string.tof_selected,it.sensor,it.point.zone,it.point.rangeMm/1000f,it.point.azimuthDegrees,it.point.elevationDegrees)
        } ?: getString(R.string.distance_tone_no_target)
    }
    private fun clearUsbDistances() {
        gridFrame=null; radar.invalidate(); spatial.invalidate(); spatialSelector.reset()
        findViewById<TextView>(R.id.tof_grid).setText(R.string.tof_grid_waiting)
        findViewById<TextView>(R.id.tof_spatial_target).setText(R.string.distance_tone_no_target)
        distanceTone.invalidate()
        findViewById<TextView>(R.id.left_distance).setText(R.string.usb_left_waiting)
        findViewById<TextView>(R.id.right_distance).setText(R.string.usb_right_waiting)
    }
    private fun updateMode() {
        radar.stop(); spatial.stop(); spatialSelector.reset()
        distanceTone.stop()
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
    override fun onStop() { foreground = false; window.decorView.removeCallbacks(gridFreshness); radar.stop(); spatial.stop(); hrtf.stop(); distanceTone.stop(); phone.stop(); distances.stop(); usb.stop(); vision.stop(); findViewById<DetectionPreview>(R.id.camera_preview).clear(); findViewById<TextView>(R.id.camera_status).setText(R.string.external_disconnected); findViewById<TextView>(R.id.video).setText(R.string.video_placeholder); speech.stop(); super.onStop() }
    override fun onDestroy() { radar.close(); spatial.close(); hrtf.close(); distanceTone.stop(); phone.close(); usb.close(); vision.close(); speech.close(); super.onDestroy() }
}
