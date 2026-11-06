
package com.gn.gestion.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gn.gestion.R
import com.gn.gestion.views.MainActivity

object Notificador {

    private const val TAG = "Notificador"

    const val CHANNEL_ID = "gestion_documental"

    const val EXTRA_DOCUMENTO_UUID = "gn_notif_documento_uuid"
    const val EXTRA_DOCUMENTO_ID = "gn_notif_documento_id"
    const val EXTRA_NOTIFICACION_ID = "gn_notif_id"

    fun tituloDe(tipo: String): String = when (tipo) {
        "DOCUMENTO_ASIGNADO" -> "Documento asignado"
        "DOCUMENTO_COMPARTIDO" -> "Documento compartido"
        "DOCUMENTO_RECIBIDO" -> "Documento recibido"
        "ANOTACION" -> "Anotación"
        "RESPUESTA" -> "Respuesta"
        "RESPUESTA_RECHAZADA" -> "Respuesta rechazada"
        "CORRECCION" -> "Corrección"
        "VALIDACION" -> "Validación"
        "ATENCION" -> "Atención requerida"
        "VENCIMIENTO" -> "Vencimiento"
        else -> "Sistema"
    }

    fun permisoConcedido(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        val concedido = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "Permiso POST_NOTIFICATIONS: $concedido")
        return concedido
    }

    /**
     * @return true si Android aceptó la publicación.
     */
    fun mostrar(
        context: Context,
        titulo: String,
        cuerpo: String,
        documentoUuid: String = "",
        documentoId: Long = 0L,
        notificacionId: Long = 0L
    ): Boolean {

        val appContext = context.applicationContext

        Log.d(
            TAG,
            "Publicando | titulo=$titulo | docId=$documentoId | " +
                    "notifId=$notificacionId"
        )

        if (cuerpo.isBlank()) {
            Log.w(TAG, "Publicación cancelada: cuerpo vacío")
            return false
        }

        // Con la app visible no se publica una notificación del sistema:
        // MainActivity muestra el aviso emergente con las acciones.
        if (NotificacionForeground.visible) {
            NotificacionForeground.emitir(
                titulo = titulo.ifBlank { tituloDe("") },
                cuerpo = cuerpo,
                documentoUuid = documentoUuid,
                documentoId = documentoId,
                notificacionId = notificacionId
            )
            Log.i(TAG, "Notificación entregada a la app en primer plano | notifId=$notificacionId")
            return true
        }

        if (!permisoConcedido(appContext)) {
            Log.w(TAG, "Publicación cancelada: permiso no concedido")
            return false
        }

        return runCatching {

            crearCanal(appContext)

            val intent = Intent(
                appContext,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

                putExtra(EXTRA_DOCUMENTO_UUID, documentoUuid)
                putExtra(EXTRA_DOCUMENTO_ID, documentoId)
                putExtra(EXTRA_NOTIFICACION_ID, notificacionId)
            }

            val codigoPendiente = if (notificacionId > 0L) {
                (notificacionId % Int.MAX_VALUE).toInt()
            } else {
                System.currentTimeMillis().toInt()
            }

            val banderas = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_IMMUTABLE
                    } else {
                        0
                    }

            val pendiente = PendingIntent.getActivity(
                appContext,
                codigoPendiente,
                intent,
                banderas
            )

            val notificacion = NotificationCompat.Builder(
                appContext,
                CHANNEL_ID
            )
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titulo.ifBlank { tituloDe("") })
                .setContentText(cuerpo)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(cuerpo)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendiente)
                .build()

            val notificationManager =
                NotificationManagerCompat.from(appContext)

            if (!notificationManager.areNotificationsEnabled()) {
                Log.w(TAG, "Las notificaciones están deshabilitadas")
                return false
            }

            val notificationTag = if (notificacionId > 0L) {
                "notif_$notificacionId"
            } else {
                null
            }

            notificationManager.notify(
                notificationTag,
                if (notificationTag != null) 0 else codigoPendiente,
                notificacion
            )

            Log.i(
                TAG,
                "Notificación publicada | tag=$notificationTag | " +
                        "notifId=$notificacionId"
            )

            true

        }.getOrElse { error ->
            Log.e(TAG, "Error al publicar notificación", error)
            false
        }
    }

    private fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }

        val canal = NotificationChannel(
            CHANNEL_ID,
            "Gestión Documental",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones del flujo documental"
            enableVibration(true)
            enableLights(true)
        }

        manager.createNotificationChannel(canal)
    }
}
