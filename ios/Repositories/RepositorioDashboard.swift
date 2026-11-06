import Foundation

/// Repositorio del panel y de movimientos recientes (`/api/dashboard` y
/// `/api/movimientos/recientes`).
final class RepositorioDashboard {

    static let shared = RepositorioDashboard()

    private init() {}

    func resumen() async throws -> ResumenDashboard {
        let (objeto, _) = try await ejecutar(
            peticion: "GET",
            ruta: "/dashboard"
        )
        let conteo = objeto["resumen"] as? [String: Any] ?? [:]
        return ResumenDashboard(
            total: conteo["total"] as? Int ?? 0,
            recibidos: conteo["recibidos"] as? Int ?? 0,
            pendientes: conteo["pendientes"] as? Int ?? 0,
            enProceso: conteo["en_proceso"] as? Int ?? 0,
            ordinarios: conteo["ordinarios"] as? Int ?? 0,
            urgentes: conteo["urgentes"] as? Int ?? 0,
            extraurgentes: conteo["extraurgentes"] as? Int ?? 0,
            contestados: conteo["contestados"] as? Int ?? 0,
            finalizados: conteo["finalizados"] as? Int ?? 0,
            vencidos: conteo["vencidos"] as? Int ?? 0,
            porVencer: conteo["por_vencer"] as? Int ?? 0,
            delDia: conteo["del_dia"] as? Int ?? 0
        )
    }

    func movimientosRecientes() async throws -> [MovimientoReciente] {
        let (objeto, _) = try await ejecutar(
            peticion: "GET",
            ruta: "/movimientos/recientes?limite=20"
        )
        let items = objeto["movimientos"] as? [[String: Any]] ?? []
        return items.map {
            MovimientoReciente(
                id: ($0["id"] as? Int) ?? 0,
                accion: ($0["accion"] as? String) ?? "",
                documentoUuid: ($0["documento_uuid"] as? String) ?? "",
                realizadoPor: ($0["realizado_por"] as? String) ?? "",
                realizadoEn: Date(timeIntervalSince1970: ($0["realizado_en"] as? Double) ?? 0)
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
        solicitud.setValue("GestioN-iOS/1.0", forHTTPHeaderField: "User-Agent")
        if let token = ServicioKeychain.tokenAcceso() {
            solicitud.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (datos, _) = try await URLSession.shared.data(for: solicitud)
        let objeto = try JSONSerialization.jsonObject(with: datos) as? [String: Any] ?? [:]
        return (objeto, 200)
    }
}
