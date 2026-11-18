package com.focustrace.studyroom

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import javax.net.ssl.*

internal object RoomTls {
    private fun alias(key: String) = "study-room-$key"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun remove(key: String) = store().deleteEntry(alias(key))
    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded).joinToString("") { "%02x".format(it) }

    fun server(key: String): Pair<SSLServerSocket, String> {
        val store = store()
        val alias = alias(key)
        if (!store.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                // Conscrypt passes the already-hashed TLS transcript to AndroidKeyStore.
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512).build())
            generator.generateKeyPair()
        }
        val cert = store.getCertificate(alias) as X509Certificate
        val keyManager = object : X509KeyManager {
            override fun chooseServerAlias(type: String, issuers: Array<java.security.Principal>?, socket: java.net.Socket?) =
                alias.takeIf { type == "EC" }
            override fun getServerAliases(type: String, issuers: Array<java.security.Principal>?) = if (type == "EC") arrayOf(alias) else null
            override fun chooseClientAlias(types: Array<String>?, issuers: Array<java.security.Principal>?, socket: java.net.Socket?): String? = null
            override fun getClientAliases(type: String?, issuers: Array<java.security.Principal>?): Array<String>? = null
            override fun getCertificateChain(request: String) = if (request == alias) arrayOf(cert) else null
            override fun getPrivateKey(request: String) = if (request == alias) store.getKey(alias, null) as java.security.PrivateKey else null
        }
        val tls = SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), null, null) }
        return tls.serverSocketFactory.createServerSocket() as SSLServerSocket to fingerprint(cert)
    }

    fun client(socket: java.net.Socket, host: String, port: Int, pin: String): SSLSocket {
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, auth: String): Unit = throw CertificateException()
            override fun checkServerTrusted(chain: Array<X509Certificate>, auth: String) {
                if (chain.isEmpty() || fingerprint(chain[0]) != pin) throw CertificateException("房间身份不匹配")
            }
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return (tls.socketFactory.createSocket(socket, host, port, true) as SSLSocket).apply { startHandshake() }
    }
}
