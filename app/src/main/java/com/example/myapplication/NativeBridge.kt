package com.example.myapplication

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.h5.H5PackageManager
import com.example.myapplication.h5.LaunchBundleOption
import org.json.JSONArray
import org.json.JSONObject

class NativeBridge(
    private val activity: Activity,
    private val packageManager: H5PackageManager,
    private val webView: WebView,
    private val recordingManager: RecordingManager
) {
    @JavascriptInterface
    fun getRuntimeInfo(): String {
        val bundle = packageManager.resolveLaunchBundle()
        val data = JSONObject().apply {
            put("appVersion", BuildConfig.VERSION_NAME)
            put("containerVersion", BuildConfig.CONTAINER_VERSION)
            put("h5Mode", bundle.mode)
            put("h5Version", bundle.version)
            put("platform", "android")
            put("entryUrl", bundle.entryUrl)
            put("selectedLaunchTarget", packageManager.getSelectedLaunchTargetId())
            put("supportedApis", JSONArray().apply {
                put("getRuntimeInfo")
                put("getDeviceInfo")
                put("getPackageInfo")
                put("getAvailableBundles")
                put("openBundlePicker")
                put("toast")
                put("closeApp")
                put("openExternalUrl")
                put("requestAudioPermission")
                put("startRecording")
                put("stopRecording")
                put("cancelRecording")
                put("getRecordingState")
                put("readRecordingFile")
            })
        }
        return success(data)
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        val data = JSONObject().apply {
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("systemVersion", Build.VERSION.RELEASE)
            put("sdkInt", Build.VERSION.SDK_INT)
        }
        return success(data)
    }

    @JavascriptInterface
    fun getPackageInfo(): String {
        val bundle = packageManager.resolveLaunchBundle()
        val data = JSONObject().apply {
            put("mode", bundle.mode)
            put("version", bundle.version)
            put("entryUrl", bundle.entryUrl)
            put("installedAt", bundle.installedAt ?: JSONObject.NULL)
            put("source", bundle.source ?: JSONObject.NULL)
            put("selectedLaunchTarget", packageManager.getSelectedLaunchTargetId())
        }
        return success(data)
    }

    @JavascriptInterface
    fun getAvailableBundles(): String {
        val bundles = JSONArray()
        packageManager.getAvailableBundleOptions().forEach { option ->
            bundles.put(option.toJson())
        }
        return success(JSONObject().apply {
            put("bundles", bundles)
            put("selectedLaunchTarget", packageManager.getSelectedLaunchTargetId())
        })
    }

    @JavascriptInterface
    fun openBundlePicker(): String {
        activity.runOnUiThread {
            activity.startActivity(Intent(activity, BundlePickerActivity::class.java))
        }
        return success(null)
    }

    @JavascriptInterface
    fun toast(message: String?): String {
        val safeMessage = message?.takeIf { it.isNotBlank() } ?: return error(
            code = "INVALID_MESSAGE",
            message = "message is empty"
        )
        activity.runOnUiThread {
            Toast.makeText(activity, safeMessage, Toast.LENGTH_SHORT).show()
        }
        return success(null)
    }

    @JavascriptInterface
    fun closeApp(): String {
        activity.runOnUiThread { activity.finish() }
        return success(null)
    }

    @JavascriptInterface
    fun openExternalUrl(url: String?): String {
        val safeUrl = url?.takeIf { it.isNotBlank() } ?: return error(
            code = "INVALID_URL",
            message = "url is empty"
        )

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))
            activity.startActivity(intent)
            success(null)
        } catch (_: ActivityNotFoundException) {
            error("ACTIVITY_NOT_FOUND", "No activity can handle this url")
        } catch (_: Exception) {
            error("OPEN_URL_FAILED", "Failed to open external url")
        }
    }

    // ── Recording APIs ──────────────────────────────────────────────

    @JavascriptInterface
    fun requestAudioPermission(): String {
        val granted = ContextCompat.checkSelfPermission(
            activity, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            dispatchPermissionEvent(true)
            return success(JSONObject().apply { put("granted", true) })
        }

        activity.runOnUiThread {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )
        }
        return success(JSONObject().apply { put("granted", false) })
    }

    @JavascriptInterface
    fun startRecording(optionsJson: String?): String {
        val granted = ContextCompat.checkSelfPermission(
            activity, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            return error("NO_PERMISSION", "Audio recording permission not granted. Call requestAudioPermission() first.")
        }

        return recordingManager.startRecording(optionsJson)
    }

    @JavascriptInterface
    fun stopRecording(): String = recordingManager.stopRecording()

    @JavascriptInterface
    fun cancelRecording(): String = recordingManager.cancelRecording()

    @JavascriptInterface
    fun getRecordingState(): String = recordingManager.getRecordingState()

    @JavascriptInterface
    fun readRecordingFile(filePath: String?): String = recordingManager.readRecordingFile(filePath)

    fun onPermissionResult(granted: Boolean) {
        dispatchPermissionEvent(granted)
    }

    private fun dispatchPermissionEvent(granted: Boolean) {
        val payload = JSONObject().apply {
            put("type", "audioPermissionResult")
            put("granted", granted)
        }.toString().replace("'", "\\'")
        activity.runOnUiThread {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('audioPermissionResult', {detail: JSON.parse('$payload')}))",
                null
            )
        }
    }

    private fun success(data: JSONObject?): String =
        JSONObject().apply {
            put("success", true)
            put("data", data ?: JSONObject.NULL)
        }.toString()

    private fun error(code: String, message: String): String =
        JSONObject().apply {
            put("success", false)
            put("code", code)
            put("message", message)
        }.toString()

    private fun LaunchBundleOption.toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("title", title)
            put("description", description)
            put("version", version)
            put("entryUrl", entryUrl)
            put("mode", mode)
            put("source", source)
            put("installedAt", installedAt ?: JSONObject.NULL)
            put("launcherAliasId", launcherAliasId ?: JSONObject.NULL)
            put("appDisplayName", appDisplayName ?: JSONObject.NULL)
        }

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 1001
    }
}
