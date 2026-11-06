import Foundation

// ===========================================================================
// Dominio documental — espejo exacto de los catálogos del backend
// (ver `web/app/static/js/app.js`: TIPOS, ESTADOS, URGENCIAS, NIVELES,
//  CLASIFICACIONES, ESTADOS_INDIVIDUALES, TIPOS_RELACION, ROLES, ACCIONES).
// ===========================================================================

// MARK: - Estado del documento

/// Estados canónicos del ciclo documental FGN 2026 (12) más los heredados.
enum EstadoDocumento: String, CaseIterable, Identifiable {
    // Ciclo canónico
    case recibido = "RECIBIDO"
    case pendienteTurno = "PENDIENTE_TURNO"
    case turnado = "TURNADO"
    case enDistribucion = "EN_DISTRIBUCION"
    case enAtencion = "EN_ATENCION"
    case atencionParcial = "ATENCION_PARCIAL"
    case respuestasCompletas = "RESPUESTAS_COMPLETAS"
    case consolidado = "CONSOLIDADO"
    case enRevision = "EN_REVISION"
    case enValidacion = "EN_VALIDACION"
    case validado = "VALIDADO"
    case concluido = "CONCLUIDO"
    // Estados de resolución
    case rechazado = "RECHAZADO"
    case archivado = "ARCHIVADO"
    case historico = "HISTORICO"
    case cancelado = "CANCELADO"
    // Heredados (compatibilidad con datos previos)
    case registrado = "REGISTRADO"
    case pendienteAsignacion = "PENDIENTE_ASIGNACION"
    case designado = "DESIGNADO"
    case asignado = "ASIGNADO"
    case respondido = "RESPONDIDO"
    case consolidando = "CONSOLIDANDO"
    case enProceso = "EN_PROCESO"
    case pendiente = "PENDIENTE"
    case contestado = "CONTESTADO"
    case devuelto = "DEVUELTO"
    case finalizado = "FINALIZADO"

    var id: String { rawValue }

    /// Texto mostrado en la interfaz (etiquetas del backend).
    var etiqueta: String {
        switch self {
        case .recibido: return "Recibido"
        case .pendienteTurno: return "Pendiente de turno"
        case .turnado: return "Turnado"
        case .enDistribucion: return "En distribución interna"
        case .enAtencion: return "En atención"
        case .atencionParcial: return "Atención parcial"
        case .respuestasCompletas: return "Respuestas completas"
        case .consolidado: return "Consolidado"
        case .enRevision: return "En revisión"
        case .enValidacion: return "En validación"
        case .validado: return "Validado"
        case .concluido: return "Concluido"
        case .rechazado: return "Rechazado"
        case .archivado: return "Archivado"
        case .historico: return "Histórico"
        case .cancelado: return "Cancelado"
        case .registrado: return "Registrado"
        case .pendienteAsignacion: return "Pendiente de asignación"
        case .designado: return "Designado"
        case .asignado: return "Asignado"
        case .respondido: return "Respondido"
        case .consolidando: return "Consolidando"
        case .enProceso: return "En proceso"
        case .pendiente: return "Pendiente"
        case .contestado: return "Contestado"
        case .devuelto: return "Devuelto"
        case .finalizado: return "Finalizado"
        }
    }

    /// Los estados terminales no admiten más acciones de trámite.
    var esTerminal: Bool {
        switch self {
        case .cancelado, .archivado, .historico, .concluido, .finalizado:
            return true
        default:
            return false
        }
    }

    /// Estados en los que el documento sigue vivo en el flujo.
    var estaVivoEnFlujo: Bool {
        switch self {
        case .recibido, .pendienteTurno, .turnado, .enDistribucion, .enAtencion,
             .atencionParcial, .respuestasCompletas, .consolidado, .enRevision,
             .enValidacion, .rechazado, .designado, .asignado, .respondido, .consolidando:
            return true
        default:
            return false
        }
    }

    /// Normaliza el valor recibido del servidor.
    static func desde(_ valor: String) -> EstadoDocumento {
        EstadoDocumento(rawValue: valor.uppercased()) ?? .recibido
    }
}

// MARK: - Urgencia y clasificación

enum UrgenciaDocumento: String, CaseIterable, Identifiable {
    case ordinario = "ORDINARIO"
    case urgente = "URGENTE"
    case extraurgente = "EXTRAURGENTE"

    var id: String { rawValue }

