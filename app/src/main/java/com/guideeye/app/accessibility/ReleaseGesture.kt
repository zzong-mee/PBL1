package com.guideeye.app.accessibility

/** Once cancelled, returning inside cannot re-arm the same touch. Coordinates are view-local. */
internal class ReleaseGesture {
    var pointerId: Int = -1
        private set
    private var cancelled = true

    fun begin(id: Int) { pointerId = id; cancelled = false }
    fun cancel(): Boolean {
        val changed = pointerId != -1 && !cancelled
        cancelled = true
        return changed
    }
    fun move(inside: Boolean): Boolean = if (!inside) cancel() else false
    fun release(id: Int, inside: Boolean): Boolean {
        val click = pointerId != -1 && pointerId == id && !cancelled && inside
        pointerId = -1
        cancelled = true
        return click
    }
}
