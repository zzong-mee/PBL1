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
import com.guideeye.app.fusion.IndependentTofRiskEngine
import com.guideeye.app.fusion.TofApproachTracker
import android.os.SystemClock
import com.guideeye.app.fusion.UnifiedWarningGate
import com.guideeye.app.fusion.TofOrientationSettings
import com.guideeye.app.fusion.ProjectedCellAssociator
import com.guideeye.app.fusion.ProjectedObjectDistance
import com.guideeye.app.fusion.ProjectedRangeTracker
import com.guideeye.app.fusion.ProjectedRiskEngine
import com.guideeye.app.usb.TofGridFrame
import com.guideeye.app.vision.TwoStageObjectTracker

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
    private val approachTracker = TofApproachTracker()
    private val tofRiskEngine = IndependentTofRiskEngine()
    private var tofRisks = emptyList<IndependentTofRiskEngine.Risk>()
    private val warningGate = UnifiedWarningGate()

    private fun dispatchWarning(now: Long) {
        if (!foreground || paused || !ready) return
        val candidates = tofRisks.sortedWith(IndependentTofRiskEngine.warningPriority).map {
            val danger = it.level == IndependentTofRiskEngine.Level.DANGER
            val prefix = if (danger) "위험." else "주의."
            UnifiedWarningGate.Candidate(danger,it.observedAtMs,
                "$prefix 가까워지는 물체가 감지되었습니다.", sensor = it.sensor, zones = it.zones)
        } + freshFusionRisks(now).filter {
            it.level != ProjectedRiskEngine.Level.SAFE &&
                (it.estimate == null || now-it.estimate.measurement.observedAtMs in 0..750)
        }.map {
            val danger = it.level == ProjectedRiskEngine.Level.DANGER
            val prefix = if (danger) "위험." else "주의."
            val m = it.estimate?.measurement
            UnifiedWarningGate.Candidate(danger,fusionAt,
                "$prefix 주변에 위험 후보 물체가 감지되었습니다.",
                it.trackId,it.classId,m?.sensor,m?.supportingZones?.toSet() ?: emptySet())
        }
        warningGate.offer(candidates,now)?.let { speech.speakWarning(it.message) }
    }

    private var orientation = TofOrientationSettings()
    private var fusionGrid: TofGridFrame? = null
    private val fusionTracker = ProjectedRangeTracker()
    private val fusionEngine = ProjectedRiskEngine()
    private var fusionRisks = emptyList<ProjectedRiskEngine.Risk>()
    private var fusionAt = -1L

    private fun resetFusion() {
        fusionGrid = null
        fusionTracker.reset()
        fusionEngine.reset()
        fusionRisks = emptyList()
        fusionAt = -1L
    }

    private fun updateFusion(tracks: List<TwoStageObjectTracker.TrackedDetection>, received: Long) {
        if (!foreground || paused) return
        val now = SystemClock.elapsedRealtime()
        if (received !in 0..now || now-received > 750 || received <= fusionAt) return
        try {
            val cells = ProjectedCellAssociator.associate(
                tracks, fusionGrid, received, now, orientation.orientations, orientation.swap)
            val measurements = ProjectedObjectDistance.estimate(cells)
            val estimates = fusionTracker.update(measurements, tracks.map { it.trackId }.toSet(), received, now)
            fusionRisks = fusionEngine.evaluate(tracks, estimates, received, now)
            fusionAt = received
            dispatchWarning(now)
            android.util.Log.d("ThirdEyeFusion", "tracks=${tracks.size}, matched=${estimates.size}, danger=${fusionRisks.count { it.level == ProjectedRiskEngine.Level.DANGER }}, caution=${fusionRisks.count { it.level == ProjectedRiskEngine.Level.CAUTION }}")
        } catch (e: Exception) {
            resetFusion()
            android.util.Log.e("ThirdEyeFusion", "Experimental fusion failed", e)
        }
    }

    private fun freshFusionRisks(now: Long): List<ProjectedRiskEngine.Risk> =
        if (foreground && !paused && fusionAt >= 0 && now-fusionAt in 0..750)
            fusionRisks else emptyList()

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
        vision = ExternalVisionController(this, ::updateExternalStatus,
            onGrid = { frame ->
                if (foreground && !paused) {
                    if (frame == null) resetFusion() else fusionGrid = frame
                }
                // ToF-only safety path: independent of camera inference or YOLO calibration.
                val approaches = approachTracker.update(frame)
                val now = SystemClock.elapsedRealtime()
                tofRisks = if (foreground && !paused && frame != null)
                    tofRiskEngine.evaluate(approaches, now) else emptyList()
                dispatchWarning(now)
            }, onTracks = ::updateFusion)
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
            resetObstacleState()
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

    private fun resetObstacleState() {
        resetFusion()
        approachTracker.reset()
        tofRisks = emptyList()
        warningGate.reset()
    }

    private fun updateExternalStatus(code: Int) {
        usbState = code
        if (code == R.string.external_vision_error || code == R.string.external_camera_error) {
            resetFusion()
            warningGate.resetVision()
        }
        if (code != R.string.usb_receiving && code != R.string.external_vision_ready &&
            code != R.string.external_vision_error && code != R.string.external_camera_error) {
            resetObstacleState()
        }
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
    override fun onStart() { super.onStart(); orientation = TofOrientationSettings.load(this); resetFusion(); foreground = true; if (!paused) { vision.start(); vision.connect() }; announceIntro() }
    override fun onStop() { foreground = false; resetObstacleState(); vision.stop(); speech.stop(); super.onStop() }
    override fun onDestroy() { vision.close(); speech.close(); super.onDestroy() }
}
