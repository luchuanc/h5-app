package com.example.myapplication

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object BundleAliasManager {
    const val ALIAS_AURORA = "aurora"
    const val ALIAS_RUNTIME = "runtime"
    const val ALIAS_DOWNLOADED = "downloaded"
    const val ALIAS_LUJIA = "lujia"
    const val ALIAS_POINTS = "points"
    const val ALIAS_CUSTOM = "custom"

    private const val TAG = "BundleAliasManager"

    fun applyAlias(context: Context, aliasId: String?) {
        val resolvedAlias = if (BuildConfig.PUBLISHING_MANAGED) "publishing" else when (aliasId) {
            ALIAS_AURORA,
            ALIAS_RUNTIME,
            ALIAS_DOWNLOADED,
            ALIAS_LUJIA,
            ALIAS_POINTS,
            ALIAS_CUSTOM -> aliasId
            else -> ALIAS_AURORA
        }

        val packageManager = context.packageManager
        aliasComponents(context).forEach { (key, component) ->
            val newState = if (key == resolvedAlias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            if (packageManager.getComponentEnabledSetting(component) != newState) {
                packageManager.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }

        Log.d(TAG, "Applied launcher alias: $resolvedAlias")
    }

    private fun aliasComponents(context: Context): Map<String, ComponentName> = mapOf(
        "publishing" to ComponentName(context, MainActivityPublishingAlias::class.java),
        ALIAS_AURORA to ComponentName(context, MainActivityAuroraAlias::class.java),
        ALIAS_RUNTIME to ComponentName(context, MainActivityRuntimeAlias::class.java),
        ALIAS_DOWNLOADED to ComponentName(context, MainActivityDownloadedAlias::class.java),
        ALIAS_LUJIA to ComponentName(context, MainActivityLujiaAlias::class.java),
        ALIAS_POINTS to ComponentName(context, MainActivityPointsAlias::class.java),
        ALIAS_CUSTOM to ComponentName(context, MainActivityCustomAlias::class.java)
    )
}

class MainActivityAuroraAlias

class MainActivityRuntimeAlias

class MainActivityDownloadedAlias

class MainActivityLujiaAlias

class MainActivityPointsAlias

class MainActivityCustomAlias

class MainActivityPublishingAlias
