package com.focustrace.studyroom

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

private const val ROOM_SERVICE_TYPE = "_focustrace._tcp."
private const val VERSION_ATTRIBUTE = "v"

internal data class RoomWifi(val network: Network, val address: Inet4Address, val prefixLength: Int) {
    fun contains(remote: InetAddress): Boolean = isRoomPeerAddress(address, remote, prefixLength)
}

internal fun isRoomPeerAddress(local: Inet4Address, remote: InetAddress, prefixLength: Int): Boolean {
    if (remote !is Inet4Address || remote == local || remote.isAnyLocalAddress || remote.isMulticastAddress ||
        remote.isLoopbackAddress || prefixLength !in 1..32) return false
    val mask = -1 shl (32 - prefixLength)
    val localIp = ByteBuffer.wrap(local.address).int
    val remoteIp = ByteBuffer.wrap(remote.address).int
    return (localIp and mask) == (remoteIp and mask)
}

internal data class RoomEndpoint(val address: Inet4Address, val port: Int, val pin: String)
internal suspend fun <T> openRoomEndpoint(
    original: RoomEndpoint, returning: Boolean, discover: suspend (String) -> RoomEndpoint?,
    open: suspend (RoomEndpoint) -> T
): Pair<RoomEndpoint, T> {
    try { return original to open(original) } catch (failure: IOException) {
        currentCoroutineContext().ensureActive()
        if (!returning) throw failure
    }
    val fresh = discover(original.pin)?.takeIf { it.pin == original.pin }
        ?: throw IOException("暂未找到上次的房间。可稍后重试、粘贴新邀请或忘记此房间")
    return fresh to open(fresh)
}

internal data class RoomListing(val key: String, val name: String, val info: NsdServiceInfo)
internal data class RoomDiscovery(val wifiAvailable: Boolean = false, val rooms: List<RoomListing> = emptyList(),
    val failed: Boolean = false, val startedAt: Long? = null)

