package com.focustrace.studyroom

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** One manually signalled, ordered ICE data channel carrying the existing TLS byte stream. */
@SuppressLint("SetJavaScriptEnabled")
internal class RoomRtc(context: Context, val invite: RoomInvite, private val offer: String?,
    private val onConnected: (Socket) -> Unit, private val onFailure: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val view = WebView(context)
    private val incoming = Channel<ByteArray>(128)
    private val ack = Channel<Unit>(1)
    private var socket: Socket? = null
    private var other: Socket? = null
    private var closed = false
    val signal = MutableStateFlow("")
    val status = MutableStateFlow("正在获取连接地址…")
    private val timeout = scope.launch { delay(180_000); fail() }

    init {
        view.settings.javaScriptEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.blockNetworkLoads = true
        view.addJavascriptInterface(object {
            @JavascriptInterface fun signal(sdp: String) { scope.launch {
                if (sdp.length !in 1..48000) { fail(); return@launch }
                signal.value = JSONObject().put("type", if (offer == null) "offer" else "answer")
                    .put("invite", invite.text).put("sdp", sdp).toString()
                status.value = if (offer == null) "分享邀请，等待对方的答复" else "请将连接答复发给房主"
            } }
            @JavascriptInterface fun opened() { scope.launch { bridge() } }
            @JavascriptInterface fun failed() { scope.launch { fail() } }
            @JavascriptInterface fun sent() { ack.trySend(Unit) }
            @JavascriptInterface fun received(data: String) {
                if (data.length > 22000 || incoming.trySend(Base64.decode(data, Base64.NO_WRAP)).isFailure)
                    scope.launch { fail() }
            }
        }, "Bridge")
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest) = true
            override fun onPageFinished(v: WebView, url: String) {
                if (!closed) js("start(" + (offer?.let(JSONObject::quote) ?: "null") + ")")
            }
        }
        view.loadDataWithBaseURL("https://room.invalid/", context.assets.open("room-rtc.html").bufferedReader().use { it.readText() },
            "text/html", "utf-8", null)
    }

    fun answer(sdp: String) { status.value = "正在尝试穿透…"; js("answer(" + JSONObject.quote(sdp) + ")") }
    private fun js(code: String) = view.evaluateJavascript(code, null)

    private suspend fun bridge() {
        try {
            val stream = if (offer == null) {
                Socket().also { socket = it; withContext(Dispatchers.IO) { it.connect(InetSocketAddress(invite.address(), invite.port), 5000) } }
            } else {
                // The guest's existing TLS client and the data channel meet over a private socket pair.
                val loop = java.net.InetAddress.getLoopbackAddress()
                val pair = withContext(Dispatchers.IO) {
                    java.net.ServerSocket(0, 1, loop).use { server ->
                        Socket(loop, server.localPort).let { client ->
                            try { client to server.accept() } catch (e: Exception) { client.close(); throw e }
                        }
                    }
                }
                other = pair.first
                socket = pair.second
                onConnected(pair.first)
                pair.second
            }
            timeout.cancel()
            status.value = "穿透通道已连接"
            coroutineScope {
                launch(Dispatchers.IO) {
                    for (bytes in incoming) stream.getOutputStream().write(bytes)
                }
                val buf = ByteArray(8192)
                while (isActive) {
                    val n = withContext(Dispatchers.IO) { stream.getInputStream().read(buf) }
                    if (n < 0) throw IOException()
                    js("send('" + Base64.encodeToString(buf, 0, n, Base64.NO_WRAP) + "')")
                    ack.receive()
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { fail() }
    }

    private fun fail() { if (!closed) { close(); onFailure() } }
    fun close() {
        if (closed) return
        closed = true
        socket?.closeQuietly(); other?.closeQuietly()
        incoming.cancel(); ack.cancel(); scope.cancel()
        view.removeJavascriptInterface("Bridge"); view.destroy()
    }
}
