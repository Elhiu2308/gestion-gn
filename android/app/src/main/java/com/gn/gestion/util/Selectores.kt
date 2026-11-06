package com.gn.gestion.util;

import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.LinearLayout
import com.gn.gestion.R
import com.google.android.material.button.MaterialButton

/** Opción de un selector: valor interno, etiqueta mostrada y color opcional. */
data class OpcionItem(
    val valor: String,
    val etiqueta: String,
    val colorRes: Int? = null
)

/**
 * Construye una fila de botones tipo "chip" sobre [container].
 * Reconstruye el grupo con la nueva selección al hacer clic.
 */
fun LinearLayout.pintarOpciones(
    opciones: List<OpcionItem>,
    seleccion: String,
    onSeleccion: (String) -> Unit
) {
    removeAllViews()
    val ctx = context
    opciones.forEach { op ->
        val btn = MaterialButton(ctx)
        btn.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = dp(ctx, 4)
            marginEnd = dp(ctx, 4)
        }
        btn.isAllCaps = false
        btn.insetTop = 0
        btn.insetBottom = 0
        btn.minHeight = dp(ctx, 44)
        btn.setPadding(0, 0, 0, 0)
        btn.text = op.etiqueta

        val selected = op.valor == seleccion
        val color = op.colorRes?.let { ctx.c(it) } ?: ctx.c(R.color.verde)
        if (selected) {
            btn.backgroundTintList = ColorStateList.valueOf(color)
            btn.setTextColor(Color.WHITE)
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(ctx.c(R.color.surface_variant))
            btn.setTextColor(ctx.c(R.color.on_surface))
        }
        btn.setOnClickListener {
            onSeleccion(op.valor)
            pintarOpciones(opciones, op.valor, onSeleccion)
        }
        addView(btn)
    }
}