    var etiqueta: String {
        switch self {
        case .ordinario: return "Ordinario"
        case .urgente: return "Urgente"
        case .extraurgente: return "Extraurgente"
        }
    }

    /// Normaliza valores heredados (`NORMAL`, `MUY_URGENTE`, `URGENTE_MAXIMA`).
    static func desde(_ valor: String) -> UrgenciaDocumento {
        switch valor.uppercased() {
        case "URGENTE": return .urgente
        case "EXTRAURGENTE", "MUY_URGENTE", "URGENTE_MAXIMA", "ALTA": return .extraurgente
        default: return .ordinario
        }
    }
}

enum ClasificacionDocumento: String, CaseIterable, Identifiable {
    case publico = "PUBLICO"
    case confidencial = "CONFIDENCIAL"
    case reservado = "RESERVADO"

    var id: String { rawValue }

    var etiqueta: String {
        switch self {
        case .publico: return "Público"
        case .confidencial: return "Confidencial"
        case .reservado: return "Reservado"
        }
    }

    static func desde(_ valor: String) -> ClasificacionDocumento {
        ClasificacionDocumento(rawValue: valor.uppercased()) ?? .publico
    }
}

// MARK: - Catálogos auxiliares

/// Niveles del turno (`NIVELES` del backend).
enum NivelTurnado: String, CaseIterable, Identifiable {
    case titular = "TITULAR"
    case direccion = "DIRECCION"
    case subdireccion = "SUBDIRECCION"
    case departamento = "DEPARTAMENTO"
    case usuario = "USUARIO"

    var id: String { rawValue }

    var etiqueta: String {
        switch self {
        case .titular: return "Titular/Permanencia"
        case .direccion: return "Dirección"
        case .subdireccion: return "Subdirección"
        case .departamento: return "Departamento"
        case .usuario: return "Usuario"
        }
    }
}

/// Estado individual del documento respecto a un usuario.
enum EstadoIndividual: String, CaseIterable, Identifiable {
    case noLeido = "NO_LEIDO"
    case leido = "LEIDO"
    case acuseRecibido = "ACUSE_RECIBIDO"
    case enAtencion = "EN_ATENCION"
    case respuestaElaborada = "RESPUESTA_ELABORADA"
    case enRevision = "EN_REVISION"
    case aprobado = "APROBADO"

    var id: String { rawValue }

    var etiqueta: String {
        switch self {
        case .noLeido: return "Sin abrir"
        case .leido: return "Leído"
        case .acuseRecibido: return "Acuse de recibo"
        case .enAtencion: return "En atención"
        case .respuestaElaborada: return "Respuesta elaborada"
        case .enRevision: return "En revisión"
        case .aprobado: return "Aprobado"
        }
    }

    static func desde(_ valor: String) -> EstadoIndividual? {
        EstadoIndividual(rawValue: valor.uppercased())
    }
}

/// Tipo de vínculo entre documentos.
enum TipoRelacion: String, CaseIterable, Identifiable {
    case relacionadoCon = "RELACIONADO_CON"
    case antecedente = "ANTECEDENTE"
    case derivadoDe = "DERIVADO_DE"

    var id: String { rawValue }

    var etiqueta: String {
        switch self {
        case .relacionadoCon: return "Relacionado con"
        case .antecedente: return "Antecedente de"
        case .derivadoDe: return "Derivado de"
        }
    }
}

/// Tipo de anotación interna.
enum TipoAnotacion: String, CaseIterable, Identifiable {
    case observacion = "OBSERVACION"
    case accion = "ACCION"
    case correccion = "CORRECCION"

    var id: String { rawValue }

    var etiqueta: String {
        switch self {
        case .observacion: return "Observación"
        case .accion: return "Acción"
        case .correccion: return "Corrección"
        }
    }
}

