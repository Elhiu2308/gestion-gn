package com.gn.gestion.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aviso que se muestra dentro de la app cuando llega una notificación con la
 * aplicación en primer plano.
 */
data class AvisoNotificacion(
    val titulo: String,
    val cuerpo: String,
    val documentoUuid: String = "",
    val documentoId: Long = 0L,
    val notificacionId: Long = 0L
)

/**
 * Puente entre los receptores de notificaciones (Socket.IO/FCM) y la actividad
 * visible. Mientras la app está en primer plano se evita publicar una
 * notificación del sistema y se muestra un mensaje emergente con acciones.
 */
object NotificacionForeground {

    @Volatile
    var visible: Boolean = false

    private val _aviso = MutableStateFlow<AvisoNotificacion?>(null)

    val aviso: StateFlow<AvisoNotificacion?> = _aviso.asStateFlow()

    /** Evita duplicados cuando Socket.IO y FCM entregan el mismo evento. */
    private val clavesMostradas = LinkedHashSet<String>()

    fun emitir(
        titulo: String,
        cuerpo: String,
        documentoUuid: String = "",
        documentoId: Long = 0L,
        notificacionId: Long = 0L
    ) {
        if (cuerpo.isBlank()) return

        val claveDocumento = "doc:$documentoId:$cuerpo"
        val claveId = if (notificacionId > 0L) "id:$notificacionId" else null

        synchronized(clavesMostradas) {
            if (clavesMostradas.contains(claveDocumento) ||
                (claveId != null && clavesMostradas.contains(claveId))
            ) {
                return
            }

            clavesMostradas.add(claveDocumento)
            if (claveId != null) clavesMostradas.add(claveId)

            if (clavesMostradas.size > 400) {
                val iterador = clavesMostradas.iterator()
                repeat(clavesMostradas.size - 300) {
                    if (iterador.hasNext()) {
                        iterador.next()
                        iterador.remove()
                    }
                }
            }
        }

        _aviso.value = AvisoNotificacion(
            titulo = titulo,
            cuerpo = cuerpo,
            documentoUuid = documentoUuid,
            documentoId = documentoId,
            notificacionId = notificacionId
        )
    }

    fun consumir(notificacionId: Long) {
        val actual = _aviso.value ?: return
        if (actual.notificacionId == notificacionId) {
            _aviso.value = null
        }
    }
}
