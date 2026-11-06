
package com.gn.gestion.socketio

import android.util.Log
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * Receptor y gestor de conexión Socket.IO con Flask-SocketIO.
 *
 * Contrato del backend:
 * - Namespace: /rt
 * - Autenticación: auth.token y query token
 * - Unión privada: join sin payload
 * - Confirmación: joined
 * - Notificaciones: notification
 * - Contador: contador_actualizado
 * - Salida: leave
 */
object SocketIOReceiver {

    private const val TAG = "SocketIOReceiver"
    private const val NAMESPACE = "/rt"

    private var socket: Socket? = null
    private var currentBaseUrl: String? = null
    private var currentUsuario: String? = null

    private var currentTokenProvider: (() -> String?)? = null

    @Synchronized
    fun connect(
        baseUrlRaw: String,
        usuario: String,
        accessToken: String,
        tokenProvider: (() -> String?)? = null
    ) {
        val baseUrl = baseUrlRaw.trim().trimEnd('/')

        if (
            baseUrl.isBlank() ||
            usuario.isBlank() ||
            accessToken.isBlank()
        ) {
            Log.w(TAG, "[RX] Conexión omitida: datos incompletos")
            return
        }

        currentTokenProvider = tokenProvider

        if (
            socket?.connected() == true &&
            currentBaseUrl == baseUrl &&
            currentUsuario == usuario
        ) {
            Log.d(TAG, "[RX] Socket.IO ya conectado")
            return
        }

        disconnect()

        currentTokenProvider = tokenProvider

        try {
            currentBaseUrl = baseUrl
            currentUsuario = usuario

            val authMap = mutableMapOf(
                "token" to accessToken
            )

            val opts = IO.Options().apply {
                transports = arrayOf("websocket", "polling")
                forceNew = true

                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
                timeout = 20000

                query = "token=$accessToken"
                auth = authMap
            }

            Log.i(
                TAG,
                "[RX] Conectando a $baseUrl$NAMESPACE | " +
                        "usuario=$usuario | tokenLen=${accessToken.length}"
            )

            val s = IO.socket("$baseUrl$NAMESPACE", opts)
            socket = s

            // Actualizar credenciales antes de cada reconexión.
            s.io().on(Manager.EVENT_RECONNECT_ATTEMPT) {
                try {
                    val fresco = currentTokenProvider?.invoke()

                    if (!fresco.isNullOrBlank()) {
                        authMap["token"] = fresco
                        opts.query = "token=$fresco"

                        Log.i(TAG, "[RX] Token actualizado para reconexión")
                    } else {
                        Log.w(TAG, "[RX] No se obtuvo token vigente")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "[RX] Error actualizando token", e)
                }
            }

            // Conexión establecida.
            s.on(Socket.EVENT_CONNECT) {
                Log.i(
                    TAG,
                    "[RX] CONNECT sid=${s.id()} url=$baseUrl$NAMESPACE"
                )

                SocketManager.marcarConexion(
                    ConexionSocket.Conectado(s.id() ?: "")
                )

                try {
                    // El servidor obtiene el usuario del JWT.
                    s.emit("join")

                    Log.i(TAG, "[RX] Evento join emitido")

                } catch (e: Exception) {
                    Log.e(TAG, "[RX] Error al emitir join", e)
                }
            }

            // Error de conexión.
            s.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString()
                    ?: "desconocido"

                Log.e(TAG, "[RX] CONNECT_ERROR: $error")

                SocketManager.marcarConexion(
                    ConexionSocket.Desconectado
                )
            }

            // Desconexión.
            s.on(Socket.EVENT_DISCONNECT) { args ->
                val reason = args.firstOrNull()?.toString()
                    ?: "desconocido"

                Log.w(TAG, "[RX] DISCONNECT | motivo=$reason")

                SocketManager.marcarConexion(
                    ConexionSocket.Desconectado
                )
            }

            // Confirmación de incorporación a la sala privada.
            s.on("joined") { args ->
                try {
                    val data = aJson(args.firstOrNull())

                    if (data == null) {
                        Log.w(TAG, "[RX] Respuesta joined inválida")
                        return@on
                    }

                    val room = data.optString("room", "")
                    val noLeidas = data.optInt("no_leidas", -1)

                    Log.i(
                        TAG,
                        "[RX] Unido a sala=$room | no_leidas=$noLeidas"
                    )

                    if (noLeidas >= 0) {
                        SocketManager.setContador(noLeidas)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "[RX] Error procesando joined", e)
                }
            }

            // Único evento oficial de notificaciones.
            s.on("notification") { args ->
                procesarNotificacion(args)
            }

            // Actualización del contador.
            s.on("contador_actualizado") { args ->
                try {
                    val data = aJson(args.firstOrNull())
                    val noLeidas = data?.optInt("no_leidas", -1) ?: -1

                    Log.d(
                        TAG,
                        "[RX] contador_actualizado -> $noLeidas"
                    )

                    if (noLeidas >= 0) {
                        SocketManager.setContador(noLeidas)
                    }

                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "[RX] Error procesando contador_actualizado",
                        e
                    )
                }
            }

