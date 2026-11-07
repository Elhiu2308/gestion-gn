package com.gn.gestion.views.busqueda;

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.gn.gestion.R
import com.gn.gestion.models.EstadoDocumento;
import com.gn.gestion.models.TipoDocumento;
import com.gn.gestion.models.Urgencia;
import com.gn.gestion.models.aDocumento
import com.gn.gestion.services.ApiFactory;
import com.gn.gestion.models.BusquedaDto;
import com.gn.gestion.viewmodels.GestionViewModel;
import com.gn.gestion.adapters.DocumentoAdapter;
import com.gn.gestion.adapters.ExpedienteAdapter;
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class BusquedaGlobalFragment : Fragment(R.layout.fragment_busqueda_global) {

    private val vm: GestionViewModel by activityViewModels()

    private var busquedaJob: Job? = null
    private var descripcionActual: String = ""

    private val adapterDocumentos = DocumentoAdapter { doc ->
        findNavController().navigate(
            R.id.detalle,
            Bundle().apply { putLong("id", doc.id) }
        )
    }

    private val adapterExpedientes = ExpedienteAdapter { exp ->
        findNavController().navigate(
            R.id.archivo_virtual,
            Bundle().apply {
                putString("q", buildString {
                    if (exp.numero.isNotBlank()) append(exp.numero)
                    if (exp.descripcion.isNotBlank()) {
                        if (isNotEmpty()) append(" ")
                        append(exp.descripcion)
                    }
                })
            }
        )
    }

    private lateinit var campos: Map<String, TextInputEditText>
    private lateinit var selTipo: MaterialAutoCompleteTextView
    private lateinit var selEstado: MaterialAutoCompleteTextView
    private lateinit var selUrgencia: MaterialAutoCompleteTextView

    private val opcionesTipo = linkedMapOf<String, String>("Todos" to "")
    private val opcionesEstado = linkedMapOf<String, String>("Todos" to "")
    private val opcionesUrgencia = linkedMapOf<String, String>("Todos" to "")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val input = view.findViewById<TextInputEditText>(R.id.input_busqueda)
        val listaDocs = view.findViewById<RecyclerView>(R.id.lista_documentos)
        val listaExps = view.findViewById<RecyclerView>(R.id.lista_expedientes)

        listaDocs.layoutManager = LinearLayoutManager(requireContext())
        listaDocs.adapter = adapterDocumentos
        listaExps.layoutManager = LinearLayoutManager(requireContext())
        listaExps.adapter = adapterExpedientes

        campos = mapOf(
            "folio" to view.findViewById(R.id.input_f_folio),
            "asunto" to view.findViewById(R.id.input_f_asunto),
            "descripcion" to view.findViewById(R.id.input_f_descripcion),
            "destinatario" to view.findViewById(R.id.input_f_destinatario),
            "remitente" to view.findViewById(R.id.input_f_remitente),
            "area_origen" to view.findViewById(R.id.input_f_area_origen),
            "area_destino" to view.findViewById(R.id.input_f_area_destino),
            "desde" to view.findViewById(R.id.input_f_desde),
            "hasta" to view.findViewById(R.id.input_f_hasta)
        )
        selTipo = view.findViewById(R.id.sel_tipo)
        selEstado = view.findViewById(R.id.sel_estado)
        selUrgencia = view.findViewById(R.id.sel_urgencia)

        configurarListas(view)
        configurarFechas(view)
        configurarPanelFiltros(view)

        input.doAfterTextChanged { texto ->
            actualizarResumenFiltros()
            if (hayFiltrosActivos()) return@doAfterTextChanged
            busquedaJob?.cancel()
            val q = texto?.toString()?.trim().orEmpty()
            if (q.length < 2) {
                limpiar()
                return@doAfterTextChanged
            }
            busquedaJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(400)
                buscar(q)
            }
        }

        input.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                buscarAhora()
                v.ocultarTeclado()
                true
            } else {
                false
            }
        }

        view.findViewById<View>(R.id.btn_buscar).setOnClickListener {
            buscarAhora()
            input.ocultarTeclado()
        }
    }

    private fun configurarListas(view: View) {
        TipoDocumento.entries.forEach { opcionesTipo[it.label] = it.name }
        EstadoDocumento.entries.forEach { opcionesEstado[it.label] = it.name }
        Urgencia.entries.forEach { opcionesUrgencia[it.label] = it.name }

        fun montar(selector: MaterialAutoCompleteTextView, opciones: LinkedHashMap<String, String>) {
            selector.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, opciones.keys.toList())
            )
            selector.setText(opciones.keys.first(), false)
        }
        montar(selTipo, opcionesTipo)
        montar(selEstado, opcionesEstado)
        montar(selUrgencia, opcionesUrgencia)

        selTipo.setOnItemClickListener { _, _, _, _ -> actualizarResumenFiltros() }
        selEstado.setOnItemClickListener { _, _, _, _ -> actualizarResumenFiltros() }
        selUrgencia.setOnItemClickListener { _, _, _, _ -> actualizarResumenFiltros() }
    }

    private fun configurarFechas(view: View) {
        listOf("desde" to R.id.input_f_desde, "hasta" to R.id.input_f_hasta).forEach { (clave, id) ->
            val campo = view.findViewById<TextInputEditText>(id)
            campo.setOnClickListener { pedirFecha(clave, campo) }
        }
    }

    private fun pedirFecha(clave: String, campo: TextInputEditText) {
        val inicial = Calendar.getInstance().apply {
            aIsoACalendario(campo.text?.toString())
        }
        DatePickerDialog(
            requireContext(),
            { _, anio, mes, dia ->
                campo.setText(String.format(Locale.US, "%02d/%02d/%04d", dia, mes + 1, anio))
                actualizarResumenFiltros()
            },
            inicial.get(Calendar.YEAR),
            inicial.get(Calendar.MONTH),
            inicial.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun configurarPanelFiltros(view: View) {
        val panel = view.findViewById<View>(R.id.panel_filtros)
        val resultados = view.findViewById<View>(R.id.panel_resultados)
        val boton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_filtros)

        boton.setOnClickListener {
            val visible = panel.isVisible
            panel.isVisible = !visible
            resultados.isVisible = visible
            boton.text = if (visible) "Filtros" else "Ocultar filtros"
        }

        view.findViewById<View>(R.id.btn_limpiar).setOnClickListener {
            campos.values.forEach { it.setText("") }
            selTipo.setText(opcionesTipo.keys.first(), false)
            selEstado.setText(opcionesEstado.keys.first(), false)
            selUrgencia.setText(opcionesUrgencia.keys.first(), false)
            actualizarResumenFiltros()
        }

        campos.values.forEach { campo ->
            campo.doAfterTextChanged { actualizarResumenFiltros() }
        }
    }

    private fun buscarAhora() {
        busquedaJob?.cancel()
        val q = view?.findViewById<TextInputEditText>(R.id.input_busqueda)
            ?.text?.toString()?.trim().orEmpty()
        if (q.length < 2 && !hayFiltrosActivos()) {
            mostrarAviso("Escribe al menos 2 caracteres o usa Filtros.")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch { buscar(q) }
    }

    private fun hayFiltrosActivos(): Boolean = filtrosActivos().isNotEmpty()

    private fun filtrosActivos(): Map<String, String> {
        if (!isAdded) return emptyMap()
        val out = linkedMapOf<String, String>()
        campos.forEach { (clave, campo) ->
            val valor = campo.text?.toString()?.trim().orEmpty()
            if (valor.isNotEmpty()) {
                if (clave == "desde" || clave == "hasta") {
                    val iso = aIso(valor)
                    if (iso != null) out[clave] = iso
                } else {
                    out[clave] = valor
                }
            }
        }
        selTipo.text?.toString()?.let { out["tipo"] = opcionesTipo[it].orEmpty() }
        selEstado.text?.toString()?.let { out["estado"] = opcionesEstado[it].orEmpty() }
        selUrgencia.text?.toString()?.let { out["urgencia"] = opcionesUrgencia[it].orEmpty() }
        return out.filterValues { it.isNotEmpty() }
    }

    private fun buscar(consulta: String) {
        if (!isAdded) return
        val filtros = filtrosActivos()
        descripcionActual = if (filtros.isEmpty()) {
            "«$consulta»"
        } else {
            buildString {
                if (consulta.isNotEmpty()) append(consulta)
                filtros.keys.forEach { clave ->
                    if (isNotEmpty()) append(" · ")
                    append(etiquetaDe(clave))
                }
            }
        }

        val progress = view?.findViewById<ProgressBar>(R.id.progress) ?: return
        progress.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = runCatching {
                ApiFactory.api(requireContext()).buscarGlobal(
                    q = consulta.takeIf { it.length >= 2 },
                    folio = filtros["folio"],
                    asunto = filtros["asunto"],
                    descripcion = filtros["descripcion"],
                    destinatario = filtros["destinatario"],
                    remitente = filtros["remitente"],
                    areaOrigen = filtros["area_origen"],
                    areaDestino = filtros["area_destino"],
                    tipo = filtros["tipo"],
                    estado = filtros["estado"],
                    urgencia = filtros["urgencia"],
                    desde = filtros["desde"],
                    hasta = filtros["hasta"]
                )
            }
            if (!isAdded) return@launch
            progress.isVisible = false

            val cuerpo = resultado.getOrNull()
            if (cuerpo != null) {
                render(cuerpo)
            } else {
                limpiar()
                mostrarAviso("No se pudo consultar el servidor. Revisa la conexión.")
            }
        }
    }

    private suspend fun render(cuerpo: BusquedaDto) {

        val guardados = if (cuerpo.documentos.isNotEmpty()) {
            vm.guardarDesdeServidor(cuerpo.documentos.map { it.aDocumento() })
        } else {
            emptyList()
        }

        adapterDocumentos.submitList(guardados)
        adapterExpedientes.submitList(cuerpo.expedientes)

        view?.findViewById<TextView>(R.id.titulo_documentos)?.isVisible = guardados.isNotEmpty()
        view?.findViewById<RecyclerView>(R.id.lista_documentos)?.isVisible = guardados.isNotEmpty()
        view?.findViewById<TextView>(R.id.titulo_expedientes)?.isVisible = cuerpo.expedientes.isNotEmpty()
        view?.findViewById<RecyclerView>(R.id.lista_expedientes)?.isVisible = cuerpo.expedientes.isNotEmpty()

        val total = guardados.size + cuerpo.expedientes.size
        view?.findViewById<TextView>(R.id.lbl_resumen)?.apply {
            isVisible = total > 0
            text = "$total resultado(s) para $descripcionActual"
        }

        view?.findViewById<TextView>(R.id.lbl_vacio)?.apply {
            isVisible = total == 0
            if (total == 0) text = "Sin resultados para $descripcionActual."
        }
    }

    private fun limpiar() {
        adapterDocumentos.submitList(emptyList())
        adapterExpedientes.submitList(emptyList())
        view?.findViewById<TextView>(R.id.titulo_documentos)?.isVisible = false
        view?.findViewById<RecyclerView>(R.id.lista_documentos)?.isVisible = false
        view?.findViewById<TextView>(R.id.titulo_expedientes)?.isVisible = false
        view?.findViewById<RecyclerView>(R.id.lista_expedientes)?.isVisible = false
        view?.findViewById<TextView>(R.id.lbl_resumen)?.isVisible = false
        view?.findViewById<ProgressBar>(R.id.progress)?.isVisible = false
        view?.findViewById<TextView>(R.id.lbl_vacio)?.apply {
            isVisible = true
            text = "Escribe al menos 2 caracteres para buscar."
        }
    }

    private fun actualizarResumenFiltros() {
        val etiqueta = view?.findViewById<TextView>(R.id.lbl_filtros_activos) ?: return
        val activos = filtrosActivos()
        etiqueta.isVisible = activos.isNotEmpty()
        if (activos.isNotEmpty()) {
            etiqueta.text = "Filtros: " + activos.keys.joinToString(", ") { etiquetaDe(it) }
        }
    }

    private fun mostrarAviso(texto: String) {
        view?.findViewById<TextView>(R.id.lbl_vacio)?.apply {
            isVisible = true
            text = texto
        }
    }

    private fun aIso(texto: String): String? {
        val partes = texto.trim().split("/")
        if (partes.size != 3) return null
        val dia = partes[0].toIntOrNull() ?: return null
        val mes = partes[1].toIntOrNull() ?: return null
        val anio = partes[2].toIntOrNull() ?: return null
        if (dia !in 1..31 || mes !in 1..12 || anio < 1900) return null
        return String.format(Locale.US, "%04d-%02d-%02d", anio, mes, dia)
    }

    private fun Calendar.aIsoACalendario(texto: String?): Calendar {
        val cal = Calendar.getInstance()
        val iso = texto?.let { aIso(it) } ?: return cal
        val partes = iso.split("-")
        cal.set(partes[0].toInt(), partes[1].toInt() - 1, partes[2].toInt())
        return cal
    }

    private fun etiquetaDe(clave: String): String = when (clave) {
        "folio" -> "Folio"
        "asunto" -> "Asunto"
        "descripcion" -> "Descripción"
        "destinatario" -> "Destinatario"
        "remitente" -> "Remitente"
        "area_origen" -> "Área de origen"
        "area_destino" -> "Área de destino"
        "tipo" -> "Tipo"
        "estado" -> "Estado"
        "urgencia" -> "Urgencia"
        "desde" -> "Desde"
        "hasta" -> "Hasta"
        else -> clave
    }

    private fun View.ocultarTeclado() {
        context.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(windowToken, 0)
    }
}
