
package com.gn.gestion.services

import android.util.Log
import com.gn.gestion.socketio.SocketManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Servicio receptor de notificaciones Firebase Cloud Messaging.
 *
 * FCM funciona como canal complementario de Socket.IO.
 *
 * La deduplicación se comparte mediante notificacion_id.
 * El ID solo se confirma cuando Android publica correctamente
 * la notificación.
 *
 * El contador de no leídas siempre procede del servidor.
 */
class GestionMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.i(
            TAG,
            "[RX] Nuevo token FCM recibido | longitud=${token.length}"
        )

        FcmRegistrador.registrarToken(
            applicationContext,
            token
        )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val datos = message.data

        val notifId = datos["notificacion_id"]
            ?.toLongOrNull() ?: 0L

        val docId = datos["documento_id"]
            ?.toLongOrNull() ?: 0L

        val docUuid = datos["documento_uuid"].orEmpty()

        val tipo = datos["tipo"].orEmpty()

        Log.i(
            TAG,
            "[RX] PUSH recibido | " +
                    "messageId=${message.messageId} | " +
                    "tipo=$tipo | " +
                    "notifId=$notifId | " +
                    "docId=$docId | " +
                    "prioridad=${message.priority}"
        )

        // No mostrar notificaciones de sesiones cerradas.
        if (!TokenProvider.sessionActiva(applicationContext)) {

            Log.w(
                TAG,
                "[RX] Push ignorado: no existe una sesión activa"
            )

            return
        }

        // Validar el identificador de la notificación persistida.
        if (notifId <= 0L) {

            Log.e(
                TAG,
                "[RX] Push ignorado: notificacion_id inválido"
            )

            return
        }

        // Reservar el ID para evitar publicación simultánea
        // desde Socket.IO y FCM.
        val reservada = SocketManager.iniciarProcesamiento(notifId)

        if (!reservada) {

            Log.i(
                TAG,
                "[RX] Push duplicado o en proceso | notifId=$notifId"
            )

            return
        }

        try {

            // El backend envía titulo y mensaje dentro de data.
            // Se conservan los campos notification por compatibilidad.
            val cuerpo = datos["mensaje"]
                ?.takeIf { it.isNotBlank() }
                ?: message.notification?.body
                    ?.takeIf { it.isNotBlank() }
                ?: "Tienes una nueva notificación."

            val titulo = datos["titulo"]
                ?.takeIf { it.isNotBlank() }
                ?: if (tipo.isNotBlank()) {
                    Notificador.tituloDe(tipo)
                } else {
                    message.notification?.title
                        ?.takeIf { it.isNotBlank() }
                        ?: Notificador.tituloDe("")
                }

            Log.i(
                TAG,
                "[RX] Preparando notificación | " +
                        "titulo=$titulo | notifId=$notifId"
            )

            val publicada = Notificador.mostrar(
                context = applicationContext,
                titulo = titulo,
                cuerpo = cuerpo,
                documentoUuid = docUuid,
                documentoId = docId,
                notificacionId = notifId
            )

            if (publicada) {

                // Registrar únicamente después de publicar correctamente.
                SocketManager.confirmarMostrada(notifId)

                Log.i(
                    TAG,
                    "[RX] PUSH publicado correctamente | notifId=$notifId"
                )

            } else {

                // Permitir que Socket.IO u otro intento pueda publicarla.
                SocketManager.cancelarProcesamiento(notifId)

                Log.w(
                    TAG,
                    "[RX] PUSH no publicado; ID liberado | notifId=$notifId"
                )
            }

        } catch (e: Exception) {

            // Si ocurre un error, liberar la reserva.
            SocketManager.cancelarProcesamiento(notifId)

            Log.e(
                TAG,
                "[RX] Error procesando PUSH | notifId=$notifId",
                e
            )
        }

        // No incrementar el contador localmente.
        // El servidor es la fuente oficial de no leídas.
    }

    private companion object {
        private const val TAG = "GestionMessagingSvc"
    }
}
