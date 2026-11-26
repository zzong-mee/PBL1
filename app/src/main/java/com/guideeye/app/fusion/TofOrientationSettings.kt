package com.guideeye.app.fusion

import android.content.Context

/** Raw sensor A/B orientation, independent of optical calibration. */
data class TofOrientationSettings(
    val aTurns: Int = 0, val aMirror: Boolean = false,
    val bTurns: Int = 0, val bMirror: Boolean = false,
    val swap: Boolean = false
) {
    val orientations get() = listOf(aTurns to aMirror, bTurns to bMirror)

    fun save(context: Context) {
        context.getSharedPreferences("tof_orientation", Context.MODE_PRIVATE).edit()
            .putInt("a_turns", aTurns.coerceIn(0,3))
            .putBoolean("a_mirror", aMirror)
            .putInt("b_turns", bTurns.coerceIn(0,3))
            .putBoolean("b_mirror", bMirror)
            .putBoolean("swap", swap).apply()
    }

    companion object {
        fun load(context: Context): TofOrientationSettings {
            val p = context.getSharedPreferences("tof_orientation", Context.MODE_PRIVATE)
            return TofOrientationSettings(
                p.getInt("a_turns",0).coerceIn(0,3), p.getBoolean("a_mirror",false),
                p.getInt("b_turns",0).coerceIn(0,3), p.getBoolean("b_mirror",false),
                p.getBoolean("swap",false))
        }
    }
}