internal class RoomLan(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val main = ContextCompat.getMainExecutor(context)

    @Suppress("DEPRECATION")
    fun wifi(): RoomWifi? = connectivity.allNetworks.firstNotNullOfOrNull { network ->
        val caps = connectivity.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return@firstNotNullOfOrNull null
        }
        val link = connectivity.getLinkProperties(network)?.linkAddresses?.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress && !it.address.isLinkLocalAddress
        } ?: return@firstNotNullOfOrNull null
        val address = link.address as Inet4Address
        RoomWifi(network, address, link.prefixLength)
    }

    suspend fun browse(publish: (RoomDiscovery) -> Unit) {
        var firstCheck = true
        var startedAt: Long? = null
        var current: RoomWifi? = null
        var listener: NsdManager.DiscoveryListener? = null
        val discovered = linkedMapOf<String, RoomListing>()
        fun stop() {
            val previous = listener
            listener = null
            previous?.let { runCatching { nsd.stopServiceDiscovery(it) } }
            discovered.clear()
        }
        fun key(info: NsdServiceInfo) = if (Build.VERSION.SDK_INT >= 33)
            "${info.serviceName}:${info.network}" else info.serviceName
        try {
            while (currentCoroutineContext().isActive) {
                val wifi = wifi()
                if (firstCheck || wifi != current || (wifi != null && listener == null)) {
                    stop()
                    firstCheck = false
                    startedAt = null
                    current = wifi
                    publish(RoomDiscovery(wifiAvailable = wifi != null))
                    if (wifi != null) {
                        val search = object : NsdManager.DiscoveryListener {
                            override fun onDiscoveryStarted(type: String) = main.execute {
                                if (listener === this) {
                                    startedAt = System.currentTimeMillis()
                                    publish(RoomDiscovery(true, discovered.values.toList(), startedAt = startedAt))
                                }
                            }
                            override fun onDiscoveryStopped(type: String) = Unit
                            override fun onStartDiscoveryFailed(type: String, code: Int) = failed()
                            override fun onStopDiscoveryFailed(type: String, code: Int) = failed()
                            private fun failed() = main.execute {
                                if (listener === this) {
                                    stop()
                                    publish(RoomDiscovery(wifiAvailable = true, failed = true))
                                }
                            }
                            override fun onServiceFound(service: NsdServiceInfo) = main.execute {
                                if (listener === this && discovered.size < 100 &&
                                    (Build.VERSION.SDK_INT < 33 || service.network == null || service.network == wifi.network)) {
                                    val id = key(service)
                                    discovered[id] = RoomListing(id, service.serviceName.substringAfter(' ', service.serviceName), service)
                                    publish(RoomDiscovery(true, discovered.values.toList(), startedAt = startedAt))
                                }
                            }
                            override fun onServiceLost(service: NsdServiceInfo) = main.execute {
                                if (listener === this && discovered.remove(key(service)) != null) {
                                    publish(RoomDiscovery(true, discovered.values.toList(), startedAt = startedAt))
                                }
                            }
                        }
                        listener = search
                        try {
                            if (Build.VERSION.SDK_INT >= 33) {
                                nsd.discoverServices(ROOM_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, wifi.network, main, search)
                            } else nsd.discoverServices(ROOM_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, search)
                        } catch (_: RuntimeException) {
                            stop()
                            publish(RoomDiscovery(wifiAvailable = true, failed = true))
                        }
                    }
                }
                delay(2_000)
            }
        } finally {
            stop()
        }
    }

    suspend fun advertise(wifi: RoomWifi, name: String, boundPort: Int, pin: String, onRegistered: (String) -> Unit) {
        val ready = CompletableDeferred<String>()
        val info = NsdServiceInfo().apply {
            serviceName = "FT-${pin.take(16)} " + name.take(12)
            serviceType = ROOM_SERVICE_TYPE
            setPort(boundPort)
            setAttribute("pin", pin)
            setAttribute(VERSION_ATTRIBUTE, RoomWire.VERSION.toString())
            if (Build.VERSION.SDK_INT >= 33) network = wifi.network
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(service: NsdServiceInfo) { ready.complete(service.serviceName) }
            override fun onRegistrationFailed(service: NsdServiceInfo, error: Int) {
                ready.completeExceptionally(IOException("无法广播自习室"))
            }
            override fun onServiceUnregistered(service: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(service: NsdServiceInfo, error: Int) = Unit
        }
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            val registered = withTimeoutOrNull(8_000) { ready.await() } ?: throw IOException("广播自习室超时")
            onRegistered(registered.take(63))
            awaitCancellation()
        } finally {
            runCatching { nsd.unregisterService(listener) }
        }
    }

    suspend fun rediscover(pin: String, wifi: RoomWifi): RoomEndpoint? = withTimeoutOrNull(10_000) {
        coroutineScope {
            val found = CompletableDeferred<RoomListing>()
            val search = launch {
                browse { result -> result.rooms.firstOrNull {
                    it.info.serviceName.startsWith("FT-${pin.take(16)} ")
                }?.let(found::complete) }
            }
            try { resolve(found.await(), wifi).takeIf { it.pin == pin } } finally { search.cancel() }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun resolve(listing: RoomListing, wifi: RoomWifi): RoomEndpoint {
        val resolved = CompletableDeferred<NsdServiceInfo>()
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(service: NsdServiceInfo, code: Int) {
                resolved.completeExceptionally(IOException("找不到这个自习室，可能已关闭"))
            }
            override fun onServiceResolved(service: NsdServiceInfo) { resolved.complete(service) }
        }
        try {
            nsd.resolveService(listing.info, listener)
            val info = withTimeoutOrNull(8_000) { resolved.await() } ?: throw IOException("连接自习室超时")
            if (info.attributes[VERSION_ATTRIBUTE]?.decodeToString() != RoomWire.VERSION.toString()) {
                throw IOException("双方的自习室版本不兼容")
            }
            if (Build.VERSION.SDK_INT >= 33 && info.network?.let { it != wifi.network } == true) {
                throw IOException("请连接到房主所在的 Wi-Fi")
            }
            val address = (if (Build.VERSION.SDK_INT >= 34) info.hostAddresses.firstOrNull(wifi::contains) else info.host)
                as? Inet4Address ?: throw IOException("找不到同一 Wi-Fi 上的房主地址")
            if (!wifi.contains(address) || info.port !in 1..65535) throw IOException("只能加入同一 Wi-Fi 的自习室")
            val pin = info.attributes["pin"]?.decodeToString().orEmpty()
            if (!pin.matches(Regex("[0-9a-f]{64}"))) throw IOException("房间缺少身份信息，请双方更新应用")
            return RoomEndpoint(address, info.port, pin)
        } finally {
            if (Build.VERSION.SDK_INT >= 34 && !resolved.isCompleted) runCatching { nsd.stopServiceResolution(listener) }
        }
    }
}
