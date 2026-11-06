package com.gn.gestion.models;

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("usuario") val usuario: String,
    @SerializedName("contrasena") val contrasena: String
)

data class UsuarioDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("nombre") val nombre: String = "",
    @SerializedName("nombre_completo") val nombreCompleto: String = "",
    @SerializedName("usuario") val usuario: String = "",
    @SerializedName("rol") val rol: String = "",
    @SerializedName("cargo") val cargo: String = "",
    @SerializedName("grado") val grado: String = "",
    @SerializedName("area") val area: String = "",
    @SerializedName("subdireccion") val subdireccion: String = "",
    @SerializedName("correo") val correo: String = "",
    @SerializedName("estado") val estado: String = "ACTIVO",
    @SerializedName("permisos") val permisos: List<String> = emptyList()
)

data class LoginResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("token") val token: String? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("usuario") val usuario: UsuarioDto? = null,
    @SerializedName("expira_en") val expiraEn: Long = 0
)

/** Respuesta de POST /api/auth/refresh: solo renueva el access token. */
data class RefreshResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("expira_en") val expiraEn: Long? = null
)

/** Representación de un documento tal como la sirve la API web (snake_case). */
data class DocumentoDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("folio") val folio: String = "",
    @SerializedName("tipo") val tipo: String = "MENSAJE",
    @SerializedName("asunto") val asunto: String = "",
    @SerializedName("remitente") val remitente: String = "",
    @SerializedName("destinatario") val destinatario: String = "",
    @SerializedName("id_remitente") val idRemitente: Long? = null,
    @SerializedName("id_destinatario") val idDestinatario: Long? = null,
    @SerializedName("dependencia") val dependencia: String = "",
    @SerializedName("area_origen") val areaOrigen: String = "",
    @SerializedName("area_destino") val areaDestino: String = "",
    @SerializedName("referencia") val referencia: String = "",
    @SerializedName("num_anexos") val numAnexos: Int = 0,
    @SerializedName("observaciones") val observaciones: String = "",
    @SerializedName("clasificacion") val clasificacion: String = "PUBLICO",
    @SerializedName("area_actual") val areaActual: String = "",
    @SerializedName("compartido") val compartido: Boolean = false,
    @SerializedName("respuesta_consolidada") val respuestaConsolidada: String = "",
    @SerializedName("fecha_documento") val fechaDocumento: String = "",
    @SerializedName("fecha_recepcion") val fechaRecepcion: Long = 0,
    @SerializedName("fecha_limite") val fechaLimite: String = "",
    @SerializedName("fecha_limite_hora") val fechaLimiteHora: String = "",
    @SerializedName("estado") val estado: String = "RECIBIDO",
    @SerializedName("urgencia") val urgencia: String = "ORDINARIO",
    @SerializedName("notas") val notas: String = "",
    @SerializedName("texto_ocr") val textoOcr: String = "",
    @SerializedName("pdf_nombre") val pdfNombre: String = "",
    @SerializedName("pdf_version") val pdfVersion: Int = 0,
    @SerializedName("hash_archivo") val hashArchivo: String = "",
    @SerializedName("codigo_qr") val codigoQr: String = "",
    @SerializedName("usuario_id") val usuarioId: Long = 0,
    @SerializedName("creado_en") val creadoEn: Long = 0,
    @SerializedName("actualizado_en") val actualizadoEn: Long = 0,
    @SerializedName("eliminado_en") val eliminadoEn: Long? = null
)

/** Movimiento tal como lo sirve la API (incluye el nombre resuelto del usuario). */
data class MovimientoDto(
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("documento_uuid") val documentoUuid: String = "",
    @SerializedName("accion") val accion: String = "",
    @SerializedName("estado_anterior") val estadoAnterior: String = "",
    @SerializedName("estado_nuevo") val estadoNuevo: String = "",
    @SerializedName("comentario") val comentario: String = "",
    @SerializedName("fecha_hora") val fechaHora: Long = 0,
    @SerializedName("dispositivo") val dispositivo: String = "",
    @SerializedName("area") val area: String = "",
    @SerializedName("usuario_nombre") val usuarioNombre: String = ""
)

data class EliminadoDto(
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("eliminado_en") val eliminadoEn: Long = 0
)

data class ServidorTjDto(
    @SerializedName("documentos") val documentos: List<DocumentoDto> = emptyList(),
    @SerializedName("eliminados") val eliminados: List<EliminadoDto> = emptyList(),
    @SerializedName("movimientos") val movimientos: List<MovimientoDto> = emptyList(),
    @SerializedName("serverTime") val serverTime: Long = 0
)