/// Acciones registradas en la línea de tiempo (`ACCIONES` del backend).
enum AccionHistorial: String {
    case creado = "CREADO"
    case editado = "EDITADO"
    case recibido = "RECIBIDO"
    case pendienteAsignacion = "PENDIENTE_ASIGNACION"
    case asignado = "ASIGNADO"
    case compartido = "COMPARTIDO"
    case turnado = "TURNADO"
    case consultado = "CONSULTADO"
    case enRevision = "EN_REVISION"
    case enProceso = "EN_PROCESO"
    case atendido = "ATENDIDO"
    case respuestaAgregada = "RESPUESTA_AGREGADA"
    case consolidado = "CONSOLIDADO"
    case enValidacion = "EN_VALIDACION"
    case validado = "VALIDADO"
    case rechazado = "RECHAZADO"
    case anotacion = "ANOTACION"
    case contestacionAgregada = "CONTESTACION_AGREGADA"
    case estadoModificado = "ESTADO_MODIFICADO"
    case pdfAgregado = "PDF_AGREGADO"
    case pdfActualizado = "PDF_ACTUALIZADO"
    case finalizado = "FINALIZADO"
    case archivado = "ARCHIVADO"
    case historico = "HISTORICO"
    case eliminado = "ELIMINADO"
    case login = "LOGIN"
    case logout = "LOGOUT"
    case descargaPDF = "DESCARGA_PDF"
    case subidaPDF = "SUBIDA_PDF"
    case ocrEditado = "OCR_EDITADO"
    case contrasenaCambiada = "CONTRASENA_CAMBIADA"
    case movimiento = "MOVIMIENTO"

    var etiqueta: String {
        switch self {
        case .creado: return "Creado"
        case .editado: return "Editado"
        case .recibido: return "Recibido"
        case .pendienteAsignacion: return "Pendiente de asignación"
        case .asignado: return "Asignado"
        case .compartido: return "Compartido"
        case .turnado: return "Turnado"
        case .consultado: return "Consultado"
        case .enRevision: return "En revisión"
        case .enProceso: return "En proceso"
        case .atendido: return "En atención"
        case .respuestaAgregada: return "Respuesta agregada"
        case .consolidado: return "Consolidado"
        case .enValidacion: return "En validación"
        case .validado: return "Validado"
        case .rechazado: return "Rechazado"
        case .anotacion: return "Anotación"
        case .contestacionAgregada: return "Contestación agregada"
        case .estadoModificado: return "Estado modificado"
        case .pdfAgregado: return "PDF agregado"
        case .pdfActualizado: return "PDF actualizado"
        case .finalizado: return "Finalizado"
        case .archivado: return "Archivado"
        case .historico: return "Histórico"
        case .eliminado: return "Eliminado"
        case .login: return "Inicio de sesión"
        case .logout: return "Cierre de sesión"
        case .descargaPDF: return "PDF descargado"
        case .subidaPDF: return "PDF subido"
        case .ocrEditado: return "OCR editado"
        case .contrasenaCambiada: return "Contraseña cambiada"
        case .movimiento: return "Movimiento"
        }
    }

    static func desde(_ valor: String) -> AccionHistorial? {
        AccionHistorial(rawValue: valor.uppercased())
    }
}

/// Roles de usuario (`ROLES` del backend).
enum RolUsuario: String, CaseIterable, Identifiable {
    case administrador = "ADMINISTRADOR"
    case capturista = "CAPTURISTA"
    case supervisor = "SUPERVISOR"
    case responsable = "RESPONSABLE"
    case operador = "OPERADOR"
    case consulta = "CONSULTA"

    var id: String { rawValue }

    var etiqueta: String {
        switch self {
        case .administrador: return "Administrador"
        case .capturista: return "Capturista (Archivo)"
        case .supervisor: return "Supervisor (Titular/Permanencia)"
        case .responsable: return "Responsable (Dirección/Staff)"
        case .operador: return "Operador (Subdirección)"
        case .consulta: return "Consulta"
        }
    }

    static func desde(_ valor: String) -> RolUsuario? {
        RolUsuario(rawValue: valor.uppercased())
    }
}

// ===========================================================================
// Documento
// ===========================================================================

/// Documento documental tal como lo devuelve el backend.
struct Documento: Identifiable, Hashable {

    let id: Int
    var uuid: String = ""
    var folio: String = ""
    var asunto: String = ""
    var tipo: TipoDocumento = .otro
    var estado: EstadoDocumento = .recibido
    var urgencia: UrgenciaDocumento = .ordinario
    var clasificacion: ClasificacionDocumento = .publico

    /// Remitente (lo registra el backend automáticamente desde la cuenta).
    var remitente: String = ""
    /// Destinatario elegido en la cascada Área → Subdirección → Persona.
    var destinatario: String = ""
    /// Identificador del destinatario en el catálogo de personas.
    var idDestinatario: Int? = nil

    var areaOrigen: String = ""
    var areaDestino: String = ""
    var areaActual: String = ""
    var usuarioAsignado: String = ""
    /// Identificador del usuario que creó el documento.
    var creadorId: Int? = nil
    /// Identificador del remitente (cuando se capturó desde el catálogo de personas).
    var idRemitente: Int? = nil

