package com.example.myapplication

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveModeControllerTest {
    @Test
    fun `game stays landscape when H5 exits fullscreen and restores on return`() {
        var orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        var barsVisible = true
        val controller = ImmersiveModeController({ orientation = it }, { barsVisible = it })
        controller.setGameMode(true)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, orientation)
        assertEquals(false, barsVisible)
        controller.exitLandscapeFullscreen()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, orientation)
        assertEquals(true, barsVisible)
        controller.restoreGameFullscreen()
        assertEquals(false, barsVisible)
        controller.setGameMode(false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, orientation)
        assertEquals(true, barsVisible)
        controller.restoreGameFullscreen()
        assertEquals(true, barsVisible)
    }

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
