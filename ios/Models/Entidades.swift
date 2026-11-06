import Foundation

/**
 * Entidades y DTOs compartidos entre la aplicación iOS y el backend.

 Todas las propiedades usan los nombres en *snake_case* tal como llegan por
 la serialización JSON del backend (la serialización se hace manualmente, sin
 codable automático, para respetar esos nombres).
 */

// ---------------------------------------------------------------------------
// Autenticación
// ---------------------------------------------------------------------------

/// Respuesta de `POST /api/auth/login` y de `/api/auth/refresh`.
struct TokenSesion {
    let accessToken: String
    let refreshToken: String
    let usuario: String
    let nombre: String
    let nombreCompleto: String
    let rol: String
    let cargo: String
    let grado: String
    let area: String
    let subdireccion: String
    let correo: String
    let permisos: Set<String>
}

/// Datos mínimos que se cargan del backend al sincronizar el perfil.
struct DatosPerfil {
    let usuario: String
    let nombre: String
    let nombreCompleto: String
    let rol: String
    let cargo: String
    let grado: String
    let area: String
    let subdireccion: String
    let correo: String
    let permisos: Set<String>
}

// ---------------------------------------------------------------------------
// Documentos y flujo
// ---------------------------------------------------------------------------

enum TipoDocumento: String, CaseIterable, Identifiable {
    case mensaje = "MENSAJE"
    case oficio = "OFICIO"
    case swear = "SWEAR"
    case memorando = "MEMORANDUM"
    case circular = "CIRCULAR"
    case informe = "INFORME"
    case tarjetaInformativa = "TARJETA_INFORMATIVA"
    case otro = "OTRO"

    var id: String { rawValue }
    var etiqueta: String {
        switch self {
        case .mensaje: return "Mensaje C.E.I."
        case .oficio: return "Oficio"
        case .swear: return "Mensaje S.W.E.A.R."
        case .memorando: return "Memorándum"
        case .circular: return "Circular"
        case .informe: return "Informe"
        case .tarjetaInformativa: return "Tarjeta Informativa"
        case .otro: return "Otro"
        }
    }
}

/// Solicitud de creación de documento (`POST /api/documentos`).
///
/// Los nombres de las claves son exactamente los que espera el backend
/// (ver `web/app/static/js/ficha.js` → `Ficha.guardar`).
struct DatosDocumento {
    var tipo: TipoDocumento = .oficio
    var folio: String = ""
    var asunto: String = ""
    /// Destinatario (texto) e identificador del catálogo de personas.
    var destinatario: String = ""
    var idDestinatario: Int? = nil
    var referencia: String = ""
    var numAnexos: Int = 0
    var observaciones: String = ""
    /// Fecha del documento (`YYYY-MM-DD`).
    var fechaDocumento: String = ""
    /// Fecha de recepción (epoch en milisegundos).
    var fechaRecepcion: Double? = nil
    /// Fecha y hora límite de atención.
    var fechaLimite: String = ""
    var fechaLimiteHora: String = ""
    var urgencia: UrgenciaDocumento = .ordinario
    var clasificacion: ClasificacionDocumento = .publico
    var notas: String = ""
    var textoOCR: String = ""
    /// Área de origen y área destinataria (texto legible).
    var areaOrigen: String = ""
    var areaDestino: String = ""
    /// Ids del organigrama para reconstruir la cascada.
    var orgDgOrigenId: Int? = nil
    var orgDirOrigenId: Int? = nil
    var orgSubOrigenId: Int? = nil
    var orgDeptOrigenId: Int? = nil
    var orgDgDestinoId: Int? = nil
    var orgDirDestinoId: Int? = nil
    var orgSubDestinoId: Int? = nil
    var orgDeptDestinoId: Int? = nil
}

/// Cambios que se envían al actualizar un documento (PUT /api/documentos/{id}).
struct CambiosDocumento {
    var estado: String?
    var areaActual: String?
    var usuarioAsignado: String?
    var comentario: String?
}

/// Respuesta de acciones sobre el flujo documental.
struct AccionFlujo {
    var ok: Bool = false
    var estado: String = ""
    var error: String = ""
    var usuarioAsignado: String?
    var documentoUuid: String?
}

/// Respuesta de compartir un documento en dos ramas.
struct CompartirRespuesta {
    let ok: Bool
    let areaA: String
    let areaB: String
    let error: String = ""
}

// ---------------------------------------------------------------------------
// Expedientes
// ---------------------------------------------------------------------------

struct Expediente: Identifiable {
    let id: Int
    var uuid: String
    var codigo: String
    var titulo: String
    var descripcion: String
    var estado: String
    var areaResponsable: String = ""
    var clasificacion: String = ""
    var creadoEn: Date? = nil
    var documentos: [Documento] = []

    /// Número oficial del expediente (`numero` en el backend).
    var numero: String { codigo }
}

struct DatosExpediente {
    var numero: String
    var descripcion: String
    var areaResponsable: String = ""
    var clasificacion: String = "CONFIDENCIAL"
}

// ---------------------------------------------------------------------------
// Archivo (histórico)
// ---------------------------------------------------------------------------

