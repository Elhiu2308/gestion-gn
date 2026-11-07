import Foundation

struct ServicioQR {

    private let repositorio = RepositorioDocumentos.shared

    func urlQRTexto(documentoId: Int) async throws -> String {
        let (texto, _) = try await repositorio.qr(documentoId: documentoId)
        return texto
    }
}