    var referencia: String = ""
    var numAnexos: Int = 0

    /// Fecha consignada en el documento (`YYYY-MM-DD`).
    var fechaDocumento: String = ""
    /// Fecha de creación del registro en el servidor.
    var creadoEn: Date? = nil
    /// Fecha de recepción (epoch en milisegundos).
    var fechaRecepcion: Date? = nil
    /// Fecha y hora límite de atención.
    var fechaLimite: String = ""
    var fechaLimiteHora: String = ""

    var notas: String = ""
    var observaciones: String = ""
    var textoOCR: String = ""

    /// Nombre del PDF adjunto y su versión.
    var pdfNombre: String = ""
    var pdfVersion: Int = 0
    /// Indica si el documento fue compartido con dos ramas (A/B).
    var compartido: Bool = false
    /// Texto de la respuesta consolidada final.
    var respuestaConsolidada: String = ""

    /// Vencimiento calculado por el backend y días restantes.
    var vencimiento: String = ""
    var diasRestantes: Int? = nil

    /// Ids del organigrama (para reconstruir la cascada al editar).
    var orgDgOrigenId: Int? = nil
    var orgDirOrigenId: Int? = nil
    var orgSubOrigenId: Int? = nil
    var orgDeptOrigenId: Int? = nil
    var orgDgDestinoId: Int? = nil
    var orgDirDestinoId: Int? = nil
    var orgSubDestinoId: Int? = nil
    var orgDeptDestinoId: Int? = nil

    // MARK: Konstrucción desde el JSON del backend

    init(id: Int, json: [String: Any]) {
        self.id = id
        uuid = json["uuid"] as? String ?? ""
        folio = json["folio"] as? String ?? ""
        asunto = json["asunto"] as? String ?? ""
        tipo = TipoDocumento(rawValue: (json["tipo"] as? String ?? "").uppercased()) ?? .otro
        estado = EstadoDocumento.desde(json["estado"] as? String ?? "")
        urgencia = UrgenciaDocumento.desde(json["urgencia"] as? String ?? "")
        clasificacion = ClasificacionDocumento.desde(json["clasificacion"] as? String ?? "")

        remitente = json["remitente"] as? String ?? ""
        destinatario = json["destinatario"] as? String ?? ""
        idDestinatario = json["id_destinatario"] as? Int

        areaOrigen = json["area_origen"] as? String ?? ""
        areaDestino = json["area_destino"] as? String ?? ""
        areaActual = json["area_actual"] as? String ?? ""
        usuarioAsignado = json["usuario_asignado"] as? String ?? ""
        creadorId = (json["creador_usuarios_id"] as? Int) ?? (json["usuario_id"] as? Int)
        idRemitente = json["id_remitente"] as? Int

        referencia = json["referencia"] as? String ?? ""
        numAnexos = (json["num_anexos"] as? Int) ?? Int(json["num_anexos"] as? String ?? "") ?? 0

        fechaDocumento = json["fecha_documento"] as? String ?? ""
        creadoEn = Documento.fecha(json["creado_en"])
        fechaRecepcion = Documento.fecha(json["fecha_recepcion"])
        fechaLimite = json["fecha_limite"] as? String ?? ""
        fechaLimiteHora = json["fecha_limite_hora"] as? String ?? ""

        notas = json["notas"] as? String ?? ""
        observaciones = json["observaciones"] as? String ?? ""
        textoOCR = json["texto_ocr"] as? String ?? ""

        pdfNombre = json["pdf_nombre"] as? String ?? ""
        pdfVersion = (json["pdf_version"] as? Int) ?? 0
        if let b = json["compartido"] as? Bool { compartido = b }
        else if let i = json["compartido"] as? Int { compartido = (i == 1) }
        else { compartido = false }
        respuestaConsolidada = json["respuesta_consolidada"] as? String ?? ""

        vencimiento = json["vencimiento"] as? String ?? ""
        diasRestantes = (json["dias_restantes"] as? Int) ?? (json["dias_restantes"] as? String).flatMap(Int.init)

        orgDgOrigenId = json["org_dg_origen_id"] as? Int
        orgDirOrigenId = json["org_dir_origen_id"] as? Int
        orgSubOrigenId = json["org_sub_origen_id"] as? Int
        orgDeptOrigenId = json["org_dept_origen_id"] as? Int
        orgDgDestinoId = json["org_dg_destino_id"] as? Int
        orgDirDestinoId = json["org_dir_destino_id"] as? Int
        orgSubDestinoId = json["org_sub_destino_id"] as? Int
        orgDeptDestinoId = json["org_dept_destino_id"] as? Int
    }

