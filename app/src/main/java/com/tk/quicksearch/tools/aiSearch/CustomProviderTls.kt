package com.tk.quicksearch.tools.aiSearch

import java.net.HttpURLConnection
import java.security.KeyStore
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/**
 * TLS for user-configured (self-hosted) providers only. Trusts system CAs plus CAs the user
 * installed on the device, so servers behind a private CA work. Built-in providers keep the
 * app-wide network security config, which trusts system CAs only.
 */
internal object CustomProviderTls {
    private val socketFactory: SSLSocketFactory? by lazy {
        runCatching {
            // AndroidCAStore holds both system and user-installed CAs.
            val keyStore = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                    init(keyStore)
                }
            SSLContext.getInstance("TLS").apply {
                init(null, trustManagerFactory.trustManagers, null)
            }.socketFactory
        }.getOrNull()
    }

    fun apply(connection: HttpURLConnection) {
        val factory = socketFactory ?: return
        (connection as? HttpsURLConnection)?.sslSocketFactory = factory
    }
}
