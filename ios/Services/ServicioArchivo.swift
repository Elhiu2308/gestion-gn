import Foundation

/**
 * Servicio del archivo histórico (`/api/archivo`).
 */
struct ServicioArchivo {

    enum Error: LocalizedError {
        case sinArchivos
    }

    private let repositorio = RepositorioArchivos.shared

    func listar() async throws -> [DocumentoArchivo] {
        try await repositorio.listar()
    }
}
