package com.example.myapplication

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.os.Build
import org.json.JSONObject

object BundleAliasManager {
    const val ALIAS_AURORA = "aurora"
    const val ALIAS_RUNTIME = "runtime"
    const val ALIAS_DOWNLOADED = "downloaded"
    const val ALIAS_LUJIA = "lujia"
    const val ALIAS_POINTS = "points"
    const val ALIAS_CUSTOM = "custom"

    private const val TAG = "BundleAliasManager"

    fun applyAlias(context: Context, aliasId: String?, launchTargetId: String? = null) {
        val components = aliasComponents(context)
        val defaultAlias = if (BuildConfig.PUBLISHING_MANAGED) "publishing" else ALIAS_AURORA
        val resolvedAlias = when {
            launchTargetId != null && components.containsKey(launchTargetId) -> launchTargetId
            launchTargetId == "builtin:aurora" || launchTargetId?.startsWith("game:") == true -> defaultAlias
            aliasId != null && components.containsKey(aliasId) -> aliasId
            else -> defaultAlias
        }
        val pm = context.packageManager
        // Enable the destination first on older Android; API 33+ changes all aliases atomically.
        val changes = components.entries.sortedBy { it.key != resolvedAlias }.mapNotNull { (key, component) ->
            val state = if (key == resolvedAlias) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(component) == state) null else component to state
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (changes.isNotEmpty()) pm.setComponentEnabledSettings(changes.map { (component, state) ->
                PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            })
        } else {
            changes.forEach { (component, state) -> pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
        }
        Log.d(TAG, "Applied launcher alias: $resolvedAlias")
    }

    // Only the registry bundled in this APK can introduce a launcher component.
    // Catalog responses and cached JSON never provide component names or icon resource IDs.
    private fun gameAliases(context: Context): Map<String, ComponentName> = runCatching {
        val json = context.assets.open("publishing-launchers.json").bufferedReader().use { JSONObject(it.readText()) }
        json.keys().asSequence().associateWith { ComponentName(context.packageName, json.getString(it)) }
    }.getOrDefault(emptyMap())

    private fun aliasComponents(context: Context): Map<String, ComponentName> = mapOf(
        "publishing" to ComponentName(context, MainActivityPublishingAlias::class.java),
        ALIAS_AURORA to ComponentName(context, MainActivityAuroraAlias::class.java),
        ALIAS_RUNTIME to ComponentName(context, MainActivityRuntimeAlias::class.java),
        ALIAS_DOWNLOADED to ComponentName(context, MainActivityDownloadedAlias::class.java),
        ALIAS_LUJIA to ComponentName(context, MainActivityLujiaAlias::class.java),
        ALIAS_POINTS to ComponentName(context, MainActivityPointsAlias::class.java),
        ALIAS_CUSTOM to ComponentName(context, MainActivityCustomAlias::class.java)
    ) + gameAliases(context)
}

class MainActivityAuroraAlias

class MainActivityRuntimeAlias

class MainActivityDownloadedAlias

class MainActivityLujiaAlias

class MainActivityPointsAlias

class MainActivityCustomAlias

class MainActivityPublishingAlias
