package com.guideeye.app.accessibility

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import com.guideeye.app.R

object ButtonTouchFeedback {
    // Stock Button inherits performClick; this listener calls it explicitly on valid release.
    // Lint's custom-view override check does not apply to this unmodified framework widget.
    @SuppressLint("ClickableViewAccessibility")
    fun install(button: Button, announce: (String) -> Unit) {
        val accessibility = button.context.getSystemService(AccessibilityManager::class.java)
        val gesture = ReleaseGesture()
        button.setOnTouchListener { view, event ->
            if (accessibility.isTouchExplorationEnabled) {
                gesture.cancel()
                view.isPressed = false
                view.parent?.requestDisallowInterceptTouchEvent(false)
                // Let TalkBack and native Button handle accessibility activation.
                return@setOnTouchListener false
            }
            fun inside(index: Int): Boolean = index >= 0 &&
                event.getX(index) >= 0 && event.getX(index) < view.width &&
                event.getY(index) >= 0 && event.getY(index) < view.height
            fun cancelWithFeedback(newlyCancelled: Boolean = gesture.cancel()) {
                if (newlyCancelled) {
                    TouchHaptics.cancel(view)
                    announce(view.context.getString(R.string.button_cancelled))
                }
                view.isPressed = false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gesture.begin(event.getPointerId(0))
                    // Keep this entire gesture on the original button, even after leaving its bounds.
                    // Otherwise ScrollView intercepts MOVE and the user cannot deliberately cancel.
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    view.isPressed = true
                    TouchHaptics.contact(view)
                    announce(button.text.toString().replace("/", " 또는 "))
                }
                MotionEvent.ACTION_MOVE -> {
                    val newlyCancelled = gesture.move(inside(event.findPointerIndex(gesture.pointerId)))
                    if (newlyCancelled) cancelWithFeedback(true)
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> cancelWithFeedback()
                MotionEvent.ACTION_UP -> {
                    val releasedInside = inside(event.actionIndex)
                    if (!releasedInside) cancelWithFeedback()
                    val click = gesture.release(event.getPointerId(event.actionIndex), releasedInside)
                    view.isPressed = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    // Same click path as TalkBack, keyboard and Switch Access. Execute only once.
                    if (click && view.isEnabled) {
                        TouchHaptics.confirm(view)
                        view.performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    gesture.cancel()
                    view.isPressed = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            true
        }
    }
}
