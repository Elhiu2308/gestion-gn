package com.gn.gestion.models;

// Modelo de datos de la aplicación

enum class TipoDocumento(val label: String, val icon: String) {
    MENSAJE("Mensaje C.E.I.", "✉"),
    OFICIO("Oficio", "📄"),
    SWEAR("Mensaje S.W.E.A.R.", "⚡"),
    MEMORANDUM("Memorándum", "📝"),
    CIRCULAR("Circular", "🔄"),
    INFORME("Informe", "📊"),
    TARJETA_INFORMATIVA("Tarjeta Informativa", "🗞"),
    OTRO("Otro", "🗂")
}

object Clasificacion {
    const val PUBLICO = "PUBLICO"
    const val CONFIDENCIAL = "CONFIDENCIAL"
    const val RESERVADO = "RESERVADO"
    val ETIQUETAS = mapOf(PUBLICO to "Público", CONFIDENCIAL to "Confidencial", RESERVADO to "Reservado")
}

// Estados del ciclo de vida documental (idénticos a web/app/utils/enums.py)
enum class EstadoDocumento(val label: String) {
    // Flujo institucional FGN (12 estados canónicos)
    RECIBIDO("Recibido"),
    PENDIENTE_TURNO("Pendiente de turno"),
    TURNADO("Turnado"),
    EN_DISTRIBUCION("En distribución"),
    EN_ATENCION("En atención"),
    ATENCION_PARCIAL("Atención parcial"),
    RESPUESTAS_COMPLETAS("Respuestas completas"),
    CONSOLIDADO("Consolidado"),
    EN_REVISION("En revisión"),
    EN_VALIDACION("En validación"),
    VALIDADO("Validado"),
    CONCLUIDO("Concluido"),
    // Resolución / cierre
    RECHAZADO("Rechazado"),
    HISTORICO("Histórico"),
    CANCELADO("Cancelado"),
    ARCHIVADO("Archivado"),
    // Legado
    PENDIENTE_ASIGNACION("Pendiente de asignación"),
    DESIGNADO("Designado"),
    ASIGNADO("Asignado"),
    RESPONDIDO("Respondido"),
    CONSOLIDANDO("Consolidando"),
    REGISTRADO("Registrado"),
    EN_PROCESO("En proceso"),
    PENDIENTE("Pendiente"),
    CONTESTADO("Contestado"),
    DEVUELTO("Devuelto"),
    FINALIZADO("Finalizado");
    companion object {
        val TERMINALES = setOf("CANCELADO", "ARCHIVADO", "HISTORICO", "CONCLUIDO")

        // Mapea etiquetas/estados legado a su equivalente canónico.
        val LEGADO_A_CANONICO = mapOf(
            "PENDIENTE_ASIGNACION" to "PENDIENTE_TURNO",
            "DESIGNADO" to "TURNADO",
            "ASIGNADO" to "EN_DISTRIBUCION",
            "RESPONDIDO" to "RESPUESTAS_COMPLETAS",
            "CONSOLIDANDO" to "CONSOLIDADO",
            "EN_PROCESO" to "EN_ATENCION",
            "PENDIENTE" to "PENDIENTE_TURNO",
            "CONTESTADO" to "RESPUESTAS_COMPLETAS",
            "DEVUELTO" to "EN_ATENCION",
            "REGISTRADO" to "RECIBIDO",
            "FINALIZADO" to "CONCLUIDO"
        )
    }
}

// Estados individuales de una asignación (web/app/utils/enums.py)
object EstadoIndividual {
    const val NO_LEIDO = "NO_LEIDO"
    const val LEIDO = "LEIDO"
    const val ACUSE_RECIBIDO = "ACUSE_RECIBIDO"
    const val EN_ATENCION = "EN_ATENCION"
    const val ATENDIDO = "ATENDIDO"
    const val RESPONDIDO = "RESPONDIDO"
    const val CONCLUIDO = "CONCLUIDO"
    val ETIQUETAS = mapOf(
        NO_LEIDO to "No leído",
        LEIDO to "Leído",
        ACUSE_RECIBIDO to "Acuse de recibo",
        EN_ATENCION to "En atención",
        ATENDIDO to "Atendido",
        RESPONDIDO to "Respondido",
        CONCLUIDO to "Concluido"
    )
    fun etiqueta(valor: String?): String = ETIQUETAS[valor] ?: (valor ?: "")
}

