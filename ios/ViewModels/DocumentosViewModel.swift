import SwiftUI

enum GrupoDocumentos: String, CaseIterable, Identifiable {
    case todos
    case pendientes
    case enProceso
    case finalizados
    case respondidos
    case vencidos
    case porVencer

    var id: String { rawValue }

    var titulo: String {
        switch self {
        case .todos: return "Todos los documentos"
        case .pendientes: return "Pendientes"
        case .enProceso: return "En proceso"
        case .finalizados: return "Finalizados"
        case .respondidos: return "Respondidos"
        case .vencidos: return "Vencidos"
        case .porVencer: return "Por vencer"
        }
    }

    var tituloCorto: String {
        switch self {
        case .todos: return "Todos"
        case .pendientes: return "Pendientes"
        case .enProceso: return "En proceso"
        case .finalizados: return "Finalizados"
        case .respondidos: return "Respondidos"
        case .vencidos: return "Vencidos"
        case .porVencer: return "Por vencer"
        }
    }

    func incluye(_ documento: Documento) -> Bool {
        let estado = documento.estado.rawValue.uppercased()

        switch self {
        case .todos:
            return true

        case .pendientes:
            return !Self.estaFinalizado(estado) && !Self.estaEnProceso(estado)

        case .enProceso:
            return Self.estaEnProceso(estado)

        case .finalizados:
            return Self.estaFinalizado(estado)

        case .respondidos:
            return Self.estaRespondido(estado)

        case .vencidos:
            return documento.estaVencido

        case .porVencer:
            guard let dias = documento.diasParaVencer else { return false }
            return (0...3).contains(dias)
        }
    }

    static func estaFinalizado(_ estado: String) -> Bool {
        ["FINALIZADO", "CONCLUIDO", "CONTESTADO", "ARCHIVADO", "VALIDADO", "HISTORICO", "HISTÓRICO"]
            .contains { estado.contains($0) }
    }

    static func estaEnProceso(_ estado: String) -> Bool {
        ["PROCESO", "ATENCION", "ATENCIÓN", "ASIGNADO", "TURNADO", "REVISION", "REVISIÓN",
         "DISTRIBUCION", "DISTRIBUCIÓN", "CONSOLIDANDO", "CONSOLIDADO", "DESIGNADO",
         "VALIDACION", "VALIDACIÓN", "RESPUESTAS_COMPLETAS"]
            .contains { estado.contains($0) }
    }

    static func estaRespondido(_ estado: String) -> Bool {
        ["RESPONDIDO", "CONTESTADO", "RESPUESTAS_COMPLETAS", "CONSOLIDADO", "VALIDADO"]
            .contains { estado.contains($0) }
    }
}

enum BandejaDocumentos: String, CaseIterable, Identifiable {
    case misDocumentos
    case pendientesTurno
    case asignadosAMi
    case enTramite
    case archivados

    var id: String { rawValue }

    var titulo: String {
        switch self {
        case .misDocumentos: return "Mis documentos"
        case .pendientesTurno: return "Pendientes de turno"
        case .asignadosAMi: return "Asignados a mí"
        case .enTramite: return "En trámite"
        case .archivados: return "Archivados"
        }
    }

    var mensajeVacio: String {
        switch self {
        case .misDocumentos: return "No hay documentos ingresados ni asignados a ti."
        case .pendientesTurno: return "No tienes documentos pendientes de turno."
        case .asignadosAMi: return "No tienes documentos asignados."
        case .enTramite: return "No hay documentos en trámite."
        case .archivados: return "Aún no hay documentos archivados o finalizados."
        }
    }

    func incluye(_ documento: Documento) -> Bool {
        let estado = documento.estado.rawValue.uppercased()

        switch self {
        case .misDocumentos, .asignadosAMi:
            return true

        case .pendientesTurno:
            return estado.contains("PENDIENTE")
                || estado == "RECIBIDO"
                || estado == "REGISTRADO"

        case .enTramite:
            return !GrupoDocumentos.estaFinalizado(estado)
                && GrupoDocumentos.estaEnProceso(estado)

        case .archivados:
            return GrupoDocumentos.estaFinalizado(estado)
        }
    }
}

@MainActor
final class DocumentosViewModel: ObservableObject {

    @Published private(set) var documentos: [Documento] = []
    @Published private(set) var estaCargando = false
    @Published var errorTexto: String?
    @Published var opcion: BandejaDocumentos

    private let servicio = ServicioDocumentos()

    init(opcion: BandejaDocumentos = .misDocumentos) {
        self.opcion = opcion
    }

    var documentosVisibles: [Documento] {
        documentos
    }

