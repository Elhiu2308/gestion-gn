
import SwiftUI

@MainActor
final class DashboardViewModel: ObservableObject {

    enum Error: LocalizedError {
        case sinSesion
    }

    @Published private(set) var resumen: ResumenDashboard?
    @Published private(set) var movimientos: [MovimientoReciente] = []
    @Published private(set) var estaCargando = false
    @Published private(set) var errorTexto: String?

    private let repositorio = RepositorioDashboard.shared

    func limpiarError() {
        Task { @MainActor [weak self] in
            self?.errorTexto = nil
        }
    }

    func vaciarSesion() {
        ServicioKeychain.cerrarSesion()
        limpiarError()
    }

    func cargar() async {
        estaCargando = true
        defer { estaCargando = false }

        do {

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

struct MovimientoReciente: Identifiable {
    let id: Int
    let accion: String
    let documentoUuid: String
    let realizadoPor: String
    let realizadoEn: Date
}
