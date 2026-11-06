package com.gn.gestion.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documentos")
data class Documento(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(defaultValue = "''")
    val uuid: String,
    val tipo: String,          // nombre de TipoDocumento
    val folio: String = "",
    val asunto: String = "",
    val destinatario: String = "",
    val remitente: String = "",
    // Relación con el catálogo institucional de personas (1:1 con usuarios).
    // El documento guarda SIEMPRE el id_persona; el texto de arriba es sólo la
    // copia histórica del nombre. Null = sin relación (texto puro legado).
    @ColumnInfo(name = "id_remitente")
    val idRemitente: Long? = null,
    @ColumnInfo(name = "id_destinatario")
    val idDestinatario: Long? = null,
    @ColumnInfo(defaultValue = "''")
    val dependencia: String = "",
    @ColumnInfo(defaultValue = "''")
    val areaOrigen: String = "",
    @ColumnInfo(defaultValue = "''")
    val areaDestino: String = "",
    @ColumnInfo(defaultValue = "''")
    val referencia: String = "",
    @ColumnInfo(defaultValue = "0")
    val numAnexos: Int = 0,
    @ColumnInfo(defaultValue = "''")
    val observaciones: String = "",
    @ColumnInfo(defaultValue = "'CONFIDENCIAL'")
    val clasificacion: String = "CONFIDENCIAL",
    @ColumnInfo(defaultValue = "''")
    val areaActual: String = "",
    @ColumnInfo(defaultValue = "0")
    val compartido: Boolean = false,
    @ColumnInfo(defaultValue = "''")
    val respuestaConsolidada: String = "",
    val fecha: Long,           // epoch millis de la fecha del documento
    @ColumnInfo(defaultValue = "''")
    val fechaDocumento: String = "",  // YYYY-MM-DD
    @ColumnInfo(defaultValue = "''")
    val fechaLimite: String = "",     // YYYY-MM-DD (vencimiento)
    @ColumnInfo(defaultValue = "''")
    val fechaLimiteHora: String = "", // HH:MM (hora límite)
    @ColumnInfo(defaultValue = "0")
    val fechaRecepcion: Long = 0,     // epoch millis
    val fechaRegistro: Long = System.currentTimeMillis(),
    val estado: String,        // nombre de EstadoDocumento
    val urgencia: String,      // nombre de Urgencia
    val notas: String = "",
    @ColumnInfo(defaultValue = "''")
    val textoOcr: String = "",
    val pdfPath: String? = null,   // ruta del PDF escaneado (local)
    @ColumnInfo(defaultValue = "''")
    val pdfNombre: String = "",
    @ColumnInfo(defaultValue = "0")
    val pdfVersion: Int = 0,
    @ColumnInfo(defaultValue = "''")
    val hashArchivo: String = "",
    @ColumnInfo(defaultValue = "''")
    val codigoQr: String = "",

    // Metadatos del servidor.
    // servidorId es el ID primario del backend: es el único identificador válido
    // para las rutas /api/documentos/<id>/... El campo `id` es la PK local de
    // Room y nunca debe usarse contra la API.
    @ColumnInfo(name = "servidor_id")
    val servidorId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val usuarioServidorId: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val creadorServidorId: Long = 0,

    // Sincronización
    @ColumnInfo(defaultValue = "'SYNCED'")
    val syncStatus: String = SYNC_SINCRONIZADO,
    @ColumnInfo(defaultValue = "0")
    val syncPendiente: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val creadoEn: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val actualizadoEn: Long = 0,
    val eliminadoEn: Long? = null
)

/** Construye un documento con UUID nuevo (modo offline/alta local). */
fun nuevoDocumento(
    tipo: String,
    estado: String,
    urgencia: String,
    fecha: Long = System.currentTimeMillis()
): Documento {
    val ahora = System.currentTimeMillis()
    return Documento(
        uuid = java.util.UUID.randomUUID().toString(),
        tipo = tipo,
        fecha = fecha,
        fechaRegistro = ahora,
        estado = estado,
        urgencia = urgencia,
        creadoEn = ahora,
        actualizadoEn = ahora,
        syncStatus = SYNC_PENDIENTE,
        syncPendiente = true
    )
}

