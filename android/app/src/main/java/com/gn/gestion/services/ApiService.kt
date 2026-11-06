package com.gn.gestion.services

import com.gn.gestion.models.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Query
import com.google.gson.JsonObject
import retrofit2.http.Part
import retrofit2.http.Multipart
import retrofit2.http.Path
import okhttp3.MultipartBody

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("api/auth/me")
    suspend fun me(): UsuarioDto

    @POST("api/auth/refresh")
    suspend fun refresh(@Header("Authorization") authorization: String): RefreshResponse

    @POST("api/logout")
    suspend fun logout()

    @POST("api/sync")
    suspend fun sync(@Body body: JsonObject): SyncResponse

    @Multipart
    @POST("api/documentos/{servernid}/pdf")
    suspend fun subirPdf(
        @Path("servernid") serverId: Long,
        @Part archivo: MultipartBody.Part
    ): retrofit2.Response<JsonObject>

    @GET("api/documentos/{servernid}")
    suspend fun detalle(@Path("servernid") serverId: Long): JsonObject

    @GET("api/documentos")
    suspend fun listarDocumentos(
        @Query("bandeja") bandeja: String? = null,
        @Query("q") q: String? = null,
        @Query("tipo") tipo: String? = null,
        @Query("estado") estado: String? = null,
        @Query("urgencia") urgencia: String? = null,
        @Query("destinatario") destinatario: String? = null,
        @Query("folio") folio: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null
    ): List<DocumentoDto>

    @GET("api/usuarios")
    suspend fun listarUsuarios(): List<UsuarioDto>

    /** QR de firma institucional emitido para el usuario autenticado. */
    @GET("api/usuarios/{id}/qr")
    suspend fun qrUsuario(@Path("id") id: Long): JsonObject

    // --------------- Estructura organizacional / catálogo de personas ---------------
    // El selector Remitente/Destinatario es jerárquico (Área → Subdirección →
    // Persona) y el filtrado SIEMPRE ocurre en el servidor: cada nivel consulta
    // su propio endpoint y jamás se descarga la lista completa para filtrarla
    // en el dispositivo.

    /**
     * Nivel de la estructura (`direcciones-generales`, `direcciones`,
     * `subdirecciones`, `departamentos`).
     *
     * Requiere el permiso `ver_organigrama`.
     */
    @GET("api/organigrama/{nivel}")
    suspend fun organigrama(
        @Path("nivel") nivel: String,
        @Query("direccion_general") direccionGeneral: Long? = null,
        @Query("direccion") direccion: Long? = null,
        @Query("subdireccion") subdireccion: Long? = null,
        @Query("solo_padre") soloPadre: String? = null,
        @Query("incluir_inactivos") incluirInactivos: String? = null
    ): List<NivelOrgDto>

    /** Catálogo de personas filtrado en base de datos por su ubicación. */
    @GET("api/personas")
    suspend fun listarPersonas(
        @Query("q") q: String? = null,
        @Query("direccion") direccion: Long? = null,
        @Query("subdireccion") subdireccion: Long? = null,
        @Query("limite") limite: Int = 500
    ): List<PersonaDto>

    /** Detalle de una persona (devuelve también inactivas: no filtra estatus). */
    @GET("api/personas/{personaId}")
    suspend fun persona(@Path("personaId") personaId: Long): PersonaDto

    @GET("api/panel/resumen")
    suspend fun panelResumen(): PanelResumenDto

    @GET("api/bandejas")
    suspend fun bandejas(): List<BandejaDto>

    @POST("api/documentos/{docId}/recibir")
    suspend fun recibir(@Path("docId") docId: Long): AccionFlujoResponse

    @POST("api/documentos/{docId}/asignar")
    suspend fun asignar(
        @Path("docId") docId: Long,
        @Body body: JsonObject
    ): AccionFlujoResponse

    @POST("api/documentos/{docId}/compartir")
    suspend fun compartir(
        @Path("docId") docId: Long,
        @Body body: JsonObject
    ): AccionFlujoResponse

    @POST("api/documentos/{docId}/atender")
    suspend fun atender(@Path("docId") docId: Long): AccionFlujoResponse

    @POST("api/documentos/{docId}/responder")
    suspend fun responder(
        @Path("docId") docId: Long,
        @Body body: JsonObject
    ): AccionFlujoResponse

    @POST("api/documentos/{docId}/consolidar")
    suspend fun consolidar(
        @Path("docId") docId: Long,
        @Body body: JsonObject
    ): AccionFlujoResponse

    @POST("api/documentos/{docId}/revisar")
    suspend fun revisar(
        @Path("docId") docId: Long,
        @Body body: JsonObject
    ): AccionFlujoResponse

    @POST("api/documentos/{docId}/validar")
    suspend fun validar(
        @Path("docId") docId: Long,
        @Body body: JsonObject
    ): AccionFlujoResponse

    @POST("api/documentos/{docId}/archivar")
    suspend fun archivar(
        @Path("docId") docId: Long,
        @Body body: JsonObject
    ): AccionFlujoResponse

    @POST("api/documentos/{docId}/historico")
    suspend fun historico(@Path("docId") docId: Long): AccionFlujoResponse

    @GET("api/documentos/{docId}/asignaciones")
    suspend fun asignaciones(@Path("docId") docId: Long): List<AsignacionDto>

    @GET("api/documentos/{docId}/anotaciones")
    suspend fun anotaciones(@Path("docId") docId: Long): List<AnotacionDto>

    @POST("api/documentos/{docId}/anotaciones")
    suspend fun crearAnotacion(
        @Path("docId") docId: Long,
        @Body body: JsonObject
    ): retrofit2.Response<AnotacionDto>

    @DELETE("api/documentos/{docId}/anotaciones/{anotacionId}")
    suspend fun eliminarAnotacion(
        @Path("docId") docId: Long,
        @Path("anotacionId") anotacionId: Long
    ): retrofit2.Response<JsonObject>

    @GET("api/documentos/{docId}/respuestas")
    suspend fun respuestas(@Path("docId") docId: Long): List<RespuestaDto>

    // --------------- Turnado jerárquico / acuse / vinculación ---------------

    /** Opciones de turno: solo el nivel inmediatamente inferior del usuario. */
    @GET("api/turnado/opciones")
    suspend fun turnadoOpciones(): TurnadoOpcionesDto

    /** Marca un documento como leído/acusado por el usuario actual. */
    @POST("api/documentos/{docId}/leer")
    suspend fun leer(
        @Path("docId") docId: Long,
        @Body body: JsonObject
    ): AccionFlujoResponse

    @GET("api/documentos/{docId}/historial")
    suspend fun historial(@Path("docId") docId: Long): List<MovimientoDto>

    @GET("api/documentos/{docId}/relaciones")
    suspend fun relaciones(@Path("docId") docId: Long): List<RelacionDto>

    @POST("api/documentos/{docId}/relaciones")
    suspend fun vincularRelacion(
        @Path("docId") docId: Long,
        @Body body: JsonObject
    ): retrofit2.Response<JsonObject>

    @DELETE("api/documentos/{docId}/relaciones/{relId}")
    suspend fun desvincularRelacion(
        @Path("docId") docId: Long,
        @Path("relId") relId: Long
    ): retrofit2.Response<JsonObject>

    @GET("api/expedientes")
    suspend fun listarExpedientes(): List<ExpedienteDto>

    @POST("api/expedientes")
    suspend fun crearExpediente(@Body body: JsonObject): retrofit2.Response<ExpedienteDto>

    @GET("api/expedientes/{expId}")
    suspend fun detalleExpediente(@Path("expId") expId: Long): JsonObject

    @DELETE("api/expedientes/{expId}")
    suspend fun eliminarExpediente(@Path("expId") expId: Long): retrofit2.Response<JsonObject>

    @POST("api/expedientes/{expId}/documentos")
    suspend fun vincularDocumentos(
        @Path("expId") expId: Long,
        @Body body: JsonObject
    ): retrofit2.Response<JsonObject>

    @DELETE("api/expedientes/{expId}/documentos/{docId}")
    suspend fun desvincularDocumento(
        @Path("expId") expId: Long,
        @Path("docId") docId: Long
    ): retrofit2.Response<JsonObject>

    // --------------- Notificaciones / FCM ---------------

    @GET("api/notificaciones")
    suspend fun listarNotificaciones(
        @Query("limite") limite: Int = 50,
        @Query("no_leidas") noLeidas: String? = null
    ): List<NotificacionDto>

    @GET("api/notificaciones/contador")
    suspend fun contadorNotificaciones(): ContadorNotificacionesDto

    @POST("api/notificaciones/leer")
    suspend fun marcarLeidas(@Body body: JsonObject): retrofit2.Response<JsonObject>

    @POST("api/fcm/token")
    suspend fun registrarFcmToken(@Body body: JsonObject): retrofit2.Response<JsonObject>

    @HTTP(method = "DELETE", path = "api/fcm/token", hasBody = true)
    suspend fun eliminarFcmToken(@Body body: JsonObject): retrofit2.Response<JsonObject>

    // --------------- Archivo virtual / Búsqueda global ---------------

    @GET("api/archivo")
    suspend fun archivoVirtual(
        @Query("anio") anio: String? = null,
        @Query("clasificacion") clasificacion: String? = null,
        @Query("tipo") tipo: String? = null,
        @Query("q") q: String? = null
    ): ArchivoDto

    /**
     * Búsqueda de documentos con filtros combinables.
     *
     * Todos los parámetros son opcionales: con `q` basta para la búsqueda rápida y el
     * resto permite acotar por campos concretos. El servidor ignora los vacíos.
     */
    @GET("api/buscar")
    suspend fun buscarGlobal(
        @Query("q") q: String? = null,
        @Query("folio") folio: String? = null,
        @Query("asunto") asunto: String? = null,
        @Query("descripcion") descripcion: String? = null,
        @Query("destinatario") destinatario: String? = null,
        @Query("remitente") remitente: String? = null,
        @Query("area_origen") areaOrigen: String? = null,
        @Query("area_destino") areaDestino: String? = null,
        @Query("tipo") tipo: String? = null,
        @Query("estado") estado: String? = null,
        @Query("urgencia") urgencia: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("limite") limite: Int = 50
    ): BusquedaDto
}