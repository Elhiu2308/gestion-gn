package com.gn.gestion.views.expedientes

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gn.gestion.R
import com.gn.gestion.models.*
import com.gn.gestion.services.ApiFactory
import com.gn.gestion.util.c
import com.gn.gestion.util.dp
import com.gn.gestion.util.mensajeServidor
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

class ExpedientesFragment : Fragment(R.layout.fragment_expedientes) {

    private var expedientes: List<ExpedienteDto> = emptyList()
    private var textoBusqueda: String = ""
    private var filtroClasificacion: String = ""
    private var filtroArea: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.btn_nuevo).setOnClickListener { dialogoCrear(view) }

        view.findViewById<TextInputEditText>(R.id.et_buscar_expediente)
            ?.doAfterTextChanged { editable ->
                textoBusqueda = editable?.toString().orEmpty()
                renderizar(view)
            }

        view.findViewById<MaterialButton>(R.id.btn_filtros_expedientes)
            ?.setOnClickListener { dialogoFiltros(view) }

        actualizarIndicadorFiltros(view)
        viewLifecycleOwner.lifecycleScope.launch { cargar(view) }
    }

    private suspend fun cargar(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.container_expedientes)
        val lbl = view.findViewById<TextView>(R.id.lbl_count)
        container.removeAllViews()
        try {
            expedientes = ApiFactory.api(requireContext()).listarExpedientes()
            renderizar(view)
        } catch (ex: Exception) {
            expedientes = emptyList()
            lbl.text = "Sin conexión"
            container.removeAllViews()
            container.addView(TextView(requireContext()).apply {
                text = ex.mensajeServidor()
                textSize = 14f
                setTextColor(requireContext().c(R.color.rojo_urgencia))
                setPadding(dp(requireContext(), 16), dp(requireContext(), 16), dp(requireContext(), 16), dp(requireContext(), 16))
            })
        }
    }

    private fun renderizar(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.container_expedientes) ?: return
        val lbl = view.findViewById<TextView>(R.id.lbl_count) ?: return
        container.removeAllViews()

        val consulta = textoBusqueda.trim()
        val clasificacion = filtroClasificacion.trim()
        val area = filtroArea.trim()

        val filtrados = expedientes.filter { e ->
            val coincideTexto = consulta.isBlank() || listOf(
                e.numero,
                e.descripcion,
                e.clasificacion,
                e.areaResponsable
            ).any { it.contains(consulta, ignoreCase = true) }

            val coincideClasificacion = clasificacion.isBlank() ||
                e.clasificacion.contains(clasificacion, ignoreCase = true)
            val coincideArea = area.isBlank() ||
                e.areaResponsable.contains(area, ignoreCase = true)

            coincideTexto && coincideClasificacion && coincideArea
        }

        lbl.text = "${filtrados.size} expediente(s)"

        if (expedientes.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "Sin expedientes registrados."
                textSize = 14f
                setTextColor(requireContext().c(R.color.gris_texto))
                setPadding(dp(requireContext(), 16), dp(requireContext(), 16), dp(requireContext(), 16), dp(requireContext(), 16))
            })
            return
        }

        if (filtrados.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.sin_coincidencias_expedientes)
                textSize = 14f
                setTextColor(requireContext().c(R.color.gris_texto))
                setPadding(dp(requireContext(), 16), dp(requireContext(), 16), dp(requireContext(), 16), dp(requireContext(), 16))
            })
            return
        }

        filtrados.forEach { e ->
            container.addView(filaExpediente(view, e.id, e.numero, e.descripcion, e.clasificacion, e.areaResponsable))
        }
    }

    private fun dialogoFiltros(view: View) {
        val ctx = requireContext()
        val (tilCls, inputCls) = crearCampoTexto(ctx, "Clasificación", "PUBLICO, CONFIDENCIAL o RESERVADO")
        val (tilArea, inputArea) = crearCampoTexto(ctx, "Área responsable", "Escribe el área a filtrar")
        inputCls.setText(filtroClasificacion)
        inputArea.setText(filtroArea)

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle(R.string.filtros_avanzados)
            .setView(column(tilCls, tilArea))
            .setPositiveButton(R.string.aplicar_filtros) { _, _ ->
                filtroClasificacion = inputCls.text?.toString()?.trim().orEmpty()
                filtroArea = inputArea.text?.toString()?.trim().orEmpty()
                actualizarIndicadorFiltros(view)
                renderizar(view)
            }
            .setNeutralButton(R.string.limpiar_filtros) { _, _ ->
                filtroClasificacion = ""
                filtroArea = ""
                textoBusqueda = ""
                view.findViewById<TextInputEditText>(R.id.et_buscar_expediente)?.setText("")
                actualizarIndicadorFiltros(view)
                renderizar(view)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun actualizarIndicadorFiltros(view: View) {
        val activos = (if (filtroClasificacion.isBlank()) 0 else 1) +
            (if (filtroArea.isBlank()) 0 else 1)
        val boton = view.findViewById<MaterialButton>(R.id.btn_filtros_expedientes) ?: return
        boton.text = if (activos > 0) {
            getString(R.string.filtros_expedientes_activos, activos)
        } else {
            getString(R.string.filtros)
        }
    }

    private fun filaExpediente(view: View, id: Long, numero: String, desc: String, cls: String, area: String): View {
        val ctx = requireContext()
        val card = MaterialCardView(ctx).apply {
            radius = dp(ctx, 16).toFloat()
            cardElevation = dp(ctx, 1).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.blanco))
            strokeColor = ContextCompat.getColor(ctx, R.color.g_grisc)
            strokeWidth = dp(ctx, 1)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(ctx, 10) }
            isClickable = true
            isFocusable = true
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val colorBar = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 4), ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.g_verde))
        }

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 12), dp(ctx, 14))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvNum = TextView(ctx).apply {
            text = numero.ifBlank { "(sin número)" }
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.texto_principal))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val colorCls = when (cls.uppercase()) {
            "RESERVADO" -> R.color.rojo_urgencia
            "PUBLICO" -> R.color.verde
            else -> R.color.dorado
        }

        val badgeCls = TextView(ctx).apply {
            text = cls.uppercase()
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, colorCls))
            setPadding(dp(ctx, 8), dp(ctx, 3), dp(ctx, 8), dp(ctx, 3))
            setBackgroundResource(R.drawable.bg_badge)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, colorCls).and(0x00FFFFFF).or(0x20000000.toInt()))
        }

        headerRow.addView(tvNum)
        headerRow.addView(badgeCls)

        val tvDesc = TextView(ctx).apply {
            text = desc.ifBlank { "Sin descripción" }
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.texto_principal))
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 2))
        }

        val tvSub = TextView(ctx).apply {
            text = area.ifBlank { "Área no especificada" }
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.gris_texto))
        }

        col.addView(headerRow)
        col.addView(tvDesc)
        col.addView(tvSub)

        val chevron = TextView(ctx).apply {
            text = "›"
            textSize = 22f
            setTextColor(ContextCompat.getColor(ctx, R.color.gris_texto))
            setPadding(0, 0, dp(ctx, 14), 0)
        }

        row.addView(colorBar)
        row.addView(col)
        row.addView(chevron)

        card.addView(row)
        card.setOnClickListener { lifecycleScope.launch { dialogoDetalle(view, id) } }
        return card
    }

    private suspend fun dialogoDetalle(view: View, expId: Long) {
        try {
            val json = ApiFactory.api(requireContext()).detalleExpediente(expId)
            val exp = json.getAsJsonObject("expediente")
            val docs = json.getAsJsonArray("documentos")

            val ctx = requireContext()
            val col = column()

            fun campo(label: String, valor: String) {
                col.addView(TextView(ctx).apply {
                    text = "$label: ${valor.ifBlank { "—" }}"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(ctx, R.color.texto_principal))
                    setPadding(0, dp(ctx, 4), 0, dp(ctx, 4))
                })
            }
            campo("Número", exp.get("numero")?.asString ?: "")
            campo("Descripción", exp.get("descripcion")?.asString ?: "")
            campo("Clasificación", exp.get("clasificacion")?.asString ?: "")
            campo("Área", exp.get("area_responsable")?.asString ?: "")

            if (docs != null && docs.size() > 0) {
                col.addView(TextView(ctx).apply {
                    text = "\nDocumentos vinculados (${docs.size()})"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(ctx, R.color.g_verde))
                })
                for (d in docs) {
                    val obj = d.asJsonObject
                    col.addView(TextView(ctx).apply {
                        text = "• ${obj.get("folio")?.asString ?: obj.get("uuid")?.asString ?: "?"} — ${obj.get("asunto")?.asString?.take(40) ?: ""}"
                        textSize = 13f
                        setTextColor(ContextCompat.getColor(ctx, R.color.gris_texto))
                    })
                }
            } else {
                col.addView(TextView(ctx).apply {
                    text = "\nSin documentos vinculados."
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(ctx, R.color.gris_texto))
                })
            }

            MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
                .setTitle("Detalle del Expediente")
                .setView(col)
                .setPositiveButton("Vincular docs") { _, _ -> lifecycleScope.launch { dialogoVincular(view, expId) } }
                .setNegativeButton("Cerrar", null)
                .setNeutralButton("Eliminar") { _, _ ->
                    lifecycleScope.launch {
                        ApiFactory.api(requireContext()).eliminarExpediente(expId)
                        cargar(view)
                    }
                }
                .show()
        } catch (ex: Exception) {
            MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
                .setTitle("Error")
                .setMessage(ex.message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private suspend fun dialogoVincular(view: View, expId: Long) {
        try {
            val docs = ApiFactory.api(requireContext()).listarDocumentos()
            if (docs.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
                    .setTitle("Sin documentos")
                    .setMessage("No hay documentos disponibles para vincular.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            val ids = docs.map { it.id }.toLongArray()
            val labels = docs.map { d ->
                "${d.folio.ifBlank { "(sin folio)" }} · ${d.asunto.take(30)}"
            }
            val selected = BooleanArray(ids.size)
            MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
                .setTitle("Vincular documentos al expediente")
                .setMultiChoiceItems(labels.toTypedArray(), selected) { _, which, isChecked -> selected[which] = isChecked }
                .setPositiveButton("Vincular") { _, _ ->
                    val vinculados = ids.filterIndexed { i, _ -> selected[i] }
                    if (vinculados.isNotEmpty()) {
                        lifecycleScope.launch {
                            val body = JsonObject().apply {
                                add("documento_ids", JsonArray().apply { vinculados.forEach { add(it) } })
                            }
                            ApiFactory.api(requireContext()).vincularDocumentos(expId, body)
                            cargar(view)
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        } catch (ex: Exception) {
            MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
                .setTitle("Error")
                .setMessage(ex.message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun dialogoCrear(view: View) {
        val ctx = requireContext()

        val (tilNum, inputNum) = crearCampoTexto(ctx, "Número del expediente", "EXP-2025-001")
        val (tilDesc, inputDesc) = crearCampoTexto(ctx, "Descripción", "Describe el asunto del expediente", minLines = 2)
        val (tilArea, inputArea) = crearCampoTexto(ctx, "Área responsable", "Área que resguarda el expediente")

        var clasificacionSel = "CONFIDENCIAL"

        val tvClsLabel = TextView(ctx).apply {
            text = "Clasificación"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.texto_principal))
            setPadding(0, dp(ctx, 12), 0, dp(ctx, 6))
        }

        val containerPills = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnPub = MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Público"
            isAllCaps = false
            textSize = 12f
            cornerRadius = dp(ctx, 16)
        }
        val btnConf = MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Confidencial"
            isAllCaps = false
            textSize = 12f
            cornerRadius = dp(ctx, 16)
        }
        val btnRes = MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Reservado"
            isAllCaps = false
            textSize = 12f
            cornerRadius = dp(ctx, 16)
        }

        fun actualizarPills() {
            val vColor = ContextCompat.getColor(ctx, R.color.g_verde)
            val gColor = ContextCompat.getColor(ctx, R.color.gris_texto)
            val wColor = ContextCompat.getColor(ctx, R.color.blanco)

            btnPub.setBackgroundColor(if (clasificacionSel == "PUBLICO") vColor else Color.TRANSPARENT)
            btnPub.setTextColor(if (clasificacionSel == "PUBLICO") wColor else gColor)

            btnConf.setBackgroundColor(if (clasificacionSel == "CONFIDENCIAL") vColor else Color.TRANSPARENT)
            btnConf.setTextColor(if (clasificacionSel == "CONFIDENCIAL") wColor else gColor)

            btnRes.setBackgroundColor(if (clasificacionSel == "RESERVADO") vColor else Color.TRANSPARENT)
            btnRes.setTextColor(if (clasificacionSel == "RESERVADO") wColor else gColor)
        }

        btnPub.setOnClickListener { clasificacionSel = "PUBLICO"; actualizarPills() }
        btnConf.setOnClickListener { clasificacionSel = "CONFIDENCIAL"; actualizarPills() }
        btnRes.setOnClickListener { clasificacionSel = "RESERVADO"; actualizarPills() }

        actualizarPills()

        containerPills.addView(btnPub)
        containerPills.addView(btnConf)
        containerPills.addView(btnRes)

        val col = column(tilNum, tilDesc, tilArea, tvClsLabel, containerPills)

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Nuevo expediente")
            .setView(col)
            .setPositiveButton("Crear expediente") { _, _ ->
                val num = inputNum.text.toString().trim()
                val desc = inputDesc.text.toString().trim()
                val area = inputArea.text.toString().trim()

                if (num.isBlank()) {
                    tilNum.error = "Indica el número del expediente"
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val body = JsonObject().apply {
                        addProperty("numero", num)
                        addProperty("descripcion", desc)
                        addProperty("area_responsable", area)
                        addProperty("clasificacion", clasificacionSel)
                    }
                    try {
                        ApiFactory.api(requireContext()).crearExpediente(body)
                        cargar(view)
                    } catch (e: Exception) {
                        MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
                            .setTitle("Error al crear expediente")
                            .setMessage(e.mensajeServidor())
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun crearCampoTexto(
        ctx: Context,
        label: String,
        placeholder: String = "",
        minLines: Int = 1
    ): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = ContextCompat.getColor(ctx, R.color.g_verde)
            hintTextColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.g_verde))
            setBoxCornerRadii(
                dp(ctx, 12).toFloat(),
                dp(ctx, 12).toFloat(),
                dp(ctx, 12).toFloat(),
                dp(ctx, 12).toFloat()
            )
            if (placeholder.isNotBlank()) {
                placeholderText = placeholder
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(ctx, 4), 0, dp(ctx, 4))
            }
        }

        val input = TextInputEditText(til.context).apply {
            this.minLines = minLines
            if (minLines > 1) gravity = Gravity.TOP
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        }
        til.addView(input)
        return Pair(til, input)
    }

    private fun column(vararg views: View): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(requireContext(), 20), dp(requireContext(), 14), dp(requireContext(), 20), dp(requireContext(), 8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            for (v in views) {
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(requireContext(), 4), 0, dp(requireContext(), 4)) }
                addView(v, lp)
            }
        }
}