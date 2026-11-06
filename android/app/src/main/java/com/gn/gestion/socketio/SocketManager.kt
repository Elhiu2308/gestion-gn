
package com.gn.gestion.socketio

import android.content.Context
import android.util.Log
import com.gn.gestion.services.ServidorManager
import com.gn.gestion.services.TokenProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Gestor singleton de conexión Socket.IO y estado del contador
 * de notificaciones.
 *
 * Centraliza:
 * - Estado de conexión.
 * - Contador de notificaciones no leídas.
 * - Distribución de eventos.
 * - Deduplicación compartida entre Socket.IO y FCM.
 *
 * Los IDs se registran como publicados únicamente después de que
 * Notificador.mostrar() confirme la publicación.
 */
object SocketManager {

    private const val TAG = "SocketManager"

    private const val MAX_IDS_GUARDADOS = 200

    private const val MAX_EVENTOS_PENDIENTES = 64

    private val _conexion =
        MutableStateFlow<ConexionSocket>(
            ConexionSocket.Desconectado
        )

    val conexion: StateFlow<ConexionSocket> = _conexion

    private val _contadorNotif =
        MutableStateFlow(0)

    val contadorNotif: StateFlow<Int> = _contadorNotif

    /**
     * Conserva eventos aunque todavía no exista un colector activo.
     *
     * replay permite recuperar eventos pendientes cuando el servicio
     * inicia su colector después de establecerse la conexión.
     */
    private val _eventosFlow =
        MutableSharedFlow<EventoSocket>(
            replay = MAX_EVENTOS_PENDIENTES,
            extraBufferCapacity = MAX_EVENTOS_PENDIENTES
        )

