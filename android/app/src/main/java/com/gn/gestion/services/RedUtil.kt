package com.gn.gestion.services;

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

object RedUtil {

    fun ipLocal(context: Context): String? {
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cm != null) {
            val red = cm.activeNetwork
            if (red != null) {
                for (dir in cm.getLinkProperties(red)?.linkAddresses.orEmpty()) {
                    val addr = dir.address
                    if (addr is Inet4Address) return addr.hostAddress
                }
            }
        }
        @Suppress("DEPRECATION")
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val n = wifi?.connectionInfo?.ipAddress ?: return null
        return String.format("%d.%d.%d.%d", n and 0xff, n shr 8 and 0xff, n shr 16 and 0xff, n shr 24 and 0xff)
    }

    suspend fun buscarServidores(context: Context, puerto: Int = 5000): List<String> {
        val local = ipLocal(context) ?: return emptyList()
        val partes = local.split(".")
        if (partes.size != 4) return emptyList()
        val prefijo = "${partes[0]}.${partes[1]}.${partes[2]}"
        val sem = Semaphore(64)
        return coroutineScope {
            (1..254).map { i ->
                async(Dispatchers.IO) {
                    val host = "$prefijo.$i"
                    sem.withPermit {
                        try {
                            Socket().use { s ->
                                s.connect(InetSocketAddress(host, puerto), 700)
                                host
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull().sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 0 }
        }
    }
}