    /// Inicializador de compatibilidad para las listas locales antiguas.
    init(
        id: Int,
        folio: String,
        uuid: String,
        asunto: String,
        tipo: TipoDocumento,
        estado: String,
        areaActual: String,
        usuarioAsignado: String,
        clasificacion: String,
        urgencia: String,
        sincronizadoEnServidor: Bool,
        creadoEn: Date,
        actualizadoEn: Date
    ) {
        self.id = id
        self.uuid = uuid
        self.folio = folio
        self.asunto = asunto
        self.tipo = tipo
        self.estado = EstadoDocumento.desde(estado)
        self.urgencia = UrgenciaDocumento.desde(urgencia)
        self.clasificacion = ClasificacionDocumento.desde(clasificacion)
        self.areaActual = areaActual
        self.areaDestino = areaActual
        self.usuarioAsignado = usuarioAsignado
        self.creadoEn = creadoEn
    }

    /// Indica si el documento tiene un archivo (PDF o imagen) adjunto.
    var tieneArchivo: Bool { !pdfNombre.isEmpty }

    /// Días restantes para atender el documento. Usa `dias_restantes` cuando el
    /// backend lo envía y, si no, calcula a partir de `fecha_limite`/`vencimiento`.
    var diasParaVencer: Int? {
        if let diasRestantes { return diasRestantes }
        let texto = fechaLimite.isEmpty ? vencimiento : fechaLimite
        guard let fecha = Documento.fecha(texto) else { return nil }
        return Calendar.current.dateComponents(
            [.day],
            from: Calendar.current.startOfDay(for: Date()),
            to: Calendar.current.startOfDay(for: fecha)
        ).day
    }

    /// Indica si el documento ya superó su fecha límite.
    var estaVencido: Bool {
        (diasParaVencer ?? 0) < 0
    }

    /// Convierte una fecha del backend (epoch en segundos/milisegundos o ISO-8601)
    /// a `Date`. El backend entrega milisegundos en la mayoría de los campos.
    static func fecha(_ valor: Any?) -> Date? {
        if let numero = valor as? Double {
            return Date(timeIntervalSince1970: numero > 100_000_000_000 ? numero / 1000 : numero)
        }
        if let numero = valor as? Int {
            let doble = Double(numero)
            return Date(timeIntervalSince1970: doble > 100_000_000_000 ? doble / 1000 : doble)
        }
        if let texto = valor as? String, !texto.isEmpty {
            if let numero = Double(texto) {
                return Date(timeIntervalSince1970: numero > 100_000_000_000 ? numero / 1000 : numero)
            }
            let iso = ISO8601DateFormatter()
            if let fecha = iso.date(from: texto) { return fecha }
            let formato = DateFormatter()
            formato.locale = Locale(identifier: "en_US_POSIX")
            formato.dateFormat = "yyyy-MM-dd"
            return formato.date(from: texto)
        }
        return nil
    }
}

// ===========================================================================
// Piezas de la ficha documental
// ===========================================================================

/// Paso de la línea de tiempo del documento (`GET /documentos/<id>/historial`).
struct PasoHistorial: Identifiable, Hashable {
    let id: Int
    var fechaHora: Date?
    var accion: String
    var etiquetaAccion: String
    var estadoAnterior: String
    var estadoNuevo: String
    var comentario: String
    var area: String
    var usuarioNombre: String
    var dispositivo: String

    init(json: [String: Any]) {
        id = (json["id"] as? Int) ?? 0
        if let ms = (json["fecha_hora"] as? Double) ?? (json["fecha_hora"] as? Int).map(Double.init) {
            fechaHora = Date(timeIntervalSince1970: ms / 1000)
        } else {
            fechaHora = nil
        }
        accion = json["accion"] as? String ?? ""
        etiquetaAccion = AccionHistorial.desde(accion)?.etiqueta ?? accion
        estadoAnterior = json["estado_anterior"] as? String ?? ""
        estadoNuevo = json["estado_nuevo"] as? String ?? ""
        comentario = json["comentario"] as? String ?? ""
        area = json["area"] as? String ?? ""
        usuarioNombre = json["usuario_nombre"] as? String ?? ""
        dispositivo = json["dispositivo"] as? String ?? "web"
    }
}

