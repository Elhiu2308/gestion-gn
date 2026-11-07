package com.gn.gestion.services

import android.content.Context
import com.gn.gestion.data.local.AppDatabase
import com.gn.gestion.data.local.DocumentoDao
import com.gn.gestion.models.Documento
import com.gn.gestion.models.DocumentoDto
import com.gn.gestion.models.Movimiento
import com.gn.gestion.models.SYNC_CONFLICTO
import com.gn.gestion.models.SYNC_PENDIENTE
import com.gn.gestion.models.SYNC_SINCRONIZADO
import com.gn.gestion.models.aDocumento
import com.gn.gestion.models.aMovimiento
import com.gn.gestion.models.fusionar
import com.gn.gestion.util.Hora
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import android.util.Log
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.security.MessageDigest

data class SyncResultado(
    val ok: Boolean,
    val subidos: Int = 0,
    val bajados: Int = 0,
    val conflictos: Int = 0,
    val errores: Int = 0,
    val detalle: String = ""
)

object SyncEngine {

    private const val TAG = "SyncEngine"
    val DISPOSITIVO = "android-gestion"

    private val syncMutex = Mutex()

    suspend fun sincronizar(context: Context): SyncResultado =
        syncMutex.withLock { sincronizarInterno(context) }

    private suspend fun sincronizarInterno(context: Context): SyncResultado = withContext(Dispatchers.IO) {
        if (!ApiConfig.SYNC_ENABLED || !TokenProvider.sessionActiva(context)) {
            Log.w(TAG, "[TX] Sincronización omitida: SYNC_ENABLED=${ApiConfig.SYNC_ENABLED}, sesionActiva=${TokenProvider.sessionActiva(context)}")
            return@withContext SyncResultado(ok = false, detalle = "Sin sesión")
        }
        val db = AppDatabase.getInstance(context)
        val dao = db.documentoDao()
        val syncDao = db.syncQueueDao()
        val movDao = db.movimientoDao()
        val api = ApiFactory.api(context)

        try {
            val filaCola = syncDao.pendientes()
            val docsPendientes = dao.pendientesSync()

            Log.i(TAG, "[TX] Iniciando sincronización con el servidor -> Documentos pendientes de envío: ${docsPendientes.size}")
            for (p in docsPendientes) {
                Log.i(TAG, "[TX] Documento a subir -> UUID=${p.uuid} | Folio=${p.folio} | Destinatario=${p.destinatario} | Estado=${p.estado}")
            }

            val faltaServidorId = dao.countSinServidorId() > 0
            val lastSync = if (faltaServidorId) 0L else TokenProvider.lastSync(context)

            val cambios = JsonObject()
            cambios.addProperty("lastSync", lastSync)
            val items = com.google.gson.JsonArray()
            for (doc in docsPendientes) items.add(doc.aJsonPush())
            cambios.add("cambios", items)

            val resp = api.sync(cambios)

            val cliente = resp.cliente
            val conflictosUuids = (cliente?.conflictos ?: emptyList())
                .mapNotNull { (it as? JsonObject)?.get("uuid")?.asString }
                .toSet()
            val errorUuids = (cliente?.errores ?: emptyList())
                .mapNotNull { (it as? JsonObject)?.get("uuid")?.asString }
                .toSet()
            val aceptadosUuids = docsPendientes
                .map { it.uuid }
                .filter { it !in conflictosUuids && it !in errorUuids }
                .toSet()

            val servidor = resp.servidor
            var bajados = 0
            if (servidor != null) {
                for (dto in servidor.documentos) {
                    val ent = dto.aDocumento()
                    val local = dao.getByUuid(ent.uuid)
                    val esEcoPropio = ent.uuid in aceptadosUuids
                    if (local != null && !esEcoPropio &&
                        (local.syncPendiente || local.syncStatus == SYNC_CONFLICTO)
                    ) {

                        dao.marcarEstadosync(local.id, SYNC_CONFLICTO, System.currentTimeMillis())
                        continue
                    }

                    val actualizado = if (local != null) local.fusionar(ent) else ent
                    dao.insert(actualizado)
                    bajados++
                }
                for (eli in servidor.eliminados) {
                    dao.getByUuid(eli.uuid)?.let {
                        dao.update(it.copy(eliminadoEn = eli.eliminadoEn, syncPendiente = false))
                    }
                }
                for (movDto in servidor.movimientos) {
                    val mov = movDto.aMovimiento()
                    if (movDao.existe(mov.uuid) == 0) {
                        movDao.insert(mov)
                    }
                }
                if (servidor.serverTime > 0) {
                    TokenProvider.setLastSync(context, servidor.serverTime)
                }

                subirPdfsPendientes(context, dao, api, servidor.documentos)
            }

            var subidos = 0
            for (doc in docsPendientes) {
                if (doc.uuid in errorUuids) continue
                if (doc.uuid in conflictosUuids) {
                    dao.marcarEstadosync(doc.id, SYNC_CONFLICTO, System.currentTimeMillis())
                    continue
                }
                dao.marcarPendienteEnvio(doc.id)
                dao.marcarEstadosync(doc.id, SYNC_SINCRONIZADO, System.currentTimeMillis())
                filaCola.filter { it.uuid == doc.uuid }.forEach { syncDao.borrar(it.id) }
                subidos++
            }

            SyncResultado(
                ok = true,
                subidos = subidos,
                bajados = bajados,
                conflictos = conflictosUuids.size,
                errores = errorUuids.size
            )
        } catch (e: Exception) {
            SyncResultado(ok = false, detalle = e.message ?: "Error de red")
        }
    }

