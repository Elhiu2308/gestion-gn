package com.gn.gestion.views.documentos

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.gn.gestion.R
import com.gn.gestion.adapters.DocumentoAdapter
import com.gn.gestion.models.aDocumento
import com.gn.gestion.services.ApiFactory
import com.gn.gestion.viewmodels.GestionViewModel
import kotlinx.coroutines.launch

class DocumentosFragment : Fragment(R.layout.fragment_documentos) {

    private val vm: GestionViewModel by activityViewModels()

    private lateinit var adapter: DocumentoAdapter
    private lateinit var lblCount: TextView
    private lateinit var lblError: TextView
    private lateinit var progress: ProgressBar
    private lateinit var emptyView: View
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var spBandeja: Spinner
    private var cargando = false

    private var bandejaSel = BANDEJA_MIS
    private var spinnerListo = false

    private var filtroEstado: String? = null
    private lateinit var chipFiltro: com.google.android.material.chip.Chip

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lblCount = view.findViewById(R.id.lbl_count)
        lblError = view.findViewById(R.id.lbl_error)
        progress = view.findViewById(R.id.progress_docs)
        emptyView = view.findViewById(R.id.empty_view)
        swipe = view.findViewById(R.id.swipe_refresh)
        chipFiltro = view.findViewById(R.id.chip_filtro_estado)

        filtroEstado = arguments?.getString("filtro_estado")?.takeIf { it != "TODOS" }
        actualizarChipFiltro()

        chipFiltro.setOnCloseIconClickListener {
            filtroEstado = null
            arguments?.remove("filtro_estado")
            actualizarChipFiltro()
            cargar()
        }

        adapter = DocumentoAdapter { doc ->
            findNavController().navigate(
                R.id.action_documentos_to_detalle,
                Bundle().apply { putLong("id", doc.id) }
            )
        }
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_docs)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        spBandeja = view.findViewById(R.id.spinner_bandeja)
        configurarBandejas()

        swipe.setColorSchemeResources(R.color.g_verde)
        swipe.setProgressBackgroundColorSchemeResource(R.color.blanco)
        swipe.setOnRefreshListener { cargar() }

        cargar()
    }

    private fun actualizarChipFiltro() {
        val f = filtroEstado
        if (f.isNullOrBlank()) {
            chipFiltro.isVisible = false
        } else {
            chipFiltro.isVisible = true
            chipFiltro.text = when (f) {
                "PENDIENTES" -> "Pendientes ✕"
                "EN_PROCESO" -> "En proceso ✕"
                "FINALIZADOS" -> "Finalizados ✕"
                else -> "$f ✕"
            }
        }
    }

    private fun configurarBandejas() {
        val opciones = linkedMapOf(BANDEJA_MIS to "Mis documentos")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            opciones.values.toList()
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spBandeja.adapter = adapter
        spBandeja.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val claves = opciones.keys.toList()
                val nueva = claves.getOrElse(position) { BANDEJA_MIS }
                if (nueva != bandejaSel) {
                    bandejaSel = nueva
                    if (spinnerListo) cargar()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerListo = true

        viewLifecycleOwner.lifecycleScope.launch {
            val bandejas = runCatching { ApiFactory.api(requireContext()).bandejas() }
                .getOrDefault(emptyList())
            if (!isAdded || bandejas.isEmpty()) return@launch
            for (b in bandejas) {
                if (b.clave.isNotBlank() && opciones[b.clave] == null) {
                    opciones[b.clave] = b.titulo.ifBlank { b.clave }
                }
            }
            spinnerListo = false
            spBandeja.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                opciones.values.toList()
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            spinnerListo = true
        }
    }

    private fun esFinalizado(estado: String): Boolean {
        val e = estado.uppercase()
        return e.contains("FINALIZADO") || e.contains("CONCLUIDO") || e.contains("CONTESTADO") || e.contains("ARCHIVADO") || e.contains("VALIDADO")
    }

    private fun esEnProceso(estado: String): Boolean {
        val e = estado.uppercase()
        return e.contains("PROCESO") || e.contains("ATENCION") || e.contains("ASIGNADO") || e.contains("TURNADO") || e.contains("REVISION")
    }

    private fun cargar() {
        if (cargando) return
        cargando = true
        if (!swipe.isRefreshing) progress.isVisible = true
        lblError.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = runCatching {
                val api = ApiFactory.api(requireContext())
                if (bandejaSel == BANDEJA_MIS) {
                    val asignados = api.listarDocumentos(bandeja = "asignados_a_mi")
                        .map { it.aDocumento() }
                    val creados = runCatching {
                        api.listarDocumentos(bandeja = "creados_por_mi").map { it.aDocumento() }
                    }.getOrDefault(emptyList())
                    (creados + asignados)
                        .distinctBy { it.uuid }
                        .sortedByDescending { it.fecha }
                } else {
                    api.listarDocumentos(bandeja = bandejaSel)
                        .map { it.aDocumento() }
                        .sortedByDescending { it.fecha }
                }
            }
            cargando = false
            if (!isAdded) return@launch
            progress.isVisible = false
            swipe.isRefreshing = false

            resultado.onSuccess { docs ->
                var locales = vm.guardarDesdeServidor(docs)

                val f = filtroEstado
                if (!f.isNullOrBlank()) {
                    locales = locales.filter { doc ->
                        val e = doc.estado.uppercase()
                        when (f) {
                            "FINALIZADOS" -> esFinalizado(e)
                            "EN_PROCESO" -> !esFinalizado(e) && esEnProceso(e)
                            "PENDIENTES" -> !esFinalizado(e) && !esEnProceso(e)
                            else -> true
                        }
                    }
                }

                adapter.submitList(locales)
                lblCount.text = if (locales.size == 1) {
                    "1 documento"
                } else {
                    "${locales.size} documentos"
                }
                emptyView.isVisible = locales.isEmpty()
                lblError.isVisible = false
            }.onFailure {
                adapter.submitList(emptyList())
                emptyView.isVisible = true
                lblError.isVisible = true
                lblError.text = "No se pudo consultar los documentos.\nRevisa tu conexión e intenta de nuevo."
            }
        }
    }

    companion object {
        private const val BANDEJA_MIS = "mis_documentos"
    }
}