data class ClienteResultDto(
    @SerializedName("procesados") val procesados: Int = 0,
    @SerializedName("conflictos") val conflictos: List<Any> = emptyList(),
    @SerializedName("errores") val errores: List<Any> = emptyList()
)

data class SyncResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("cliente") val cliente: ClienteResultDto? = null,
    @SerializedName("servidor") val servidor: ServidorTjDto? = null,
    @SerializedName("serverTime") val serverTime: Long = 0
)

/** Asignación / compartición de un documento en el flujo institucional. */
data class AsignacionDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("documento_id") val documentoId: Long = 0,
    @SerializedName("area_destino") val areaDestino: String = "",
    @SerializedName("asignador_nombre") val asignadorNombre: String = "",
    @SerializedName("asignado_nombre") val asignadoNombre: String = "",
    @SerializedName("tipo") val tipo: String = "ASIGNACION",
    @SerializedName("nivel") val nivel: String = "DIRECCION",
    @SerializedName("estado_individual") val estadoIndividual: String = "NO_LEIDO",
    @SerializedName("rama") val rama: Int = 0,
    @SerializedName("comentario") val comentario: String = "",
    @SerializedName("fecha_asignacion") val fechaAsignacion: Long = 0,
    @SerializedName("creado_en") val creadoEn: Long = 0
)

/** Anotación / observación sobre un documento. */
data class AnotacionDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("usuario_nombre") val usuarioNombre: String = "",
    @SerializedName("area") val area: String = "",
    @SerializedName("comentario") val comentario: String = "",
    @SerializedName("tipo") val tipo: String = "OBSERVACION",
    @SerializedName("creado_en") val creadoEn: Long = 0
)

/** Respuesta oficial emitida por una área (rama 0, 1 o 2). */
data class RespuestaDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("usuario_nombre") val usuarioNombre: String = "",
    @SerializedName("area") val area: String = "",
    @SerializedName("rama") val rama: Int = 0,
    @SerializedName("texto") val texto: String = "",
    @SerializedName("observaciones") val observaciones: String = "",
    @SerializedName("pdf_nombre") val pdfNombre: String = "",
    @SerializedName("consolidado") val consolidado: Int = 0,
    @SerializedName("fecha_hora") val fechaHora: Long = 0
)

/** Expediente documental / DCAI. */
data class ExpedienteDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("numero") val numero: String = "",
    @SerializedName("descripcion") val descripcion: String = "",
    @SerializedName("clasificacion") val clasificacion: String = "CONFIDENCIAL",
    @SerializedName("area_responsable") val areaResponsable: String = "",
    @SerializedName("creado_en") val creadoEn: Long = 0
)

/** Respuesta de bandejas del servidor. */
data class BandejaDto(
    @SerializedName("clave") val clave: String = "",
    @SerializedName("titulo") val titulo: String = ""
)

/** Respuesta del panel de indicadores. */
data class PanelResumenDto(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("en_tramite") val enTramite: Int = 0,
    @SerializedName("pendientes_asignacion") val pendientesAsignacion: Int = 0,
    @SerializedName("asignados") val asignados: Int = 0,
    @SerializedName("en_validacion") val enValidacion: Int = 0,
    @SerializedName("validados") val validados: Int = 0,
    @SerializedName("archivados") val archivados: Int = 0,
    @SerializedName("mis_asignados") val misAsignados: Int = 0
)

/** Wrapper genérico para respuestas de éxito de acción de flujo. */
data class AccionFlujoResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("estado") val estado: String = "",
    @SerializedName("error") val error: String = ""
)

/** Notificación del centro de notificaciones del servidor. */
data class NotificacionDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("documento_id") val documentoId: Long? = null,
    @SerializedName("documento_uuid") val documentoUuid: String = "",
    @SerializedName("tipo") val tipo: String = "SISTEMA",
    @SerializedName("mensaje") val mensaje: String = "",
    @SerializedName("leida") val leida: Boolean = false,
    @SerializedName("creado_en") val creadoEn: Long = 0
)

/** Contador de notificaciones no leídas. */
data class ContadorNotificacionesDto(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("no_leidas") val noLeidas: Int = 0
)

/** Expediente de referencia para un documento del archivo. */
data class ExpReferenciaDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("numero") val numero: String = ""
)

/** Documento administrativo del archivo, con sus expedientes asociados. */
data class DocumentoArchivoDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("folio") val folio: String = "",
    @SerializedName("asunto") val asunto: String = "",
    @SerializedName("tipo") val tipo: String = "",
    @SerializedName("clasificacion") val clasificacion: String = "CONFIDENCIAL",
    @SerializedName("estado") val estado: String = "ARCHIVADO",
    @SerializedName("fecha_recepcion") val fechaRecepcion: Long = 0,
    @SerializedName("expedientes") val expedientes: List<ExpReferenciaDto> = emptyList()
)

