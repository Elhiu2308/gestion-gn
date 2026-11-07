
package com.gn.gestion.socketio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gn.gestion.R
import com.gn.gestion.services.ApiConfig
import com.gn.gestion.services.TokenProvider
import com.gn.gestion.views.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SocketIOBackground : Service() {

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var collectorJob: Job? = null

    private var netCallback:
            ConnectivityManager.NetworkCallback? = null

    private var servicioDeteniendose = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "[RX] SocketIOBackground onCreate")

        crearCanalServicio()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val accion = intent?.action

        Log.i(TAG, "[RX] onStartCommand accion=$accion")

        if (accion == ACTION_DETENER) {
            detenerServicio()
            return START_NOT_STICKY
        }

        servicioDeteniendose = false

        if (!iniciarForeground()) {
            Log.e(TAG, "[RX] No fue posible iniciar foreground")
            detenerServicio()
            return START_NOT_STICKY
        }

        if (!sesionValida()) {
            Log.w(TAG, "[RX] Sesión no válida")
            detenerServicio()
            return START_NOT_STICKY
        }

        iniciarColector()

        registrarCallbackRed()

        conectarSocket()

        return START_STICKY
    }

    private fun iniciarForeground(): Boolean {

        val intent = Intent(
            this,
            MainActivity::class.java
        )

        val pendingIntent = PendingIntent.getActivity(
            this,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            this,
            CHANNEL_SERVICE_ID
        )
            .setSmallIcon(R.drawable.ic_notif_bell)
            .setContentTitle("GestióN - Notificaciones")
            .setContentText("Servicio de notificaciones activo.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {

                startForeground(
                    NOTIFICATION_SERVICE_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )

            } else {

                startForeground(
                    NOTIFICATION_SERVICE_ID,
                    notification
                )
            }

            Log.i(TAG, "[RX] Foreground iniciado correctamente")

            true

        } catch (e: Exception) {

            Log.e(TAG, "[RX] Error iniciando foreground", e)

            false
        }
    }

    private fun sesionValida(): Boolean {

        val sync = ApiConfig.SYNC_ENABLED

        val sesion = TokenProvider.sessionActiva(this)

        val usuario =
            TokenProvider.usuario(this).orEmpty()

        val token =
            TokenProvider.token(this).orEmpty()

        Log.i(
            TAG,
            "[RX] Estado: " +
                    "SYNC_ENABLED=$sync | " +
                    "sesionActiva=$sesion | " +
                    "usuarioValido=${usuario.isNotBlank()} | " +
                    "tokenValido=${token.isNotBlank()}"
        )

        return sync &&
                sesion &&
                usuario.isNotBlank() &&
                token.isNotBlank()
    }

    private fun conectarSocket() {

        if (!sesionValida()) {
            Log.w(TAG, "[RX] Conexión omitida: sesión inválida")
            return
        }

        val usuario =
            TokenProvider.usuario(this).orEmpty()

        val token =
            TokenProvider.token(this).orEmpty()

        if (usuario.isBlank() || token.isBlank()) {
            Log.w(TAG, "[RX] Usuario o token vacío")
            return
        }

        Log.i(TAG, "[RX] Solicitando conexión Socket.IO")

        try {

            SocketManager.conectar(
                this,
                usuario,
                token
            )

        } catch (e: Exception) {

            Log.e(TAG, "[RX] Error solicitando conexión", e)
        }
    }

    private fun iniciarColector() {

        if (collectorJob?.isActive == true) {
            Log.d(TAG, "[RX] El colector ya está activo")
            return
        }

        collectorJob = serviceScope.launch {

            Log.i(TAG, "[RX] Colector de eventos iniciado")

            try {

                SocketManager.eventos().collect { evento ->

                    when (evento) {

                        is EventoSocket.NuevaNotificacion -> {

                            val datos = evento.datos

                            Log.i(
                                TAG,
                                "[RX] Nueva notificación: " +
                                        "tipo=${datos.tipo} | " +
                                        "documentoUuid=${datos.documentoUuid} | " +
                                        "documentoId=${datos.documentoId} | " +
                                        "notificacionId=${datos.notificacionId}"
                            )

                            try {

                                val publicada =
                                    NotificationMap.procesarYMostrar(
                                        this@SocketIOBackground,
                                        datos
                                    )

                                Log.d(
                                    TAG,
                                    "[RX] Resultado de publicación: " +
                                            "id=${datos.notificacionId} | " +
                                            "publicada=$publicada"
                                )

                            } catch (e: Exception) {

                                Log.e(
                                    TAG,
                                    "[RX] Error mostrando notificación",
                                    e
                                )
                            }
                        }

                        is EventoSocket.ContadorActualizado -> {

                            Log.d(
                                TAG,
                                "[RX] Contador actualizado: ${evento.noLeidas}"
                            )
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "[RX] Error en el colector de eventos",
                    e
                )
            }
        }
    }

    private fun registrarCallbackRed() {

        if (netCallback != null) {
            Log.d(TAG, "[RX] Callback de red ya registrado")
            return
        }

        try {

            val cm = getSystemService(
                ConnectivityManager::class.java
            )

            if (cm == null) {
                Log.e(TAG, "[RX] ConnectivityManager no disponible")
                return
            }

            val callback = object :
                ConnectivityManager.NetworkCallback() {

                override fun onAvailable(network: Network) {

                    Log.i(
                        TAG,
                        "[RX] Red disponible. Socket.IO administrará la reconexión."
                    )

                    if (!sesionValida()) {
                        Log.w(TAG, "[RX] Sesión inválida al recuperar red")
                        return
                    }

                    if (SocketIOReceiver.isConnected()) {
                        Log.d(TAG, "[RX] Socket.IO continúa conectado")
                    } else {
                        Log.d(
                            TAG,
                            "[RX] Socket.IO está desconectado; " +
                                    "se espera su mecanismo de reconexión"
                        )
                    }
                }

                override fun onLost(network: Network) {

                    Log.w(
                        TAG,
                        "[RX] Se perdió la conexión de red"
                    )

                }
            }

            cm.registerDefaultNetworkCallback(callback)

            netCallback = callback

            Log.i(TAG, "[RX] Callback de red registrado")

        } catch (e: Exception) {

            Log.e(
                TAG,
                "[RX] No se pudo registrar callback de red",
                e
            )
        }
    }

    private fun liberarCallbackRed() {

        val callback = netCallback ?: return

        try {

            val cm = getSystemService(
                ConnectivityManager::class.java
            )

            cm?.unregisterNetworkCallback(callback)

            Log.i(TAG, "[RX] Callback de red liberado")

        } catch (e: Exception) {

            Log.w(TAG, "[RX] Error liberando callback de red", e)

        } finally {

            netCallback = null
        }
    }

    private fun detenerServicio() {

        if (servicioDeteniendose) {
            return
        }

        servicioDeteniendose = true

        Log.i(TAG, "[RX] Deteniendo servicio")

        liberarCallbackRed()

        collectorJob?.cancel()
        collectorJob = null

        try {

            SocketManager.desconectar()

        } catch (e: Exception) {

            Log.e(TAG, "[RX] Error desconectando Socket.IO", e)
        }

        try {

            stopForeground(STOP_FOREGROUND_REMOVE)

        } catch (e: Exception) {

            Log.w(TAG, "[RX] Error retirando foreground", e)
        }

        stopSelf()
    }

    override fun onDestroy() {

        Log.i(TAG, "[RX] Servicio onDestroy")

        liberarCallbackRed()

        collectorJob?.cancel()
        collectorJob = null

        try {

            SocketManager.desconectar()

        } catch (e: Exception) {

            Log.e(TAG, "[RX] Error desconectando en onDestroy", e)
        }

        serviceScope.cancel()

        super.onDestroy()
    }

    private fun crearCanalServicio() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(
            NOTIFICATION_SERVICE
        ) as NotificationManager

        if (manager.getNotificationChannel(CHANNEL_SERVICE_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_SERVICE_ID,
            "Servicio de Notificaciones GestióN",
            NotificationManager.IMPORTANCE_LOW
        ).apply {

            description =
                "Mantiene activa la conexión para recibir notificaciones en tiempo real."

            setShowBadge(false)
        }

        manager.createNotificationChannel(channel)
    }

    companion object {

        private const val TAG = "SocketIOBackground"

        private const val CHANNEL_SERVICE_ID =
            "gestion_socket_service"

        private const val NOTIFICATION_SERVICE_ID = 9001

        private const val ACTION_INICIAR =
            "com.gn.gestion.ACTION_START_SOCKET_SERVICE"

        private const val ACTION_DETENER =
            "com.gn.gestion.ACTION_STOP_SOCKET_SERVICE"

        fun iniciar(context: Context) {

            val sync = ApiConfig.SYNC_ENABLED
            val sesion = TokenProvider.sessionActiva(context)

            if (!sync || !sesion) {

                Log.w(
                    TAG,
                    "[RX] Inicio omitido: " +
                            "SYNC_ENABLED=$sync, sesionActiva=$sesion"
                )

                return
            }

            Log.i(TAG, "[RX] Solicitud de inicio del servicio")

            val intent = Intent(
                context,
                SocketIOBackground::class.java
            ).apply {
                action = ACTION_INICIAR
            }

            try {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    context.startForegroundService(intent)

                } else {

                    context.startService(intent)
                }

            } catch (e: Exception) {

                Log.e(TAG, "[RX] No se pudo iniciar el servicio", e)
            }
        }

        fun detener(context: Context) {

            val intent = Intent(
                context,
                SocketIOBackground::class.java
            ).apply {
                action = ACTION_DETENER
            }

            try {

                context.startService(intent)

            } catch (e: Exception) {

                Log.e(TAG, "[RX] No se pudo solicitar detención", e)
            }
        }
    }
}
