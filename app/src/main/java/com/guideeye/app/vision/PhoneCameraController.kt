package com.guideeye.app.vision

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.SystemClock
import android.view.Surface
import com.guideeye.app.R

/** Foreground-only rear camera adapter. JPEG frames reuse the existing bounded inference worker. */
class PhoneCameraController(private val activity: Activity, private val status: (Int) -> Unit,
    result: (android.graphics.Bitmap, List<Detection>, Long) -> Unit = { bitmap, _, _ -> bitmap.recycle() }) {
    private val main = Handler(activity.mainLooper)
    private val vision = ExternalVisionController(activity, { code ->
        status(if (code == R.string.external_vision_ready) R.string.phone_ready else code)
    }, result)
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var generation = 0
    private var active = false
    private var opening = false
    private var sensorOrientation = 0
    private var lastFrame = 0L
    val isConnected get() = device != null
    fun central() = vision.central()
    fun hasFreshFrame() = vision.hasFreshFrame()
    fun start() { active = true }

    fun connect() {
        if (!active || opening || device != null) return
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status(R.string.phone_permission)
            return
        }
        val token = ++generation
        opening = true
        status(R.string.phone_waiting)
        try {
            val manager = activity.getSystemService(CameraManager::class.java)
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: error("No rear camera")
            val info = manager.getCameraCharacteristics(id)
            sensorOrientation = info.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val sizes = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG) ?: error("No JPEG output")
            val size = sizes.filter { it.width <= 1280 && it.height <= 1280 }
                .minByOrNull { kotlin.math.abs(it.width * it.height - 640 * 480) }
                ?: error("No supported small JPEG size")
            val output = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
            reader = output
            output.setOnImageAvailableListener({ source ->
                if (token != generation || !active) return@setOnImageAvailableListener
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    if (token == generation && active) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()); buffer.get(bytes)
                        lastFrame = SystemClock.elapsedRealtime()
                        vision.acceptJpeg(bytes, lastFrame, respectExif = true)
                    }
                } finally { image.close() }
            }, main)
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (token != generation || !active) { camera.close(); return }
                    device = camera; opening = false
                    camera.createCaptureSession(listOf(output.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(configured: CameraCaptureSession) {
                            if (token != generation || !active) { configured.close(); return }
                            session = configured
                            lastFrame = SystemClock.elapsedRealtime()
                            capture(token)
                        }
                        override fun onConfigureFailed(failed: CameraCaptureSession) { failed.close(); fail(token) }
                    }, main)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); fail(token) }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); fail(token) }
            }, main)
        } catch (_: Exception) { fail(token) }
        // A stalled camera must not keep displaying a healthy state or old inference.
        main.postDelayed(object : Runnable {
            override fun run() {
                if (token != generation || !active) return
                if (SystemClock.elapsedRealtime() - lastFrame > 5000) fail(token)
                else main.postDelayed(this, 1000)
            }
        }, 5000)
    }

    private fun capture(token: Int) {
        if (token != generation || !active) return
        try {
            val camera = device ?: return
            val surface = reader?.surface ?: return
            @Suppress("DEPRECATION")
            val rotation = when (activity.windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0
            }
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_ORIENTATION, (sensorOrientation - rotation + 360) % 360)
            }.build()
            val requestedAt = SystemClock.elapsedRealtime()
            session?.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    // Target 10 capture requests/sec without overlapping still captures.
                    val delay = maxOf(0L, 100L - (SystemClock.elapsedRealtime() - requestedAt))
                    main.postDelayed({ capture(token) }, delay)
                }
                override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest, failure: CaptureFailure) { fail(token) }
            }, main)
        } catch (_: Exception) { fail(token) }
    }
    private fun fail(token: Int) {
        if (token != generation) return
        stop()
        status(R.string.phone_error)
    }
    fun stop() {
        active = false; generation++; opening = false
        session?.close(); session = null
        device?.close(); device = null
        reader?.close(); reader = null
        vision.stop()
    }
    fun close() { stop(); vision.close() }
}
