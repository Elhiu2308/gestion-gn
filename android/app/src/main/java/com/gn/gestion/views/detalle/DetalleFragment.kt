package com.gn.gestion.views.detalle

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gn.gestion.R
import com.gn.gestion.models.*
import com.gn.gestion.services.ApiFactory
import com.gn.gestion.services.FlujoRepository
import com.gn.gestion.services.TokenProvider
import com.gn.gestion.util.c
import com.gn.gestion.util.colorRes
import com.gn.gestion.util.dp
import com.gn.gestion.util.estadoFromString
import com.gn.gestion.util.formatFecha
import com.gn.gestion.util.formatHora
import com.gn.gestion.util.textoBadge
import com.gn.gestion.util.tipoFromString
import com.gn.gestion.util.urgenciaFromString
import com.gn.gestion.viewmodels.GestionViewModel
import com.gn.gestion.views.PdfArchivo
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DetalleFragment : Fragment(R.layout.fragment_detalle) {

    private val vm: GestionViewModel by activityViewModels()
    private lateinit var flujo: FlujoRepository

    @Volatile
    private var cargando = false

    @Volatile
    private var leidoEnviado = false

    // Paso de pestaña activa:
    // 0 = Datos, 1 = PDF, 2 = OCR, 3 = Trámite, 4 = Anotaciones, 5 = Historial
    private var tabActiva = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val id = arguments?.getLong("id", -1L) ?: -1L
        viewLifecycleOwner.lifecycleScope.launch {
            val doc = vm.documentoPorId(id)
                ?: vm.todos.value.firstOrNull { it.id == id }
            if (doc == null) {
                findNavController().popBackStack()
                return@launch
            }
            flujo = FlujoRepository.get(requireContext())

            configurarPestanas(view)
            render(view, doc)
            cargarTramite(view, doc)
        }
    }

    private fun configurarPestanas(view: View) {
        val btnDatos = view.findViewById<MaterialButton>(R.id.tab_datos)
        val btnPdf = view.findViewById<MaterialButton>(R.id.tab_pdf)
        val btnOcr = view.findViewById<MaterialButton>(R.id.tab_ocr)
        val btnTramite = view.findViewById<MaterialButton>(R.id.tab_tramite)
        val btnAnotaciones = view.findViewById<MaterialButton>(R.id.tab_anotaciones)
        val btnHistorial = view.findViewById<MaterialButton>(R.id.tab_historial)

        btnDatos?.setOnClickListener { cambiarPestana(view, 0) }
        btnPdf?.setOnClickListener { cambiarPestana(view, 1) }
        btnOcr?.setOnClickListener { cambiarPestana(view, 2) }
        btnTramite?.setOnClickListener { cambiarPestana(view, 3) }
        btnAnotaciones?.setOnClickListener { cambiarPestana(view, 4) }
        btnHistorial?.setOnClickListener { cambiarPestana(view, 5) }

        cambiarPestana(view, 0)
    }

    private fun cambiarPestana(view: View, indice: Int) {
        tabActiva = indice
        val ctx = requireContext()

        val tabs = listOf(
            view.findViewById<MaterialButton>(R.id.tab_datos),
            view.findViewById<MaterialButton>(R.id.tab_pdf),
            view.findViewById<MaterialButton>(R.id.tab_ocr),
            view.findViewById<MaterialButton>(R.id.tab_tramite),
            view.findViewById<MaterialButton>(R.id.tab_anotaciones),
            view.findViewById<MaterialButton>(R.id.tab_historial)
        )

        val contents = listOf(
            view.findViewById<View>(R.id.content_tab_datos),
            view.findViewById<View>(R.id.content_tab_pdf),
            view.findViewById<View>(R.id.content_tab_ocr),
            view.findViewById<View>(R.id.content_tab_tramite),
            view.findViewById<View>(R.id.content_tab_anotaciones),
            view.findViewById<View>(R.id.content_tab_historial)
        )

        for ((i, tab) in tabs.withIndex()) {
            if (tab == null) continue
            val activa = i == indice
            if (activa) {
                tab.setBackgroundColor(ctx.c(R.color.g_verde))
                tab.setTextColor(ctx.c(R.color.g_white))
                tab.strokeWidth = 0
            } else {
                tab.setBackgroundColor(Color.TRANSPARENT)
                tab.setTextColor(ctx.c(R.color.gris_texto))
                tab.setStrokeColorResource(R.color.gris_sutil)
                tab.strokeWidth = dp(ctx, 1)
            }
        }

        for ((i, content) in contents.withIndex()) {
            content?.isVisible = (i == indice)
        }
    }

    private fun render(view: View, doc: Documento) {
        val ctx = requireContext()
        val tipoView = tipoFromString(doc.tipo)
        val estado = estadoFromString(doc.estado)
        val urg = urgenciaFromString(doc.urgencia)
        val cls = doc.clasificacion.uppercase()

        // Encabezado
        val tipoFechaLabel = "${tipoView.label} · recibido ${formatFecha(doc.fecha)}"
        view.findViewById<TextView>(R.id.tipo_fecha_label)?.text = tipoFechaLabel
        view.findViewById<TextView>(R.id.asunto)?.text = doc.asunto.ifBlank { "Sin asunto" }

        // Badges
        val badges = view.findViewById<LinearLayout>(R.id.badges_top)
        badges?.removeAllViews()
        badges?.addView(ctx.textoBadge(estado.label, ctx.c(estado.colorRes())))
        badges?.addView(ctx.textoBadge(urg.label, ctx.c(urg.colorRes())))
        badges?.addView(ctx.textoBadge(
            Clasificacion.ETIQUETAS[cls] ?: cls,
            colorClasificacion(cls)
        ))
        if (doc.compartido) {
            badges?.addView(ctx.textoBadge("Compartido 2D", ctx.c(R.color.verde)))
        }

        // PESTAÑA 1: DATOS GENERALES
        val containerInfo = view.findViewById<LinearLayout>(R.id.container_info)
        containerInfo?.removeAllViews()
        if (containerInfo != null) {
            infoRow(ctx, containerInfo, "Folio", doc.folio.ifBlank { "—" })
            infoRow(ctx, containerInfo, "Asunto", doc.asunto.ifBlank { "—" })
            infoRow(ctx, containerInfo, "Tipo", tipoView.label)
            infoRow(ctx, containerInfo, "Remitente", doc.remitente.ifBlank { "—" })
            infoRow(ctx, containerInfo, "Destinatario", doc.destinatario.ifBlank { "—" })
            infoRow(ctx, containerInfo, "Área de origen", doc.areaOrigen.ifBlank { "—" })
            infoRow(ctx, containerInfo, "Área destinataria", doc.areaDestino.ifBlank { "—" })
            infoRow(ctx, containerInfo, "Área actual", doc.areaActual.ifBlank { "—" })
            infoRow(ctx, containerInfo, "Clasificación", Clasificacion.ETIQUETAS[cls] ?: cls)
            infoRow(ctx, containerInfo, "Referencia", doc.referencia.ifBlank { "—" })
            infoRow(ctx, containerInfo, "Anexos", doc.numAnexos.toString())
            infoRow(ctx, containerInfo, "Fecha documento", formatFecha(doc.fecha))
            infoRow(ctx, containerInfo, "Fecha recepción", formatFecha(doc.fecha))
            if (doc.fechaLimite.isNotBlank()) {
                val horaLimite = doc.fechaLimiteHora.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                infoRow(ctx, containerInfo, "Fecha límite", doc.fechaLimite + horaLimite)
            }
            infoRow(ctx, containerInfo, "Estado", estado.label)
            infoRow(ctx, containerInfo, "Prioridad", urg.label)
            if (doc.notas.isNotBlank()) {
                infoRow(ctx, containerInfo, "Notas", doc.notas)
            }
            infoRow(ctx, containerInfo, "UUID", doc.uuid)
        }

        // PESTAÑA 6: HISTORIAL DE TRAZABILIDAD
        val containerTrazab = view.findViewById<LinearLayout>(R.id.container_trazabilidad)
        if (containerTrazab != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                vm.movimientosDe(doc.uuid).collect { movimientos ->
                    renderTrazabilidad(ctx, containerTrazab, movimientos)
                }
            }
        }

        // PESTAÑA 2: PDF
        val pdfNombre = view.findViewById<TextView>(R.id.pdf_nombre)
        val containerPdf = view.findViewById<LinearLayout>(R.id.container_pdf_paginas)

        cargarPdfEnTab(ctx, doc, pdfNombre, containerPdf)

        view.findViewById<View>(R.id.btn_recargar_pdf)?.setOnClickListener {
            cargarPdfEnTab(ctx, doc, pdfNombre, containerPdf)
            toast("Vista previa actualizada")
        }

        view.findViewById<View>(R.id.btn_compartir_pdf)?.setOnClickListener {
            compartirPdf(doc)
        }

        // PESTAÑA 3: OCR
        val tvOcr = view.findViewById<TextView>(R.id.tv_ocr_texto)
        val textoOcr = doc.textoOcr.ifBlank { "Sin texto OCR reconocido para este documento." }
        tvOcr?.text = textoOcr

        view.findViewById<View>(R.id.btn_copiar_ocr)?.setOnClickListener {
            if (doc.textoOcr.isNotBlank()) {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Texto OCR", doc.textoOcr)
                clipboard.setPrimaryClip(clip)
                toast("Texto copiado al portapapeles")
            } else {
                toast("No hay texto OCR para copiar")
            }
        }

        // PESTAÑA 4: TRÁMITE
        renderPasosTramite(ctx, view, doc.estado)
        renderAcciones(view, doc)

        // PESTAÑA 5: ANOTACIONES
        val btnNuevaAnotacionTop = view.findViewById<View>(R.id.btn_nueva_anotacion_top)
        val btnAnotar = view.findViewById<View>(R.id.btn_anotar)

        val puedeAnotar = doc.sincronizadoEnServidor &&
                TokenProvider.puede(ctx, Permisos.DOCUMENTO_ANOTAR)

        btnNuevaAnotacionTop?.isVisible = puedeAnotar
        btnNuevaAnotacionTop?.setOnClickListener {
            doc.servidorIdRequerido?.let { sid -> dialogoAnotar(view, doc, sid) }
        }

        btnAnotar?.isVisible = puedeAnotar
        btnAnotar?.setOnClickListener {
            doc.servidorIdRequerido?.let { sid -> dialogoAnotar(view, doc, sid) }
        }

        // Vincular documento relacionado
        val btnVincular = view.findViewById<View>(R.id.btn_vincular)
        btnVincular?.isVisible = doc.sincronizadoEnServidor &&
                TokenProvider.puede(ctx, Permisos.DOCUMENTO_ANOTAR)
        btnVincular?.setOnClickListener { dialogoVincular(view, doc) }

        // Configurar menú contextual de opciones (3 puntos ⋮ en Toolbar)
        val btnMenuOpciones = requireActivity().findViewById<View>(R.id.btn_menu_opciones)
        btnMenuOpciones?.setOnClickListener { anchor ->
            mostrarPopupMenuDocumento(anchor, doc)
        }
    }

    private fun mostrarPopupMenuDocumento(anchorView: View, doc: Documento) {
        val ctx = requireContext()
        val popup = androidx.appcompat.widget.PopupMenu(ctx, anchorView)

        if (TokenProvider.puede(ctx, Permisos.DOCUMENTO_EDITAR)) {
            popup.menu.add(0, 1, 0, "Editar")
        }
        popup.menu.add(0, 2, 1, "Ver QR")
        if (TokenProvider.puede(ctx, Permisos.DOCUMENTO_ELIMINAR)) {
            popup.menu.add(0, 3, 2, "Eliminar")
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    findNavController().navigate(
                        R.id.action_detalle_to_editar,
                        Bundle().apply { putLong("id", doc.id) }
                    )
                    true
                }
                2 -> {
                    ampliarCodigoQr(doc)
                    true
                }
                3 -> {
                    confirmarEliminarDocumento(doc)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmarEliminarDocumento(doc: Documento) {
        val dialogo = MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
            .setTitle("Eliminar documento")
            .setMessage("¿Está seguro de que desea eliminar este documento?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                doc.pdfPath?.let { runCatching { File(it).delete() } }
                vm.eliminar(doc)
                findNavController().popBackStack()
            }
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.rojo_urgencia))
        }
        dialogo.show()
    }

    private fun cargarPdfEnTab(
        ctx: Context,
        doc: Documento,
        pdfNombre: TextView?,
        containerPdf: LinearLayout?
    ) {
        val pdfFile = PdfArchivo.obtenerOGenerarPdf(ctx, doc)
        pdfNombre?.text = pdfFile.name

        if (containerPdf != null) {
            containerPdf.removeAllViews()
            renderPdfVistaPrevia(ctx, pdfFile, containerPdf)
        }
    }

    private fun ampliarCodigoQr(doc: Documento) {
        val ctx = requireContext()
        val textoQr = doc.codigoQr.ifBlank { "GESTIONN::${doc.uuid}" }
        val bitmapGrand = qrBitmap(textoQr, 800, 800) ?: return

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 20), dp(ctx, 20), dp(ctx, 20), dp(ctx, 20))

            val img = ImageView(ctx).apply {
                setImageBitmap(bitmapGrand)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    dp(ctx, 280),
                    dp(ctx, 280)
                )
            }

            val tvFolio = TextView(ctx).apply {
                text = doc.folio.ifBlank { "Sin folio" }
                textSize = 18f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(ctx.c(R.color.texto_principal))
                setPadding(0, dp(ctx, 12), 0, dp(ctx, 4))
            }

            val tvUuid = TextView(ctx).apply {
                text = doc.uuid
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(ctx.c(R.color.gris_texto))
            }

            addView(img)
            addView(tvFolio)
            addView(tvUuid)
        }

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Código QR del documento")
            .setView(layout)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun renderPasosTramite(ctx: Context, view: View, estadoActual: String) {
        val containerPasos = view.findViewById<LinearLayout>(R.id.container_pasos_tramite) ?: return
        containerPasos.removeAllViews()

        val pasos = listOf(
            "RECIBIDO" to "Recibido",
            "PENDIENTE_TURNO" to "Pendiente de turno",
            "TURNADO" to "Turnado",
            "EN_DISTRIBUCION" to "En distribución interna",
            "EN_ATENCION" to "En atención",
            "RESPUESTAS_COMPLETAS" to "Respuestas completas",
            "CONSOLIDADO" to "Consolidado",
            "EN_REVISION" to "En revisión",
            "EN_VALIDACION" to "En validación",
            "VALIDADO" to "Validado",
            "CONCLUIDO" to "Concluido"
        )

        val eCanonico = estadoFromString(estadoActual).name
        var alcanzado = true

        for ((clave, etiqueta) in pasos) {
            val esActual = clave == eCanonico
            val colorPunto = if (alcanzado || esActual) "#059669" else "#CBD5E1"
            if (esActual) alcanzado = false

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(ctx, 8) }
            }

            val punto = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 10), dp(ctx, 10)).apply {
                    marginEnd = dp(ctx, 12)
                }
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_circle)?.apply {
                    setTint(Color.parseColor(colorPunto))
                }
            }

            val tvPasos = TextView(ctx).apply {
                text = etiqueta
                textSize = 14f
                setTextColor(if (esActual) ctx.c(R.color.g_verde) else ctx.c(R.color.gris_texto))
                if (esActual) setTypeface(null, Typeface.BOLD)
            }

            row.addView(punto)
            row.addView(tvPasos)
            containerPasos.addView(row)
        }
    }

    private fun colorClasificacion(cls: String): Int {
        val ctx = requireContext()
        return when (cls) {
            Clasificacion.PUBLICO -> ctx.c(R.color.verde)
            Clasificacion.RESERVADO -> ctx.c(R.color.rojo)
            else -> ctx.c(R.color.azul)
        }
    }

    private fun compartirPdf(doc: Documento) {
        val file = PdfArchivo.obtenerOGenerarPdf(requireContext(), doc)
        if (!file.exists()) {
            toast("No se encontró el archivo PDF para compartir")
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartir PDF vía:"))
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                }
                startActivity(Intent.createChooser(intent, "Compartir PDF vía:"))
            } catch (_: Exception) {
                toast("No se pudo compartir el archivo PDF")
            }
        }
    }

    // ---------------- Acciones del Flujo Institucional ----------------

    private data class AccionItem(val texto: String, val color: Int, val run: () -> Unit)

    private fun renderAcciones(view: View, doc: Documento) {
        val ctx = requireContext()
        val containerHeader = view.findViewById<LinearLayout>(R.id.container_acciones_header)
        val containerTramite = view.findViewById<LinearLayout>(R.id.container_acciones)

        containerHeader?.removeAllViews()
        containerTramite?.removeAllViews()

        val acc = mutableListOf<AccionItem>()
        val est = doc.estado

        val sid = doc.servidorIdRequerido
        if (sid == null) {
            if (est !in EstadoDocumento.TERMINALES) {
                val aviso = avisoSinServidor(ctx)
                containerTramite?.addView(aviso)
            }
            return
        }

        val puedeTurnar = TokenProvider.puede(ctx, Permisos.DOCUMENTO_ASIGNAR) ||
                TokenProvider.puede(ctx, Permisos.DOCUMENTO_TURNAR)
        val puedeResponder = TokenProvider.puede(ctx, Permisos.DOCUMENTO_RESPONDER) ||
                TokenProvider.puede(ctx, Permisos.DOCUMENTO_ASIGNAR)

        if (est in setOf(
                "RECIBIDO", "PENDIENTE_TURNO", "PENDIENTE_ASIGNACION",
                "TURNADO", "EN_DISTRIBUCION", "DESIGNADO", "ASIGNADO"
            ) && puedeTurnar
        ) {
            acc += AccionItem("Turnar", ctx.c(R.color.fondo_claro)) { dialogoTurnar(view, doc, sid) }
        }

        if (est in setOf("TURNADO", "EN_DISTRIBUCION", "DESIGNADO", "ASIGNADO") &&
            TokenProvider.puede(ctx, Permisos.DOCUMENTO_COMPARTIR)
        ) {
            acc += AccionItem("Compartir", ctx.c(R.color.fondo_claro)) { dialogoCompartir(view, doc, sid) }
        }
        if (est in setOf("TURNADO", "EN_DISTRIBUCION", "DESIGNADO", "ASIGNADO", "EN_ATENCION") &&
            puedeResponder
        ) {
            acc += AccionItem("Atender", ctx.c(R.color.fondo_claro)) {
                confirmarSimple(view, doc, "Atender documento") { flujo.atender(sid) }
            }
        }
        if (est in setOf("EN_ATENCION", "ATENCION_PARCIAL", "EN_PROCESO") &&
            TokenProvider.puede(ctx, Permisos.DOCUMENTO_RESPONDER)
        ) {
            acc += AccionItem("Responder", ctx.c(R.color.fondo_claro)) { dialogoResponder(view, doc, sid) }
        }
        if (est in setOf("RESPUESTAS_COMPLETAS", "RESPONDIDO", "CONTESTADO") &&
            TokenProvider.puede(ctx, Permisos.DOCUMENTO_CONSOLIDAR)
        ) {
            acc += AccionItem("Consolidar", ctx.c(R.color.fondo_claro)) { dialogoConsolidar(view, doc, sid) }
        }
        if (est in setOf("CONSOLIDADO", "CONSOLIDANDO", "EN_REVISION") &&
            TokenProvider.puede(ctx, Permisos.DOCUMENTO_REVISAR)
        ) {
            acc += AccionItem("Revisar", ctx.c(R.color.fondo_claro)) { confirmarRevisar(view, doc, sid) }
        }
        if (est == "EN_VALIDACION" && TokenProvider.puede(ctx, Permisos.DOCUMENTO_VALIDAR)) {
            acc += AccionItem("Validar", ctx.c(R.color.fondo_claro)) { confirmarValidar(view, doc, sid) }
        }
        if (est == "VALIDADO" && TokenProvider.puede(ctx, Permisos.DOCUMENTO_ARCHIVAR)) {
            acc += AccionItem("Archivar", ctx.c(R.color.fondo_claro)) { dialogoArchivar(view, doc, sid) }
        }
        if (est in setOf("ARCHIVADO", "HISTORICO") &&
            TokenProvider.puede(ctx, Permisos.DOCUMENTO_ARCHIVAR)
        ) {
            acc += AccionItem("Histórico", ctx.c(R.color.fondo_claro)) {
                confirmarSimple(view, doc, "Mover a histórico") { flujo.historico(sid) }
            }
        }
        // Acuse explícito de recibo.
        if (est !in EstadoDocumento.TERMINALES && puedeResponder) {
            acc += AccionItem("Acuse", ctx.c(R.color.fondo_claro)) {
                ejecutar(view, doc) { flujo.leer(sid, acuse = true) }
            }
        }
        if (TokenProvider.puede(ctx, Permisos.DOCUMENTO_ANOTAR)) {
            acc += AccionItem("Anotar", ctx.c(R.color.fondo_claro)) {
                dialogoAnotar(view, doc, sid)
            }
        }

        for (a in acc) {
            val btnHeader = crearBotonAccion(ctx, a) { lifecycleScope.launch { a.run() } }
            val btnTramite = crearBotonAccion(ctx, a) { lifecycleScope.launch { a.run() } }

            containerHeader?.addView(btnHeader)
            containerTramite?.addView(btnTramite)
        }
    }

    private fun crearBotonAccion(
        ctx: Context,
        accion: AccionItem,
        onClick: () -> Unit
    ): MaterialButton {

        val iconoRes = when (accion.texto) {
            "Atender" -> android.R.drawable.ic_menu_view
            "Turnar" -> android.R.drawable.ic_menu_send
            "Responder" -> android.R.drawable.ic_menu_revert
            "Anotar" -> android.R.drawable.ic_menu_edit
            "Compartir" -> android.R.drawable.ic_menu_share
            "Acuse" -> android.R.drawable.ic_menu_info_details
            "Consolidar" -> android.R.drawable.ic_menu_save
            "Revisar" -> android.R.drawable.ic_menu_search
            "Validar" -> android.R.drawable.ic_menu_info_details
            "Archivar" -> android.R.drawable.ic_menu_agenda
            "Histórico" -> android.R.drawable.ic_menu_recent_history
            else -> android.R.drawable.ic_menu_info_details
        }

        val verdeInstitucional = ctx.c(R.color.g_verde)

        val icono = ContextCompat.getDrawable(ctx, iconoRes)?.mutate()

        return MaterialButton(ctx).apply {
            text = accion.texto
            isAllCaps = false
            textSize = 12f

            backgroundTintList = ColorStateList.valueOf(accion.color)

            // Color del texto.
            setTextColor(verdeInstitucional)

            // Color directo del ícono.
            icon = icono
            iconTint = ColorStateList.valueOf(verdeInstitucional)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = dp(ctx, 6)
            iconSize = dp(ctx, 18)

            cornerRadius = dp(ctx, 16)

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(ctx, 36)
            ).apply {
                marginEnd = dp(ctx, 8)
            }

            setOnClickListener {
                onClick()
            }
        }

    }

    private fun ejecutar(view: View, doc: Documento, run: suspend () -> Unit) {
        if (cargando) return
        cargando = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                run()
                vm.sincronizar()
                val actualizado = vm.documentoPorId(doc.id) ?: doc
                render(view, actualizado)
                cargarTramite(view, actualizado)
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
                    .setTitle("Error")
                    .setMessage(e.message ?: "No se pudo completar la acción.")
                    .setPositiveButton("OK", null)
                    .show()
            } finally {
                cargando = false
            }
        }
    }

    private fun confirmarSimple(
        view: View,
        doc: Documento,
        titulo: String,
        run: suspend () -> Unit
    ) {
        MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
            .setTitle(titulo)
            .setMessage("¿Continuar con la acción sobre ${doc.folio.ifBlank { "el documento" }}?")
            .setPositiveButton("Sí") { _, _ -> ejecutar(view, doc, run) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun avisoSinServidor(ctx: Context): TextView =
        TextView(ctx).apply {
            text = "Este documento aún no está en el servidor. Sincroniza para poder operar su trámite."
            textSize = 13f
            setTextColor(ctx.c(R.color.ambar))
            setTypeface(null, Typeface.BOLD)
        }

    private fun confirmarRevisar(view: View, doc: Documento, sid: Long) {
        MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
            .setTitle("Revisar documento")
            .setMessage("¿La revisión es satisfactoria? El documento pasará a validación.")
            .setPositiveButton("Aprobar") { _, _ -> ejecutar(view, doc) { flujo.revisar(sid, true) } }
            .setNegativeButton("Rechazar") { _, _ -> dialogoRechazo(view, doc) { flujo.revisar(sid, false, it) } }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun confirmarValidar(view: View, doc: Documento, sid: Long) {
        MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
            .setTitle("Validar documento")
            .setMessage("¿Validar la respuesta emitida?")
            .setPositiveButton("Validar") { _, _ -> ejecutar(view, doc) { flujo.validar(sid, true) } }
            .setNegativeButton("Rechazar") { _, _ -> dialogoRechazo(view, doc) { flujo.validar(sid, false, it) } }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun dialogoRechazo(view: View, doc: Documento, run: suspend (String) -> Unit) {
        val ctx = requireContext()
        val (tilMotivo, inputMotivo) = crearCampoTexto(ctx, "Motivo del rechazo", minLines = 2, maxLines = 4)
        val dialogView = column(tilMotivo)

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Rechazar documento")
            .setView(dialogView)
            .setPositiveButton("Rechazar") { _, _ ->
                ejecutar(view, doc) { run(inputMotivo.text.toString().trim()) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun crearSelectorDropdown(
        ctx: Context,
        label: String,
        placeholderInicial: String
    ): Pair<TextInputLayout, MaterialAutoCompleteTextView> {
        val til = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = ctx.c(R.color.g_verde)
            hintTextColor = ColorStateList.valueOf(ctx.c(R.color.g_verde))
            setBoxCornerRadii(
                dp(ctx, 12).toFloat(),
                dp(ctx, 12).toFloat(),
                dp(ctx, 12).toFloat(),
                dp(ctx, 12).toFloat()
            )
            if (placeholderInicial.isNotBlank()) {
                placeholderText = placeholderInicial
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(ctx, 6), 0, dp(ctx, 6))
            }
        }

        val autoTv = MaterialAutoCompleteTextView(til.context).apply {
            inputType = InputType.TYPE_NULL
            isFocusable = false
            isClickable = true
            threshold = 0
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
            setOnClickListener {
                if (isEnabled && adapter != null && adapter.count > 0) {
                    showDropDown()
                }
            }
        }
        til.addView(autoTv)
        return Pair(til, autoTv)
    }

    private fun crearCampoTexto(
        ctx: Context,
        label: String,
        placeholder: String = "",
        minLines: Int = 1,
        maxLines: Int = 1,
        inputType: Int = InputType.TYPE_CLASS_TEXT
    ): Pair<TextInputLayout, com.google.android.material.textfield.TextInputEditText> {
        val til = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = ctx.c(R.color.g_verde)
            hintTextColor = ColorStateList.valueOf(ctx.c(R.color.g_verde))
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
                setMargins(0, dp(ctx, 6), 0, dp(ctx, 6))
            }
        }

        val input = com.google.android.material.textfield.TextInputEditText(til.context).apply {
            this.inputType = inputType
            this.minLines = minLines
            this.maxLines = maxLines
            if (minLines > 1) {
                gravity = Gravity.TOP
            }
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

    private fun dialogoTurnar(view: View, doc: Documento, sid: Long) {
        val ctx = requireContext()

        val (tilArea, spArea) = crearSelectorDropdown(ctx, "1. Área / Dirección", "Cargando Áreas…")
        val (tilSub, spSub) = crearSelectorDropdown(ctx, "2. Subdirección", "Primero seleccione un Área")
        val (tilPer, spPer) = crearSelectorDropdown(ctx, "3. Servidor Público / Persona", "Primero seleccione Subdirección")
        val (tilComent, inputComent) = crearCampoTexto(ctx, "Comentario (opcional)", minLines = 2, maxLines = 4)

        spSub.isEnabled = false
        spPer.isEnabled = false

        val tvAyuda = TextView(ctx).apply {
            text = "Selecciona Área, Subdirección y la persona responsable. El servidor valida la jerarquía y registra la trazabilidad."
            textSize = 12f
            setTextColor(ctx.c(R.color.gris_texto))
            setPadding(0, 0, 0, dp(ctx, 8))
        }

        var listAreas: List<NivelOrgDto> = emptyList()
        var listSubs: List<NivelOrgDto> = emptyList()
        var listPersonas: List<PersonaDto> = emptyList()

        var areaSel: NivelOrgDto? = null
        var subSel: NivelOrgDto? = null
        var personaSel: PersonaDto? = null

        val dialogView = column(
            tvAyuda,
            tilArea,
            tilSub,
            tilPer,
            tilComent
        )

        val dialogo = MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Turnar (estructura institucional)")
            .setView(dialogView)
            .setPositiveButton("Turnar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p = personaSel
                val s = subSel
                val a = areaSel

                val (tipo, idDestino, usuarioDestinoId) = when {
                    p != null -> {
                        val uid = p.idUsuario ?: p.clave
                        when {
                            p.idDepartamento != null ->
                                Triple("departamento", p.idDepartamento!!, uid)
                            p.idSubdireccion != null ->
                                Triple("subdireccion", p.idSubdireccion!!, uid)
                            p.idDireccion != null ->
                                Triple("direccion", p.idDireccion!!, uid)
                            else -> Triple("usuario", uid ?: 0L, uid)
                        }
                    }
                    s != null -> Triple("subdireccion", s.id, null)
                    a != null -> Triple("direccion", a.id, null)
                    else -> {
                        toast("Selecciona un Área, Subdirección o Persona de la lista")
                        return@setOnClickListener
                    }
                }

                ejecutar(view, doc) {
                    var currentSid = sid
                    if (currentSid <= 0L) {
                        vm.sincronizar()
                        currentSid = vm.resolverIdLocal(doc.uuid, doc.servidorId ?: 0L) ?: 0L
                        // Si era un id local, necesitamos el servidorId real
                        val docActualizado = vm.documentoPorId(doc.id)
                        currentSid = docActualizado?.servidorIdRequerido ?: currentSid
                        if (currentSid <= 0L) {
                            throw IllegalStateException("El documento aún no está registrado en el servidor. Sincronice o guarde de nuevo.")
                        }
                    }
                    flujo.turnar(
                        currentSid,
                        tipo,
                        idDestino ?: 0L,
                        usuarioDestinoId = usuarioDestinoId,
                        comentario = inputComent.text.toString().trim()
                    )
                }
                dialogo.dismiss()
            }
        }

        // Cargar Áreas
        viewLifecycleOwner.lifecycleScope.launch {
            dialogo.show()
            try {
                val api = ApiFactory.api(ctx)
                val dgs = runCatching { api.organigrama("direcciones-generales", soloPadre = "1") }.getOrNull().orEmpty()
                val dgId = dgs.firstOrNull()?.id ?: 0L
                var areas = if (dgId > 0L) runCatching { api.organigrama("direcciones", direccionGeneral = dgId) }.getOrNull().orEmpty() else emptyList()
                if (areas.isEmpty()) {
                    areas = runCatching { api.organigrama("direcciones") }.getOrNull().orEmpty()
                }

                listAreas = areas
                if (areas.isEmpty()) {
                    tilArea.placeholderText = "Sin Áreas disponibles"
                    return@launch
                }

                val nombresAreas = areas.map { it.nombre }
                spArea.setSimpleItems(nombresAreas.toTypedArray())
                tilArea.placeholderText = "Seleccionar Área"
                spArea.isEnabled = true

                spArea.setOnItemClickListener { _, _, position, _ ->
                    val areaElegida = listAreas.getOrNull(position) ?: return@setOnItemClickListener
                    areaSel = areaElegida
                    subSel = null
                    personaSel = null

                    spSub.setText("", false)
                    spPer.setText("", false)
                    spPer.isEnabled = false
                    tilPer.placeholderText = "Primero seleccione Subdirección"

                    // Cargar Subdirecciones
                    viewLifecycleOwner.lifecycleScope.launch {
                        tilSub.placeholderText = "Cargando Subdirecciones…"
                        spSub.isEnabled = false
                        val subs = runCatching { api.organigrama("subdirecciones", direccion = areaElegida.id) }.getOrNull().orEmpty()
                        listSubs = subs

                        if (subs.isNotEmpty()) {
                            spSub.setSimpleItems(subs.map { it.nombre }.toTypedArray())
                            tilSub.placeholderText = "Seleccionar Subdirección"
                            spSub.isEnabled = true

                            spSub.setOnItemClickListener { _, _, posSub, _ ->
                                val subElegida = listSubs.getOrNull(posSub) ?: return@setOnItemClickListener
                                subSel = subElegida
                                personaSel = null
                                spPer.setText("", false)

                                // Cargar Personas
                                viewLifecycleOwner.lifecycleScope.launch {
                                    tilPer.placeholderText = "Cargando Personas…"
                                    spPer.isEnabled = false
                                    val personas = runCatching {
                                        api.listarPersonas(direccion = areaElegida.id, subdireccion = subElegida.id, limite = 500)
                                    }.getOrNull().orEmpty()
                                    listPersonas = personas

                                    if (personas.isNotEmpty()) {
                                        spPer.setSimpleItems(personas.map { it.nombreCompleto }.toTypedArray())
                                        tilPer.placeholderText = "Seleccionar Persona"
                                        spPer.isEnabled = true

                                        spPer.setOnItemClickListener { _, _, posPer, _ ->
                                            personaSel = listPersonas.getOrNull(posPer)
                                        }
                                    } else {
                                        tilPer.placeholderText = "Sin personas en esta Subdirección"
                                    }
                                }
                            }
                        } else {
                            tilSub.placeholderText = "Sin Subdirecciones en esta Área"
                            spSub.isEnabled = false

                            // Si no hay subdirecciones, cargar Personas por Área directamente
                            viewLifecycleOwner.lifecycleScope.launch {
                                tilPer.placeholderText = "Cargando Personas…"
                                spPer.isEnabled = false
                                val personas = runCatching {
                                    api.listarPersonas(direccion = areaElegida.id, limite = 500)
                                }.getOrNull().orEmpty()
                                listPersonas = personas

                                if (personas.isNotEmpty()) {
                                    spPer.setSimpleItems(personas.map { it.nombreCompleto }.toTypedArray())
                                    tilPer.placeholderText = "Seleccionar Persona"
                                    spPer.isEnabled = true

                                    spPer.setOnItemClickListener { _, _, posPer, _ ->
                                        personaSel = listPersonas.getOrNull(posPer)
                                    }
                                } else {
                                    tilPer.placeholderText = "Sin personas en esta Área"
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                toast(e.message ?: "Error al cargar la estructura organizacional")
            }
        }
    }

    private fun dialogoCompartir(view: View, doc: Documento, sid: Long) {
        val ctx = requireContext()
        val (tilA, inputA) = crearCampoTexto(ctx, "Dirección A")
        val (tilB, inputB) = crearCampoTexto(ctx, "Dirección B")
        val (tilComent, inputComent) = crearCampoTexto(ctx, "Comentario (opcional)")

        val dialogView = column(tilA, tilB, tilComent)

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Compartir A/B")
            .setView(dialogView)
            .setPositiveButton("Compartir") { _, _ ->
                val a = inputA.text.toString().trim()
                val b = inputB.text.toString().trim()
                if (a.isBlank() || b.isBlank()) {
                    toast("Indica ambas direcciones")
                } else {
                    ejecutar(view, doc) { flujo.compartir(sid, a, b, inputComent.text.toString().trim()) }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun dialogoResponder(view: View, doc: Documento, sid: Long) {
        val ctx = requireContext()
        val (tilTexto, inputTexto) = crearCampoTexto(ctx, "Texto de la respuesta", minLines = 3, maxLines = 6)
        val (tilObs, inputObs) = crearCampoTexto(ctx, "Observaciones (opcional)")
        val (tilRama, inputRama) = crearCampoTexto(ctx, "Rama (0 = principal, 1, 2)", inputType = InputType.TYPE_CLASS_NUMBER)
        inputRama.setText("0")

        val dialogView = column(tilTexto, tilObs, tilRama)

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Responder documento")
            .setView(dialogView)
            .setPositiveButton("Enviar respuesta") { _, _ ->
                val texto = inputTexto.text.toString().trim()
                if (texto.isBlank()) {
                    toast("La respuesta no puede ir vacía")
                } else {
                    val rama = inputRama.text.toString().trim().toIntOrNull() ?: 0
                    ejecutar(view, doc) {
                        flujo.responder(sid, texto, inputObs.text.toString().trim(), rama)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun dialogoConsolidar(view: View, doc: Documento, sid: Long) {
        val ctx = requireContext()
        val (tilTexto, inputTexto) = crearCampoTexto(ctx, "Texto final consolidado", minLines = 3, maxLines = 6)
        val (tilComent, inputComent) = crearCampoTexto(ctx, "Comentario (opcional)")

        val dialogView = column(tilTexto, tilComent)

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Consolidar respuestas")
            .setView(dialogView)
            .setPositiveButton("Consolidar") { _, _ ->
                ejecutar(view, doc) {
                    flujo.consolidar(sid, inputTexto.text.toString().trim(), inputComent.text.toString().trim())
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun dialogoArchivar(view: View, doc: Documento, sid: Long) {
        val ctx = requireContext()
        val (tilFolio, inputFolio) = crearCampoTexto(ctx, "Folio final (opcional)")
        val (tilComent, inputComent) = crearCampoTexto(ctx, "Comentario (opcional)")

        val dialogView = column(tilFolio, tilComent)

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Archivar documento")
            .setView(dialogView)
            .setPositiveButton("Archivar") { _, _ ->
                ejecutar(view, doc) {
                    flujo.archivar(sid, inputFolio.text.toString().trim(), inputComent.text.toString().trim())
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun dialogoAnotar(view: View, doc: Documento, sid: Long) {
        val ctx = requireContext()
        val (tilAnot, inputAnot) = crearCampoTexto(ctx, "Anotación / observación", minLines = 3, maxLines = 5)

        val dialogView = column(tilAnot)

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Añadir anotación")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val texto = inputAnot.text.toString().trim()
                if (texto.isBlank()) {
                    toast("La anotación no puede ir vacía")
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            flujo.anotar(sid, texto)
                            cargarTramite(view, doc)
                        } catch (e: Exception) {
                            toast(e.message ?: "Error")
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun dialogoVincular(view: View, doc: Documento) {
        val ctx = requireContext()
        val sid = doc.servidorIdRequerido ?: return
        val (tilOtro, inputOtro) = crearCampoTexto(ctx, "ID del documento a vincular", inputType = InputType.TYPE_CLASS_NUMBER)

        val tipos = listOf("RELACIONADO_CON", "ANTECEDENTE", "RESPUESTAS_A", "COMPLEMENTA_A")
        val (tilTipo, spTipo) = crearSelectorDropdown(ctx, "Tipo de relación", "")
        spTipo.setSimpleItems(tipos.toTypedArray())
        spTipo.setText(tipos.first(), false)

        val dialogView = column(tilOtro, tilTipo)

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Vincular documento")
            .setView(dialogView)
            .setPositiveButton("Vincular") { _, _ ->
                val otro = inputOtro.text.toString().trim().toLongOrNull()
                if (otro == null) {
                    toast("Indica el ID del documento")
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            flujo.vincularRelacion(sid, otro, spTipo.text.toString().trim())
                            cargarTramite(view, doc)
                        } catch (e: Exception) {
                            toast(e.message ?: "No se pudo vincular")
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toast(msj: String) {
        Toast.makeText(requireContext(), msj, Toast.LENGTH_SHORT).show()
    }

    // ---------------- Carga del trámite desde el servidor ----------------

    private suspend fun cargarTramite(view: View, doc: Documento) {
        val serverId = doc.servidorIdRequerido ?: return
        runCatching {
            Triple(
                flujo.asignaciones(serverId),
                flujo.respuestas(serverId),
                flujo.anotaciones(serverId)
            ) to flujo.relaciones(serverId)
        }.onSuccess { (tramite, rels) ->
            val (asg, resp, anots) = tramite
            viewLifecycleOwner.lifecycleScope.launch {
                renderAsignaciones(view, asg)
                renderRespuestas(view, resp)
                renderAnotaciones(view, anots)
                renderRelaciones(view, doc, rels)
            }
        }
        if (!leidoEnviado) {
            leidoEnviado = true
            runCatching { flujo.leer(serverId, acuse = false) }
        }
    }

    private fun renderAsignaciones(view: View, rows: List<AsignacionDto>) {
        val ctx = requireContext()
        val container = view.findViewById<LinearLayout>(R.id.container_asignaciones) ?: return
        val emptyView = view.findViewById<TextView>(R.id.empty_asignaciones)
        container.removeAllViews()
        emptyView?.isVisible = rows.isEmpty()

        rows.forEach { a ->
            val rama = if (a.tipo == "COMPARTIR") " · Rama ${a.rama + 1}" else ""
            rowTexto(
                ctx, container,
                "${tituloAsignacion(a.tipo)} → ${a.areaDestino.ifBlank { "—" }}$rama",
                "${a.asignadorNombre.ifBlank { "—" }} — ${formatHora(a.fechaAsignacion)}"
            )
        }
    }

    private fun tituloAsignacion(tipo: String): String = when (tipo) {
        "COMPARTIR" -> "💠 Compartido"
        "TRANSFERENCIA" -> "⇄ Transferido"
        else -> "📨 Asignado"
    }

    private fun renderRespuestas(view: View, rows: List<RespuestaDto>) {
        val ctx = requireContext()
        val container = view.findViewById<LinearLayout>(R.id.container_respuestas) ?: return
        val emptyView = view.findViewById<TextView>(R.id.empty_respuestas)
        container.removeAllViews()
        emptyView?.isVisible = rows.isEmpty()

        rows.forEach { r ->
            rowTexto(
                ctx, container,
                "Rama ${r.rama + 1} · ${r.usuarioNombre.ifBlank { "—" }}",
                buildString {
                    append(r.texto.ifBlank { "Respuesta sin texto" })
                    if (r.observaciones.isNotBlank()) append("\n📌 ${r.observaciones}")
                    if (r.pdfNombre.isNotBlank()) append("\n📕 ${r.pdfNombre}")
                }.trim()
            )
        }
    }

    private fun renderAnotaciones(view: View, rows: List<AnotacionDto>) {
        val ctx = requireContext()
        val container = view.findViewById<LinearLayout>(R.id.container_anotaciones) ?: return
        val emptyView = view.findViewById<TextView>(R.id.empty_anotaciones)
        container.removeAllViews()
        emptyView?.isVisible = rows.isEmpty()

        rows.forEach { a ->
            rowTexto(
                ctx, container,
                "${a.tipo} · ${a.area.ifBlank { "—" }}",
                "${a.comentario}\n${a.usuarioNombre.ifBlank { "" }} · ${formatHora(a.creadoEn)}".trim()
            )
        }
    }

    private fun renderRelaciones(view: View, doc: Documento, rows: List<RelacionDto>) {
        val ctx = requireContext()
        val container = view.findViewById<LinearLayout>(R.id.container_relaciones) ?: return
        val emptyView = view.findViewById<TextView>(R.id.empty_relaciones)
        container.removeAllViews()
        emptyView?.isVisible = rows.isEmpty()

        rows.forEach { r ->
            val tipo = r.tipoRelacion.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(ctx, 10) }
            }
            row.addView(TextView(ctx).apply {
                text = "🔗 $tipo · #${r.otroDocumentoId} ${r.documentoReferencia}"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ctx.c(R.color.verde))
            })
            if (r.comentario.isNotBlank() || r.usuarioNombre.isNotBlank()) {
                row.addView(TextView(ctx).apply {
                    text = listOf(r.comentario, r.usuarioNombre).filter { it.isNotBlank() }
                        .joinToString(" · ")
                    textSize = 13f
                    setTextColor(ctx.c(R.color.gris_texto))
                })
            }
            row.addView(TextView(ctx).apply {
                text = "Desvincular"
                textSize = 13f
                setTextColor(ctx.c(R.color.rojo))
                setPadding(0, dp(ctx, 4), 0, 0)
                setOnClickListener {
                    doc.servidorIdRequerido?.let { sid ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching { flujo.desvincularRelacion(sid, r.id) }
                            cargarTramite(view, doc)
                        }
                    }
                }
            })
            container.addView(row)
        }
    }

    private fun rowTexto(ctx: Context, container: LinearLayout, titulo: String, detalle: String) {
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 10) }
        }
        val t = TextView(ctx).apply {
            text = titulo
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ctx.c(R.color.verde))
        }
        val d = TextView(ctx).apply {
            text = detalle
            textSize = 13f
            setTextColor(ctx.c(R.color.gris_texto))
        }
        column.addView(t)
        column.addView(d)
        container.addView(column)
    }

    private fun renderTrazabilidad(
        ctx: Context,
        container: LinearLayout,
        movimientos: List<Movimiento>
    ) {
        container.removeAllViews()
        if (movimientos.isEmpty()) {
            val vacio = TextView(ctx).apply {
                text = "Sin movimientos registrados."
                textSize = 14f
                setTextColor(ctx.c(R.color.gris_texto))
            }
            container.addView(vacio)
            return
        }
        movimientos.sortedBy { it.fechaHora }.forEach { mov ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(ctx, 14) }
            }
            val titulo = TextView(ctx).apply {
                text = mov.accion.ifBlank { "Movimiento" }
                textSize = 15f
                setTextColor(ctx.c(R.color.verde))
                setTypeface(null, Typeface.BOLD)
            }
            val detalle = TextView(ctx).apply {
                text = buildString {
                    if (mov.estadoNuevo.isNotBlank() && mov.estadoNuevo != mov.estadoAnterior) {
                        append(estadoFromString(mov.estadoNuevo).label)
                        if (mov.estadoAnterior.isNotBlank()) {
                            append(" (desde ${estadoFromString(mov.estadoAnterior).label})")
                        }
                        append(" · ")
                    }
                    append(formatHora(mov.fechaHora))
                }
                textSize = 13f
                setTextColor(ctx.c(R.color.gris_texto))
            }
            val pie = TextView(ctx).apply {
                text = listOf(
                    mov.usuarioNombre.takeIf { it.isNotBlank() }
                        ?.let { if (mov.area.isNotBlank()) "$it (${mov.area})" else it },
                    mov.comentario.takeIf { it.isNotBlank() }
                ).filterNotNull().joinToString(" — ")
                textSize = 13f
            }
            row.addView(titulo)
            row.addView(detalle)
            if (pie.text.isNotBlank()) row.addView(pie)
            container.addView(row)
        }
    }

    private fun qrBitmap(texto: String, ancho: Int, alto: Int): Bitmap? {
        return try {
            val matrix = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, ancho, alto)
            val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.RGB_565)
            for (x in 0 until ancho) {
                for (y in 0 until alto) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun infoRow(
        ctx: Context,
        container: LinearLayout,
        label: String,
        value: String
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(ctx, 12) }
        }
        val lbl = TextView(ctx).apply {
            text = label
            textSize = 14f
            setTextColor(ctx.c(R.color.gris_texto))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        val valText = TextView(ctx).apply {
            text = value
            textSize = 14f
            gravity = Gravity.END
            setTextColor(ctx.c(R.color.texto_principal))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        row.addView(lbl)
        row.addView(valText)
        container.addView(row)
    }

    private fun renderPdfVistaPrevia(
        ctx: Context,
        file: File,
        container: LinearLayout
    ) {
        if (!file.exists()) return
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                val density = ctx.resources.displayMetrics.density
                val displayWidth = ctx.resources.displayMetrics.widthPixels
                val paginasVisibles = renderer.pageCount.coerceAtMost(5)
                repeat(paginasVisibles) { index ->
                    renderer.openPage(index).use { page ->
                        val scale = (displayWidth - (36 * density).toInt()) / (page.width * density)
                        val w = (page.width * density * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * density * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val img = ImageView(ctx).apply {
                            setImageBitmap(bitmap)
                            setBackgroundColor(Color.WHITE)
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = "Página ${index + 1}"
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = dp(ctx, 8); bottomMargin = dp(ctx, 8) }
                        }
                        container.addView(img)
                    }
                }
            } finally {
                renderer.close()
                pfd.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