    func cargar() async {
        guard !estaCargando else { return }

        estaCargando = true
        defer { estaCargando = false }

        do {
            let lista: [Documento]

            switch opcion {
            case .misDocumentos:
                async let asignados = servicio.listar(
                    filtros: RepositorioDocumentos.Filtros(bandeja: "asignados_a_mi")
                )
                async let creados = servicio.listar(
                    filtros: RepositorioDocumentos.Filtros(bandeja: "creados_por_mi")
                )
                let (documentosAsignados, documentosCreados) = try await (asignados, creados)
                lista = deduplicar(documentosAsignados + documentosCreados)

            case .asignadosAMi:
                lista = try await servicio.listar(
                    filtros: RepositorioDocumentos.Filtros(bandeja: "asignados_a_mi")
                )

            case .pendientesTurno, .enTramite, .archivados:
                let visibles = try await servicio.listar()
                lista = visibles.filter { opcion.incluye($0) }
            }

            documentos = lista.sorted { lhs, rhs in
                (lhs.creadoEn ?? lhs.fechaRecepcion ?? .distantPast)
                    > (rhs.creadoEn ?? rhs.fechaRecepcion ?? .distantPast)
            }
            errorTexto = nil

        } catch {
            documentos = []
            errorTexto = "No se pudieron cargar los documentos. Revisa tu conexión e intenta de nuevo."
        }
    }

    func recargar() async {
        await cargar()
    }

    private func deduplicar(_ lista: [Documento]) -> [Documento] {
        var vistos = Set<String>()

        return lista.filter { documento in
            let clave = documento.uuid.isEmpty ? "id-\(documento.id)" : documento.uuid
            return vistos.insert(clave).inserted
        }
    }
}

@MainActor
final class BuscarDocumentoViewModel: ObservableObject {

    @Published var busqueda = ""
    @Published var folio = ""
    @Published var asunto = ""
    @Published var descripcion = ""
    @Published var destinatario = ""
    @Published var remitente = ""
    @Published var areaOrigen = ""
    @Published var areaDestino = ""
    @Published var tipoSeleccionado = ""
    @Published var urgenciaSeleccionada = ""
    @Published var fechaDesde: Date?
    @Published var fechaHasta: Date?

    @Published private(set) var resultados: [Documento] = []
    @Published private(set) var estaCargando = false
    @Published private(set) var haBuscado = false
    @Published var mensajeEstado: String?

    private let servicio = ServicioDocumentos()

    var hayFiltrosAvanzados: Bool {
        ![
            folio,
            asunto,
            descripcion,
            destinatario,
            remitente,
            areaOrigen,
            areaDestino,
            tipoSeleccionado,
            urgenciaSeleccionada
        ].allSatisfy { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            || fechaDesde != nil
            || fechaHasta != nil
    }

    var tipoTitulo: String {
        TipoDocumento(rawValue: tipoSeleccionado)?.etiqueta ?? "Todos"
    }

    var urgenciaTitulo: String {
        UrgenciaDocumento(rawValue: urgenciaSeleccionada)?.etiqueta ?? "Todos"
    }

    func buscar() async {
        guard !estaCargando else { return }

        let consulta = busqueda.trimmingCharacters(in: .whitespacesAndNewlines)

        guard consulta.count >= 2 || hayFiltrosAvanzados else {
            resultados = []
            haBuscado = true
            mensajeEstado = "Escribe al menos 2 caracteres o usa Filtros."
            return
        }

        estaCargando = true
        defer { estaCargando = false }

        do {
            let filtros = RepositorioDocumentos.FiltrosBusqueda(
                q: consulta.count >= 2 ? consulta : "",
                folio: folio.trimmingCharacters(in: .whitespacesAndNewlines),
                asunto: asunto.trimmingCharacters(in: .whitespacesAndNewlines),
                descripcion: descripcion.trimmingCharacters(in: .whitespacesAndNewlines),
                destinatario: destinatario.trimmingCharacters(in: .whitespacesAndNewlines),
                remitente: remitente.trimmingCharacters(in: .whitespacesAndNewlines),
                areaOrigen: areaOrigen.trimmingCharacters(in: .whitespacesAndNewlines),
                areaDestino: areaDestino.trimmingCharacters(in: .whitespacesAndNewlines),
                tipo: tipoSeleccionado,
                urgencia: urgenciaSeleccionada,
                desde: fechaISO(fechaDesde),
                hasta: fechaISO(fechaHasta)
            )

            let encontrados = try await servicio.buscar(filtros: filtros)
            resultados = encontrados.sorted { lhs, rhs in
                (lhs.creadoEn ?? lhs.fechaRecepcion ?? .distantPast)
                    > (rhs.creadoEn ?? rhs.fechaRecepcion ?? .distantPast)
            }
            haBuscado = true
            mensajeEstado = resultados.isEmpty ? "Sin resultados para los criterios indicados." : nil

        } catch {
            resultados = []
            haBuscado = true
            mensajeEstado = "No se pudo consultar el servidor. Revisa tu conexión e intenta de nuevo."
        }
    }

    func limpiarFiltros() {
        folio = ""
        asunto = ""
        descripcion = ""
        destinatario = ""
        remitente = ""
        areaOrigen = ""
        areaDestino = ""
        tipoSeleccionado = ""
        urgenciaSeleccionada = ""
        fechaDesde = nil
        fechaHasta = nil
    }

    private func fechaISO(_ fecha: Date?) -> String {
        guard let fecha else { return "" }

        let formateador = DateFormatter()
        formateador.locale = Locale(identifier: "en_US_POSIX")
        formateador.timeZone = .current
        formateador.dateFormat = "yyyy-MM-dd"
        return formateador.string(from: fecha)
    }
}