    suspend fun encolarAlta(context: Context, doc: Documento) {
        val db = AppDatabase.getInstance(context)
        val dao = db.documentoDao()
        val syncDao = db.syncQueueDao()
        dao.update(doc.copy(syncPendiente = true, syncStatus = SYNC_PENDIENTE))
        dao.marcarEstadosync(doc.id, SYNC_PENDIENTE, System.currentTimeMillis())
        syncDao.insert(SyncQueue(documentoId = doc.id, uuid = doc.uuid, accion = "ALTA"))
    }

    suspend fun encolarEliminado(context: Context, doc: Documento) {
        val db = AppDatabase.getInstance(context)
        val dao = db.documentoDao()
        val syncDao = db.syncQueueDao()
        dao.marcarEstadosync(doc.id, SYNC_PENDIENTE, System.currentTimeMillis())
        syncDao.insert(SyncQueue(documentoId = doc.id, uuid = doc.uuid, accion = "ELIMINAR"))
    }

    private fun Documento.aJsonPush(): JsonObject {
        val o = JsonObject()
        o.addProperty("uuid", uuid)
        o.addProperty("folio", folio.orEmpty())
        o.addProperty("tipo", tipo)
        o.addProperty("asunto", asunto.orEmpty())
        o.addProperty("remitente", remitente.orEmpty())
        o.addProperty("destinatario", destinatario.orEmpty())
        if (idRemitente != null) o.addProperty("id_remitente", idRemitente) else o.add("id_remitente", com.google.gson.JsonNull.INSTANCE)
        if (idDestinatario != null) o.addProperty("id_destinatario", idDestinatario) else o.add("id_destinatario", com.google.gson.JsonNull.INSTANCE)
        o.addProperty("dependencia", dependencia.orEmpty())
        o.addProperty("area_origen", areaOrigen.orEmpty())
        o.addProperty("area_destino", areaDestino.orEmpty())
        o.addProperty("referencia", referencia.orEmpty())
        o.addProperty("num_anexos", numAnexos)
        o.addProperty("observaciones", observaciones.orEmpty())
        o.addProperty("clasificacion", clasificacion)
        if (fechaDocumento.isNotBlank()) o.addProperty("fecha_documento", fechaDocumento) else o.add("fecha_documento", com.google.gson.JsonNull.INSTANCE)
        if (fechaLimite.isNotBlank()) o.addProperty("fecha_limite", fechaLimite) else o.add("fecha_limite", com.google.gson.JsonNull.INSTANCE)
        val horaServidor = Hora.a24(fechaLimiteHora)
        if (horaServidor.isNotBlank()) o.addProperty("fecha_limite_hora", horaServidor) else o.add("fecha_limite_hora", com.google.gson.JsonNull.INSTANCE)
        o.addProperty("fecha_recepcion", fechaRecepcion)
        o.addProperty("urgencia", urgencia)
        o.addProperty("notas", notas.orEmpty())
        o.addProperty("texto_ocr", textoOcr.orEmpty())
        o.addProperty("estado", estado)
        o.addProperty("actualizado_en", actualizadoEn)
        o.addProperty("disponible", eliminadoEn == null)
        if (eliminadoEn != null) {
            o.addProperty("eliminado_en", eliminadoEn)
        } else {
            o.add("eliminado_en", com.google.gson.JsonNull.INSTANCE)
        }
        return o
    }

    private suspend fun subirPdfsPendientes(
        context: Context,
        dao: DocumentoDao,
        api: ApiService,
        documentosServidor: List<DocumentoDto>
    ) {
        val porUuid = documentosServidor.associateBy { it.uuid }
        for (local in dao.getAllOnce()) {
            val pdfPath = local.pdfPath
            if (pdfPath.isNullOrBlank()) continue
            val server = porUuid[local.uuid] ?: continue
            val file = File(pdfPath)
            if (!file.exists()) continue

            if (!server.pdfNombre.isNullOrBlank()) {
                val hashLocal = sha256DeArchivo(file)
                if (hashLocal.isNotEmpty() && hashLocal == server.hashArchivo) continue
            }
            val nombreArchivo = file.name
            val extension = nombreArchivo.substringAfterLast('.').lowercase()
            val mediaType = when (extension) {
                "png" -> "image/png".toMediaType()
                "jpg", "jpeg" -> "image/jpeg".toMediaType()
                else -> "application/pdf".toMediaType()
            }
            val part = MultipartBody.Part.createFormData(
                "archivo", file.name, file.asRequestBody(mediaType)
            )
            runCatching {
                val respuesta = api.subirPdf(server.id, part)
                if (!respuesta.isSuccessful) {
                    val cuerpo = runCatching { respuesta.errorBody()?.string().orEmpty() }.getOrDefault("")
                    Log.e(
                        TAG,
                        "[TX] PDF rechazado doc=${server.id} archivo=${file.name} " +
                                "HTTP ${respuesta.code()} | $cuerpo"
                    )
                } else {
                    Log.i(TAG, "[TX] PDF subido doc=${server.id} archivo=${file.name}")
                }
            }.onFailure {
                Log.e(TAG, "[TX] Error subiendo PDF doc=${server.id} archivo=${file.name}", it)
            }
        }
    }

    private fun sha256DeArchivo(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { ins ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}