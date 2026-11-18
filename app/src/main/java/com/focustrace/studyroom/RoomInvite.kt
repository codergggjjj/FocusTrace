package com.focustrace.studyroom

import java.net.InetAddress
import java.util.UUID

internal data class RoomInvite(val host: String, val port: Int, val key: String, val pin: String) {
    val text get() = "focustrace://room/$host/$port/$key/$pin"
    fun address(): InetAddress = InetAddress.getByAddress(host.split('.').map { it.toInt().toByte() }.toByteArray())

    companion object {
        private val format = Regex("""focustrace://room/([0-9.]{7,15})/([0-9]{1,5})/([0-9a-f-]{36})/([0-9a-f]{64})""")
        fun parse(text: String): RoomInvite? {
            val (host, port, key, pin) = format.matchEntire(text.trim())?.destructured ?: return null
            val parts = host.split('.')
            if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 || it != it.toIntOrNull().toString() } ||
                parts.first().toInt() !in 1..223 || parts.first() == "127" || port.toInt() !in 1..65535 ||
                runCatching { UUID.fromString(key).toString() }.getOrNull() != key) return null
            return RoomInvite(host, port.toInt(), key, pin)
        }
    }
}
