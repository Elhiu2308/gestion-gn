import Foundation

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

struct DatosDocumento {
    var tipo: TipoDocumento = .oficio
    var folio: String = ""
    var asunto: String = ""

    var destinatario: String = ""
    var idDestinatario: Int? = nil
    var referencia: String = ""
    var numAnexos: Int = 0
    var observaciones: String = ""

    var fechaDocumento: String = ""

    var fechaRecepcion: Double? = nil

    var fechaLimite: String = ""
    var fechaLimiteHora: String = ""
    var urgencia: UrgenciaDocumento = .ordinario
    var clasificacion: ClasificacionDocumento = .publico
    var notas: String = ""
    var textoOCR: String = ""

    var areaOrigen: String = ""
    var areaDestino: String = ""

    var orgDgOrigenId: Int? = nil
    var orgDirOrigenId: Int? = nil
    var orgSubOrigenId: Int? = nil
    var orgDeptOrigenId: Int? = nil
    var orgDgDestinoId: Int? = nil
    var orgDirDestinoId: Int? = nil
    var orgSubDestinoId: Int? = nil
    var orgDeptDestinoId: Int? = nil
}

struct CambiosDocumento {
    var estado: String?
    var areaActual: String?
    var usuarioAsignado: String?
    var comentario: String?
}

struct AccionFlujo {
    var ok: Bool = false
    var estado: String = ""
    var error: String = ""
    var usuarioAsignado: String?
    var documentoUuid: String?
}

struct CompartirRespuesta {
    let ok: Bool
    let areaA: String
    let areaB: String
    let error: String = ""
}

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

    var numero: String { codigo }
}

struct DatosExpediente {
    var numero: String
    var descripcion: String
    var areaResponsable: String = ""
    var clasificacion: String = "CONFIDENCIAL"
}

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

    var asignadoAMi: Bool = false

    var creadoPorMi: Bool = false

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

struct Notificacion: Identifiable {

    let id: Int
    let uuid: String

    var documentoId: Int?

    var documentoUuid: String

    var tipo: String
    var mensaje: String
    var leida: Bool
    var emitidaEn: Date
    var leidaEn: Date? = nil

    var tieneDocumento: Bool {
        guard let documentoId else {
            return false
        }

        return documentoId > 0
    }
}

struct CentroNotificaciones {
    var noLeidas: Int
    var recientes: [Notificacion] = []
}

struct Version: Identifiable {
    let id: Int
    let numero: Int
    var nombre: String
    var creadoEn: Date
}

struct DatosServidor {
    var url: String
    var sincronizacionAutomatica: Bool
    var ultimaSincronizacion: Date?
    var fechaSiguienteSincronizacion: Date?
}

struct DatosPerfilRequest {
    var cargo: String
    var grado: String
    var area: String
    var subdireccion: String
    var correo: String
    var telefono: String?
}

enum Permisos {

    static func puedeSubirDocumento(_ permisosDisponibles: Set<String>) -> Bool {
        return permisosDisponibles.contains("DOCUMENTO_SUBIR_PDF")
            || permisosDisponibles.contains("DOCUMENTO_CREAR")
    }
}

struct DatosOCR {
    let texto: String
    let procesadoEn: Date
}
