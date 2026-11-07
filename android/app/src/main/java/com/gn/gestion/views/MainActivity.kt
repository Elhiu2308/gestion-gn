package com.gn.gestion.views

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.gn.gestion.R
import com.gn.gestion.models.Documento
import com.gn.gestion.services.ApiConfig
import com.gn.gestion.services.ApiFactory
import com.gn.gestion.services.AvisoNotificacion
import com.gn.gestion.services.FcmRegistrador
import com.gn.gestion.services.NotificacionForeground
import com.gn.gestion.services.Notificador
import com.gn.gestion.socketio.SocketIOBackground
import com.gn.gestion.socketio.SocketManager
import com.gn.gestion.services.TokenProvider
import com.gn.gestion.util.formatFecha
import com.gn.gestion.util.formatHora
import com.gn.gestion.viewmodels.GestionViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView

    private val vm: GestionViewModel by viewModels()

    private data class PendienteNotificacion(
        val uuid: String,
        val servidorId: Long,
        val notificacionId: Long
    )

    private var pendiente: PendienteNotificacion? = null

    private var dialogoAviso: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Notificador.crearCanal(this)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        navController = navHostFragment.navController

        bottomNav = findViewById(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        findViewById<View>(R.id.btn_notificaciones)?.setOnClickListener {
            abrirNotificaciones()
        }

        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            navController.navigateUp()
        }

        findViewById<View>(R.id.btn_logout)?.setOnClickListener {
            confirmarCerrarSesion()
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val enLogin = destination.id == R.id.login
            val enInicio = destination.id == R.id.dashboard

            toolbar.isVisible = !enLogin
            bottomNav.isVisible = !enLogin

            if (enLogin) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.show()
            }

            findViewById<TextView>(R.id.toolbar_title)?.text = "GestióN Documental"

            findViewById<View>(R.id.container_notif)?.isVisible = enInicio && !enLogin
            findViewById<View>(R.id.btn_back)?.isVisible = !enInicio && !enLogin

            ejecutarPendienteSiProcede()
        }

        registrarFcmSiProcede()
        cargarContadorInicial()

        observeContador()

        pedirPermisoNotificaciones()
        procesarIntent(intent)
        observarAvisosForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        procesarIntent(intent)
    }

    private fun procesarIntent(intent: Intent?) {

        val uuid = intent?.getStringExtra(Notificador.EXTRA_DOCUMENTO_UUID).orEmpty()
            .ifBlank { intent?.getStringExtra("documento_uuid").orEmpty() }
        val servidorId = intent?.getLongExtra(Notificador.EXTRA_DOCUMENTO_ID, 0L)?.takeIf { it > 0L }
            ?: intent?.getStringExtra("documento_id")?.toLongOrNull()
            ?: 0L
        val notificacionId = intent?.getLongExtra(Notificador.EXTRA_NOTIFICACION_ID, 0L)?.takeIf { it > 0L }
            ?: intent?.getStringExtra("notificacion_id")?.toLongOrNull()
            ?: 0L

        Log.i(
            "MainActivity",
            "procesarIntent uuid=$uuid servidorId=$servidorId notificacionId=$notificacionId " +
                "extras=${intent?.extras?.keySet()?.joinToString()}"
        )

        intent?.removeExtra(Notificador.EXTRA_DOCUMENTO_UUID)
        intent?.removeExtra(Notificador.EXTRA_DOCUMENTO_ID)
        intent?.removeExtra(Notificador.EXTRA_NOTIFICACION_ID)
        intent?.removeExtra("documento_uuid")
        intent?.removeExtra("documento_id")
        intent?.removeExtra("notificacion_id")

        if (uuid.isBlank() && servidorId <= 0L && notificacionId <= 0L) return

        pendiente = PendienteNotificacion(uuid, servidorId, notificacionId)
        ejecutarPendienteSiProcede()
        reintentarPendienteTrasNavegar()
    }

    private fun ejecutarPendienteSiProcede() {
        val accion = pendiente ?: return
        if (!TokenProvider.sessionActiva(this)) return

        val actual = navController.currentDestination?.id ?: return
        if (actual == R.id.login) return

        pendiente = null

        if (accion.notificacionId > 0L) {
            marcarNotificacionLeida(accion.notificacionId)
        }

        if (accion.uuid.isNotBlank() || accion.servidorId > 0L) {
            lifecycleScope.launch {
                abrirDocumento(accion.uuid, accion.servidorId)
            }
        }
    }

    private fun reintentarPendienteTrasNavegar() {
        window.decorView.postDelayed({ ejecutarPendienteSiProcede() }, 700)
    }

    private fun marcarNotificacionLeida(notificacionId: Long) {
        if (!ApiConfig.SYNC_ENABLED || !TokenProvider.sessionActiva(this)) return

        lifecycleScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                runCatching {
                    val ids = JsonArray().apply { add(notificacionId) }
                    val cuerpo = JsonObject().apply { add("ids", ids) }
                    ApiFactory.api(this@MainActivity).marcarLeidas(cuerpo)
                    ApiFactory.api(this@MainActivity).contadorNotificaciones()
                }
            }

            resultado.onSuccess {
                SocketManager.setContador(it.noLeidas)
            }
        }
    }

    private suspend fun abrirDocumento(uuid: String, servidorId: Long) {
        val localId = withContext(Dispatchers.IO) {
            vm.descargarYObtenerId(uuid, servidorId)
        }
        Log.i("MainActivity", "abrirDocumento uuid=$uuid servidorId=$servidorId localId=$localId destino=${navController.currentDestination?.id}")

        if (localId == null || localId <= 0L) {
            abrirNotificaciones()
            return
        }

        if (navController.currentDestination?.id == R.id.detalle) return

        val opciones = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .build()

        navController.navigate(
            R.id.detalle,
            Bundle().apply { putLong("id", localId) },
            opciones
        )
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            registrarFcmSiProcede()
            return
        }

        val permiso = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED) {
            registrarFcmSiProcede()
            return
        }

        registroNotificaciones.launch(permiso)
    }

    private val registroNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) registrarFcmSiProcede()
        }

    private fun registrarFcmSiProcede() {
        if (!ApiConfig.SYNC_ENABLED || !TokenProvider.sessionActiva(this)) return
        FcmRegistrador.registrar(this)
    }

    private fun confirmarCerrarSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Deseas cerrar sesión?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ ->
                cerrarSesion()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun cargarContadorInicial() {
        if (!ApiConfig.SYNC_ENABLED || !TokenProvider.sessionActiva(this)) return

        lifecycleScope.launch {
            try {
                val dto = withContext(Dispatchers.IO) {
                    ApiFactory.api(this@MainActivity).contadorNotificaciones()
                }
                SocketManager.setContador(dto.noLeidas)
            } catch (_: Exception) {
            }
        }
    }

    private fun conectarSocket() {
        if (!ApiConfig.SYNC_ENABLED || !TokenProvider.sessionActiva(this)) return
        SocketIOBackground.iniciar(this)
    }

    private fun observeContador() {
        val badge = findViewById<TextView>(R.id.badge_notificaciones)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SocketManager.contadorNotif.collect { n ->
                    badge.isVisible = n > 0
                    badge.text = if (n > 99) "99+" else n.toString()
                }
            }
        }
    }

    private fun observarAvisosForeground() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificacionForeground.aviso.collect { aviso ->
                    if (aviso != null) mostrarAvisoForeground(aviso)
                }
            }
        }
    }

    private fun mostrarAvisoForeground(aviso: AvisoNotificacion) {
        dialogoAviso?.dismiss()

        val constructor = MaterialAlertDialogBuilder(this, R.style.GestioN_Dialog_Base)
            .setTitle(aviso.titulo)
            .setMessage(aviso.cuerpo)
            .setPositiveButton("Abrir") { _, _ ->
                abrirDesdeAviso(aviso)
            }
            .setNegativeButton("Cerrar", null)

        if (aviso.documentoId > 0L || aviso.documentoUuid.isNotBlank()) {
            constructor.setNeutralButton("Ver datos") { _, _ ->
                verDatosDesdeAviso(aviso)
            }
        }

        val dialogo = constructor.create()
        dialogo.setOnDismissListener {
            NotificacionForeground.consumir(aviso.notificacionId)
            if (dialogoAviso === dialogo) dialogoAviso = null
        }
        dialogoAviso = dialogo
        dialogo.show()
    }

    private fun abrirDesdeAviso(aviso: AvisoNotificacion) {
        if (aviso.notificacionId > 0L) {
            marcarNotificacionLeida(aviso.notificacionId)
        }
        if (aviso.documentoId <= 0L && aviso.documentoUuid.isBlank()) {
            abrirNotificaciones()
            return
        }
        lifecycleScope.launch {
            abrirDocumento(aviso.documentoUuid, aviso.documentoId)
        }
    }

    private fun verDatosDesdeAviso(aviso: AvisoNotificacion) {
        if (aviso.notificacionId > 0L) {
            marcarNotificacionLeida(aviso.notificacionId)
        }
        lifecycleScope.launch {
            val localId = withContext(Dispatchers.IO) {
                vm.descargarYObtenerId(aviso.documentoUuid, aviso.documentoId)
            }
            val documento = localId?.let {
                withContext(Dispatchers.IO) { vm.documentoPorId(it) }
            }
            if (documento == null) {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "No se pudieron obtener los datos del documento.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            MaterialAlertDialogBuilder(this@MainActivity, R.style.GestioN_Dialog_Base)
                .setTitle("Datos del documento")
                .setMessage(resumenDocumento(documento))
                .setPositiveButton("Abrir") { _, _ ->
                    lifecycleScope.launch {
                        abrirDocumento(documento.uuid, documento.servidorId ?: 0L)
                    }
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }
    }

    private fun resumenDocumento(d: Documento): String = buildString {
        append("Folio: ").append(d.folio.ifBlank { "—" }).append('\n')
        append("Asunto: ").append(d.asunto.ifBlank { "—" }).append('\n')
        append("Tipo: ").append(d.tipo).append('\n')
        append("Estado: ").append(d.estado).append('\n')
        append("Área: ").append(d.areaActual.ifBlank { d.areaDestino }.ifBlank { "—" }).append('\n')
        append("Remitente: ").append(d.remitente.ifBlank { "—" }).append('\n')
        append("Destinatario: ").append(d.destinatario.ifBlank { "—" }).append('\n')
        if (d.creadoEn > 0L) {
            append("Registrado: ").append(formatHora(d.creadoEn)).append('\n')
        } else if (d.fecha > 0L) {
            append("Fecha: ").append(formatFecha(d.fecha))
        }
    }

    override fun onStart() {
        super.onStart()
        NotificacionForeground.visible = true

        if (ApiConfig.SYNC_ENABLED && TokenProvider.sessionActiva(this)) {
            conectarSocket()
        }
    }

    override fun onStop() {
        NotificacionForeground.visible = false
        super.onStop()
    }

    fun cerrarSesion() {
        SocketIOBackground.detener(this)
        SocketManager.limpiarSesion()
        FcmRegistrador.eliminar(this)
        TokenProvider.cerrarSesion(this)

        val options = NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, true)
            .build()

        navController.navigate(R.id.login, null, options)
    }

    private fun abrirNotificaciones() {
        if (navController.currentDestination?.id == R.id.notificaciones) return
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .build()

        navController.navigate(R.id.notificaciones, null, options)
    }

}