    /**
     * IDs de notificaciones publicadas correctamente.
     *
     * LinkedHashMap con acceso ordenado para conservar los últimos
     * IDs utilizados y limitar el consumo de memoria.
     */
    private val vistas =
        object : LinkedHashMap<Long, Boolean>(
            64,
            0.75f,
            true
        ) {

            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Long, Boolean>?
            ): Boolean {
                return size > MAX_IDS_GUARDADOS
            }
        }

    /**
     * IDs que actualmente están siendo procesados por FCM o Socket.IO.
     */
    private val enProceso = mutableSetOf<Long>()

    /**
     * Reserva el ID para iniciar la publicación.
     *
     * Retorna false si ya fue publicado o está siendo procesado
     * por el otro canal.
     */
    fun iniciarProcesamiento(id: Long): Boolean {

        if (id <= 0L) {
            Log.w(
                TAG,
                "ID inválido: no se puede deduplicar | id=$id"
            )
            return true
        }

        synchronized(this) {

            if (vistas.containsKey(id)) {
                Log.d(
                    TAG,
                    "Notificación ya publicada | id=$id"
                )
                return false
            }

            if (!enProceso.add(id)) {
                Log.d(
                    TAG,
                    "Notificación en proceso por otro canal | id=$id"
                )
                return false
            }

            return true
        }
    }

    /**
     * Confirma que Android publicó correctamente la notificación.
     */
    fun confirmarMostrada(id: Long) {

        if (id <= 0L) return

        synchronized(this) {

            enProceso.remove(id)

            vistas[id] = true
        }

        Log.d(
            TAG,
            "Notificación registrada como publicada | id=$id"
        )
    }

    /**
     * Libera la reserva cuando la publicación falla.
     */
    fun cancelarProcesamiento(id: Long) {

        if (id <= 0L) return

        synchronized(this) {

            enProceso.remove(id)
        }

        Log.w(
            TAG,
            "Reserva liberada para reintento | id=$id"
        )
    }

    /**
     * Consulta si una notificación ya fue publicada
     * o está siendo procesada.
     */
    fun esDuplicada(id: Long): Boolean {

        if (id <= 0L) return false

        synchronized(this) {

            return vistas.containsKey(id) ||
                    enProceso.contains(id)
        }
    }

    /**
     * Inicia o verifica la conexión Socket.IO.
     */
    fun conectar(
        context: Context,
        usuario: String,
        token: String
    ) {

        if (usuario.isBlank() || token.isBlank()) {

            Log.w(
                TAG,
                "[RX] No se puede conectar: usuario o token vacío"
            )

            marcarConexion(
                ConexionSocket.Desconectado
            )

            return
        }

        val appContext = context.applicationContext

        val baseUrl = try {

            ServidorManager.activo(appContext).trim()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "[RX] Error obteniendo servidor activo",
                e
            )

            marcarConexion(
                ConexionSocket.Desconectado
            )

            return
        }

        if (baseUrl.isBlank()) {

            Log.e(
                TAG,
                "[RX] El servidor devolvió una URL vacía"
            )

            marcarConexion(
                ConexionSocket.Desconectado
            )

            return
        }

        Log.i(
            TAG,
            "[RX] conectar() -> $baseUrl | " +
                    "usuario=$usuario | tokenLen=${token.length}"
        )

        if (!SocketIOReceiver.isConnected()) {

            marcarConexion(
                ConexionSocket.Conectando
            )
        }

        try {

            SocketIOReceiver.connect(
                baseUrl,
                usuario,
                token
            ) {
                TokenProvider.token(appContext)
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "[RX] Error solicitando conexión Socket.IO",
                e
            )

            marcarConexion(
                ConexionSocket.Desconectado
            )
        }
    }

    /**
     * Actualiza el estado de conexión.
     */
    fun marcarConexion(estado: ConexionSocket) {

        Log.i(
            TAG,
            "[RX] Estado de conexión -> $estado"
        )

        _conexion.value = estado
    }

    /**
     * Recibe el evento Socket.IO y lo distribuye.
     *
     * La deduplicación se realiza posteriormente,
     * cuando NotificationMap intenta publicar la notificación.
     */
    fun emitirNotificacion(notif: NotificacionSocket) {

        if (notif.notificacionId <= 0L) {

            Log.w(
                TAG,
                "[RX] Notificación ignorada: ID inválido"
            )

            return
        }

        if (notif.mensaje.isBlank()) {

            Log.w(
                TAG,
                "[RX] Notificación ignorada: mensaje vacío | " +
                        "id=${notif.notificacionId}"
            )

            return
        }

        Log.i(
            TAG,
            "[RX] Nueva notificación recibida | " +
                    "notifId=${notif.notificacionId} | " +
                    "doc=${notif.documentoUuid} | " +
                    "tipo=${notif.tipo}"
        )

        // El contador solamente se actualiza con el valor del servidor.
        notif.noLeidas?.let { valor ->
            setContador(valor)
        }

        val emitido = _eventosFlow.tryEmit(
            EventoSocket.NuevaNotificacion(notif)
        )

        if (!emitido) {

            Log.e(
                TAG,
                "[RX] No se pudo distribuir el evento. " +
                        "Buffer de eventos lleno | " +
                        "id=${notif.notificacionId}"
            )
        }
    }

    /**
     * Flujo de eventos para SocketIOBackground y otros colectores.
     */
    fun eventos(): Flow<EventoSocket> {

        return _eventosFlow.asSharedFlow()
    }

    /**
     * Actualiza el contador oficial recibido del servidor.
     */
    fun setContador(valor: Int) {

        val nuevoValor = valor.coerceAtLeast(0)

        if (_contadorNotif.value == nuevoValor) {
            return
        }

        Log.d(
            TAG,
            "[RX] Contador no leídas: " +
                    "${_contadorNotif.value} -> $nuevoValor"
        )

        _contadorNotif.value = nuevoValor

        val emitido = _eventosFlow.tryEmit(
            EventoSocket.ContadorActualizado(nuevoValor)
        )

        if (!emitido) {

            Log.w(
                TAG,
                "[RX] No se pudo distribuir actualización del contador"
            )
        }
    }

    /**
     * Desconexión temporal.
     *
     * Conserva contador, IDs publicados y eventos pendientes.
     */
    fun desconectar() {

        Log.i(
            TAG,
            "[RX] Desconexión temporal desde SocketManager"
        )

        try {

            SocketIOReceiver.disconnect()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "[RX] Error desconectando Socket.IO",
                e
            )
        }

        _conexion.value = ConexionSocket.Desconectado
    }

    /**
     * Limpieza definitiva al cerrar sesión.
     *
     * Elimina:
     * - Conexión Socket.IO.
     * - Contador.
     * - IDs publicados.
     * - IDs en proceso.
     * - Eventos pendientes de la sesión anterior.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun limpiarSesion() {

        Log.i(
            TAG,
            "[RX] Limpiando estado de sesión"
        )

        try {

            SocketIOReceiver.disconnect()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "[RX] Error desconectando durante limpieza",
                e
            )
        }

        _conexion.value = ConexionSocket.Desconectado

        _contadorNotif.value = 0

        synchronized(this) {

            vistas.clear()

            enProceso.clear()
        }

        _eventosFlow.resetReplayCache()

        Log.i(
            TAG,
            "[RX] Estado de sesión limpiado correctamente"
        )
    }
}
