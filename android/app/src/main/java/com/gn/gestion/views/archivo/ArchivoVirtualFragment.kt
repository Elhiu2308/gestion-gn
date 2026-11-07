package com.gn.gestion.views.archivo

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gn.gestion.R
import com.gn.gestion.adapters.DocumentoAdapter
import com.gn.gestion.adapters.ExpedienteAdapter
import com.gn.gestion.models.ArchivoDto
import com.gn.gestion.models.Documento
import com.gn.gestion.models.TipoDocumento
import com.gn.gestion.models.aDocumento
import com.gn.gestion.services.ApiFactory
import com.gn.gestion.viewmodels.GestionViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ArchivoVirtualFragment : Fragment(R.layout.fragment_archivo_virtual) {

    private val vm: GestionViewModel by activityViewModels()

    private val adapterDocumentos = DocumentoAdapter { doc ->
        findNavController().navigate(
            R.id.detalle,
            Bundle().apply { putLong("id", doc.id) }
        )
    }

    private val adapterExpedientes = ExpedienteAdapter { exp ->
        buscarPorExpediente(exp.numero, exp.descripcion)
    }

    private var todosLosDocumentosGuardados: List<Documento> = emptyList()

    private var filtroFolio = ""
    private var filtroAsunto = ""
    private var filtroRemitente = ""
    private var filtroDestinatario = ""
    private var filtroTipo = ""
    private var filtroClasificacion = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listaDocs = view.findViewById<RecyclerView>(R.id.lista_documentos)
        listaDocs.layoutManager = LinearLayoutManager(requireContext())
        listaDocs.adapter = adapterDocumentos

        val listaExps = view.findViewById<RecyclerView>(R.id.lista_expedientes)
        listaExps.layoutManager = LinearLayoutManager(requireContext())
        listaExps.adapter = adapterExpedientes

        val inputBuscarGlobal = view.findViewById<TextInputEditText>(R.id.input_buscar_archivo)
        val containerFiltrosAvanzados = view.findViewById<View>(R.id.container_filtros_avanzados)
        val btnToggleFiltros = view.findViewById<MaterialButton>(R.id.btn_toggle_filtros)

        val inputFolio = view.findViewById<TextInputEditText>(R.id.input_filtro_folio)
        val inputAsunto = view.findViewById<TextInputEditText>(R.id.input_filtro_asunto)
        val inputRemitente = view.findViewById<TextInputEditText>(R.id.input_filtro_remitente)
        val inputDestinatario = view.findViewById<TextInputEditText>(R.id.input_filtro_destinatario)

        val spTipo = view.findViewById<Spinner>(R.id.spinner_filtro_tipo)
        val spClasificacion = view.findViewById<Spinner>(R.id.spinner_filtro_clasificacion)

        val btnAplicar = view.findViewById<MaterialButton>(R.id.btn_aplicar_filtros)
        val btnLimpiar = view.findViewById<MaterialButton>(R.id.btn_limpiar_filtros)

        inputBuscarGlobal?.doAfterTextChanged { text ->
            filtroAsunto = text.toString().trim()
            aplicarFiltrosEnMemoria()
        }

        btnToggleFiltros?.setOnClickListener {
            val visible = containerFiltrosAvanzados.isVisible
            containerFiltrosAvanzados.isVisible = !visible
            btnToggleFiltros.setIconResource(
                if (!visible) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
            )
        }

        val tiposOpciones = listOf("Todos") + TipoDocumento.entries.map { it.label }
        spTipo?.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tiposOpciones).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spTipo?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                filtroTipo = if (position == 0) "" else TipoDocumento.entries.getOrNull(position - 1)?.name.orEmpty()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val clasificacionesOpciones = listOf("Todas", "Público", "Confidencial", "Reservado")
        spClasificacion?.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, clasificacionesOpciones).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spClasificacion?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                filtroClasificacion = when (position) {
                    1 -> "PUBLICO"
                    2 -> "CONFIDENCIAL"
                    3 -> "RESERVADO"
                    else -> ""
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnAplicar?.setOnClickListener {
            filtroFolio = inputFolio?.text.toString().trim()
            val asuntoAvanzado = inputAsunto?.text.toString().trim()
            if (asuntoAvanzado.isNotBlank()) filtroAsunto = asuntoAvanzado
            filtroRemitente = inputRemitente?.text.toString().trim()
            filtroDestinatario = inputDestinatario?.text.toString().trim()

            aplicarFiltrosEnMemoria()
        }

        btnLimpiar?.setOnClickListener {
            inputBuscarGlobal?.setText("")
            inputFolio?.setText("")
            inputAsunto?.setText("")
            inputRemitente?.setText("")
            inputDestinatario?.setText("")
            spTipo?.setSelection(0)
            spClasificacion?.setSelection(0)

            filtroFolio = ""
            filtroAsunto = ""
            filtroRemitente = ""
            filtroDestinatario = ""
            filtroTipo = ""
            filtroClasificacion = ""

            cargar(emptyMap())
        }

        cargar(filtroDesdeArgumentos())
    }

    private fun filtroDesdeArgumentos(): Map<String, String> = buildMap {
        arguments?.getString("anio")?.let { put("anio", it) }
        arguments?.getString("clasificacion")?.let { put("clasificacion", it) }
        arguments?.getString("tipo")?.let { put("tipo", it) }
        arguments?.getString("q")?.let { put("q", it) }
    }

    private fun cargar(filtro: Map<String, String>) {
        if (!isAdded) return
        val progress = view?.findViewById<ProgressBar>(R.id.progress) ?: return
        progress.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = runCatching {
                ApiFactory.api(requireContext()).archivoVirtual(
                    anio = filtro["anio"],
                    clasificacion = filtro["clasificacion"],
                    tipo = filtro["tipo"],
                    q = filtro["q"]
                )
            }
            if (!isAdded) return@launch
            progress.isVisible = false

            val cuerpo = resultado.getOrNull()
            if (cuerpo != null) {
                render(cuerpo)
            } else {
                adapterDocumentos.submitList(emptyList())
                view?.findViewById<TextView>(R.id.lbl_vacio)?.apply {
                    isVisible = true
                    text = "No se pudo consultar el archivo. Revisa la conexión."
                }
            }
        }
    }

    private suspend fun render(cuerpo: ArchivoDto) {
        todosLosDocumentosGuardados = vm.guardarDesdeServidor(
            cuerpo.documentos.map { it.aDocumento() }
        )

        adapterExpedientes.submitList(cuerpo.expedientes)

        val totalCount = if (cuerpo.total > 0) cuerpo.total else todosLosDocumentosGuardados.size
        var asignadosCount = 0
        var vencidosCount = 0

        for (doc in todosLosDocumentosGuardados) {
            val e = doc.estado.uppercase()
            if (e.contains("ASIGNADO") || e.contains("TURNADO") || e.contains("ATENCION")) {
                asignadosCount++
            }
            if (doc.fechaLimite.isNotBlank()) {
                val eCanonico = doc.estado.uppercase()
                if (!eCanonico.contains("FINALIZADO") && !eCanonico.contains("ARCHIVADO") && !eCanonico.contains("CONCLUIDO")) {
                    vencidosCount++
                }
            }
        }

        view?.findViewById<TextView>(R.id.card_total_count)?.text = totalCount.toString()
        view?.findViewById<TextView>(R.id.card_asignados_count)?.text = asignadosCount.toString()
        view?.findViewById<TextView>(R.id.card_vencidos_count)?.text = vencidosCount.toString()

        val hayExpedientes = cuerpo.expedientes.isNotEmpty()
        view?.findViewById<TextView>(R.id.titulo_expedientes)?.isVisible = hayExpedientes
        view?.findViewById<RecyclerView>(R.id.lista_expedientes)?.isVisible = hayExpedientes

        aplicarFiltrosEnMemoria()
        renderizarFiltros(cuerpo)
    }

    private fun aplicarFiltrosEnMemoria() {
        var filtrados = todosLosDocumentosGuardados

        if (filtroFolio.isNotBlank()) {
            filtrados = filtrados.filter { it.folio.contains(filtroFolio, ignoreCase = true) }
        }
        if (filtroAsunto.isNotBlank()) {
            filtrados = filtrados.filter {
                it.asunto.contains(filtroAsunto, ignoreCase = true) ||
                    it.folio.contains(filtroAsunto, ignoreCase = true) ||
                    it.remitente.contains(filtroAsunto, ignoreCase = true) ||
                    it.destinatario.contains(filtroAsunto, ignoreCase = true)
            }
        }
        if (filtroRemitente.isNotBlank()) {
            filtrados = filtrados.filter { it.remitente.contains(filtroRemitente, ignoreCase = true) }
        }
        if (filtroDestinatario.isNotBlank()) {
            filtrados = filtrados.filter { it.destinatario.contains(filtroDestinatario, ignoreCase = true) }
        }
        if (filtroTipo.isNotBlank()) {
            filtrados = filtrados.filter { it.tipo.equals(filtroTipo, ignoreCase = true) }
        }
        if (filtroClasificacion.isNotBlank()) {
            filtrados = filtrados.filter { it.clasificacion.equals(filtroClasificacion, ignoreCase = true) }
        }

        adapterDocumentos.submitList(filtrados)

        view?.findViewById<TextView>(R.id.lbl_documentos)?.text =
            if (filtrados.size == 1) "1 documento" else "${filtrados.size} documento(s)"

        view?.findViewById<TextView>(R.id.lbl_vacio)?.isVisible = filtrados.isEmpty()
    }

    private fun renderizarFiltros(archivo: ArchivoDto) {
        val agrupaciones = LinkedHashMap<String, Map<String, Int>>()

        archivo.porAnio.takeIf { it.isNotEmpty() }?.let { agrupaciones["Año"] = it }
        archivo.porClasificacion.takeIf { it.isNotEmpty() }?.let { agrupaciones["Clasificación"] = it }
        archivo.documentos.map { it.tipo }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().takeIf { it.isNotEmpty() }
            ?.let { agrupaciones["Tipo"] = it }

        val grupo = view?.findViewById<ChipGroup>(R.id.chips_filtros) ?: return
        grupo.removeAllViews()

        agrupaciones.forEach { (etiqueta, valores) ->
            valores.forEach { (clave, cuenta) ->
                grupo.addView(
                    Chip(requireContext()).apply {
                        text = "$etiqueta · $clave ($cuenta)"
                        isClickable = true
                        setOnClickListener { cargar(mapOf(parametroDe(etiqueta) to clave)) }
                    }
                )
            }
        }
    }

    private fun parametroDe(etiqueta: String): String = when (etiqueta) {
        "Año" -> "anio"
        "Clasificación" -> "clasificacion"
        "Tipo" -> "tipo"
        else -> "q"
    }

    private fun buscarPorExpediente(numero: String, descripcion: String) {
        val q = listOfNotNull(numero.takeIf { it.isNotBlank() }, descripcion.takeIf { it.isNotBlank() })
            .joinToString(" ")
        if (q.isBlank()) return
        cargar(mapOf("q" to q))
    }
}