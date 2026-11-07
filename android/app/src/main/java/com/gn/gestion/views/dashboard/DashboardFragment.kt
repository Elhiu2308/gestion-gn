package com.gn.gestion.views.dashboard

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.gn.gestion.R
import com.gn.gestion.models.Permisos
import com.gn.gestion.models.aDocumento
import com.gn.gestion.services.ApiFactory
import com.gn.gestion.services.TokenProvider
import com.gn.gestion.viewmodels.GestionViewModel
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val vm: GestionViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        val nombre = TokenProvider.nombreCompleto(ctx)
            .ifBlank { TokenProvider.nombre(ctx) }
            .ifBlank { TokenProvider.usuario(ctx).orEmpty() }
            .ifBlank { "Usuario" }
        view.findViewById<TextView>(R.id.dash_saludo)?.text = nombre

        val adscripcion = TokenProvider.subdireccion(ctx)
            .ifBlank { TokenProvider.area(ctx) }
            .ifBlank { "Guardia Nacional" }
        view.findViewById<TextView>(R.id.dash_area_chip)?.text = adscripcion

        val cardEscanear = view.findViewById<View>(R.id.card_escanear)
        val puedeCrear = TokenProvider.puede(ctx, Permisos.DOCUMENTO_CREAR)
        cardEscanear?.isVisible = puedeCrear
        cardEscanear?.setOnClickListener { findNavController().navigate(R.id.escanner) }

        view.findViewById<View>(R.id.card_mis_documentos)?.setOnClickListener {
            findNavController().navigate(R.id.documentos)
        }

        view.findViewById<View>(R.id.card_buscar_documento)?.setOnClickListener {
            findNavController().navigate(R.id.busqueda_global)
        }

        view.findViewById<View>(R.id.card_stat_total)?.setOnClickListener {
            findNavController().navigate(
                R.id.documentos,
                Bundle().apply { putString("filtro_estado", "TODOS") }
            )
        }
        view.findViewById<View>(R.id.card_stat_pendientes)?.setOnClickListener {
            findNavController().navigate(
                R.id.documentos,
                Bundle().apply { putString("filtro_estado", "PENDIENTES") }
            )
        }
        view.findViewById<View>(R.id.card_stat_proceso)?.setOnClickListener {
            findNavController().navigate(
                R.id.documentos,
                Bundle().apply { putString("filtro_estado", "EN_PROCESO") }
            )
        }
        view.findViewById<View>(R.id.card_stat_finalizados)?.setOnClickListener {
            findNavController().navigate(
                R.id.documentos,
                Bundle().apply { putString("filtro_estado", "FINALIZADOS") }
            )
        }

        val tvTotal = view.findViewById<TextView>(R.id.dash_count_total)
        val tvPendientes = view.findViewById<TextView>(R.id.dash_count_pendientes)
        val tvProceso = view.findViewById<TextView>(R.id.dash_count_proceso)
        val tvFinalizados = view.findViewById<TextView>(R.id.dash_count_finalizados)

        fun esFinalizado(estado: String): Boolean {
            val e = estado.uppercase()
            return e.contains("FINALIZADO") || e.contains("CONCLUIDO") || e.contains("CONTESTADO") || e.contains("ARCHIVADO") || e.contains("VALIDADO")
        }

        fun esEnProceso(estado: String): Boolean {
            val e = estado.uppercase()
            return e.contains("PROCESO") || e.contains("ATENCION") || e.contains("ASIGNADO") || e.contains("TURNADO") || e.contains("REVISION")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    runCatching {
                        val api = ApiFactory.api(requireContext())
                        val asignados = api.listarDocumentos(bandeja = "asignados_a_mi").map { it.aDocumento() }
                        val creados = runCatching { api.listarDocumentos(bandeja = "creados_por_mi").map { it.aDocumento() } }.getOrDefault(emptyList())
                        val listaMisDocs = (creados + asignados).distinctBy { it.uuid }
                        vm.guardarDesdeServidor(listaMisDocs)
                    }
                }

                vm.todos.collect { docs ->
                    val total = docs.size
                    var countPendientes = 0
                    var countProceso = 0
                    var countFinalizados = 0

                    for (doc in docs) {
                        val e = doc.estado.uppercase()
                        when {
                            esFinalizado(e) -> countFinalizados++
                            esEnProceso(e) -> countProceso++
                            else -> countPendientes++
                        }
                    }

                    tvTotal?.text = total.toString()
                    tvPendientes?.text = countPendientes.toString()
                    tvProceso?.text = countProceso.toString()
                    tvFinalizados?.text = countFinalizados.toString()
                }
            }
        }
    }
}