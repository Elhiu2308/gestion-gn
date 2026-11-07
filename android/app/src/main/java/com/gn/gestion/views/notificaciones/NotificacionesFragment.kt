package com.gn.gestion.views.notificaciones;

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gn.gestion.R
import com.gn.gestion.services.ApiFactory
import com.gn.gestion.socketio.EventoSocket
import com.gn.gestion.models.NotificacionDto
import com.gn.gestion.socketio.SocketManager
import com.gn.gestion.viewmodels.GestionViewModel
import com.gn.gestion.adapters.NotificacionAdapter;
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificacionesFragment : Fragment(R.layout.fragment_notificaciones) {

    private val vm: GestionViewModel by activityViewModels()

    private var lista: MutableList<NotificacionDto> = mutableListOf()
    private val adapter = NotificacionAdapter { n -> alAbrir(n) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_marcar_leidas).setOnClickListener {
            marcarTodasLeidas()
        }

        val rv = view.findViewById<RecyclerView>(R.id.lista_notificaciones)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        actualizarEncabezado()
        cargar()
        observarEventosSocket()
        observarContador()
    }

    override fun onResume() {
        super.onResume()
        if (isVisible) cargar()
    }

    private fun observarContador() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SocketManager.contadorNotif.collect { actualizarEncabezado() }
            }
        }
    }

    private fun actualizarEncabezado() {
        val contador = view?.findViewById<TextView>(R.id.lbl_contador) ?: return
        val boton = view?.findViewById<View>(R.id.btn_marcar_leidas) ?: return
        val noLeidas = SocketManager.contadorNotif.value
        contador.isVisible = noLeidas > 0
        contador.text = if (noLeidas > 99) "99+" else noLeidas.toString()
        boton.isEnabled = noLeidas > 0
        boton.alpha = if (noLeidas > 0) 1f else 0.45f
    }

    private fun observarEventosSocket() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SocketManager.eventos().collect { evento ->
                    when (evento) {
                        is EventoSocket.NuevaNotificacion -> {

                            cargar()
                        }
                        is EventoSocket.ContadorActualizado -> {
                            cargar()
                        }
                    }
                }
            }
        }
    }

    private fun cargar() {
        val progress = view?.findViewById<ProgressBar>(R.id.progress) ?: return
        val lblVacio = view?.findViewById<TextView>(R.id.lbl_vacio) ?: return
        val estadoVacio = view?.findViewById<View>(R.id.empty_state) ?: return
        progress.isVisible = true
        estadoVacio.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                ApiFactory.api(requireContext()).contadorNotificaciones()
            }.onSuccess {
                SocketManager.setContador(it.noLeidas)
            }
            runCatching {
                ApiFactory.api(requireContext()).listarNotificaciones(limite = 100)
            }.onSuccess { list ->
                if (!isAdded) return@onSuccess
                lista = list.toMutableList()
                adapter.submitList(lista)
                progress.isVisible = false
                estadoVacio.isVisible = lista.isEmpty()
                lblVacio.text = "Sin notificaciones pendientes."
            }.onFailure {
                if (!isAdded) return@onFailure
                progress.isVisible = false
                estadoVacio.isVisible = true
                lblVacio.text = "No se pudieron cargar las notificaciones."
            }
        }
    }

    private fun alAbrir(n: NotificacionDto) {
        marcarLeida(n.id)
        val servidorId = n.documentoId ?: 0L
        if (n.documentoUuid.isBlank() && servidorId <= 0L) return
        viewLifecycleOwner.lifecycleScope.launch {
            val localId = withContext(Dispatchers.IO) {
                vm.descargarYObtenerId(n.documentoUuid, servidorId)
            }
            if (!isAdded || localId == null || localId <= 0L) return@launch
            if (findNavController().currentDestination?.id == R.id.detalle) return@launch
            findNavController().navigate(
                R.id.detalle,
                Bundle().apply { putLong("id", localId) }
            )
        }
    }

    private fun marcarLeida(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    ApiFactory.api(requireContext()).marcarLeidas(jsonIds(listOf(id)))
                }
                runCatching { ApiFactory.api(requireContext()).contadorNotificaciones() }
            }.onSuccess { SocketManager.setContador(it.noLeidas) }
            cargar()
        }
    }

    private fun marcarTodasLeidas() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { ApiFactory.api(requireContext()).marcarLeidas(JsonObject()) }
                runCatching { ApiFactory.api(requireContext()).contadorNotificaciones() }
            }.onSuccess { SocketManager.setContador(it.noLeidas) }
            cargar()
        }
    }

    private fun jsonIds(ids: List<Long>): JsonObject {
        val arr = com.google.gson.JsonArray()
        ids.forEach { arr.add(it) }
        return JsonObject().apply { add("ids", arr) }
    }
}
