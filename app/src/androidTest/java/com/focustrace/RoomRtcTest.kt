package com.focustrace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focustrace.studyroom.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class RoomRtcTest {
    @Test fun manuallyExchangedSignalsCarryRoomBytesBothWays() = runBlocking<Unit> {
        withTimeout(75_000) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val loop = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
            ServerSocket(0, 1, loop).use { server ->
                server.soTimeout = 15000
                val invite = RoomInvite(loop.hostAddress!!, server.localPort, "test", "pin")
                val client = CompletableDeferred<Socket>()
                val host = withContext(Dispatchers.Main) { RoomRtc(context, invite, null, {}, {}) }
                var guest: RoomRtc? = null
                try {
                    val offer = JSONObject(host.signal.first { it.isNotEmpty() }).getString("sdp")
                    val peer = withContext(Dispatchers.Main) {
                        RoomRtc(context, invite, offer, { client.complete(it) }, {})
                    }
                    guest = peer
                    val answer = JSONObject(peer.signal.first { it.isNotEmpty() }).getString("sdp")
                    withContext(Dispatchers.Main) { host.answer(answer) }
                    val socket = client.await()
                    withContext(Dispatchers.IO) {
                        server.accept().use { remote ->
                            remote.soTimeout = 5000; socket.soTimeout = 5000
                            val bytes = ByteArray(24576) { it.toByte() }
                            socket.getOutputStream().write(bytes)
                            val read = ByteArray(bytes.size)
                            java.io.DataInputStream(remote.getInputStream()).readFully(read)
                            assertArrayEquals(bytes, read)
                            remote.getOutputStream().write(bytes)
                            java.io.DataInputStream(socket.getInputStream()).readFully(read)
                            assertArrayEquals(bytes, read)
                        }
                    }
                } finally { withContext(NonCancellable + Dispatchers.Main) { guest?.close(); host.close() } }
            }
        }
    }
}
