package com.example.myapplication

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveModeControllerTest {
    @Test
    fun `enter requests sensor landscape and hides system bars`() {
        val events = mutableListOf<String>()
        val controller = ImmersiveModeController(
            requestOrientation = { events += "orientation:$it" },
            setSystemBarsVisible = { events += "system-bars:$it" }
        )

        controller.enterLandscapeFullscreen()

        assertEquals(
            listOf(
                "orientation:${ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE}",
                "system-bars:false"
            ),
            events
        )
    }

    @Test
    fun `exit restores portrait and system bars`() {
        val events = mutableListOf<String>()
        val controller = ImmersiveModeController(
            requestOrientation = { events += "orientation:$it" },
            setSystemBarsVisible = { events += "system-bars:$it" }
        )

        controller.exitLandscapeFullscreen()

        assertEquals(
            listOf(
                "system-bars:true",
                "orientation:${ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}"
            ),
            events
        )
    }
}
