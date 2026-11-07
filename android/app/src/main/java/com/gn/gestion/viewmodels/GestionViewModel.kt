package com.gn.gestion.viewmodels;

import android.app.Application;
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gn.gestion.data.local.AppDatabase;
import com.gn.gestion.models.Documento;
import com.gn.gestion.services.DocumentoRepository;
import com.gn.gestion.services.FlujoRepository;
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

    suspend fun activarBiometria(cipher: Cipher): String? = auth.activarBiometria(cipher)?.message

    suspend fun desactivarBiometria() {
        auth.cerrarSesionBiometrica()
    }

    fun cerrarSesion() {
        viewModelScope.launch {
            auth.cerrarSesion()
        }
    }

    fun sincronizar() {
        _syncEstado.value = "sync"
        viewModelScope.launch {
            sincronizarAhora()
        }
    }

    suspend fun sincronizarAhora(): SyncResultado {
        _syncEstado.value = "sync"
        val res = SyncEngine.sincronizar(getApplication())
        _ultimoSync.value = res
        _syncEstado.value = if (res.ok) "ok" else "error"
        return res
    }

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

    fun turnarAlGuardar(
        localId: Long,
        opcionTipo: String,
        opcionId: Long,
        usuarioDestinoId: Long?,
        comentario: String = "Turnado al registrar el documento"
    ) {
        viewModelScope.launch {
            runCatching {
                val sync = SyncEngine.sincronizar(getApplication())
                if (!sync.ok) {
                    Log.w("GN_GUARDAR", "Turnado al guardar omitido: sync=${sync.detalle}")
                    return@runCatching
                }
                val local = documentoPorId(localId) ?: return@runCatching
                val sid = local.servidorId ?: 0L
                if (sid <= 0L) {
                    Log.w("GN_GUARDAR", "Turnado al guardar omitido: sin servidorId para localId=$localId")
                    return@runCatching
                }

                val flujo = FlujoRepository(ApiFactory.api(getApplication()))
                val opciones = flujo.turnadoOpciones()
                val valida = opciones.opciones.firstOrNull {
                    it.id == opcionId && it.tipo.equals(opcionTipo, ignoreCase = true)
                } ?: usuarioDestinoId?.let { uid ->
                    opciones.opciones.firstOrNull {
                        it.id == uid && it.tipo.equals("usuario", ignoreCase = true)
                    }
                }
                if (valida == null) {
                    Log.w(
                        "GN_GUARDAR",
                        "Turnado al guardar omitido: $opcionTipo/$opcionId no está en " +
                                opciones.opciones.joinToString { "${it.tipo}/${it.id}" }
                    )
                    return@runCatching
                }

                val resp = flujo.turnar(
                    sid,
                    valida.tipo,
                    valida.id,
                    usuarioDestinoId = usuarioDestinoId,
                    comentario = comentario
                )
                Log.i(
                    "GN_GUARDAR",
                    "Turnado al guardar OK doc=$sid tipo=${valida.tipo} id=${valida.id} " +
                            "usuario=$usuarioDestinoId ok=${resp.ok}"
                )
                sincronizar()
            }.onFailure {
                Log.e("GN_GUARDAR", "Turnado al guardar falló (se ignora para no perder el alta)", it)
            }
        }
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

    suspend fun documentoPorId(id: Long): Documento? = repository.porIdOnce(id)

    fun movimientosDe(uuid: String): Flow<List<Movimiento>> = repository.movimientosDe(uuid)

    suspend fun guardarDesdeServidor(docs: List<Documento>): List<Documento> {
        for (d in docs) repository.guardarRemote(d)
        return docs.mapNotNull { repository.porUuid(it.uuid) }
    }

    suspend fun resolverIdLocal(uuid: String, servidorId: Long): Long? {
        if (uuid.isNotBlank()) repository.porUuid(uuid)?.let { return it.id }
        if (servidorId > 0) repository.porServidorId(servidorId)?.let { return it.id }
        return null
    }

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