            // El servidor rechazó la incorporación a la sala.
            s.on("error_sala") { args ->
                val error = args.firstOrNull()?.toString()
                    ?: "error desconocido"

                Log.e(TAG, "[RX] Error al incorporarse a sala: $error")

                SocketManager.marcarConexion(
                    ConexionSocket.Desconectado
                )

                // No conservar una conexión que no está autorizada
                // para recibir notificaciones privadas.
                try {
                    s.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "[RX] Error cerrando socket rechazado", e)
                }
            }

            s.connect()

        } catch (e: URISyntaxException) {
            Log.e(TAG, "[RX] URL inválida: $baseUrl", e)

            SocketManager.marcarConexion(
                ConexionSocket.Desconectado
            )

        } catch (e: Exception) {
            Log.e(TAG, "[RX] Excepción al conectar Socket.IO", e)

            SocketManager.marcarConexion(
                ConexionSocket.Desconectado
            )
        }
    }

    /**
     * Convierte el contenido recibido a JSONObject.
     */
    private fun aJson(raw: Any?): JSONObject? {
        return when (raw) {
            is JSONObject -> raw

            is JSONArray -> raw.optJSONObject(0)

            is String -> runCatching {
                JSONObject(raw)
            }.getOrNull()

            else -> null
        }
    }

    /**
     * Procesa una notificación recibida desde el evento notification.
     *
     * El backend envía:
     * {
     *   "type": "notification",
     *   "data": {
     *     "notificacion_id": 123,
     *     "documento_id": 456,
     *     "documento_uuid": "...",
     *     "tipo": "...",
     *     "mensaje": "..."
     *   },
     *   "no_leidas": 5
     * }
     */
    private fun procesarNotificacion(args: Array<out Any?>) {

        Log.i(
            TAG,
            "[RX] Evento notification recibido | args=${args.size}"
        )

        val raw = args.firstOrNull()
        val json = aJson(raw)

        if (json == null) {
            Log.e(TAG, "[RX] Payload inválido para notification")
            return
        }

        try {
            val data = json.optJSONObject("data") ?: json

            // El contador normalmente viene en el nivel superior.
            val noLeidas = when {
                json.has("no_leidas") ->
                    json.optInt("no_leidas", -1)

                data.has("no_leidas") ->
                    data.optInt("no_leidas", -1)

                else -> -1
            }.takeIf { it >= 0 }

            val notifId = data.optLong("notificacion_id", 0L)

            val documentoId = data.optLong(
                "documento_id",
                0L
            ).takeIf { it > 0L }

            val documentoUuid = data.optString(
                "documento_uuid",
                ""
            )

            val tipo = data.optString(
                "tipo",
                "SISTEMA"
            )

            val mensaje = data.optString(
                "mensaje",
                ""
            ).trim()

            // Solo procesar notificaciones persistidas.
            if (notifId <= 0L) {
                Log.e(
                    TAG,
                    "[RX] Notificación ignorada: ID inválido | payload=$json"
                )
                return
            }

            if (mensaje.isBlank()) {
                Log.w(
                    TAG,
                    "[RX] Notificación ignorada: mensaje vacío | id=$notifId"
                )
                return
            }

            val notif = NotificacionSocket(
                mensaje = mensaje,
                documentoId = documentoId,
                documentoUuid = documentoUuid,
                tipo = tipo,
                noLeidas = noLeidas,
                notificacionId = notifId
            )

            Log.i(
                TAG,
                "[RX] Notificación validada | " +
                        "tipo=${notif.tipo} | " +
                        "documento=${notif.documentoUuid} | " +
                        "notifId=${notif.notificacionId} | " +
                        "noLeidas=${notif.noLeidas}"
            )

            SocketManager.emitirNotificacion(notif)

        } catch (e: Exception) {
            Log.e(TAG, "[RX] Error procesando notification", e)
        }
    }

    /**
     * Desconecta y libera los listeners del socket.
     *
     * No limpia contador ni IDs publicados.
     */
    @Synchronized
    fun disconnect() {

        try {
            val s = socket

            if (s != null) {

                if (s.connected()) {
                    try {
                        s.emit("leave")
                    } catch (e: Exception) {
                        Log.w(TAG, "[RX] No se pudo emitir leave", e)
                    }
                }

                s.io().off()
                s.off()
                s.disconnect()
                s.close()
            }

        } catch (e: Exception) {
            Log.w(TAG, "[RX] Error al desconectar Socket.IO", e)

        } finally {
            socket = null
            currentBaseUrl = null
            currentUsuario = null
            currentTokenProvider = null

            Log.d(TAG, "[RX] Socket.IO desconectado y liberado")
        }
    }

    /**
     * Indica si existe una conexión activa.
     */
    @Synchronized
    fun isConnected(): Boolean {
        return socket?.connected() == true
    }
}
