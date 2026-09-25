package com.example.myapplication

import android.content.pm.ActivityInfo

class ImmersiveModeController(
    private val requestOrientation: (Int) -> Unit,
    private val setSystemBarsVisible: (Boolean) -> Unit
) {
    private var gameMode = false

    fun setGameMode(enabled: Boolean) {
        gameMode = enabled
        if (enabled) enterLandscapeFullscreen() else exitLandscapeFullscreen()
    }

    fun restoreGameFullscreen() {
        if (gameMode) enterLandscapeFullscreen()
    }

    fun enterLandscapeFullscreen() {
        requestOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        setSystemBarsVisible(false)
    }

    fun exitLandscapeFullscreen() {
        setSystemBarsVisible(true)
        requestOrientation(if (gameMode) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }
}
