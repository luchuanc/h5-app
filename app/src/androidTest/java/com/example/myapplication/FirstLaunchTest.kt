package com.example.myapplication

import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.myapplication.h5.H5PackageManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FirstLaunchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @After fun cleanup() {
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
        context.getSharedPreferences("h5_bundle_prefs", 0).edit().clear().commit()
        context.getSharedPreferences("publishing_catalog", 0).edit().clear().commit()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun js(webView: WebView, script: String): String {
        val done = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync { webView.evaluateJavascript(script) { result = it; done.countDown() } }
        assertTrue("JavaScript callback", done.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun eventually(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15000
        while (!check()) {
            if (System.currentTimeMillis() >= deadline) fail("Condition did not become true")
            Thread.sleep(100)
        }
    }

    private fun pickerVisible(): Boolean {
        var visible = false
        instrumentation.runOnMainSync {
            visible = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is BundlePickerActivity }
        }
        return visible
    }

    @Test fun posterRequiresTenClicksAndHasNoInjectedConsole() {
        cleanup()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var web: WebView
            scenario.onActivity { web = requireNotNull(findWebView(it.window.decorView)) }
            eventually { js(web, "!!document.getElementById('heroTapTarget')") == "true" }
            assertEquals("false", js(web, "!!document.getElementById('__vconsole')"))
            // Separate calls avoid hiding an off-by-one error at the threshold.
            js(web, "for(var i=0;i<9;i++) document.getElementById('heroTapTarget').click()")
            instrumentation.waitForIdleSync()
            assertFalse(pickerVisible())
            js(web, "document.getElementById('heroTapTarget').click()")
            eventually { pickerVisible() }
        }
    }

    @Test fun selectedGameRejectsBridgeAndActivityEntryAndRetainsWebViewOnRotation() {
        cleanup()
        val manager = H5PackageManager(context)
        try {
            assertTrue(manager.selectLaunchTarget("game:xiangsu"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var web: WebView
                scenario.onActivity {
                    web = requireNotNull(findWebView(it.window.decorView))
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, it.requestedOrientation)
                }
                // A deterministic page isolates native lifecycle and bridge behavior from the network.
                instrumentation.runOnMainSync { web.loadData("<html><body>Lifecycle fixture</body></html>", "text/html", "utf-8") }
                eventually { js(web, "document.body && document.body.textContent") == "\"Lifecycle fixture\"" }
                assertTrue(js(web, "NativeBridgeHost.openBundlePicker()").contains("SELECTION_LOCKED"))
                assertFalse(pickerVisible())
                js(web, "window.rotationMarker = 'retained'")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertSame(web, findWebView(it.window.decorView)) }
                assertEquals("\"retained\"", js(web, "window.rotationMarker"))
                scenario.onActivity { it.startActivity(Intent(it, BundlePickerActivity::class.java)) }
                instrumentation.waitForIdleSync()
                assertFalse(pickerVisible())
                assertEquals("game:xiangsu", manager.getSelectedLaunchTargetId())
            }
        } finally { manager.shutdown() }
    }
}
