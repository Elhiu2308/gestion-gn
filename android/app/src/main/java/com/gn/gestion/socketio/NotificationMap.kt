
package com.gn.gestion.socketio

import android.content.Context
import android.util.Log
import com.gn.gestion.services.Notificador

/**
 * Notificación recibida por Socket.IO, ya normalizada.
 */
data class NotificacionSocket(
    val mensaje: String,
    val documentoId: Long? = null,
    val documentoUuid: String = "",
    val tipo: String = "SISTEMA",
    val noLeidas: Int? = null,
    val notificacionId: Long = 0L
)

/**
 * Eventos emitidos por el servidor mediante Socket.IO.
 */
sealed interface EventoSocket {
    data class NuevaNotificacion(
        val datos: NotificacionSocket
    ) : EventoSocket

    data class ContadorActualizado(
        val noLeidas: Int
    ) : EventoSocket
}

/**
 * Estado de la conexión del socket en tiempo real.
 */
sealed interface ConexionSocket {
    data object Desconectado : ConexionSocket
    data object Conectando : ConexionSocket
    data class Conectado(val id: String) : ConexionSocket
}

/**
 * Mapeador y despachador de notificaciones visuales.
 */
object NotificationMap {

    private const val TAG = "NotificationMap"

    /**
     * Muestra una notificación recibida por Socket.IO.
     *
     * El ID se confirma únicamente cuando Android acepta
     * la publicación de la notificación.
     */
    fun procesarYMostrar(
        context: Context,
        notif: NotificacionSocket
    ): Boolean {

        val notifId = notif.notificacionId

        Log.i(
            TAG,
            "[RX] procesarYMostrar | " +
                    "tipo=${notif.tipo} | " +
                    "docUuid=${notif.documentoUuid} | " +
                    "docId=${notif.documentoId} | " +
                    "notifId=$notifId"
        )

        // Reservar el ID para evitar que FCM publique simultáneamente.
        val reservada = SocketManager.iniciarProcesamiento(notifId)

        if (!reservada) {
            Log.i(
                TAG,
                "[RX] Notificación duplicada o en proceso | id=$notifId"
            )
            return false
        }

        return try {

            val titulo = Notificador.tituloDe(notif.tipo)

            val publicada = Notificador.mostrar(
                context = context.applicationContext,
                titulo = titulo,
                cuerpo = notif.mensaje,
                documentoUuid = notif.documentoUuid,
                documentoId = notif.documentoId ?: 0L,
                notificacionId = notifId
            )

            if (publicada) {
                SocketManager.confirmarMostrada(notifId)

                Log.i(
                    TAG,
                    "[RX] Notificación publicada correctamente | id=$notifId"
                )
            } else {
                SocketManager.cancelarProcesamiento(notifId)

                Log.w(
                    TAG,
                    "[RX] No se publicó; ID liberado para reintento | id=$notifId"
                )
            }

            publicada

        } catch (e: Exception) {

            SocketManager.cancelarProcesamiento(notifId)

            Log.e(
                TAG,
                "[RX] Error mostrando notificación | id=$notifId",
                e
            )

            false
        }
    }
}

