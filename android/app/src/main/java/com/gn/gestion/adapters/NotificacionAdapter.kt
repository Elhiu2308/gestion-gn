package com.gn.gestion.adapters

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gn.gestion.R
import com.gn.gestion.models.NotificacionDto
import com.gn.gestion.services.Notificador
import com.gn.gestion.util.c
import com.gn.gestion.util.formatHora
import com.google.android.material.card.MaterialCardView

class NotificacionAdapter(
    private val onClick: (NotificacionDto) -> Unit
) : ListAdapter<NotificacionDto, NotificacionAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notificacion, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tarjeta = itemView as MaterialCardView
        private val iconoBg: View = itemView.findViewById(R.id.icon_bg)
        private val icono: ImageView = itemView.findViewById(R.id.icono)
        private val titulo: TextView = itemView.findViewById(R.id.titulo)
        private val mensaje: TextView = itemView.findViewById(R.id.mensaje)
        private val fecha: TextView = itemView.findViewById(R.id.fecha)
        private val punto: View = itemView.findViewById(R.id.punto_no_leida)

        fun bind(n: NotificacionDto, onClick: (NotificacionDto) -> Unit) {
            val ctx = itemView.context

            val esDocumento = n.tipo.contains("DOCUMENTO") ||
                n.tipo == "ANOTACION" ||
                n.tipo == "RESPUESTA" ||
                n.tipo == "CORRECCION" ||
                n.tipo == "VALIDACION" ||
                n.tipo == "ATENCION" ||
                n.tipo == "VENCIMIENTO" ||
                (n.documentoId ?: 0L) > 0L

            val color = ctx.c(if (esDocumento) R.color.g_verde else R.color.g_dorado)

            iconoBg.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 28))
            icono.setColorFilter(color)
            icono.setImageResource(
                if (esDocumento) R.drawable.ic_nav_escanner else R.drawable.ic_notifications
            )

            titulo.text = if (n.tipo.isBlank() && esDocumento) {
                "Documento asignado"
            } else {
                Notificador.tituloDe(n.tipo)
            }
            titulo.setTypeface(null, if (n.leida) Typeface.NORMAL else Typeface.BOLD)
            titulo.setTextColor(ctx.c(if (n.leida) R.color.gris_texto else R.color.texto_principal))

            mensaje.text = n.mensaje
            mensaje.setTextColor(ctx.c(R.color.gris_texto))

            fecha.text = if (n.creadoEn > 0L) formatHora(n.creadoEn) else ""
            fecha.visibility = if (n.creadoEn > 0L) View.VISIBLE else View.GONE

            punto.visibility = if (n.leida) View.GONE else View.VISIBLE

            tarjeta.strokeColor = ctx.c(
                if (n.leida) R.color.borde_suave else R.color.g_verde
            )
            tarjeta.strokeWidth = if (n.leida) 1 else 2
            tarjeta.setOnClickListener { onClick(n) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NotificacionDto>() {
            override fun areItemsTheSame(oldItem: NotificacionDto, newItem: NotificacionDto): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: NotificacionDto, newItem: NotificacionDto): Boolean =
                oldItem == newItem
        }
    }
}
