package com.gn.gestion.services

import android.content.Context
import com.gn.gestion.models.*
import com.google.gson.JsonObject

/** Invoca las acciones del flujo documental en el servidor. */
class FlujoRepository(private val api: ApiService) {

    private fun body(vararg pares: Pair<String, Any?>): JsonObject =
        JsonObject().apply {
            for ((k, v) in pares) {
                when (v) {
                    null -> {}
                    is Int -> addProperty(k, v)
                    is Long -> addProperty(k, v)
                    is Boolean -> addProperty(k, v)
                    else -> addProperty(k, v.toString())
                }
            }
        }

    private fun resultado(r: AccionFlujoResponse): AccionFlujoResponse =
        r.also {
            if (!it.ok && it.error.isNotBlank())
                throw IllegalStateException(it.error)
        }

    /**
     * Entrega el documento a un área o a un usuario concreto.
     *
     * `usuarioDestino` es opcional: si se envía, el servidor lo resuelve por ID o por
     * nombre de usuario, toma su área si falta y deja el documento asignado a esa
     * persona. Sin él se mantiene la asignación solo por área.
     */
    suspend fun asignar(
        docId: Long,
        nivel: String,
        areaDestino: String,
        usuarioDestino: String? = null,
        comentario: String = ""
    ): AccionFlujoResponse =
        resultado(
            api.asignar(
                docId,
                body(
                    "nivel" to nivel,
                    "area_destino" to areaDestino,
                    "usuario_destino_id" to usuarioDestino?.takeIf { it.isNotBlank() },
                    "comentario" to comentario
                )
            )
        )

    /**
     * Usuarios activos que pueden recibir un documento. Si el rol no tiene
     * `USUARIOS_CONSULTAR`, el servidor responde 403 y se devuelve una lista vacía:
     * el llamador degrada a la asignación por área.
     */
    suspend fun usuariosDestino(): List<UsuarioDto> =
        runCatching { api.listarUsuarios().filter { it.estado.equals("ACTIVO", true) } }
            .getOrDefault(emptyList())

    suspend fun compartir(docId: Long, areaA: String, areaB: String, comentario: String = ""): AccionFlujoResponse =
        resultado(api.compartir(docId, body("area_a" to areaA, "area_b" to areaB, "comentario" to comentario)))

    suspend fun atender(docId: Long): AccionFlujoResponse = resultado(api.atender(docId))

    suspend fun responder(docId: Long, texto: String, observaciones: String = "", rama: Int = 0): AccionFlujoResponse =
        resultado(api.responder(docId, body("texto" to texto, "observaciones" to observaciones, "rama" to rama)))

    suspend fun consolidar(docId: Long, texto: String, comentario: String = ""): AccionFlujoResponse =
        resultado(api.consolidar(docId, body("texto" to texto, "comentario" to comentario)))

    suspend fun revisar(docId: Long, aceptar: Boolean = true, comentario: String = ""): AccionFlujoResponse =
        resultado(api.revisar(docId, body("aceptar" to aceptar, "comentario" to comentario)))

    suspend fun validar(docId: Long, aceptar: Boolean = true, comentario: String = ""): AccionFlujoResponse =
        resultado(api.validar(docId, body("aceptar" to aceptar, "comentario" to comentario)))

    suspend fun archivar(docId: Long, folioFinal: String = "", comentario: String = ""): AccionFlujoResponse =
        resultado(api.archivar(docId, body("folio_final" to folioFinal, "comentario" to comentario)))

    suspend fun historico(docId: Long): AccionFlujoResponse = resultado(api.historico(docId))

    suspend fun recibir(docId: Long): AccionFlujoResponse = resultado(api.recibir(docId))

    suspend fun anotar(docId: Long, comentario: String, tipo: String = "OBSERVACION"): AnotacionDto? {
        val resp = api.crearAnotacion(docId, body("comentario" to comentario, "tipo" to tipo))
        return if (resp.isSuccessful) resp.body() else null
    }

    suspend fun eliminarAnotacion(docId: Long, anotacionId: Long): Boolean =
        api.eliminarAnotacion(docId, anotacionId).isSuccessful

    suspend fun asignaciones(docId: Long): List<AsignacionDto> = api.asignaciones(docId)

    suspend fun respuestas(docId: Long): List<RespuestaDto> = api.respuestas(docId)

    suspend fun anotaciones(docId: Long): List<AnotacionDto> = api.anotaciones(docId)

    // --------------- Turnado jerárquico / acuse / vinculación ---------------

    /** Opciones de turno: solo el nivel inmediatamente inferior del usuario. */
    suspend fun turnadoOpciones(): TurnadoOpcionesDto = api.turnadoOpciones()

    /**
     * Turna el documento al nivel inmediatamente inferior. `opcionTipo` es
     * `direccion`/`subdireccion`/`departamento`/`usuario` y `opcionId` la clave
     * de la estructura organizacional (el servidor valida el salto).
     */
    suspend fun turnar(
        docId: Long,
        opcionTipo: String,
        opcionId: Long,
        usuarioDestinoId: Long? = null,
        comentario: String = ""
    ): AccionFlujoResponse =
        resultado(
            api.asignar(
                docId,
                body(
                    "opcion_tipo" to opcionTipo,
                    "opcion_id" to opcionId,
                    "usuario_destino_id" to usuarioDestinoId,
                    "comentario" to comentario
                )
            )
        )

    /** Marca el documento como leído por el usuario actual (o su acuse de recibo). */
    suspend fun leer(
        docId: Long,
        acuse: Boolean = false,
        comentario: String = ""
    ): AccionFlujoResponse =
        resultado(api.leer(docId, body("acuse" to acuse, "comentario" to comentario)))

    suspend fun relaciones(docId: Long): List<RelacionDto> = api.relaciones(docId)

    suspend fun vincularRelacion(
        docId: Long,
        otroDocumentoId: Long,
        tipoRelacion: String = "RELACIONADO_CON",
        comentario: String = ""
    ): Boolean =
        api.vincularRelacion(
            docId,
            body(
                "documento_relacionado_id" to otroDocumentoId,
                "tipo_relacion" to tipoRelacion,
                "comentario" to comentario
            )
        ).isSuccessful

    suspend fun desvincularRelacion(docId: Long, relId: Long): Boolean =
        api.desvincularRelacion(docId, relId).isSuccessful

    companion object {
        @Volatile
        private var INSTANCE: FlujoRepository? = null

        fun get(context: Context): FlujoRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FlujoRepository(ApiFactory.api(context)).also { INSTANCE = it }
            }
    }
}