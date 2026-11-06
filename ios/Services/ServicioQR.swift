import Foundation

/// Servicio de QR para documentos (`GET /api/documentos/<id>/qr`).
struct ServicioQR {

    private let repositorio = RepositorioDocumentos.shared

    /// Devuelve el texto del código QR del documento.
    func urlQRTexto(documentoId: Int) async throws -> String {
        let (texto, _) = try await repositorio.qr(documentoId: documentoId)
        return texto
    }
}
