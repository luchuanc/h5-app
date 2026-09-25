package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.h5.H5PackageManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class GameSelectionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @After fun clearPreferences() {
        context.getSharedPreferences("publishing_catalog", 0).edit().clear().commit()
        context.getSharedPreferences("h5_bundle_prefs", 0).edit().clear().commit()
    }
    @Test fun productionDefaultsIncludeAllGames() {
        clearPreferences()
        val manager = H5PackageManager(context)
        try {
            assertEquals("https://games.lucc.site:8888/api/catalog", manager.gameCatalog.url())
            assertEquals(setOf("game:zizou", "game:xiangsu", "game:backHome"), manager.gameCatalog.options().map { it.id }.toSet())
            assertEquals("https://games.lucc.site:8888/xiangsu/", manager.resolveLaunchBundle().entryUrl)
        } finally { manager.shutdown() }
    }

    @Test fun upgradingMigratesCachedAndSelectedAddressesBeforeNetworkAccess() {
        clearPreferences()
        val old = "http://192.168.31.155:8200"
        val games = JSONArray().put(JSONObject().put("id", "backHome").put("url", "$old/backHome/?save=1#town"))
            .put(JSONObject().put("id", "external").put("url", "https://other.example.com/demo/"))
        context.getSharedPreferences("publishing_catalog", 0).edit()
            .putString("url", "$old/api/catalog").putString("baseUrl", old)
            .putString("games", games.toString())
            .putString("selected", JSONObject().put("id", "zizou").put("url", "$old/zizou/").toString()).commit()
        context.getSharedPreferences("h5_bundle_prefs", 0).edit().putString("selected_launch_target", "game:backHome").commit()
        repeat(2) {
            val manager = H5PackageManager(context)
            try {
                assertEquals("game:backHome", manager.getSelectedLaunchTargetId())
                assertEquals("https://games.lucc.site:8888/api/catalog", manager.gameCatalog.url())
                assertEquals("https://games.lucc.site:8888/backHome/?save=1#town", manager.resolveLaunchBundle().entryUrl)
                assertEquals("https://games.lucc.site:8888/zizou/", manager.gameCatalog.options().first { it.id == "game:zizou" }.entryUrl)
                assertEquals("https://other.example.com/demo/", manager.gameCatalog.options().first { it.id == "game:external" }.entryUrl)
            } finally { manager.shutdown() }
        }
    }

    @Test fun customCatalogIsPreservedWhenTheAppDefaultChanges() {
        clearPreferences()
        val custom = "https://custom.example.com/api/catalog"
        context.getSharedPreferences("publishing_catalog", 0).edit().putString("url", custom).commit()
        val manager = H5PackageManager(context)
        try { assertEquals(custom, manager.gameCatalog.url()) } finally { manager.shutdown() }
    }

    @Test fun refreshedAddressesAndSelectionSurviveRecreationAndNetworkFailure() {
        clearPreferences()
        val server = ServerSocket(0)
        val endpoint = "http://127.0.0.1:${server.localPort}/api/catalog"
        val responder = thread {
            repeat(2) { revision ->
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* read request headers */ }
                    val origin = if (revision == 0) "http://old.example.com:8200" else "https://games.example.com:8888"
                    val body = """{"version":1,"baseUrl":"$origin","catalogUrl":"$endpoint","games":[{"id":"qa-game","name":"测试游戏","url":"$origin/qa-game/","version":"$revision"}]}""".toByteArray()
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray() + body)
                }
            }
        }
        val first = H5PackageManager(context)
        try {
            assertTrue(first.gameCatalog.saveUrl(endpoint))
            assertEquals(1, first.gameCatalog.refresh())
            assertTrue(first.selectLaunchTarget("game:qa-game"))
            assertEquals("http://old.example.com:8200/qa-game/", first.resolveLaunchBundle().entryUrl)
            assertEquals(1, first.gameCatalog.refresh())
            responder.join(5000)
            server.close()
            val restarted = H5PackageManager(context)
            try {
                assertEquals("game:qa-game", restarted.getSelectedLaunchTargetId())
                assertEquals("https://games.example.com:8888/qa-game/", restarted.resolveLaunchBundle().entryUrl)
                assertTrue(runCatching { restarted.gameCatalog.refresh() }.isFailure)
                assertEquals("https://games.example.com:8888/qa-game/", restarted.resolveLaunchBundle().entryUrl)
            } finally { restarted.shutdown() }
        } finally { first.shutdown(); server.close() }
    }
}
