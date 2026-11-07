package com.gn.gestion.util

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gn.gestion.R
import com.google.android.material.snackbar.Snackbar

object GestioNMessageManager {

    enum class Tipo(val color: Int, val icono: Int) {
        EXITO(R.color.g_verde, R.drawable.ic_msg_exito),
        ERROR(R.color.rojo_urgencia, R.drawable.ic_msg_error),
        ADVERTENCIA(R.color.g_dorado, R.drawable.ic_msg_advertencia),
        INFORMACION(R.color.azul, R.drawable.ic_msg_informacion);
    }

    private var toastActivo: Toast? = null

    fun mostrarToast(
        context: Context,
        mensaje: String,
        tipo: Tipo = Tipo.INFORMACION,
        duracion: Int = Toast.LENGTH_SHORT
    ) {
        toastActivo?.cancel()
        toastActivo = Toast(context).apply {
            val vista = LayoutInflater.from(context).inflate(R.layout.layout_toast_mensaje, null)
            vista.findViewById<ImageView>(R.id.toast_icono).apply {
                setImageDrawable(ContextCompat.getDrawable(context, tipo.icono))
                setColorFilter(context.getColor(tipo.color))
            }
            vista.findViewById<TextView>(R.id.toast_texto).text = mensaje
            setView(vista)
            setDuration(duracion)
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0)
            setMargin(0f, 0.15f)
        }
        toastActivo?.show()
    }

    fun setGravity(gravity: Int, xOffset: Int) {}

    fun mostrarSnackbar(
        vista: View,
        mensaje: String,
        accion: String? = null,
        listener: (() -> Unit)? = null
    ) {
        Snackbar.make(vista, mensaje, Snackbar.LENGTH_LONG)
            .apply {
                setAction(accion) { listener?.invoke() }
                setBackgroundTint(ContextCompat.getColor(vista.context, R.color.texto_principal))
                setTextColor(ContextCompat.getColor(vista.context, R.color.blanco))
                setActionTextColor(ContextCompat.getColor(vista.context, R.color.g_dorado))
                show()
            }
    }

    fun cancelarToast() {
        toastActivo?.cancel()
        toastActivo = null
    }
}
