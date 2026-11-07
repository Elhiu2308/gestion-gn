import Foundation

struct ServicioExpedientes {

    enum Error: LocalizedError {
        case sinExpediente
    }

    private let repositorio = RepositorioExpedientes.shared

    func crear(datos: DatosExpediente) async throws -> Expediente {
        try await repositorio.crearExpediente(datos: datos)
    }

    func obtener(id: Int) async throws -> Expediente? {
        try await repositorio.obtener(id: id)
    }

    func detalle(id: Int) async throws -> (Expediente?, [Documento]) {
        try await repositorio.detalle(id: id)
    }

    func documentos(expedienteId: Int) async throws -> [Documento] {
        try await repositorio.documentosDelExpediente(expedienteId: expedienteId)
    }
}
