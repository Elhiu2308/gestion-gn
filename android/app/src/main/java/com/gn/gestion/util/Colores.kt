package com.gn.gestion.util;

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.gn.gestion.R
import com.gn.gestion.models.EstadoDocumento;
import com.gn.gestion.models.TipoDocumento;
import com.gn.gestion.models.Urgencia;
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.c(colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

fun TipoDocumento.colorRes(): Int = when (this) {
    TipoDocumento.MENSAJE -> R.color.tipo_mensaje
    TipoDocumento.OFICIO -> R.color.tipo_oficio
    TipoDocumento.SWEAR -> R.color.tipo_swear
    TipoDocumento.MEMORANDUM -> R.color.tipo_mensaje
    TipoDocumento.CIRCULAR -> R.color.estado_pasado
    TipoDocumento.INFORME -> R.color.tipo_oficio
    TipoDocumento.TARJETA_INFORMATIVA -> R.color.tipo_swear
    TipoDocumento.OTRO -> R.color.tipo_otro
}

fun EstadoDocumento.colorRes(): Int = when (this) {
    EstadoDocumento.RECIBIDO, EstadoDocumento.PENDIENTE_TURNO,
    EstadoDocumento.PENDIENTE_ASIGNACION, EstadoDocumento.PENDIENTE -> R.color.estado_pendiente
    EstadoDocumento.TURNADO, EstadoDocumento.DESIGNADO -> R.color.tipo_mensaje
    EstadoDocumento.EN_DISTRIBUCION, EstadoDocumento.ASIGNADO -> R.color.tipo_oficio
    EstadoDocumento.EN_ATENCION, EstadoDocumento.EN_PROCESO -> R.color.ambar
    EstadoDocumento.ATENCION_PARCIAL -> R.color.ambar
    EstadoDocumento.RESPUESTAS_COMPLETAS, EstadoDocumento.RESPONDIDO,
    EstadoDocumento.CONTESTADO -> R.color.estado_contestado
    EstadoDocumento.CONSOLIDADO, EstadoDocumento.CONSOLIDANDO -> R.color.estado_pasado
    EstadoDocumento.EN_REVISION -> R.color.ambar
    EstadoDocumento.EN_VALIDACION -> R.color.tipo_oficio
    EstadoDocumento.VALIDADO -> R.color.estado_contestado
    EstadoDocumento.CONCLUIDO, EstadoDocumento.FINALIZADO -> R.color.estado_contestado
    EstadoDocumento.RECHAZADO, EstadoDocumento.DEVUELTO -> R.color.rojo_urgencia
    EstadoDocumento.HISTORICO, EstadoDocumento.REGISTRADO,
    EstadoDocumento.CANCELADO -> R.color.gris_sutil
    EstadoDocumento.ARCHIVADO -> R.color.tipo_otro
}

fun Urgencia.colorRes(): Int = when (this) {
    Urgencia.ORDINARIO -> R.color.gris_sutil
    Urgencia.URGENTE -> R.color.ambar
    Urgencia.EXTRAURGENTE -> R.color.rojo_urgencia
}

fun tipoFromString(s: String): TipoDocumento =
    if (s == "SWEAP") TipoDocumento.SWEAR
    else TipoDocumento.entries.firstOrNull { it.name == s } ?: TipoDocumento.OTRO

fun estadoFromString(s: String): EstadoDocumento {
    val canonico = EstadoDocumento.LEGADO_A_CANONICO[s] ?: s
    return EstadoDocumento.entries.firstOrNull { it.name == canonico } ?: EstadoDocumento.RECIBIDO
}

fun urgenciaFromString(s: String): Urgencia =
    Urgencia.LEGADO_A_CANONICO[s]
        ?: Urgencia.entries.firstOrNull { it.name == s }
        ?: Urgencia.ORDINARIO

fun formatFecha(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatHora(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun dp(context: Context, value: Int): Int =
    (context.resources.displayMetrics.density * value).toInt()

fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

/** Construye una "etiqueta" tipo badge con fondo redondeado. */
fun Context.textoBadge(texto: String, textColor: Int, bgColRes: Int = R.color.fondo_claro): TextView {
    return TextView(this).apply {
        text = texto
        setTextColor(textColor)
        textSize = 12f
        val density = resources.displayMetrics.density
        val badgeBg = GradientDrawable().apply {
            cornerRadius = 50 * density
            setColor(ContextCompat.getColor(this@textoBadge, bgColRes))
        }
        this.background = badgeBg
        setPadding(
            (10 * density).toInt(),
            (4 * density).toInt(),
            (10 * density).toInt(),
            (4 * density).toInt()
        )
        gravity = Gravity.CENTER
    }
}