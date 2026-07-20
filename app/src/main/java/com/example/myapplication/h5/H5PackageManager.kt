package com.example.myapplication.h5

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.BundleAliasManager
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

class H5PackageManager(
    private val context: Context
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val rootDir = File(context.filesDir, "h5")
    private val activeDir = File(rootDir, "active")
    private val activeMetaFile = File(activeDir, META_FILE_NAME)

    fun resolveLaunchBundle(): H5BundleInfo {
        ensureRootDir()

        val selected = getAvailableBundleOptions().firstOrNull { it.id == getSelectedLaunchTargetId() }
        return when {
            selected != null -> selected.toBundleInfo()
            else -> defaultBuiltinOption().toBundleInfo()
        }
    }

    fun getAvailableBundleOptions(): List<LaunchBundleOption> {
        val builtin = builtinBundleOptions()
        val customRemote = customRemoteOption()?.let { listOf(it) }.orEmpty()
        val local = localPackageOption()?.let { listOf(it) }.orEmpty()
        return builtin + customRemote + local
    }

    fun getSelectedLaunchTargetId(): String =
        prefs.getString(PREF_SELECTED_LAUNCH_TARGET, defaultBuiltinOption().id)
            ?: defaultBuiltinOption().id

    fun selectLaunchTarget(id: String): Boolean {
        val option = getAvailableBundleOptions().firstOrNull { it.id == id } ?: return false

        val stored = prefs.edit().putString(PREF_SELECTED_LAUNCH_TARGET, id).commit()
        if (stored) {
            BundleAliasManager.applyAlias(context, option.launcherAliasId)
        }
        return stored
    }

    fun getCustomRemoteUrl(): String =
        prefs.getString(PREF_CUSTOM_REMOTE_URL, "").orEmpty()

    fun saveCustomRemoteUrl(rawUrl: String): Boolean {
        val normalizedUrl = normalizeRemoteUrl(rawUrl) ?: return false
        val stored = prefs.edit()
            .putString(PREF_CUSTOM_REMOTE_URL, normalizedUrl)
            .putString(PREF_SELECTED_LAUNCH_TARGET, CUSTOM_REMOTE_OPTION_ID)
            .commit()
        if (stored) {
            BundleAliasManager.applyAlias(context, BundleAliasManager.ALIAS_CUSTOM)
        }
        return stored
    }

    fun syncLauncherAliasWithSelection() {
        val option = getAvailableBundleOptions().firstOrNull { it.id == getSelectedLaunchTargetId() }
            ?: defaultBuiltinOption()
        BundleAliasManager.applyAlias(context, option.launcherAliasId)
    }

    private fun builtInOption(
        id: String,
        fallbackTitle: String,
        fallbackDescription: String,
        assetRoot: String,
        entryUrl: String,
        fallbackAliasId: String,
        fallbackAppName: String,
        mode: String = MODE_BUILTIN
    ): LaunchBundleOption {
        val config = readAssetBundleConfig(assetRoot)
        return LaunchBundleOption(
            id = id,
            title = config?.title ?: fallbackTitle,
            description = config?.description ?: fallbackDescription,
            version = BuildConfig.BUILT_IN_H5_VERSION,
            entryUrl = config?.entryUrl ?: entryUrl,
            mode = mode,
            source = config?.entryUrl ?: assetRoot,
            launcherAliasId = config?.launcherAliasId ?: fallbackAliasId,
            appDisplayName = config?.appDisplayName ?: fallbackAppName
        )
    }

    private fun defaultBuiltinOption(): LaunchBundleOption =
        builtinBundleOptions().firstOrNull { it.id == "builtin:aurora" }
            ?: builtinBundleOptions().first()

    private fun builtinBundleOptions(): List<LaunchBundleOption> {
        val options = mutableListOf(
            builtInOption(
                id = "builtin:aurora",
                fallbackTitle = "Startup Poster",
                fallbackDescription = "Image-only startup bundle",
                assetRoot = "bundles/aurora",
                entryUrl = "file:///android_asset/bundles/aurora/www/index.html",
                fallbackAliasId = BundleAliasManager.ALIAS_AURORA,
                fallbackAppName = "H5 Poster"
            ),
            builtInOption(
                id = "builtin:midnight",
                fallbackTitle = "Runtime Lab",
                fallbackDescription = "Built-in test page for native bridge and bundle APIs",
                assetRoot = "bundles/midnight",
                entryUrl = "file:///android_asset/bundles/midnight/www/index.html",
                fallbackAliasId = BundleAliasManager.ALIAS_RUNTIME,
                fallbackAppName = "Runtime Lab"
            ),
            builtInOption(
                id = "remote:lujia-ledger",
                fallbackTitle = "\u9646\u5bb6\u8d26\u672c",
                fallbackDescription = "Remote H5 bundle loaded directly over HTTPS",
                assetRoot = "bundles/lujia",
                entryUrl = "https://money.lucc.site:8888/",
                fallbackAliasId = BundleAliasManager.ALIAS_LUJIA,
                fallbackAppName = "\u9646\u5bb6\u8d26\u672c",
                mode = MODE_REMOTE_URL
            ),
            builtInOption(
                id = "remote:points",
                fallbackTitle = "\u9646\u5955\u51e1\u79ef\u5206",
                fallbackDescription = "Remote H5 bundle loaded directly over HTTPS",
                assetRoot = "bundles/points",
                entryUrl = "https://point.lucc.site:8888/",
                fallbackAliasId = BundleAliasManager.ALIAS_POINTS,
                fallbackAppName = "\u9646\u5955\u51e1\u79ef\u5206",
                mode = MODE_REMOTE_URL
            )
        )

        if (BuildConfig.DEBUG) {
            options.add(
                1,
                builtInOption(
                    id = "builtin:debug",
                    fallbackTitle = "Debug Console",
                    fallbackDescription = "Container debug page with native bridge and recording APIs",
                    assetRoot = "www",
                    entryUrl = "file:///android_asset/www/index.html",
                    fallbackAliasId = BundleAliasManager.ALIAS_RUNTIME,
                    fallbackAppName = "Debug Console"
                )
            )
        }

        return options
    }

    private fun localPackageOption(): LaunchBundleOption? {
        val localBundle = readInstalledLocalBundle() ?: return null
        val config = readLocalBundleConfig()
        return LaunchBundleOption(
            id = LOCAL_OPTION_ID,
            title = config?.title ?: "Downloaded Package",
            description = config?.description ?: "Latest verified local package from remote manifest",
            version = localBundle.version,
            entryUrl = localBundle.entryUrl,
            mode = localBundle.mode,
            source = localBundle.source ?: "files/h5/active",
            installedAt = localBundle.installedAt,
            launcherAliasId = config?.launcherAliasId ?: BundleAliasManager.ALIAS_DOWNLOADED,
            appDisplayName = config?.appDisplayName ?: "Downloaded Bundle"
        )
    }

    private fun customRemoteOption(): LaunchBundleOption? {
        val remoteUrl = getCustomRemoteUrl().takeIf { it.isNotBlank() } ?: return null
        val host = runCatching { URI(remoteUrl).host.orEmpty() }.getOrDefault("").ifBlank {
            "Custom URL"
        }
        return LaunchBundleOption(
            id = CUSTOM_REMOTE_OPTION_ID,
            title = "Custom URL",
            description = "Open and persist a custom remote H5 page: $host",
            version = "custom",
            entryUrl = remoteUrl,
            mode = MODE_REMOTE_URL,
            source = remoteUrl,
            launcherAliasId = BundleAliasManager.ALIAS_CUSTOM,
            appDisplayName = "Custom H5"
        )
    }

    private fun normalizeRemoteUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return null
        }

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return null
        }
        if (uri.host.isNullOrBlank()) {
            return null
        }
        return uri.toString()
    }

    private fun ensureRootDir() {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    private fun readInstalledLocalBundle(): H5BundleInfo? {
        if (!activeMetaFile.exists()) {
            return null
        }

        return runCatching {
            val json = JSONObject(activeMetaFile.readText())
            H5BundleInfo(
                mode = json.optString("mode", MODE_LOCAL_PACKAGE),
                version = json.optString("version", BuildConfig.BUILT_IN_H5_VERSION),
                entryUrl = File(activeDir, json.getString("entryFile")).toBundleUrl(),
                installedAt = json.optLong("installedAt").takeIf { it > 0L },
                source = json.optString("source").takeIf { it.isNotBlank() }
            )
        }.getOrNull()?.takeIf { isBundleUsable(it) }
    }

    fun syncRemotePackageAsync(
        onSuccess: (H5BundleInfo) -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        val manifestUrl = BuildConfig.REMOTE_MANIFEST_URL
        if (manifestUrl.isBlank()) {
            return
        }

        executor.execute {
            try {
                val manifest = fetchManifest(manifestUrl)
                val currentLocalVersion = localPackageOption()?.version ?: "0.0.0"
                if (compareVersions(manifest.version, currentLocalVersion) <= 0) {
                    Log.d(TAG, "Skip bundle sync, local package is up to date")
                    return@execute
                }

                val stageDir = File(rootDir, "stage-${System.currentTimeMillis()}")
                stageDir.mkdirs()
                val archiveFile = File(rootDir, "bundle-${manifest.version}.zip")
                downloadToFile(manifest.packageUrl, archiveFile)

                if (manifest.sha256.isNotBlank()) {
                    val actualHash = archiveFile.sha256()
                    if (!actualHash.equals(manifest.sha256, ignoreCase = true)) {
                        archiveFile.delete()
                        stageDir.deleteRecursively()
                        throw IllegalStateException("sha256 mismatch")
                    }
                }

                unzipToDirectory(archiveFile, stageDir)
                val entryFile = File(stageDir, manifest.entryFile)
                if (!entryFile.exists()) {
                    archiveFile.delete()
                    stageDir.deleteRecursively()
                    throw IllegalStateException("entry file missing: ${manifest.entryFile}")
                }

                val bundleInfo = H5BundleInfo(
                    mode = MODE_LOCAL_PACKAGE,
                    version = manifest.version,
                    entryUrl = entryFile.toBundleUrl(),
                    installedAt = System.currentTimeMillis(),
                    source = manifest.packageUrl
                )

                val bundleConfig = readStageBundleConfig(stageDir)
                writeBundleMeta(stageDir, bundleInfo, bundleConfig)
                replaceActiveBundle(stageDir)
                archiveFile.delete()

                onSuccess(resolveLaunchBundle())
            } catch (throwable: Throwable) {
                onFailure(throwable)
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun isBundleUsable(bundle: H5BundleInfo): Boolean {
        if (bundle.mode != MODE_LOCAL_PACKAGE) {
            return true
        }

        val entryFile = File(UriPathParser.pathFromFileUrl(bundle.entryUrl))
        return entryFile.exists()
    }

    private fun fetchManifest(manifestUrl: String): RemoteManifest {
        val connection = URL(manifestUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/json")
        return connection.inputStream.bufferedReader().use { reader ->
            try {
                val json = JSONObject(reader.readText())
                val version = json.getString("version")
                val downloadUrl = URL(URL(manifestUrl), json.getString("downloadUrl")).toString()
                val sha256 = json.optString("sha256")
                val entryFile = json.optString("entryFile", "index.html")
                RemoteManifest(version, downloadUrl, sha256, entryFile)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun downloadToFile(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        connection.inputStream.use { input ->
            try {
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun unzipToDirectory(archiveFile: File, targetDir: File) {
        ZipInputStream(archiveFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val destination = File(targetDir, entry.name)
                val canonicalRoot = targetDir.canonicalPath + File.separator
                val canonicalDestination = destination.canonicalPath
                if (!canonicalDestination.startsWith(canonicalRoot)) {
                    throw IllegalStateException("Blocked zip entry outside target dir")
                }

                if (entry.isDirectory) {
                    destination.mkdirs()
                } else {
                    destination.parentFile?.mkdirs()
                    destination.outputStream().buffered().use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun writeBundleMeta(
        stageDir: File,
        bundleInfo: H5BundleInfo,
        bundleConfig: BundleConfig?
    ) {
        val relativeEntry = stageDir.toPath().relativize(
            File(UriPathParser.pathFromFileUrl(bundleInfo.entryUrl)).toPath()
        ).toString().replace(File.separatorChar, '/')

        val json = JSONObject().apply {
            put("mode", bundleInfo.mode)
            put("version", bundleInfo.version)
            put("entryFile", relativeEntry)
            put("installedAt", bundleInfo.installedAt ?: System.currentTimeMillis())
            put("source", bundleInfo.source ?: JSONObject.NULL)
            put("title", bundleConfig?.title ?: JSONObject.NULL)
            put("description", bundleConfig?.description ?: JSONObject.NULL)
            put("launcherAliasId", bundleConfig?.launcherAliasId ?: JSONObject.NULL)
            put("appDisplayName", bundleConfig?.appDisplayName ?: JSONObject.NULL)
        }

        File(stageDir, META_FILE_NAME).writeText(json.toString())
    }

    private fun readAssetBundleConfig(assetRoot: String): BundleConfig? =
        runCatching {
            context.assets.open("$assetRoot/$BUNDLE_CONFIG_FILE_NAME").bufferedReader().use { reader ->
                parseBundleConfig(JSONObject(reader.readText()))
            }
        }.getOrNull()

    private fun readLocalBundleConfig(): BundleConfig? =
        runCatching {
            val json = JSONObject(activeMetaFile.readText())
            parseBundleConfig(json)
        }.getOrNull()

    private fun readStageBundleConfig(stageDir: File): BundleConfig? {
        val file = File(stageDir, BUNDLE_CONFIG_FILE_NAME)
        if (!file.exists()) {
            return null
        }
        return runCatching {
            parseBundleConfig(JSONObject(file.readText()))
        }.getOrNull()
    }

    private fun parseBundleConfig(json: JSONObject): BundleConfig {
        val launcher = json.optJSONObject("launcher")
        return BundleConfig(
            title = json.optString("title").takeIf { it.isNotBlank() },
            description = json.optString("description").takeIf { it.isNotBlank() },
            launcherAliasId = launcher?.optString("aliasId")?.takeIf { it.isNotBlank() }
                ?: json.optString("launcherAliasId").takeIf { it.isNotBlank() },
            appDisplayName = launcher?.optString("appName")?.takeIf { it.isNotBlank() }
                ?: json.optString("appDisplayName").takeIf { it.isNotBlank() },
            entryUrl = json.optString("entryUrl").takeIf { it.isNotBlank() }
        )
    }

    private fun replaceActiveBundle(stageDir: File) {
        val backupDir = File(rootDir, "backup")
        if (backupDir.exists()) {
            backupDir.deleteRecursively()
        }
        if (activeDir.exists()) {
            if (!activeDir.renameTo(backupDir)) {
                activeDir.copyRecursively(backupDir, overwrite = true)
                activeDir.deleteRecursively()
            }
        }
        if (!stageDir.renameTo(activeDir)) {
            stageDir.copyRecursively(activeDir, overwrite = true)
            stageDir.deleteRecursively()
        }
        backupDir.deleteRecursively()
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split('.').map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until maxSize) {
            val leftValue = leftParts.getOrElse(index) { 0 }
            val rightValue = rightParts.getOrElse(index) { 0 }
            if (leftValue != rightValue) {
                return leftValue.compareTo(rightValue)
            }
        }
        return 0
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.toBundleUrl(): String = Uri.fromFile(this).toString()

    private data class RemoteManifest(
        val version: String,
        val packageUrl: String,
        val sha256: String,
        val entryFile: String
    )

    private data class BundleConfig(
        val title: String?,
        val description: String?,
        val launcherAliasId: String?,
        val appDisplayName: String?,
        val entryUrl: String?
    )

    private object UriPathParser {
        fun pathFromFileUrl(value: String): String = java.net.URI(value).path
    }

    companion object {
        private const val TAG = "H5PackageManager"
        private const val META_FILE_NAME = "bundle_meta.json"
        private const val BUNDLE_CONFIG_FILE_NAME = "bundle.json"
        private const val MODE_BUILTIN = "builtin"
        private const val MODE_REMOTE_URL = "remote_url"
        private const val MODE_LOCAL_PACKAGE = "local_package"
        private const val PREFS_NAME = "h5_bundle_prefs"
        private const val PREF_SELECTED_LAUNCH_TARGET = "selected_launch_target"
        private const val PREF_CUSTOM_REMOTE_URL = "custom_remote_url"
        private const val LOCAL_OPTION_ID = "local:downloaded"
        private const val CUSTOM_REMOTE_OPTION_ID = "remote:custom"

        fun restartApp(activity: Activity) {
            val launchIntent = activity.packageManager
                .getLaunchIntentForPackage(activity.packageName)
                ?.component
                ?.let { Intent.makeRestartActivityTask(it) }
                ?: return

            activity.startActivity(launchIntent)
            activity.finishAffinity()
            Runtime.getRuntime().exit(0)
        }
    }
}
