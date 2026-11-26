package com.guideeye.app.speech

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.widget.*
import java.util.concurrent.Executors

class MeasuredHrtfTest(private val activity: Activity) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val audio = StereoWarningAudio()
    @Volatile private var renderer: HrtfRenderer? = null
    @Volatile private var loaded = false
    private var generation = 0L
    private var dialog: AlertDialog? = null
    init { worker.execute { renderer = HrirAssetLoader.load(activity.applicationContext); loaded = true } }

    fun show(legacyAngle: Int, beforePlay: () -> Unit, sweepDefault: Boolean = false) {
        stop()
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32,16,32,16) }
        val status = TextView(activity)
        fun slider(label: String, max: Int, offset: Int, initial: Int): SeekBar {
            val text = TextView(activity)
            panel.addView(text)
            return SeekBar(activity).apply {
                this.max = max; progress = initial + offset
                text.text = "$label: ${progress-offset}°"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(b: SeekBar?, p: Int, user: Boolean) { text.text = "$label: ${p-offset}°" }
                    override fun onStartTrackingTouch(b: SeekBar?) {}
                    override fun onStopTrackingTouch(b: SeekBar?) {}
                })
                panel.addView(this)
            }
        }
        val az = slider("방위각 (오른쪽 +)",360,180,((180-legacyAngle)%360+360)%360-180)
        val el = slider("고도각",180,90,0)
        val danger = CheckBox(activity).apply { text = "위험 450ms (해제: 주의 150ms)"; isChecked = true }
        val pan = CheckBox(activity).apply { text = "스테레오 패닝 비교" }
        val sweep = CheckBox(activity).apply { text = "8방향 순차 재생"; isChecked = sweepDefault }
        panel.addView(danger); panel.addView(pan); panel.addView(sweep); panel.addView(status)
        val play = Button(activity).apply { text = "재생" }
        panel.addView(play)
        play.setOnClickListener {
            beforePlay()
            generation++; audio.stop()
            val token = generation
            val d = danger.isChecked; val p = pan.isChecked; val e = el.progress-90
            val angles = if (sweep.isChecked) listOf(0,45,90,135,180,-135,-90,-45) else listOf(az.progress-180)
            fun next(index: Int) {
                if (token != generation || index >= angles.size) return
                val a = angles[index]
                worker.execute {
                    val start = android.os.SystemClock.elapsedRealtime()
                    val h = renderer
                    val pcm = if (!p && h != null) runCatching { h.render(d,a.toDouble(),e.toDouble()) }.getOrNull() else null
                    val elapsed = android.os.SystemClock.elapsedRealtime()-start
                    main.post {
                        if (token == generation) {
                            val ok = if (pcm != null) audio.playPcm(pcm) else if (p) audio.play(d,a.toDouble()) else false
                            status.text = if (ok) "${if(p) "PAN" else "HRTF"} · $a° / $e° · 연산 ${elapsed}ms"
                                else if (!loaded) "HRIR 로딩 중" else "재생 실패 또는 HRIR 파일 없음"
                            if (ok) main.postDelayed({ next(index+1) }, 1800)
                        }
                    }
                }
            }
            next(0)
        }
        dialog = AlertDialog.Builder(activity).setTitle("실측 HRTF 경고음 테스트")
            .setView(panel).setNegativeButton("닫기",null).create().also {
                it.setOnDismissListener { generation++; audio.stop(); dialog = null }
                it.show()
            }
    }
    fun stop() { generation++; audio.stop(); dialog?.dismiss(); dialog = null }
    override fun close() { stop(); worker.shutdownNow(); audio.close() }
}
