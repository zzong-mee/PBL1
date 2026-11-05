package com.guideeye.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Experiment sounds must follow the media volume described by the developer UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExperimentAudioRoutingTest {
    @Test fun allExperimentSoundsUseMediaVolume() {
        val context: Context = RuntimeEnvironment.getApplication()
        val distance = DistanceToneController(context, {})
        val hrtf = HrtfTestController(context) { _, _ -> }
        val spatial = TofSpatialController(context) {}
        val radar = RadarScanController(context) { _, _, _ -> }
        try {
            listOf(distance to "attributes", hrtf to "attributes",
                spatial to "attrs", radar to "attrs").forEach { (controller, fieldName) ->
                val field = controller.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
                val attributes = field.get(controller) as AudioAttributes
                assertEquals(controller.javaClass.simpleName,
                    AudioManager.STREAM_MUSIC, attributes.volumeControlStream)
            }
        } finally {
            distance.stop()
            hrtf.close()
            spatial.close()
            radar.close()
        }
    }
}
