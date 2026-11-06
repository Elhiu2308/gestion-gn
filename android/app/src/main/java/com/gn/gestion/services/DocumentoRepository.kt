package com.gn.gestion.services

import com.gn.gestion.data.local.DocumentoDao
import com.gn.gestion.data.local.MovimientoDao
import com.gn.gestion.data.local.SyncQueueDao
import com.gn.gestion.models.Documento
import com.gn.gestion.models.Movimiento
import com.gn.gestion.models.fusionar
import com.gn.gestion.models.servidorIdRequerido
import com.gn.gestion.models.SYNC_SINCRONIZADO
import kotlinx.coroutines.flow.Flow


class DocumentoRepository(
    private val dao: DocumentoDao,
    private val movimientoDao: MovimientoDao,
    private val syncQueueDao: SyncQueueDao
) {

    val todos: Flow<List<Documento>> = dao.getAll()

    fun porId(id: Long): Flow<Documento?> = dao.getById(id)

    suspend fun insertar(doc: Documento) = dao.insert(doc)

    suspend fun actualizar(doc: Documento) = dao.update(doc)

    suspend fun eliminar(doc: Documento) = dao.delete(doc)

    suspend fun obtenerTodos(): List<Documento> = dao.getAllOnce()

    suspend fun porUuid(uuid: String): Documento? = dao.getByUuid(uuid)

    suspend fun porIdOnce(id: Long): Documento? = dao.getByIdOnce(id)

    suspend fun porServidorId(servidorId: Long): Documento? = dao.getByServidorId(servidorId)

    /**
     * Resuelve el identificador con el que hay que hablarle al backend.
     *
     * Devuelve null cuando el documento todavía no existe en el servidor, en cuyo
     * caso las acciones de flujo deben bloquearse en lugar de invocarse con la PK
     * local de Room (que produce 404 o, peor, opera sobre otro documento).
     */
    suspend fun resolverServidorId(doc: Documento): Long? {
        doc.servidorIdRequerido?.let { return it }
        // La entidad pudo venir desactualizada respecto a la tabla (p. ej. tras un
        // sync concurrente): se reintenta contra la BD por uuid.
        if (doc.uuid.isBlank()) return null
        return dao.getByUuid(doc.uuid)?.servidorIdRequerido
    }

    suspend fun pendientesSync(): List<Documento> = dao.pendientesSync()

    suspend fun marcarSincronizado(id: Long) {
        dao.marcarPendienteEnvio(id)
        dao.marcarEstadosync(id, SYNC_SINCRONIZADO, System.currentTimeMillis())
    }

    suspend fun marcarEstadoSync(id: Long, estado: String) {
        dao.marcarEstadosync(id, estado, System.currentTimeMillis())
    }

    /**
     * Upsert por uuid: inserta si no existe, si no fusiona.
     *
     * Se usa fusionar() en lugar de update() para no perder lo que solo existe en
     * el dispositivo: la ruta del PDF escaneado, el estado de la cola de sync y el
     * servidor_id ya conocido.
     */
    suspend fun guardarRemote(doc: Documento) {
        val existente = dao.getByUuid(doc.uuid)
        if (existente == null) {
            dao.insert(doc)
        } else {
            dao.update(existente.fusionar(doc).copy(id = existente.id))
        }
    }

    fun movimientosDe(uuid: String): Flow<List<Movimiento>> = movimientoDao.porDocumento(uuid)

    suspend fun movimientosDeOnce(uuid: String): List<Movimiento> = movimientoDao.porDocumentoOnce(uuid)

    suspend fun guardarMovimiento(mov: Movimiento) = movimientoDao.insert(mov)

    suspend fun guardarMovimientos(movs: List<Movimiento>) = movimientoDao.insertAll(movs)

    suspend fun syncPendientes(): List<SyncQueue> = syncQueueDao.pendientes()

    suspend fun encolarSync(docId: Long, uuid: String, accion: String) {
        syncQueueDao.insert(SyncQueue(documentoId = docId, uuid = uuid, accion = accion))
    }

    suspend fun borrarCola(id: Long) = syncQueueDao.borrar(id)

    suspend fun ajusteColaEspera(docId: Long, accion: String) =
        syncQueueDao.marcarResuelto(docId, accion)

    // Filtros en memoria
    fun filtrar(
        docs: List<Documento>,
        tipo: String? = null,
        estado: String? = null,
        urgencia: String? = null,
        destino: String? = null,
        desde: Long? = null,
        hasta: Long? = null,
        busqueda: String? = null
    ): List<Documento> {
        var result = docs
        if (tipo != null && tipo != "ALL") result = result.filter { it.tipo == tipo }
        if (estado != null && estado != "ALL") result = result.filter { it.estado == estado }
        if (urgencia != null && urgencia != "ALL") result = result.filter { it.urgencia == urgencia }
        if (destino != null && destino != "ALL") result = result.filter { it.destinatario == destino }

        val desdeDay = desde ?: Long.MIN_VALUE
        val hastaDay = hasta ?: Long.MAX_VALUE
        result = result.filter { it.fecha in desdeDay..hastaDay }

        if (!busqueda.isNullOrBlank()) {
            val q = busqueda.trim().lowercase()
            result = result.filter {
                it.folio.lowercase().contains(q) ||
                it.asunto.lowercase().contains(q) ||
                it.destinatario.lowercase().contains(q) ||
                it.remitente.lowercase().contains(q) ||
                it.dependencia.lowercase().contains(q) ||
                it.notas.lowercase().contains(q)
            }
        }
        return result
    }
}