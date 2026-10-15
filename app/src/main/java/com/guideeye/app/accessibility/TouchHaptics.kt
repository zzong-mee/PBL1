package com.guideeye.app.accessibility

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Platform effects respect system touch-feedback preferences; no forced vibration. */
object TouchHaptics {
    fun contact(view: View) { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun confirm(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    }
    fun cancel(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    }
}
