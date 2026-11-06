import Foundation

/**
 * Servicio de configuración (servidor, sincronización y preferencias locales).
 */
struct ServicioConfiguracion {

    enum Error: LocalizedError {
        case sinConfiguracion
    }

    private let repositorio = RepositorioConfiguracion.shared

    func datosServidor() async throws -> DatosServidor {
        try await repositorio.datosServidor()
    }

    /// Sincroniza los cambios locales con el backend (`POST /api/sync`).
    func sincronizar() async throws {
        try await repositorio.sincronizar()
    }

    /// Busca un servidor de GestióN accesible en la red local.
    ///
    /// Sondea la URL actual y un conjunto de direcciones habituales; devuelve la
    /// primera que responda.
    func buscarServidorEnRed(partiendoDe actual: String) async -> String? {
        var candidatos = [actual]
        candidatos.append(contentsOf: [
            "http://192.168.0.3:5000",
            "http://192.168.1.161:5000",
            "http://192.168.1.1:5000",
            "http://localhost:5000"
        ])
        for candidato in candidatos {
            let limpio = candidato.trimmingCharacters(in: .whitespaces)
            if await ServicioConfiguracion.sondea(limpio) {
                return limpio
            }
        }
        return nil
    }

    /// Comprueba si una dirección base responde (cualquier código HTTP cuenta).
    static func sondea(_ base: String) async -> Bool {
        let limpia = base.hasSuffix("/") ? String(base.dropLast()) : base
        guard let url = URL(string: "\(limpia)/api") else { return false }
        var solicitud = URLRequest(url: url)
        solicitud.timeoutInterval = 2.5
        solicitud.httpMethod = "GET"
        do {
            _ = try await URLSession.shared.data(for: solicitud)
            return true
        } catch {
            return false
        }
    }
}
