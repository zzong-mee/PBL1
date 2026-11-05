package com.guideeye.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.GridLayout
import android.widget.LinearLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.graphics.Typeface
import com.guideeye.app.usb.TofGridColorPalette
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
    private var fullTestEnabled = false
    private var lastJpegAt = 0L
    private var lastDetectionAt = 0L
    private var lastSpeechAt = 0L
    private var lastDetectionCount = 0
    private val testBlocks = linkedMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        setContentView(R.layout.activity_developer)
        setupFullTest()
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
                        this@DeveloperActivity.findViewById<CheckBox>(R.id.tof_mount_confirm).isChecked=false
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
                lastDetectionAt = android.os.SystemClock.elapsedRealtime()
                lastDetectionCount = detections.size
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
                lastDetectionAt = android.os.SystemClock.elapsedRealtime()
                lastDetectionCount = detections.size
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
        }, { bytes, received ->
            lastJpegAt = android.os.SystemClock.elapsedRealtime()
            vision.acceptJpeg(bytes,received)
        }, { left,right ->
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
                lastSpeechAt = android.os.SystemClock.elapsedRealtime()
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

    private fun setupFullTest() {
        val button = findViewById<Button>(R.id.full_test_toggle)
        val panel = findViewById<LinearLayout>(R.id.full_test_blocks)
        val stages = listOf(
            "USB 연결·수신" to "USB 데이터 수신 루프",
            "ToF 8×8 수신" to "좌·우 ToF 8×8 배열",
            "거리 유효성 검사" to "거리 유효성 검사·군집화 중 유효성 검사",
            "거리·방향 후보" to "거리·방향 위험 후보 (후보 투영만)",
            "카메라 JPEG" to "카메라 JPEG 프레임",
            "YOLO 탐지" to "YOLO 객체 탐지",
            "레이더 스캔" to "레이더 스캔 음향 (개발자 기능)",
            "공간 음향" to "ToF 공간 음향 (개발자 기능)",
            "한국어 TTS" to "한국어 TTS / 오디오 출력 (TTS 테스트)"
        )
        stages.forEach { (key, label) ->
            val view = TextView(this).apply {
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                text = label
                contentDescription = label + ", 대기"
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            panel.addView(view, params)
            testBlocks[key] = view
        }
        button.setOnClickListener {
            fullTestEnabled = !fullTestEnabled
            panel.visibility = if (fullTestEnabled) View.VISIBLE else View.GONE
            button.text = if (fullTestEnabled) "전체 테스트 종료" else "전체 테스트 시작"
            findViewById<TextView>(R.id.full_test_status).text =
                if (fullTestEnabled) "실시간 모니터링 중 · 미구현 단계 제외" else "테스트 대기"
            if (fullTestEnabled) refreshFullTest()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun refreshFullTest() {
        val now = android.os.SystemClock.elapsedRealtime()
        val recent = { time: Long, duration: Long -> time > 0 && now >= time && now - time <= duration }
        val frame = gridFrame
        val elapsed = frame?.let { now - it.receivedAt } ?: -1L
        val leftValid = frame != null && elapsed >= 0 &&
            frame.left.ageAtReceiptMs in 0..1000 && elapsed <= 1000 - frame.left.ageAtReceiptMs
        val rightValid = frame != null && elapsed >= 0 &&
            frame.right.ageAtReceiptMs in 0..1000 && elapsed <= 1000 - frame.right.ageAtReceiptMs
        val validGrid = leftValid || rightValid
        val validPoints = if (validGrid && frame != null) {
            (if (leftValid) frame.left.mm.count { it != null } else 0) +
                (if (rightValid) frame.right.mm.count { it != null } else 0)
        } else 0
        fun mark(key: String, active: Boolean, detail: String) {
            val view = testBlocks[key] ?: return
            val label = view.text.toString().substringBefore("  ·  ")
            val state = if (active) "동작" else "대기"
            view.text = "$label  ·  $state ($detail)"
            view.contentDescription = "$label, $state, $detail"
            val background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (active) Color.rgb(17, 76, 106) else Color.rgb(38, 49, 63))
                setStroke(dp(if (active) 2 else 1),
                    if (active) Color.rgb(91, 210, 255) else Color.rgb(83, 98, 112))
            }
            view.background = background
            view.elevation = if (active) dp(9).toFloat() else 0f
        }
        mark("USB 연결·수신", usbMode && recent(lastSensorAt, 1500), "ToF 수신 기준")
        mark("ToF 8×8 수신", validGrid, if (validGrid) "유효 프레임" else "새 프레임 없음")
        mark("거리 유효성 검사", validGrid, "유효 거리 $validPoints/128")
        val projectionReady = validGrid && validPoints > 0 &&
            findViewById<CheckBox>(R.id.tof_mount_confirm).isChecked &&
            (radar.enabled || spatial.enabled)
        mark("거리·방향 후보", projectionReady, "보정 확인 + 방향 기능 실행 시")
        mark("카메라 JPEG", usbMode && recent(lastJpegAt, 1500), "USB JPEG 수신")
        mark("YOLO 탐지", recent(lastDetectionAt, 1500),
            "최근 탐지 결과 $lastDetectionCount 개")
        mark("레이더 스캔", radar.enabled && projectionReady, "활성화 상태")
        mark("공간 음향", spatial.enabled && projectionReady, "활성화 상태")
        mark("한국어 TTS", recent(lastSpeechAt, 2500), "개발자 TTS 테스트 기준")
    }

    private val gridFreshness=object:Runnable {
        override fun run() {
            if(!foreground)return
            if(gridFrame?.let { android.os.SystemClock.elapsedRealtime()-it.receivedAt>1000 }==true) {
                gridFrame=null; refreshGrid()
            } else if(gridFrame!=null) refreshGrid()
            if (fullTestEnabled) refreshFullTest()
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

    private fun orientedGrid(grid: TofGrid, m: TofMount, elapsed: Long): List<Int?> {
        val cells = MutableList<Int?>(64) { null }
        if (elapsed < 0 || grid.ageAtReceiptMs !in 0..1000 ||
            elapsed > 1000 - grid.ageAtReceiptMs) return cells
        grid.mm.forEachIndexed { i, distance ->
            var row = i / 8
            var column = i % 8
            repeat(m.quarterTurns) {
                val oldRow = row
                row = column
                column = 7 - oldRow
            }
            if (m.mirrorColumns) column = 7 - column
            cells[row * 8 + column] = distance
        }
        return cells
    }

    private fun updateGridHeatmap(gridId: Int, readings: List<Int?>?) {
        val grid = findViewById<GridLayout>(gridId)
        // Reuse the 64 views: no allocation on each sensor refresh.
        if (grid.childCount == 0) {
            val density = resources.displayMetrics.density
            val cellWidth = (47 * density + 0.5f).toInt()
            val cellHeight = (40 * density + 0.5f).toInt()
            val margin = (1 * density + 0.5f).toInt()
            repeat(64) {
                val cell = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setPadding(0, 0, 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(it / 8),
                    GridLayout.spec(it % 8)
                ).apply {
                    width = cellWidth
                    height = cellHeight
                    setMargins(margin, margin, margin, margin)
                }
                grid.addView(cell, params)
            }
        }
        repeat(64) { index ->
            val cell = grid.getChildAt(index) as TextView
            val distance = readings?.getOrNull(index)
            cell.text = distance?.toString() ?: "—"
            cell.setBackgroundColor(TofGridColorPalette.backgroundColor(distance))
            cell.setTextColor(TofGridColorPalette.textColor(distance))
        }
    }

    private fun refreshGrid() {
        val frame=gridFrame
        if(frame==null) {
            updateGridHeatmap(R.id.tof_grid_a, null)
            updateGridHeatmap(R.id.tof_grid_b, null)
            findViewById<TextView>(R.id.tof_grid_waiting).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tof_spatial_target).setText(R.string.distance_tone_no_target)
            radar.invalidate(); spatial.invalidate(); spatialSelector.reset(); return
        }
        findViewById<TextView>(R.id.tof_grid_waiting).visibility = View.GONE
        val now=android.os.SystemClock.elapsedRealtime()
        val elapsed=now-frame.receivedAt
        val a=mount(true); val b=mount(false)
        updateGridHeatmap(R.id.tof_grid_a, orientedGrid(frame.left,a,elapsed))
        updateGridHeatmap(R.id.tof_grid_b, orientedGrid(frame.right,b,elapsed))
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
        updateGridHeatmap(R.id.tof_grid_a, null)
        updateGridHeatmap(R.id.tof_grid_b, null)
        findViewById<TextView>(R.id.tof_grid_waiting).visibility = View.VISIBLE
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
