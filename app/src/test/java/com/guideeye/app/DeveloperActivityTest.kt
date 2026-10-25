package com.guideeye.app

import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeveloperActivityTest {
    @Test fun opensDeveloperScreenWithoutUsbAndReturns() {
        val controller = Robolectric.buildActivity(DeveloperActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val activity = controller.get()
        assertFalse(activity.findViewById<Button>(R.id.radar_toggle).text.isEmpty())
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun changingEitherGridRotationRevokesMountConfirmation() {
        val controller = Robolectric.buildActivity(DeveloperActivity::class.java).setup()
        val activity = controller.get()
        val confirmation = activity.findViewById<CheckBox>(R.id.tof_mount_confirm)
        for (id in listOf(R.id.tof_rotation_a, R.id.tof_rotation_b)) {
            confirmation.isChecked = true
            activity.findViewById<Spinner>(id).setSelection(1)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(confirmation.isChecked)
        }
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
