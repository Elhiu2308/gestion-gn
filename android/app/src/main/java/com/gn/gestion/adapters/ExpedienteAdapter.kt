package com.gn.gestion.adapters;

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gn.gestion.R
import com.gn.gestion.models.ExpedienteDto
import com.gn.gestion.util.c
import com.gn.gestion.util.formatFecha

/** Fila de expediente para resultados de búsqueda global y archivo. */
class ExpedienteAdapter(
    private val onClick: (ExpedienteDto) -> Unit
) : ListAdapter<ExpedienteDto, ExpedienteAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expediente, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(exp: ExpedienteDto, onClick: (ExpedienteDto) -> Unit) {
            val ctx = itemView.context

            itemView.findViewById<TextView>(R.id.titulo).text =
                exp.descripcion.ifBlank { "Expediente ${exp.numero}" }

            val clasif = exp.clasificacion.uppercase()
            val lblClasif = itemView.findViewById<TextView>(R.id.clasificacion)
            lblClasif.text = clasif
            lblClasif.setTextColor(
                ctx.c(
                    when (clasif) {
                        "HABIL" -> R.color.verde
                        "RESERVADO" -> R.color.ambar
                        "CONFIDENCIAL" -> R.color.rojo
                        else -> R.color.gris_texto
                    }
                )
            )

            itemView.findViewById<TextView>(R.id.cantidad).text =
                if (exp.numero.isNotBlank()) "No. ${exp.numero}" else "Expediente"

            itemView.findViewById<TextView>(R.id.detalle).text = buildString {
                if (exp.areaResponsable.isNotBlank()) append("Área: ${exp.areaResponsable}")
                if (exp.creadoEn > 0) {
                    if (isNotEmpty()) append("\n")
                    append("Registrado: ${formatFecha(exp.creadoEn)}")
                }
            }.ifBlank { "Sin información adicional" }

            itemView.setOnClickListener { onClick(exp) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ExpedienteDto>() {
            override fun areItemsTheSame(oldItem: ExpedienteDto, newItem: ExpedienteDto): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ExpedienteDto, newItem: ExpedienteDto): Boolean =
                oldItem == newItem
        }
    }
}