/** Convierte la representación del servidor a entidad local. */
fun DocumentoDto.aDocumento(): Documento =
    Documento(
        uuid = uuid.ifBlank { java.util.UUID.randomUUID().toString() },
        tipo = tipo.uppercase(),
        folio = folio ?: "",
        asunto = asunto ?: "",
        destinatario = destinatario ?: "",
        remitente = remitente ?: "",
        idRemitente = idRemitente,
        idDestinatario = idDestinatario,
        dependencia = dependencia ?: "",
        areaOrigen = areaOrigen ?: "",
        areaDestino = areaDestino ?: "",
        referencia = referencia ?: "",
        numAnexos = numAnexos ?: 0,
        observaciones = observaciones ?: "",
        clasificacion = clasificacion ?: "PUBLICO",
        areaActual = areaActual ?: "",
        compartido = compartido ?: false,
        respuestaConsolidada = respuestaConsolidada ?: "",
        fecha = fechaRecepcion.takeIf { it > 0 } ?: creadoEn,
        fechaDocumento = fechaDocumento ?: "",
        fechaLimite = fechaLimite ?: "",
        fechaLimiteHora = fechaLimiteHora ?: "",
        fechaRecepcion = fechaRecepcion ?: 0,
        estado = estado.uppercase(),
        urgencia = urgencia.uppercase(),
        notas = notas ?: "",
        textoOcr = textoOcr ?: "",
        pdfNombre = pdfNombre ?: "",
        pdfVersion = pdfVersion ?: 0,
        hashArchivo = hashArchivo ?: "",
        codigoQr = codigoQr ?: "",
        servidorId = id.takeIf { it > 0L },
        usuarioServidorId = usuarioId ?: 0,
        creadorServidorId = 0,
        syncStatus = SYNC_SINCRONIZADO,
        syncPendiente = false,
        creadoEn = creadoEn ?: 0,
        actualizadoEn = actualizadoEn ?: 0,
        eliminadoEn = eliminadoEn
    )

/** Aplica sobre esta entidad los datos venidos del servidor (upsert por uuid). */
fun Documento.fusionar(campos: Documento): Documento =
    copy(
        uuid = campos.uuid.ifBlank { uuid },
        tipo = campos.tipo, folio = campos.folio, asunto = campos.asunto,
        destinatario = campos.destinatario, remitente = campos.remitente,
        idRemitente = campos.idRemitente, idDestinatario = campos.idDestinatario,
        dependencia = campos.dependencia, areaOrigen = campos.areaOrigen,
        areaDestino = campos.areaDestino, referencia = campos.referencia,
        numAnexos = campos.numAnexos, observaciones = campos.observaciones,
        clasificacion = campos.clasificacion, areaActual = campos.areaActual,
        compartido = campos.compartido, respuestaConsolidada = campos.respuestaConsolidada,
        fecha = campos.fecha,
        fechaDocumento = campos.fechaDocumento, fechaLimite = campos.fechaLimite,
        fechaLimiteHora = campos.fechaLimiteHora,
        fechaRecepcion = campos.fechaRecepcion, estado = campos.estado,
        urgencia = campos.urgencia, notas = campos.notas, textoOcr = campos.textoOcr,
        pdfNombre = campos.pdfNombre, pdfVersion = campos.pdfVersion,
        hashArchivo = campos.hashArchivo, codigoQr = campos.codigoQr,
        servidorId = campos.servidorId ?: servidorId,
        usuarioServidorId = campos.usuarioServidorId,
        creadorServidorId = campos.creadorServidorId,
        syncStatus = if (syncPendiente) SYNC_PENDIENTE else campos.syncStatus,
        creadoEn = maxOf(creadoEn, campos.creadoEn),
        actualizadoEn = maxOf(actualizadoEn, campos.actualizadoEn),
        eliminadoEn = campos.eliminadoEn
    )

/** Convierte un documento del archivo virtual a entidad local. */
fun DocumentoArchivoDto.aDocumento(): Documento =
    Documento(
        uuid = uuid.ifBlank { java.util.UUID.randomUUID().toString() },
        tipo = tipo.uppercase().ifBlank { "MENSAJE" },
        folio = folio ?: "",
        asunto = asunto ?: "",
        clasificacion = clasificacion ?: "PUBLICO",
        fecha = fechaRecepcion.takeIf { it > 0 } ?: System.currentTimeMillis(),
        fechaRecepcion = fechaRecepcion,
        estado = estado.uppercase(),
        urgencia = "ORDINARIO",
        servidorId = id.takeIf { it > 0L },
        syncStatus = SYNC_SINCRONIZADO,
        syncPendiente = false,
        creadoEn = fechaRecepcion,
        actualizadoEn = fechaRecepcion
    )

// Vocabulario compartido con el servidor (web/app/utils/enums.py)
const val SYNC_PENDIENTE = "SYNC_PENDING"
const val SYNC_SINCRONIZANDO = "SYNCING"
const val SYNC_SINCRONIZADO = "SYNCED"
const val SYNC_ERROR = "SYNC_ERROR"
const val SYNC_CONFLICTO = "CONFLICT"

/**
 * Identificador del documento en el backend, o null si el documento todavía
 * existe solo en el dispositivo (nunca se ha sincronizado).
 *
 * Toda llamada a /api/documentos/<id>/... debe usar este valor. Usar [Documento.id]
 * (PK autoincremental de Room) produce 404 o, peor, opera sobre otro documento.
 */
val Documento.servidorIdRequerido: Long?
    get() = servidorId?.takeIf { it > 0L }

/** El documento ya existe en el servidor y admite acciones de flujo. */
val Documento.sincronizadoEnServidor: Boolean
    get() = servidorIdRequerido != null