package com.gn.gestion.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gn.gestion.util.Hora

@Entity(tableName = "documentos")
data class Documento(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(defaultValue = "''")
    val uuid: String,
    val tipo: String,
    val folio: String = "",
    val asunto: String = "",
    val destinatario: String = "",
    val remitente: String = "",

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
    val fecha: Long,
    @ColumnInfo(defaultValue = "''")
    val fechaDocumento: String = "",
    @ColumnInfo(defaultValue = "''")
    val fechaLimite: String = "",
    @ColumnInfo(defaultValue = "''")
    val fechaLimiteHora: String = "",
    @ColumnInfo(defaultValue = "0")
    val fechaRecepcion: Long = 0,
    val fechaRegistro: Long = System.currentTimeMillis(),
    val estado: String,
    val urgencia: String,
    val notas: String = "",
    @ColumnInfo(defaultValue = "''")
    val textoOcr: String = "",
    val pdfPath: String? = null,
    @ColumnInfo(defaultValue = "''")
    val pdfNombre: String = "",
    @ColumnInfo(defaultValue = "0")
    val pdfVersion: Int = 0,
    @ColumnInfo(defaultValue = "''")
    val hashArchivo: String = "",
    @ColumnInfo(defaultValue = "''")
    val codigoQr: String = "",

    @ColumnInfo(name = "servidor_id")
    val servidorId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val usuarioServidorId: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val creadorServidorId: Long = 0,

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
        fechaLimiteHora = Hora.a12(fechaLimiteHora ?: ""),
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

const val SYNC_PENDIENTE = "SYNC_PENDING"
const val SYNC_SINCRONIZANDO = "SYNCING"
const val SYNC_SINCRONIZADO = "SYNCED"
const val SYNC_ERROR = "SYNC_ERROR"
const val SYNC_CONFLICTO = "CONFLICT"

val Documento.servidorIdRequerido: Long?
    get() = servidorId?.takeIf { it > 0L }

val Documento.sincronizadoEnServidor: Boolean
    get() = servidorIdRequerido != null