/// Asignación del documento (`GET /documentos/<id>/asignaciones`).
struct AsignacionDocumento: Identifiable, Hashable {
    let id: Int
    var fechaAsignacion: Date?
    var asignadorNombre: String
    var asignadoNombre: String
    var areaDestino: String
    var nivel: String
    var etiquetaNivel: String
    var estadoIndividual: String
    var tipo: String
    var rama: Int
    var estadoAnterior: String
    var estadoNuevo: String
    var comentario: String

    init(json: [String: Any]) {
        id = (json["id"] as? Int) ?? 0
        let ms = (json["fecha_asignacion"] as? Double)
            ?? (json["creado_en"] as? Double)
            ?? (json["fecha_asignacion"] as? Int).map(Double.init)
        if let ms {
            fechaAsignacion = Date(timeIntervalSince1970: ms / 1000)
        } else {
            fechaAsignacion = nil
        }
        asignadorNombre = json["asignador_nombre"] as? String ?? ""
        asignadoNombre = json["asignado_nombre"] as? String ?? ""
        areaDestino = json["area_destino"] as? String ?? ""
        nivel = json["nivel"] as? String ?? ""
        etiquetaNivel = NivelTurnado(rawValue: nivel.uppercased())?.etiqueta ?? nivel
        estadoIndividual = json["estado_individual"] as? String ?? ""
        tipo = json["tipo"] as? String ?? ""
        rama = (json["rama"] as? Int) ?? 0
        estadoAnterior = json["estado_anterior"] as? String ?? ""
        estadoNuevo = json["estado_nuevo"] as? String ?? ""
        comentario = json["comentario"] as? String ?? ""
    }
}

/// Respuesta oficial registrada (`GET /documentos/<id>/respuestas`).
struct RespuestaOficial: Identifiable, Hashable {
    let id: Int
    var fechaHora: Date?
    var area: String
    var rama: Int
    var usuarioNombre: String
    var texto: String
    var observaciones: String
    var consolidado: Bool

    init(json: [String: Any]) {
        id = (json["id"] as? Int) ?? 0
        if let ms = (json["fecha_hora"] as? Double) ?? (json["fecha_hora"] as? Int).map(Double.init) {
            fechaHora = Date(timeIntervalSince1970: ms / 1000)
        } else {
            fechaHora = nil
        }
        area = json["area"] as? String ?? ""
        rama = (json["rama"] as? Int) ?? 0
        usuarioNombre = json["usuario_nombre"] as? String ?? ""
        texto = json["texto"] as? String ?? ""
        observaciones = json["observaciones"] as? String ?? ""
        if let b = json["consolidado"] as? Bool { consolidado = b }
        else if let i = json["consolidado"] as? Int { consolidado = (i == 1) }
        else { consolidado = false }
    }

    var etiquetaRama: String {
        switch rama {
        case 1: return "Rama A"
        case 2: return "Rama B"
        default: return "—"
        }
    }
}

/// Vínculo con otro documento (`GET /documentos/<id>/relaciones`).
struct RelacionDocumento: Identifiable, Hashable {
    let id: Int
    var documentoRelacionadoId: Int
    var documentoReferencia: String
    var tipoRelacion: String
    var usuarioNombre: String

    init(json: [String: Any]) {
        id = (json["id"] as? Int) ?? 0
        documentoRelacionadoId = (json["documento_relacionado_id"] as? Int) ?? 0
        documentoReferencia = json["documento_referencia"] as? String ?? ""
        tipoRelacion = json["tipo_relacion"] as? String ?? ""
        usuarioNombre = json["usuario_nombre"] as? String ?? ""
    }

    var etiquetaTipo: String {
        TipoRelacion(rawValue: tipoRelacion.uppercased())?.etiqueta ?? tipoRelacion
    }
}

/// Anotación interna del documento.
struct Anotacion: Identifiable, Hashable {
    let id: Int
    var documentoId: Int
    var tipo: String
    var texto: String
    var creadoPor: String
    var area: String
    var creadoEn: Date?

    init(id: Int, documentoId: Int, tipo: String, texto: String, creadoPor: String, area: String = "", creadoEn: Date? = nil) {
        self.id = id
        self.documentoId = documentoId
        self.tipo = tipo
        self.texto = texto
        self.creadoPor = creadoPor
        self.area = area
        self.creadoEn = creadoEn
    }

