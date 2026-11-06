package com.gn.gestion.views.servidor;

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gn.gestion.R
import com.gn.gestion.services.RedUtil;
import com.gn.gestion.services.ServidorManager;
import com.gn.gestion.services.ApiFactory;
import com.gn.gestion.socketio.SocketManager;
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ServidorFragment : Fragment(R.layout.fragment_servidor) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val input = view.findViewById<TextInputEditText>(R.id.input_servidor)
        val estado = view.findViewById<TextView>(R.id.lbl_estado)

        input.setText(ServidorManager.activo(requireContext()))

        view.findViewById<View>(R.id.btn_probar).setOnClickListener {
            estado.text = "Probando conexión…"
            viewLifecycleOwner.lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) { probar(input.text?.toString().orEmpty()) }
                estado.text = res
            }
        }

        view.findViewById<View>(R.id.btn_buscar).setOnClickListener {
            val boton = it
            boton.isEnabled = false
            estado.text = "Buscando servidores en la red WiFi…"
            viewLifecycleOwner.lifecycleScope.launch {
                val ctx = requireContext().applicationContext
                val candidatos = withContext(Dispatchers.IO) { RedUtil.buscarServidores(ctx) }
                var elegido: String? = null
                withContext(Dispatchers.IO) {
                    for (host in candidatos) {
                        val url = "http://$host:5000"
                        if (responde(url)) { elegido = url; break }
                    }
                }
                if (elegido != null) {
                    input.setText(elegido)
                    estado.text = "Servidor encontrado: $elegido\nPulsa “Guardar y usar este servidor” para aplicarlo."
                    Toast.makeText(requireContext(), "Servidor encontrado", Toast.LENGTH_SHORT).show()
                } else {
                    estado.text = if (candidatos.isEmpty()) {
                        "No se encontró ningún equipo con el puerto 5000 abierto.\nRevisa que el equipo esté encendido y conectado al mismo WiFi."
                    } else {
                        "Hay equipos con el puerto 5000 abierto, pero ninguno respondió como servidor de Gestión."
                    }
                }
                boton.isEnabled = true
            }
        }

        view.findViewById<View>(R.id.btn_guardar).setOnClickListener {
            val url = input.text?.toString().orEmpty()
            if (!ServidorManager.guardar(requireContext(), url)) {
                estado.text = "Dirección inválida. Ejemplo: http://192.168.0.3:5000"
                return@setOnClickListener
            }
            ApiFactory.limpiar()
            SocketManager.desconectar()
            Toast.makeText(requireContext(), "Servidor guardado", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        view.findViewById<View>(R.id.btn_restablecer).setOnClickListener {
            ServidorManager.restablecer(requireContext())
            ApiFactory.limpiar()
            SocketManager.desconectar()
            input.setText(ServidorManager.activo(requireContext()))
            estado.text = "Se usará la dirección instalada: ${ServidorManager.activo(requireContext())}"
        }
    }

    private fun probar(url: String): String {
        val direccion = ServidorManager.normalizar(url)
            ?: return "Dirección inválida. Ejemplo: http://192.168.0.3:5000"
        return try {
            "Servidor responde (HTTP ${codigo(direccion)})"
        } catch (e: Exception) {
            "No se pudo conectar: ${e.javaClass.simpleName}"
        }
    }

    private fun responde(url: String): Boolean = try {
        codigo(url)
        true
    } catch (e: Exception) {
        false
    }

    private fun codigo(url: String): Int {
        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
        val peticion = Request.Builder()
            .url("${url.trimEnd('/')}/api/panel/resumen")
            .header("User-Agent", "gestion-android")
            .header("ngrok-skip-browser-warning", "true")
            .build()
        return client.newCall(peticion).execute().use { it.code }
    }
}