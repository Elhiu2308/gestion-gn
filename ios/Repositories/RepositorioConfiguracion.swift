
import Foundation

final class RepositorioConfiguracion {

    static let shared = RepositorioConfiguracion()

    private init() {}

    func datosServidor() async throws -> DatosServidor {

        let ultima = ConfiguracionServidor.ultimaSincronizacion
        let siguiente = ConfiguracionServidor.proxima

        return DatosServidor(
            url: ConfiguracionServidor.url,
            sincronizacionAutomatica: ConfiguracionServidor.automatica,
            ultimaSincronizacion: ultima == .distantPast ? nil : ultima,
            fechaSiguienteSincronizacion: siguiente == .distantPast ? nil : siguiente
        )
    }

    func sincronizar() async throws {

        _ = try await RedCentral.enviar(
            "POST",
            "/sync"
        )

        let ahora = Date()

        let formato = String(ahora.timeIntervalSince1970)

        let almacen = UserDefaults.standard

        if let configuracion = almacen.object(
            forKey: "configuracion"
        ) as? NSDictionary {

            let copia = configuracion.mutableCopy() as! NSMutableDictionary

            copia["ultima"] = formato

            almacen.set(
                copia,
                forKey: "configuracion"
            )
        }
    }

    func guardarConfiguracion(
        url: String,
        sincronizacionAutomatica: Bool
    ) throws {

        let direccion = url.trimmingCharacters(
            in: .whitespacesAndNewlines
        )

        guard
            let componente = URLComponents(string: direccion),
            let esquema = componente.scheme,
            let host = componente.host,
            ["http", "https"].contains(esquema.lowercased()),
            !host.isEmpty
        else {
            throw URLError(.badURL)
        }

        ConfiguracionServidor.guardar(
            url: direccion,
            automatica: sincronizacionAutomatica
        )
    }
}
