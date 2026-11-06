package com.gn.gestion.viewmodels;

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gn.gestion.data.local.AppDatabase;
import com.gn.gestion.models.Documento;
import com.gn.gestion.services.DocumentoRepository;
import com.gn.gestion.models.Movimiento;
import com.gn.gestion.models.TipoDocumento;
import com.gn.gestion.services.TokenProvider;
import com.gn.gestion.models.aDocumento
import com.gn.gestion.services.ApiFactory;
import com.gn.gestion.services.AuthRepository;
import com.gn.gestion.models.DocumentoDto;
import com.gn.gestion.socketio.SocketManager
import com.gn.gestion.socketio.SocketIOBackground
import com.gn.gestion.services.SyncEngine;
import com.gn.gestion.services.SyncResultado;
import com.gn.gestion.data.sync.SyncWorker
import com.gn.gestion.services.ApiConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

data class Filtros(
    var tipo: String = "ALL",
    var estado: String = "ALL",
    var urgencia: String = "ALL",
    var destinatario: String = "ALL",
    var desde: Long? = null,
    var hasta: Long? = null
)

class GestionViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val repository = DocumentoRepository(
        db.documentoDao(), db.movimientoDao(), db.syncQueueDao()
    )
    private val auth = AuthRepository(app.applicationContext)

    val todos: StateFlow<List<Documento>> = repository.todos.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    private val _filtros = MutableStateFlow(Filtros())
    val filtros: StateFlow<Filtros> = _filtros

    val busqueda = MutableStateFlow("")

    private val _syncEstado = MutableStateFlow("")
    val syncEstado: StateFlow<String> = _syncEstado

    private val _ultimoSync = MutableStateFlow<SyncResultado?>(null)
    val ultimoSync: StateFlow<SyncResultado?> = _ultimoSync

    val documentosFiltrados: Flow<List<Documento>> = combine(
        todos, _filtros, busqueda
    ) { docs: List<Documento>, f: Filtros, q: String ->
        repository.filtrar(
            docs, f.tipo, f.estado, f.urgencia, f.destinatario, f.desde, f.hasta, q
        )
    }

    val destinatarios: Flow<List<String>> = todos.map { docs ->
        docs.map { it.destinatario }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val sesionUsuario: String
        get() = TokenProvider.usuario(getApplication()) ?: ""
    val sesionNombre: String
        get() = TokenProvider.nombre(getApplication())
    val syncActivo: Boolean
        get() = ApiConfig.SYNC_ENABLED

    // ---------------- Autenticación ----------------

    fun iniciarSesion(
        usuario: String,
        contrasena: String,
        onError: (String) -> Unit,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                auth.login(usuario, contrasena)
            } catch (e: Exception) {
                onError(e.message ?: "Error")
                return@launch
            }
            onSuccess?.invoke()
            runCatching { SyncWorker.programar(getApplication()) }
            sincronizar()
        }
    }

    /** Login biométrico: renueva la sesión con el refresh token ya descifrado. */
    fun iniciarSesionBiometrica(
        refreshToken: String,
        onError: (String) -> Unit,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                auth.loginBiometrico(refreshToken)
            } catch (e: Exception) {
                onError(e.message ?: "Error")
                return@launch
            }
            runCatching {
                SocketIOBackground.iniciar(getApplication())
            }
            onSuccess()
            runCatching { SyncWorker.programar(getApplication()) }
            sincronizar()
        }
    }

    fun biometriaActivada(): Boolean = auth.biometriaActivada()

    fun biometriaUtilizable(): Boolean = auth.biometriaUtilizable()

    fun ultimoRefreshDisponible(): Boolean = auth.hayRefreshPendiente()

    /**
     * Cifra el refresh token con el cipher ya autorizado por el BiometricPrompt.
     * Devuelve null si quedó activa.
     */
    suspend fun activarBiometria(cipher: Cipher): String? = auth.activarBiometria(cipher)?.message

    suspend fun desactivarBiometria() {
        auth.cerrarSesionBiometrica()
    }

    fun cerrarSesion() {
        viewModelScope.launch {
            auth.cerrarSesion()
        }
    }

    // ---------------- Sincronización ----------------

    fun sincronizar() {
        _syncEstado.value = "sync"
        viewModelScope.launch {
            val res = SyncEngine.sincronizar(getApplication())
            _ultimoSync.value = res
            _syncEstado.value = if (res.ok) "ok" else "error"
        }
    }

    // ---------------- CRUD local ----------------

    suspend fun guardar(doc: Documento?): Long {
        val id = if (doc != null && doc.id != 0L) {
            repository.actualizar(doc)
            repository.encolarSync(doc.id, doc.uuid, "EDITAR")
            doc.id
        } else if (doc != null) {
            val nuevoId = repository.insertar(doc)
            SyncEngine.encolarAlta(getApplication(), doc.copy(id = nuevoId))
            nuevoId
        } else {
            -1L
        }
        sincronizar()
        return id
    }

    fun eliminar(doc: Documento) {
        viewModelScope.launch {
            repository.actualizar(
                doc.copy(eliminadoEn = System.currentTimeMillis(), syncPendiente = true)
            )
            SyncEngine.encolarEliminado(getApplication(), doc)
            sincronizar()
        }
    }

    // ---------------- Trazabilidad ----------------

    suspend fun documentoPorId(id: Long): Documento? = repository.porIdOnce(id)

    fun movimientosDe(uuid: String): Flow<List<Movimiento>> = repository.movimientosDe(uuid)

    /**
     * Persiste resultados llegados del servidor y devuelve las filas locales.
     *
     * Necesario porque un DTO remoto no tiene PK de Room: sin guardarlo, el
     * documento se navegaría con id = 0 y el detalle no sabría abrirlo.
     */
    suspend fun guardarDesdeServidor(docs: List<Documento>): List<Documento> {
        for (d in docs) repository.guardarRemote(d)
        return docs.mapNotNull { repository.porUuid(it.uuid) }
    }

    /**
     * Id local (Room) de un documento a partir de su uuid o de su id de servidor.
     * Es el que necesita [com.gn.gestion.ui.fragments.DetalleFragment] para abrirse.
     */
    suspend fun resolverIdLocal(uuid: String, servidorId: Long): Long? {
        if (uuid.isNotBlank()) repository.porUuid(uuid)?.let { return it.id }
        if (servidorId > 0) repository.porServidorId(servidorId)?.let { return it.id }
        return null
    }

    /**
     * Resuelve o descarga el documento del servidor, lo persiste en Room y devuelve su id local.
     * Se usa al abrir una notificación de un documento asignado o recibido.
     */
    suspend fun descargarYObtenerId(uuid: String = "", servidorId: Long = 0L): Long? {
        val existente = resolverIdLocal(uuid, servidorId)
        if (existente != null && existente > 0L) return existente

        runCatching { SyncEngine.sincronizar(getApplication()) }
        val postSync = resolverIdLocal(uuid, servidorId)
        if (postSync != null && postSync > 0L) return postSync

        if (servidorId > 0L) {
            val json = runCatching {
                withContext(Dispatchers.IO) {
                    ApiFactory.api(getApplication()).detalle(servidorId)
                }
            }.getOrNull()
            val dto = runCatching {
                Gson().fromJson(
                    json?.getAsJsonObject("documento"), DocumentoDto::class.java
                )
            }.getOrNull()
            if (dto != null && dto.uuid.isNotBlank()) {
                return guardarDesdeServidor(listOf(dto.aDocumento())).firstOrNull()?.id
            }
        }

        if (uuid.isNotBlank()) {
            val lista = runCatching {
                withContext(Dispatchers.IO) {
                    ApiFactory.api(getApplication()).listarDocumentos(q = uuid)
                }
            }.getOrNull()
            val dto = lista?.firstOrNull { it.uuid == uuid }
            if (dto != null) {
                return guardarDesdeServidor(listOf(dto.aDocumento())).firstOrNull()?.id
            }
        }

        return null
    }

    // ---------------- Filtros ----------------

    fun setFiltro(block: (Filtros) -> Unit) {
        val f = _filtros.value
        block(f)
        _filtros.value = Filtros(
            tipo = f.tipo, estado = f.estado, urgencia = f.urgencia,
            destinatario = f.destinatario, desde = f.desde, hasta = f.hasta
        )
    }

    fun limpiarFiltros() {
        _filtros.value = Filtros()
        busqueda.value = ""
    }
}