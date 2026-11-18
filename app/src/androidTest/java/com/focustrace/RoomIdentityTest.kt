package com.focustrace

import com.focustrace.studyroom.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class RoomIdentityTest {
    @Test fun restoredIdentityAndNewEndpointAreVerifiedBeforeData() = runBlocking<Unit> {
        val key = UUID.randomUUID().toString()
        try {
            val (first, pin) = RoomTls.server(key)
            first.close()
            val (server, restored) = RoomTls.server(key)
            server.use {
                assertEquals(pin, restored)
                val ip = InetAddress.getByName("127.0.0.1") as Inet4Address
                server.bind(InetSocketAddress(ip, 0))
                server.soTimeout = 5000
                val endpoint = RoomEndpoint(ip, server.localPort, pin)
                val reader = async(Dispatchers.IO) {
                    List(2) {
                        server.accept().use { socket ->
                            socket.soTimeout = 5000
                            try { socket.getInputStream().read() } catch (_: IOException) { -1 }
                        }
                    }
                }
                fun send(target: RoomEndpoint) = Socket(ip, target.port).use { raw ->
                    raw.soTimeout = 5000
                    RoomTls.client(raw, ip.hostAddress!!, target.port, target.pin).use {
                        it.getOutputStream().write(42)
                    }
                }
                val wrong = endpoint.copy(pin = "0".repeat(64))
                try { send(wrong); fail("accepted wrong identity") } catch (_: javax.net.ssl.SSLException) {}
                val stale = endpoint.copy(port = 0)
                val result = openRoomEndpoint(stale, true, { expected ->
                    assertEquals(pin, expected); endpoint
                }) { target -> if (target == stale) throw IOException("old address"); send(target) }
                assertEquals(endpoint, result.first)
                assertEquals(listOf(-1, 42), reader.await())
            }
        } finally { RoomTls.remove(key) }
    }
}