    init(json: [String: Any], documentoId: Int) {
        self.id = (json["id"] as? Int) ?? 0
        self.documentoId = documentoId
        self.tipo = json["tipo"] as? String ?? "OBSERVACION"
        self.texto = json["comentario"] as? String ?? (json["texto"] as? String ?? "")
        self.creadoPor = json["usuario_nombre"] as? String ?? (json["creado_por"] as? String ?? "")
        self.area = json["area"] as? String ?? ""
        if let ms = (json["creado_en"] as? Double) ?? (json["creado_en"] as? Int).map(Double.init) {
            self.creadoEn = Date(timeIntervalSince1970: ms / 1000)
        } else {
            self.creadoEn = nil
        }
    }

    var etiquetaTipo: String {
        TipoAnotacion(rawValue: tipo.uppercased())?.etiqueta ?? tipo
    }
}

// ===========================================================================
// Turnado, organigrama y bandejas
// ===========================================================================

/// Opción de destino del turno (`GET /turnado/opciones`).
struct OpcionTurnado: Identifiable, Hashable {
    let id: Int
    var tipo: String
    var nombre: String

    var identificador: String { "\(id)" }

    init(json: [String: Any]) {
        id = (json["id"] as? Int) ?? 0
        tipo = json["tipo"] as? String ?? ""
        nombre = json["nombre"] as? String ?? ""
    }
}

/// Opciones de turno del usuario, con su nivel actual y el siguiente permitido.
struct OpcionesTurnado {
    var nivelActual: String = ""
    var nivelSiguiente: String = ""
    var opciones: [OpcionTurnado] = []

    var etiquetaNivelActual: String {
        NivelTurnado(rawValue: nivelActual.uppercased())?.etiqueta ?? nivelActual
    }

    var etiquetaNivelSiguiente: String {
        NivelTurnado(rawValue: nivelSiguiente.uppercased())?.etiqueta ?? nivelSiguiente
    }

    init(json: [String: Any]) {
        nivelActual = json["nivel_actual"] as? String ?? ""
        nivelSiguiente = json["nivel_siguiente"] as? String ?? ""
        opciones = (json["opciones"] as? [[String: Any]] ?? []).map(OpcionTurnado.init(json:))
    }
}

/// Bandeja de trabajo (`GET /bandejas`).
struct Bandeja: Identifiable, Hashable {
    let id: String
    var clave: String
    var titulo: String
    var descripcion: String

    init(json: [String: Any]) {
        clave = json["clave"] as? String ?? ""
        titulo = json["titulo"] as? String ?? ""
        descripcion = json["descripcion"] as? String ?? ""
        id = clave.isEmpty ? titulo : clave
    }
}

/// Registro del organigrama (`GET /organigrama/<nivel>`).
struct NivelOrganizacional: Identifiable, Hashable {
    let id: Int
    var nombre: String
    var estatus: Bool
    /// Nombre del nivel superior (sólo informativo).
    var padre: String

    init(json: [String: Any]) {
        id = (json["id"] as? Int) ?? 0
        nombre = json["nombre"] as? String ?? ""
        if let b = json["estatus"] as? Bool { estatus = b }
        else if let i = json["estatus"] as? Int { estatus = (i == 1) }
        else { estatus = true }
        padre = json["nombre_padre"] as? String ?? (json["padre_nombre"] as? String ?? "")
    }
}

/// Niveles del organigrama institucional.
enum NivelOrganigrama: String, CaseIterable, Identifiable {
    case direccionGeneral = "direcciones-generales"
    case direccion = "direcciones"
    case subdireccion = "subdirecciones"
    case departamento = "departamentos"

    var id: String { rawValue }

    var etiqueta: String {
        switch self {
        case .direccionGeneral: return "Dirección General"
        case .direccion: return "Dirección"
        case .subdireccion: return "Subdirección"
        case .departamento: return "Departamento"
        }
    }

    /// Parámetro que recibe el identificador del nivel superior.
    var parametroPadre: String? {
        switch self {
        case .direccion: return "direccion_general"
        case .subdireccion: return "direccion"
        case .departamento: return "subdireccion"
        case .direccionGeneral: return nil
        }
    }
}