enum class Urgencia(val label: String) {
    ORDINARIO("Ordinario"),
    URGENTE("Urgente"),
    EXTRAURGENTE("Extraurgente");
    companion object {
        // Mapea urgencias legado a su equivalente canónico.
        val LEGADO_A_CANONICO = mapOf(
            "NORMAL" to ORDINARIO,
            "MUY_URGENTE" to EXTRAURGENTE,
            "URGENTE_MAXIMA" to EXTRAURGENTE
        )
    }
}

object NivelAsignacion {
    const val TITULAR = "TITULAR"
    const val DIRECCION = "DIRECCION"
    const val SUBDIRECCION = "SUBDIRECCION"
    const val DEPARTAMENTO = "DEPARTAMENTO"
    const val USUARIO = "USUARIO"
    const val RESPONSABLE = "RESPONSABLE"
    val ETIQUETAS = mapOf(
        TITULAR to "Titular/Permanencia",
        DIRECCION to "Dirección",
        SUBDIRECCION to "Subdirección",
        DEPARTAMENTO to "Departamento",
        USUARIO to "Usuario",
        RESPONSABLE to "Responsable"
    )
}

// Roles RBAC (el servidor es la autoridad; la app solo oculta opciones con esto)
enum class RolSistema(val label: String) {
    ADMINISTRADOR("Administrador"),
    CAPTURISTA("Capturista (Archivo)"),
    SUPERVISOR("Supervisor (Titular/Permanencia)"),
    RESPONSABLE("Responsable (Dirección/Staff)"),
    OPERADOR("Operador (Subdirección)"),
    CONSULTA("Consulta")
}

// Códigos de permiso RBAC compartidos con web/app/utils/enums.py
object Permisos {
    const val DOCUMENTO_CONSULTAR = "DOCUMENTO_CONSULTAR"
    const val DOCUMENTO_CREAR = "DOCUMENTO_CREAR"
    const val DOCUMENTO_EDITAR = "DOCUMENTO_EDITAR"
    const val DOCUMENTO_ELIMINAR = "DOCUMENTO_ELIMINAR"
    const val DOCUMENTO_ARCHIVAR = "DOCUMENTO_ARCHIVAR"
    const val DOCUMENTO_TURNAR = "DOCUMENTO_TURNAR"
    const val DOCUMENTO_CAMBIAR_ESTADO = "DOCUMENTO_CAMBIAR_ESTADO"
    const val DOCUMENTO_DESCARGAR_PDF = "DOCUMENTO_DESCARGAR_PDF"
    const val DOCUMENTO_SUBIR_PDF = "DOCUMENTO_SUBIR_PDF"
    const val DOCUMENTO_ESCANEAR = "DOCUMENTO_ESCANEAR"
    const val DOCUMENTO_OCR = "DOCUMENTO_OCR"
    const val DOCUMENTO_ASIGNAR = "DOCUMENTO_ASIGNAR"
    const val DOCUMENTO_COMPARTIR = "DOCUMENTO_COMPARTIR"
    const val DOCUMENTO_CONSOLIDAR = "DOCUMENTO_CONSOLIDAR"
    const val DOCUMENTO_RESPONDER = "DOCUMENTO_RESPONDER"
    const val DOCUMENTO_REVISAR = "DOCUMENTO_REVISAR"
    const val DOCUMENTO_VALIDAR = "DOCUMENTO_VALIDAR"
    const val DOCUMENTO_RECHAZAR = "DOCUMENTO_RECHAZAR"
    const val DOCUMENTO_ANOTAR = "DOCUMENTO_ANOTAR"
    const val TRAZABILIDAD_CONSULTAR = "TRAZABILIDAD_CONSULTAR"
    const val REPORTES_CONSULTAR = "REPORTES_CONSULTAR"
    const val EXPEDIENTES_CONSULTAR = "EXPEDIENTES_CONSULTAR"
    const val EXPEDIENTES_ADMINISTRAR = "EXPEDIENTES_ADMINISTRAR"
    const val USUARIOS_CONSULTAR = "USUARIOS_CONSULTAR"
    const val AUDITORIA_CONSULTAR = "AUDITORIA_CONSULTAR"
}