package com.example.myapplication

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.h5.H5PackageManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherAliasTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun clear() {
        context.getSharedPreferences("h5_bundle_prefs", 0).edit().clear().commit()
        context.getSharedPreferences("publishing_catalog", 0).edit().clear().commit()
    }
    @After fun reset() {
        clear()
        val manager = H5PackageManager(context)
        try { manager.syncLauncherAliasWithSelection() } finally { manager.shutdown() }
    }
    private fun onlyLauncher(): android.content.pm.ActivityInfo {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val matches = context.packageManager.queryIntentActivities(intent, 0)
        assertEquals("Exactly one enabled launcher", 1, matches.size)
        return matches.single().activityInfo
    }
    @Test fun defaultBuiltinAndAllPackagedGamesKeepOneLauncherAcrossRecreationAndReset() {
        reset()
        val default = onlyLauncher()
        assertTrue(default.name.endsWith("MainActivityPublishingAlias"))
        val registry = context.assets.open("publishing-launchers.json").bufferedReader().use { JSONObject(it.readText()) }
        val expected = mapOf("remote:lujia-ledger" to "${context.packageName}.MainActivityLujiaAlias",
            "remote:points" to "${context.packageName}.MainActivityPointsAlias") +
            registry.keys().asSequence().associateWith { registry.getString(it) }
        assertTrue(expected.size >= 5)
        for ((id, alias) in expected) {
            clear()
            val manager = H5PackageManager(context)
            try {
                assertTrue(manager.selectLaunchTarget(id))
                assertEquals(alias, onlyLauncher().name)
                if (id.startsWith("remote:")) assertNotEquals(default.icon, onlyLauncher().icon)
                val restarted = H5PackageManager(context)
                try { restarted.syncLauncherAliasWithSelection(); assertEquals(alias, onlyLauncher().name) }
                finally { restarted.shutdown() }
                // Same path used by Android's package-replaced broadcast.
                PackageUpdatedReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
                assertEquals(alias, onlyLauncher().name)
                assertEquals(id, manager.getSelectedLaunchTargetId())
                assertFalse(manager.selectLaunchTarget("game:xiangsu"))
            } finally { manager.shutdown() }
        }
        reset()
        assertEquals(default.name, onlyLauncher().name)
        assertEquals(default.icon, onlyLauncher().icon)
    }
    @Test fun remoteCatalogCannotInjectAliasAndNewGamesFallBackToDefault() {
        reset()
        val default = onlyLauncher()
        context.getSharedPreferences("publishing_catalog", 0).edit().putString("games", """[
            {"id":"new-game","name":"New game","url":"https://example.com/",
             "launcherAliasId":"lujia","icon":"https://example.com/icon.png"}
        ]""").commit()
        val manager = H5PackageManager(context)
        try {
            assertTrue(manager.selectLaunchTarget("game:new-game"))
            assertEquals(default.name, onlyLauncher().name)
            assertEquals(default.icon, onlyLauncher().icon)
            assertTrue(manager.hasSelectedLaunchTarget())
        } finally { manager.shutdown() }
    }
}
