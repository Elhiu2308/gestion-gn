
import Foundation

/// Repositorio de configuración del servidor y sincronización
/// de la aplicación GestióN.
final class RepositorioConfiguracion {

    // MARK: - Singleton

    static let shared = RepositorioConfiguracion()

    private init() {}

    // MARK: - Obtener configuración

    /// Obtiene los datos de configuración almacenados localmente.
    ///
    /// La URL y las preferencias se administran mediante
    /// ConfiguracionServidor y UserDefaults.
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

    // MARK: - Sincronización

    /// Ejecuta la sincronización con el backend Flask.
    ///
    /// Endpoint: POST /api/sync
    func sincronizar() async throws {

        _ = try await RedCentral.enviar(
            "POST",
            "/sync"
        )

        // Actualiza la fecha de la última sincronización.
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

    // MARK: - Guardar preferencias

    /// Guarda la URL del servidor y el modo de sincronización automática.
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
