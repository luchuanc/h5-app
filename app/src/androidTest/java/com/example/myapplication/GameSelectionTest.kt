package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.h5.H5PackageManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class GameSelectionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @After fun clearPreferences() {
        context.getSharedPreferences("publishing_catalog", 0).edit().clear().commit()
        context.getSharedPreferences("h5_bundle_prefs", 0).edit().clear().commit()
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
