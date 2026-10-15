package com.guideeye.app.accessibility

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Button

/**
 * Adds feedback on contact, preserving Button's native release-to-click and scroll/cancel behavior.
 * Returning false is intentional: keyboard, Switch Access and performClick still use the same action.
 */
object ButtonTouchFeedback {
    // This observer never consumes a touch or detects a click. Button.onTouchEvent performs
    // the click itself; calling performClick here as well would execute the action twice.
    @SuppressLint("ClickableViewAccessibility")
    fun install(button: Button, announce: (String) -> Unit) {
        val accessibility = button.context.getSystemService(AccessibilityManager::class.java)
        button.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && view.isEnabled &&
                !accessibility.isTouchExplorationEnabled) {
                // Respect the device's touch-feedback settings instead of forcing vibration.
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                announce(button.text.toString().replace("/", " 또는 "))
            }
            // Native Button executes on ACTION_UP, not DOWN; leaving/scrolling cancels the click.
            false
        }
    }
}
