
import SwiftUI

/// ViewModel de la pantalla de inicio (dashboard).
///
/// Obtiene el resumen desde `/api/dashboard` y los movimientos recientes desde
/// `/api/movimientos/recientes`, y ofrece acceso rápido a las pantallas principales.

@MainActor
final class DashboardViewModel: ObservableObject {

    enum Error: LocalizedError {
        case sinSesion
    }

    // MARK: - Estado

    @Published private(set) var resumen: ResumenDashboard?
    @Published private(set) var movimientos: [MovimientoReciente] = []
    @Published private(set) var estaCargando = false
    @Published private(set) var errorTexto: String?

    // MARK: - Dependencias

    private let repositorio = RepositorioDashboard.shared

    // MARK: - Gestión de errores

    func limpiarError() {
        Task { @MainActor [weak self] in
            self?.errorTexto = nil
        }
    }

    // MARK: - Sesión

    /// Cierra la sesión guardada en el Keychain.
    func vaciarSesion() {
        ServicioKeychain.cerrarSesion()
        limpiarError()
    }

    // MARK: - Carga del dashboard

    func cargar() async {
        estaCargando = true
        defer { estaCargando = false }

        do {
            // Las dos consultas son independientes: se lanzan en paralelo para
            // que el panel aparezca en cuanto responde la más lenta.
            async let resumenServidor = repositorio.resumen()
            async let movimientosServidor = repositorio.movimientosRecientes()

            let (resumenCargado, movimientosCargados) = try await (
                resumenServidor,
                movimientosServidor
            )

            resumen = resumenCargado
            movimientos = movimientosCargados
            errorTexto = nil

        } catch {
            errorTexto = "No se pudo cargar el panel."
        }
    }
}

// MARK: - Modelos

/// Datos que devuelve GET /api/dashboard.
struct ResumenDashboard {
    let total: Int
    let recibidos: Int
    let pendientes: Int
    let enProceso: Int
    let ordinarios: Int
    let urgentes: Int
    let extraurgentes: Int
    let contestados: Int
    let finalizados: Int
    let vencidos: Int
    let porVencer: Int
    let delDia: Int
}

/// Movimiento registrado en /api/movimientos/recientes.
struct MovimientoReciente: Identifiable {
    let id: Int
    let accion: String
    let documentoUuid: String
    let realizadoPor: String
    let realizadoEn: Date
}
