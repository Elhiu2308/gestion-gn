package com.gn.gestion.adapters

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gn.gestion.R
import com.gn.gestion.models.Documento
import com.gn.gestion.models.EstadoDocumento
import com.gn.gestion.models.Urgencia
import com.gn.gestion.util.c
import com.gn.gestion.util.estadoFromString
import com.gn.gestion.util.formatFecha
import com.gn.gestion.util.textoBadge
import com.gn.gestion.util.tipoFromString
import com.gn.gestion.util.urgenciaFromString
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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

            val colorUrgencia = when (urgenciaFromString(doc.urgencia)) {
                Urgencia.URGENTE -> R.color.ambar
                Urgencia.EXTRAURGENTE -> R.color.rojo_urgencia
                else -> R.color.g_verde
            }
            val dot = itemView.findViewById<View>(R.id.punto_urgencia)
            dot.visibility = View.VISIBLE
            dot.backgroundTintList = ContextCompat.getColorStateList(ctx, colorUrgencia)
            dot.alpha = 1f

            val badges = itemView.findViewById<LinearLayout>(R.id.badges_container)
            badges.removeAllViews()
            val densidad = ctx.resources.displayMetrics.density
            fun dp(valor: Int) = (valor * densidad).toInt()
            fun badge(texto: String) {
                val vista = ctx.textoBadge(texto, colorVerde, R.color.fondo_claro)
                vista.maxLines = 1
                vista.ellipsize = android.text.TextUtils.TruncateAt.END
                vista.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
                badges.addView(vista)
            }

            val esTurnado = estado == EstadoDocumento.TURNADO
            val esPendienteTurno = estado == EstadoDocumento.PENDIENTE_TURNO ||
                estado == EstadoDocumento.PENDIENTE
            val esEnAtencion = estado == EstadoDocumento.EN_ATENCION ||
                estado == EstadoDocumento.EN_PROCESO

            if (esTurnado || esPendienteTurno || esEnAtencion) {
                val textoEstado = when {
                    esTurnado -> "Turnado"
                    esPendienteTurno -> "Pendiente de turno"
                    else -> "En atención"
                }
                badge(textoEstado)
            }

            badge(formatFecha(doc.fecha))

            badges.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            diasRestantes(doc)?.let { dias ->
                badges.addView(TextView(ctx).apply {
                    text = if (dias == 1L) "1 día" else "$dias días"
                    setTextColor(ContextCompat.getColor(ctx, R.color.gris_texto))
                    textSize = 10f
                    setTypeface(typeface, Typeface.NORMAL)
                    maxLines = 1
                    gravity = Gravity.END
                })
            }
            badges.addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_forward)
                imageTintList = ContextCompat.getColorStateList(ctx, R.color.gris_texto)
                alpha = 0.6f
                contentDescription = "Abrir documento"
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                    marginStart = dp(4)
                }
            })

            itemView.setOnClickListener { onClick(doc) }
        }

        private fun diasRestantes(doc: Documento): Long? {
            if (doc.estado in EstadoDocumento.TERMINALES) return null
            val fecha = doc.fechaLimite.trim()
            if (fecha.isBlank()) return null
            return runCatching {
                ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(fecha.take(10)))
            }.getOrNull()
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
