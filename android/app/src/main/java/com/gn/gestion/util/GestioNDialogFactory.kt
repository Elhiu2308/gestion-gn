package com.gn.gestion.util

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gn.gestion.R

object GestioNDialogFactory {

    private var dialogoCarga: Dialog? = null

    fun mostrarConfirmacion(
        context: Context,
        titulo: String,
        mensaje: String,
        tipo: GestioNMessageManager.Tipo = GestioNMessageManager.Tipo.INFORMACION,
        textoPositivo: String = "Aceptar",
        textoNegativo: String = "Cancelar",
        onPositivo: () -> Unit,
        onNegativo: (() -> Unit)? = null
    ) {
        val vista = LayoutInflater.from(context).inflate(R.layout.dialog_confirmacion, null)

        vista.findViewById<TextView>(R.id.dialog_title).text = titulo
        vista.findViewById<TextView>(R.id.dialog_message).text = mensaje

        vista.findViewById<ImageView>(R.id.dialog_icon).apply {
            setImageDrawable(ContextCompat.getDrawable(context, tipo.icono))
            setColorFilter(context.getColor(tipo.color))
        }

        val btnNegativo = vista.findViewById<Button>(R.id.dialog_button_negative)
        val btnPositivo = vista.findViewById<Button>(R.id.dialog_button_positive)

        btnNegativo.text = textoNegativo
        btnPositivo.text = textoPositivo

        val dialog = MaterialAlertDialogBuilder(context, R.style.GestioN_Dialog_Base)
            .setView(vista)
            .create()
        dialog.setCancelable(onNegativo != null)

        btnNegativo.setOnClickListener {
            onNegativo?.invoke()
            dialog.dismiss()
        }
        btnPositivo.setOnClickListener {
            onPositivo()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun mostrarCarga(context: Context) {
        if (dialogoCarga?.isShowing == true) return
        dialogoCarga = Dialog(context, R.style.GestioN_Dialog_Carga)
        dialogoCarga?.setContentView(R.layout.dialog_carga)
        dialogoCarga?.setCancelable(false)
        dialogoCarga?.show()
    }

    fun ocultarCarga() {
        dialogoCarga?.dismiss()
        dialogoCarga = null
    }

    fun mostrarError(
        context: Context,
        mensaje: String,
        onReintentar: (() -> Unit)? = null
    ) {
        mostrarConfirmacion(
            context = context,
            titulo = "No se pudo completar",
            mensaje = mensaje,
            tipo = GestioNMessageManager.Tipo.ERROR,
            textoPositivo = if (onReintentar != null) "Reintentar" else "Cerrar",
            onPositivo = { onReintentar?.invoke() }
        )
    }

    fun mostrarExito(
        context: Context,
        mensaje: String,
        onAceptar: (() -> Unit)? = null
    ) {
        mostrarConfirmacion(
            context = context,
            titulo = "Operación completada",
            mensaje = mensaje,
            tipo = GestioNMessageManager.Tipo.EXITO,
            textoPositivo = "Aceptar",
            onPositivo = { onAceptar?.invoke() }
        )
    }
}