/** Respuesta del archivo virtual (documentos archivados/ histórico y expedientes). */
data class ArchivoDto(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("documentos") val documentos: List<DocumentoArchivoDto> = emptyList(),
    @SerializedName("expedientes") val expedientes: List<ExpedienteDto> = emptyList(),
    @SerializedName("por_anio") val porAnio: Map<String, Int> = emptyMap(),
    @SerializedName("por_clasificacion") val porClasificacion: Map<String, Int> = emptyMap(),
    @SerializedName("por_estado") val porEstado: Map<String, Int> = emptyMap()
)

/** Resultado de la búsqueda global. */
data class BusquedaDto(
    @SerializedName("q") val q: String = "",
    @SerializedName("total_documentos") val totalDocumentos: Int = 0,
    @SerializedName("total_expedientes") val totalExpedientes: Int = 0,
    @SerializedName("documentos") val documentos: List<DocumentoDto> = emptyList(),
    @SerializedName("expedientes") val expedientes: List<ExpedienteDto> = emptyList()
)

/**
 * Nodo de la estructura organizacional tal como lo sirve
 * `GET /api/organigrama/<nivel>`.
 *
 * `id` es la clave genérica que usan los spinners de la web; `id_direccion`,
 * `id_subdireccion`… son las claves específicas del nivel.
 */
data class NivelOrgDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("nombre") val nombre: String = "",
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("estatus") val estatus: Boolean = true,
    @SerializedName("ruta_texto") val rutaTexto: String = "",
    @SerializedName("id_direccion_general") val idDireccionGeneral: Long? = null,
    @SerializedName("id_direccion") val idDireccion: Long? = null,
    @SerializedName("id_subdireccion") val idSubdireccion: Long? = null
)

/**
 * Fila del catálogo institucional `GET /api/personas`.
 *
 * Es la única fuente de Remitente/Destinatario: el documento guarda
 * `id_persona` y, como copia histórica, el texto del nombre.
 */
data class PersonaDto(
    @SerializedName("id_persona") val idPersona: Long? = null,
    @SerializedName("id") val id: Long? = null,
    @SerializedName("id_usuario") val idUsuario: Long? = null,
    @SerializedName("nombre_completo") val nombreCompleto: String = "",
    @SerializedName("cargo") val cargo: String = "",
    @SerializedName("estatus") val estatus: Boolean = true,
    @SerializedName("ruta_texto") val rutaTexto: String = "",
    @SerializedName("id_direccion_general") val idDireccionGeneral: Long? = null,
    @SerializedName("id_direccion") val idDireccion: Long? = null,
    @SerializedName("id_subdireccion") val idSubdireccion: Long? = null,
    @SerializedName("id_departamento") val idDepartamento: Long? = null
) {
    /** Clave estable del catálogo (el servidor siempre envía una de las dos). */
    val clave: Long?
        get() = idPersona ?: id
}

/** Opción concreta de turno (Dirección/Subdirección/Departamento/Usuario). */
data class TurnadoOpcionDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("nombre") val nombre: String = "",
    @SerializedName("tipo") val tipo: String = ""
)

/**
 * Respuesta de `GET /api/turnado/opciones`: solo se puede turnar al nivel
 * INMEDIATAMENTE INFERIOR de la ubicación del usuario (sin saltos).
 */
data class TurnadoOpcionesDto(
    @SerializedName("nivel_actual") val nivelActual: String = "",
    @SerializedName("etiqueta_actual") val etiquetaActual: String = "",
    @SerializedName("nivel_siguiente") val nivelSiguiente: String = "",
    @SerializedName("etiqueta_siguiente") val etiquetaSiguiente: String = "",
    @SerializedName("opciones") val opciones: List<TurnadoOpcionDto> = emptyList()
)

/** Documento relacionado (Vincular). `otroDocumentoId` es el documento enlazado. */
data class RelacionDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("documento_id") val documentoId: Long = 0,
    @SerializedName("documento_relacionado_id") val documentoRelacionadoId: Long = 0,
    @SerializedName("otro_documento_id") val otroDocumentoId: Long = 0,
    @SerializedName("documento_referencia") val documentoReferencia: String = "",
    @SerializedName("tipo_relacion") val tipoRelacion: String = "RELACIONADO_CON",
    @SerializedName("usuario_id") val usuarioId: Long = 0,
    @SerializedName("usuario_nombre") val usuarioNombre: String = "",
    @SerializedName("comentario") val comentario: String = "",
    @SerializedName("creado_en") val creadoEn: Long = 0
)
