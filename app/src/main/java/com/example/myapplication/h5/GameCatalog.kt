package com.example.myapplication.h5

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Stable game IDs are persisted separately from addresses, so domain changes can be refreshed. */
class GameCatalog(context: Context) {
    private val prefs = context.getSharedPreferences("publishing_catalog", Context.MODE_PRIVATE)
    private val bundled = runCatching {
        context.assets.open("publishing.json").bufferedReader().use { JSONObject(it.readText()) }
    }.getOrElse { JSONObject() }

    init { migrateBundledAddress() }

    fun url(): String = prefs.getString("url", null) ?: bundled.optString("catalogUrl")
    fun saveUrl(value: String): Boolean {
        val normalized = validUrl(value.trim()) ?: return false
        return prefs.edit().putString("url", normalized).commit()
    }

    private fun migrateBundledAddress() {
        val endpoint = validUrl(bundled.optString("catalogUrl")) ?: return
        val previousBundle = prefs.getString("bundledCatalogUrl", null)
        if (previousBundle == endpoint) return
        val previous = mutableSetOf<String>()
        previousBundle?.let { previous.add(it) }
        bundled.optJSONArray("previousCatalogUrls")?.let { urls ->
            for (i in 0 until urls.length()) validUrl(urls.optString(i))?.let { previous.add(it) }
        }
        previous.remove(endpoint)
        val saved = prefs.getString("url", null)
        val edit = prefs.edit().putString("bundledCatalogUrl", endpoint)
        // Only migrate known platform endpoints; an unrelated custom catalog stays selected.
        if (saved == null || saved in previous) {
            val target = URI(endpoint)
            val origins = previous.map { URI(it) }
            fun migrateGame(game: JSONObject): JSONObject {
                val address = validUrl(game.optString("url"))?.let(::URI) ?: return game
                if (origins.any { it.scheme == address.scheme && it.rawAuthority == address.rawAuthority }) {
                    game.put("url", URI(target.scheme, target.rawAuthority, address.path, address.query, address.fragment).toASCIIString())
                }
                return game
            }
            runCatching { prefs.getString("games", null)?.let(::JSONArray) }.getOrNull()?.let { games ->
                for (i in 0 until games.length()) games.optJSONObject(i)?.let(::migrateGame)
                edit.putString("games", games.toString())
            }
            runCatching { prefs.getString("selected", null)?.let(::JSONObject) }.getOrNull()?.let {
                edit.putString("selected", migrateGame(it).toString())
            }
            edit.putString("url", endpoint)
            edit.putString("baseUrl", URI(target.scheme, target.rawAuthority, null, null, null).toASCIIString())
        }
        edit.commit()
    }
    fun defaultId(): String? = when {
        bundled.optBoolean("customGameUrl") -> "platform:default"
        bundled.optString("defaultGameId").isNotBlank() -> "game:" + bundled.optString("defaultGameId")
        bundled.optString("gameUrl").isNotBlank() -> "platform:default"
        else -> null
    }
    fun remember(option: LaunchBundleOption) {
        if (option.id.startsWith("game:") || option.id == "platform:default") {
            prefs.edit().putString("selected", JSONObject().apply {
                put("id", option.id.removePrefix("game:")); put("name", option.title)
                put("url", option.entryUrl); put("version", option.version)
            }.toString()).apply()
        }
    }
    fun options(): List<LaunchBundleOption> {
        val cached = prefs.getString("games", null)
        val array = runCatching { cached?.let { JSONArray(it) } }.getOrNull()
            ?: bundled.optJSONArray("games") ?: JSONArray()
        val result = parseGames(array).toMutableList()
        // Keep the selected entry available offline or if it has been removed from the current list.
        runCatching { prefs.getString("selected", null)?.let { JSONObject(it) } }.getOrNull()?.let {
            val option = parseGame(it)
            if (option != null && result.none { game -> game.id == option.id }) result.add(option)
        }
        val defaultUrl = validUrl(bundled.optString("gameUrl"))?.let(::currentAddress)
        if (defaultUrl != null) {
            val id = defaultId() ?: "platform:default"
            if (result.none { it.id == id }) result.add(0, LaunchBundleOption(
                id, bundled.optString("appName", "默认游戏"), "平台配置的默认游戏", bundled.optString("versionName", "1.0.0"),
                defaultUrl, H5PackageManager.MODE_REMOTE_URL, defaultUrl
            ))
        }
        return result
    }
    fun refresh(): Int {
        val endpoint = validUrl(url()) ?: error("请填写游戏列表地址")
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6000; connection.readTimeout = 6000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) error("游戏列表请求失败（${connection.responseCode}）")
            val bytes = connection.inputStream.use { it.readBytesLimited(512 * 1024) }
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            if (json.optInt("version") != 1 || !json.has("games")) error("游戏列表格式无效")
            val games = json.getJSONArray("games")
            val options = parseGames(games)
            val edit = prefs.edit().putString("games", games.toString())
            validUrl(json.optString("baseUrl"))?.let { edit.putString("baseUrl", it) }
            val selected = runCatching { prefs.getString("selected", null)?.let { JSONObject(it) } }.getOrNull()
            options.firstOrNull { it.id == "game:" + selected?.optString("id") }?.let { option ->
                selected?.put("url", option.entryUrl)?.put("version", option.version)
                edit.putString("selected", selected.toString())
            }
            validUrl(json.optString("catalogUrl"))?.let { edit.putString("url", it) }
            edit.commit()
            return options.size
        } finally { connection.disconnect() }
    }
    private fun currentAddress(value: String): String {
        val origin = runCatching { URI(bundled.optString("catalogUrl")) }.getOrNull() ?: return value
        val current = runCatching { URI(prefs.getString("baseUrl", "")) }.getOrNull() ?: return value
        val address = URI(value)
        if (current.host.isNullOrBlank() || address.scheme != origin.scheme || address.rawAuthority != origin.rawAuthority) return value
        return URI(current.scheme, current.rawAuthority, address.path, address.query, address.fragment).toASCIIString()
    }

    private fun parseGames(array: JSONArray): List<LaunchBundleOption> {
        require(array.length() <= 500) { "游戏列表过大" }
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::parseGame) }.distinctBy { it.id }
    }
    private fun parseGame(json: JSONObject): LaunchBundleOption? {
        val id = json.optString("id")
        if (!Regex("[a-zA-Z][a-zA-Z0-9-]{1,39}").matches(id)) return null
        val url = validUrl(json.optString("url")) ?: return null
        return LaunchBundleOption("game:$id", json.optString("name", id), "发布平台游戏 · 选择后记住为启动首页", json.optString("version", "latest").take(12), url, H5PackageManager.MODE_REMOTE_URL, url)
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer); if (count < 0) break
            require(output.size() + count <= limit) { "游戏列表过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    companion object {
        fun validUrl(value: String): String? = runCatching {
            val uri = URI(value)
            value.takeIf { uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null }
        }.getOrNull()
    }
}
