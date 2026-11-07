import Foundation

final class RepositorioArchivos {

    static let shared = RepositorioArchivos()

    private init() {}

    func listar() async throws -> [DocumentoArchivo] {
        let (respuesta, _) = try await ejecutar(
            peticion: "GET",
            ruta: "/archivo"
        )
        return (respuesta["documentos"] as? [[String: Any]] ?? []).map {
            DocumentoArchivo(
                id: ($0["id"] as? Int) ?? 0,
                uuid: ($0["uuid"] as? String) ?? "",
                folio: ($0["folio"] as? String) ?? "",
                asunto: ($0["asunto"] as? String) ?? "",
                estado: ($0["estado"] as? String) ?? "",
                areaActual: ($0["area_actual"] as? String) ?? "",
                usuarioAsignado: ($0["usuario_asignado"] as? String) ?? "",
                archivo: ($0["archivo"] as? String) ?? "",
                creadoEn: Documento.fecha($0["creado_en"])
                    ?? Documento.fecha($0["fecha_recepcion"])
                    ?? .distantPast,
                tipo: ($0["tipo"] as? String) ?? "",
                clasificacion: ($0["clasificacion"] as? String) ?? "",
                fechaLimite: ($0["fecha_limite"] as? String) ?? "",
                diasRestantes: ($0["dias_restantes"] as? Int)
                    ?? ($0["dias_restantes"] as? String).flatMap(Int.init),
                archivado: true
            )
        }
    }

    private func ejecutar(
        peticion: String,
        ruta: String
    ) async throws -> ([String: Any], Int) {
        var solicitud = URLRequest(url: ConfiguracionServidor.urlCon(ruta: ruta))
        solicitud.timeoutInterval = 20
        solicitud.httpMethod = peticion
        solicitud.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = ServicioKeychain.tokenAcceso() {
            solicitud.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (datos, respuesta) = try await URLSession.shared.data(for: solicitud)
        let codigo = (respuesta as? HTTPURLResponse)?.statusCode ?? 0
        let objeto = try JSONSerialization.jsonObject(with: datos) as? [String: Any] ?? [:]
        return (objeto, codigo)
    }
}
