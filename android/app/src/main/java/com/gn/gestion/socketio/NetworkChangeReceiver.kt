package com.gn.gestion.socketio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.gn.gestion.services.ApiConfig
import com.gn.gestion.services.TokenProvider

class NetworkChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val hayRed = hayConexionAInternet(context)
        Log.i(TAG, "[RX] Cambio de red detectado | Conectado: $hayRed")

        if (hayRed && ApiConfig.SYNC_ENABLED && TokenProvider.sessionActiva(context)) {
            Log.i(TAG, "[RX] Reconectando SocketIO tras recuperar conexión a red...")
            SocketIOBackground.iniciar(context)
            val usuario = TokenProvider.usuario(context) ?: ""
            val token = TokenProvider.token(context) ?: ""
            if (usuario.isNotBlank() && token.isNotBlank()) {
                SocketManager.conectar(context, usuario, token)
            }
        }
    }

    private fun hayConexionAInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val TAG = "NetworkChangeReceiver"
    }
}