struct DocumentoArchivo: Identifiable {
    let id: Int
    var uuid: String
    var folio: String
    var asunto: String
    var estado: String
    var areaActual: String
    var usuarioAsignado: String
    var archivo: String
    var creadoEn: Date
    var tipo: String = ""
    var clasificacion: String = ""
    var fechaLimite: String = ""
    var diasRestantes: Int? = nil
    /// El documento fue turnado/asignado al usuario autenticado.
    var asignadoAMi: Bool = false
    /// El documento fue creado por el usuario autenticado.
    var creadoPorMi: Bool = false
    /// El documento proviene del archivo histórico (`/api/archivo`).
    var archivado: Bool = false

    init(
        id: Int,
        uuid: String,
        folio: String,
        asunto: String,
        estado: String,
        areaActual: String,
        usuarioAsignado: String,
        archivo: String,
        creadoEn: Date,
        tipo: String = "",
        clasificacion: String = "",
        fechaLimite: String = "",
        diasRestantes: Int? = nil,
        asignadoAMi: Bool = false,
        creadoPorMi: Bool = false,
        archivado: Bool = false
    ) {
        self.id = id
        self.uuid = uuid
        self.folio = folio
        self.asunto = asunto
        self.estado = estado
        self.areaActual = areaActual
        self.usuarioAsignado = usuarioAsignado
        self.archivo = archivo
        self.creadoEn = creadoEn
        self.tipo = tipo
        self.clasificacion = clasificacion
        self.fechaLimite = fechaLimite
        self.diasRestantes = diasRestantes
        self.asignadoAMi = asignadoAMi
        self.creadoPorMi = creadoPorMi
        self.archivado = archivado
    }

    init(documento: Documento, asignadoAMi: Bool = false, creadoPorMi: Bool = false, archivado: Bool = false) {
        self.init(
            id: documento.id,
            uuid: documento.uuid,
            folio: documento.folio,
            asunto: documento.asunto,
            estado: documento.estado.rawValue,
            areaActual: documento.areaActual.isEmpty ? documento.areaDestino : documento.areaActual,
            usuarioAsignado: documento.usuarioAsignado,
            archivo: documento.pdfNombre,
            creadoEn: documento.creadoEn ?? documento.fechaRecepcion ?? .distantPast,
            tipo: documento.tipo.rawValue,
            clasificacion: documento.clasificacion.rawValue,
            fechaLimite: documento.fechaLimite.isEmpty
                ? documento.vencimiento
                : documento.fechaLimite,
            diasRestantes: documento.diasRestantes,
            asignadoAMi: asignadoAMi,
            creadoPorMi: creadoPorMi,
            archivado: archivado
        )
    }

    /// Días restantes calculados a partir de la fecha límite cuando el backend
    /// no envía `dias_restantes`.
    var diasParaVencer: Int? {
        if let diasRestantes { return diasRestantes }
        guard let fecha = Documento.fecha(fechaLimite) else { return nil }
        return Calendar.current.dateComponents(
            [.day],
            from: Calendar.current.startOfDay(for: Date()),
            to: Calendar.current.startOfDay(for: fecha)
        ).day
    }

    var estaVencido: Bool {
        if let dias = diasParaVencer { return dias < 0 }
        return false
    }
}

// ---------------------------------------------------------------------------
// Notificaciones
// ---------------------------------------------------------------------------


struct Notificacion: Identifiable {

    let id: Int
    let uuid: String

    /// Identificador del documento relacionado.
    var documentoId: Int?

    /// UUID del documento relacionado.
    var documentoUuid: String

    var tipo: String
    var mensaje: String
    var leida: Bool
    var emitidaEn: Date
    var leidaEn: Date? = nil

    /// Indica si la notificación tiene un documento asociado.
    var tieneDocumento: Bool {
        guard let documentoId else {
            return false
        }

        return documentoId > 0
    }
}


/// Conjunto de notificaciones del centro de notificaciones.
struct CentroNotificaciones {
    var noLeidas: Int
    var recientes: [Notificacion] = []
}

/// Versión del documento (PDF cargado).
struct Version: Identifiable {
    let id: Int
    let numero: Int
    var nombre: String
    var creadoEn: Date
}

// ---------------------------------------------------------------------------
// Configuración del servidor
// ---------------------------------------------------------------------------

struct DatosServidor {
    var url: String
    var sincronizacionAutomatica: Bool
    var ultimaSincronizacion: Date?
    var fechaSiguienteSincronizacion: Date?
}

// ---------------------------------------------------------------------------
// Perfil
// ---------------------------------------------------------------------------

struct DatosPerfilRequest {
    var cargo: String
    var grado: String
    var area: String
    var subdireccion: String
    var correo: String
    var telefono: String?
}

// MARK: - Permisos efectivos

/// Permisos efectivos del usuario autenticado.
enum Permisos {

    /// Determina si el usuario puede subir documentos (`DOCUMENTO_SUBIR_PDF` o
    /// `DOCUMENTO_CREAR`), permiso que el backend otorga a SUPERVISOR,
    /// RESPONSABLE y CAPTURISTA (ver `PERMISOS_POR_ROL` en el backend).
    static func puedeSubirDocumento(_ permisosDisponibles: Set<String>) -> Bool {
        return permisosDisponibles.contains("DOCUMENTO_SUBIR_PDF")
            || permisosDisponibles.contains("DOCUMENTO_CREAR")
    }
}

/// Resultado del OCR de un documento.
struct DatosOCR {
    let texto: String
    let procesadoEn: Date
}
