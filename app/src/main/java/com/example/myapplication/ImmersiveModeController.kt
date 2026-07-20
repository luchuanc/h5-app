package com.example.myapplication

import android.content.pm.ActivityInfo

class ImmersiveModeController(
    private val requestOrientation: (Int) -> Unit,
    private val setSystemBarsVisible: (Boolean) -> Unit
) {
    fun enterLandscapeFullscreen() {
        requestOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        setSystemBarsVisible(false)
    }

    fun exitLandscapeFullscreen() {
        setSystemBarsVisible(true)
        requestOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }
}