/// Persona del catálogo (`GET /personas?direccion=|subdireccion=`).
struct PersonaCatalogo: Identifiable, Hashable {
    let id: Int
    var nombre: String
    var grado: String
    var cargo: String
    var direccion: String
    var subdireccion: String
    var idUsuario: Int?
    var idDireccion: Int?
    var idSubdireccion: Int?
    var idDepartamento: Int?

    init(json: [String: Any]) {
        id = (json["id"] as? Int) ?? ((json["id_persona"] as? Int) ?? 0)
        nombre = json["nombre_completo"] as? String ?? (json["nombre"] as? String ?? "")
        grado = json["grado"] as? String ?? ""
        cargo = json["cargo"] as? String ?? ""
        direccion = json["direccion"] as? String ?? ""
        subdireccion = json["subdireccion"] as? String ?? ""
        idUsuario = (json["id_usuario"] as? Int) ?? (json["id"] as? Int)
        idDireccion = json["id_direccion"] as? Int
        idSubdireccion = json["id_subdireccion"] as? Int
        idDepartamento = json["id_departamento"] as? Int
    }
}

// ===========================================================================
// Panel y perfil
// ===========================================================================

/// Resumen del panel institucional (`GET /panel/resumen`).
struct ResumenPanel {
    var total: Int = 0
    var pendientesAsignacion: Int = 0
    var asignados: Int = 0
    var enTramite: Int = 0
    var enValidacion: Int = 0
    var validados: Int = 0
    var archivados: Int = 0
    var misAsignados: Int = 0
    /// Documentos que me asignaron (`GET /documentos?bandeja=ASIGNADOS_POR_MI`).
    var asignadosPorMi: Int = 0
    /// Documentos que me asignaron a mí.
    var asignadosAMi: Int = 0

    init(json: [String: Any]) {
        total = (json["total"] as? Int) ?? 0
        pendientesAsignacion = (json["pendientes_asignacion"] as? Int) ?? 0
        asignados = (json["asignados"] as? Int) ?? 0
        enTramite = (json["en_tramite"] as? Int) ?? 0
        enValidacion = (json["en_validacion"] as? Int) ?? 0
        validados = (json["validados"] as? Int) ?? 0
        archivados = (json["archivados"] as? Int) ?? 0
        misAsignados = (json["mis_asignados"] as? Int) ?? 0
        asignadosPorMi = (json["asignados_por_mi"] as? Int) ?? 0
        asignadosAMi = (json["asignados_a_mi"] as? Int) ?? 0
    }
}

/// Perfil del usuario tal como lo devuelve el servicio (`GET /auth/me`).
struct PerfilServicio {
    var id: Int = 0
    var usuario: String = ""
    var nombre: String = ""
    var nombreCompleto: String = ""
    var grado: String = ""
    var rol: String = ""
    var cargo: String = ""
    var area: String = ""
    var subdireccion: String = ""
    var correo: String = ""
    var telefono: String = ""
    var permisos: Set<String> = []
    var direccion: String = ""
    var idDireccionGeneral: Int? = nil
    var idDireccion: Int? = nil
    var idSubdireccion: Int? = nil
    var idDepartamento: Int? = nil

    init(json: [String: Any]) {
        id = (json["id"] as? Int) ?? 0
        usuario = json["usuario"] as? String ?? ""
        nombre = json["nombre"] as? String ?? ""
        nombreCompleto = json["nombre_completo"] as? String ?? ""
        grado = json["grado"] as? String ?? ""
        rol = json["rol"] as? String ?? ""
        cargo = json["cargo"] as? String ?? ""
        area = json["area"] as? String ?? ""
        subdireccion = json["subdireccion"] as? String ?? ""
        correo = json["correo"] as? String ?? ""
        telefono = json["telefono"] as? String ?? ""
        permisos = Set(json["permisos"] as? [String] ?? [])
        direccion = json["direccion"] as? String ?? area
        idDireccionGeneral = json["id_direccion_general"] as? Int
        idDireccion = json["id_direccion"] as? Int
        idSubdireccion = json["id_subdireccion"] as? Int
        idDepartamento = json["id_departamento"] as? Int
    }

    var etiquetaRol: String {
        RolUsuario.desde(rol)?.etiqueta ?? rol
    }

    /// Nombre shown en la cabecera de la app.
    var nombreMostrado: String {
        let completo = nombreCompleto.trimmingCharacters(in: .whitespaces)
        if !completo.isEmpty { return completo }
        if !nombre.isEmpty { return nombre }
        return usuario
    }
}
