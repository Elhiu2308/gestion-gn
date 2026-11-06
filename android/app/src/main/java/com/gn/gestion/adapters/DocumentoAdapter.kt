package com.gn.gestion.adapters;

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gn.gestion.R
import com.gn.gestion.models.Documento
import com.gn.gestion.models.EstadoDocumento
import com.gn.gestion.util.c
import com.gn.gestion.util.estadoFromString
import com.gn.gestion.util.formatFecha
import com.gn.gestion.util.textoBadge
import com.gn.gestion.util.tipoFromString

class DocumentoAdapter(
    private val onClick: (Documento) -> Unit
) : ListAdapter<Documento, DocumentoAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_documento, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(doc: Documento, onClick: (Documento) -> Unit) {
            val ctx = itemView.context
            val tipo = tipoFromString(doc.tipo)
            val estado = estadoFromString(doc.estado)
            val colorVerde = ctx.c(R.color.g_verde)

            itemView.findViewById<View>(R.id.barra_tipo)
                .backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.g_verde)
            itemView.findViewById<TextView>(R.id.tipo_label).apply {
                text = tipo.label
                setTextColor(colorVerde)
            }
            itemView.findViewById<TextView>(R.id.folio).text =
                doc.folio.ifBlank { "Sin folio" }
            itemView.findViewById<TextView>(R.id.asunto).text =
                doc.asunto.ifBlank { "Sin asunto" }

            // Todos los puntos de prioridad en verde
            val dot = itemView.findViewById<View>(R.id.punto_urgencia)
            dot.visibility = View.VISIBLE
            dot.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.g_verde)
            dot.alpha = 1f

            val badges = itemView.findViewById<LinearLayout>(R.id.badges_container)
            badges.removeAllViews()
            val densidad = ctx.resources.displayMetrics.density
            fun badge(texto: String) {
                val vista = ctx.textoBadge(texto, colorVerde, R.color.fondo_claro)
                vista.maxLines = 1
                vista.ellipsize = android.text.TextUtils.TruncateAt.END
                vista.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (6 * densidad).toInt() }
                badges.addView(vista)
            }

            val esTurnado = estado == EstadoDocumento.TURNADO
            val esPendienteTurno = estado == EstadoDocumento.PENDIENTE_TURNO || estado == EstadoDocumento.PENDIENTE
            val esEnAtencion = estado == EstadoDocumento.EN_ATENCION || estado == EstadoDocumento.EN_PROCESO

            if (esTurnado || esPendienteTurno || esEnAtencion) {
                val textoEstado = when {
                    esTurnado -> "Turnado"
                    esPendienteTurno -> "Pendiente de turno"
                    else -> "En atención"
                }
                badge(textoEstado)
            }

            badge(formatFecha(doc.fecha))

            val dias = maxOf(0L, (System.currentTimeMillis() - doc.fecha) / (1000 * 60 * 60 * 24))
            itemView.findViewById<TextView>(R.id.tv_dias_transcurridos).text =
                "$dias ${if (dias == 1L) "día" else "días"}"

            itemView.setOnClickListener { onClick(doc) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Documento>() {
            override fun areItemsTheSame(oldItem: Documento, newItem: Documento): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Documento, newItem: Documento): Boolean =
                oldItem == newItem
        }
    }
}
