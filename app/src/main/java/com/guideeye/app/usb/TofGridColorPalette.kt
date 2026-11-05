package com.guideeye.app.usb

import android.graphics.Color

/**
 * Developer-only distance heatmap palette.
 * All distances are millimeters; invalid/expired readings are null.
 */
object TofGridColorPalette {
    val invalidColor: Int = Color.rgb(80, 87, 99)

    fun backgroundColor(mm: Int?): Int = when {
        mm == null || mm <= 0 -> invalidColor
        mm <= 100 -> Color.rgb(211, 47, 47)
        mm <= 200 -> Color.rgb(245, 124, 0)
        mm <= 300 -> Color.rgb(251, 192, 45)
        mm <= 500 -> Color.rgb(124, 179, 66)
        mm <= 1000 -> Color.rgb(67, 160, 71)
        else -> Color.rgb(25, 118, 210)
    }

    fun textColor(mm: Int?): Int =
        if (mm != null && mm in 201..500) Color.rgb(24, 35, 28) else Color.WHITE
}
