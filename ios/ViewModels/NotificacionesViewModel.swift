
import Foundation
import SwiftUI

/// ViewModel del centro de notificaciones.
///
/// Flask es la fuente oficial del historial,
/// contador y estado de lectura.
///
/// Socket.IO utiliza el evento canónico "notification".
@MainActor
final class NotificacionesViewModel: ObservableObject {

    // MARK: - Estado

    @Published private(set) var notificaciones: [Notificacion] = []
    @Published private(set) var noLeidas: Int = 0
    @Published private(set) var estaCargando = false
    @Published private(set) var mensajeError: String?

    // MARK: - Dependencias

    private let repositorio = RepositorioNotificaciones.shared
    private let socket = SocketManager.shared

    // MARK: - Suscripción Socket.IO

    private var suscripcionSocket: UUID?

    // MARK: - Control de consultas

    private var tareaCarga: Task<Void, Never>?
    private var cargaPendiente = false

    // MARK: - Inicialización

    init() {
        registrarEventosSocket()
    }

    deinit {
        if let suscripcionSocket {
            SocketManager.shared.cancelarSuscripcion(suscripcionSocket)
        }
    }

    // MARK: - Registro de eventos Socket.IO

    private func registrarEventosSocket() {

        suscripcionSocket = socket.suscribir(
            EventosSocketNotificaciones(

                notificacion: { [weak self] payload in

                    Task { @MainActor [weak self] in
                        await self?.recibirSocket(payload)
                    }
                },

                contador: { [weak self] contador in

                    Task { @MainActor [weak self] in

                        guard let self,
                              contador >= 0 else {
                            return
                        }

                        // Contador provisional.
                        // Flask confirmará el valor definitivo.
                        self.noLeidas = contador
                    }
                },

                unido: { [weak self] _, contador in

                    Task { @MainActor [weak self] in

                        guard let self else {
                            return
                        }

                        if contador >= 0 {
                            self.noLeidas = contador
                        }

                        // Al ingresar a la sala se sincroniza
                        // el centro con el backend.
                        await self.cargar()
                    }
                }
            )
        )
    }

    // MARK: - Recepción Socket.IO

    private func recibirSocket(
        _ payload: NotificacionSocketPayload
    ) async {

        // El contador recibido por Socket.IO es provisional.
        if payload.noLeidas >= 0 {
            noLeidas = payload.noLeidas
        }

        // Flask proporciona el historial y contador oficiales.
        await cargar()
    }

    // MARK: - Centro de notificaciones

    func cargar() async {

        // Si existe una consulta activa, solicita otra
        // actualización al terminar la consulta actual.
        if let tareaCarga {

            cargaPendiente = true

            await tareaCarga.value
            return
        }

        let tarea = Task { @MainActor [weak self] in

            guard let self else {
                return
            }

            repeat {

                self.cargaPendiente = false
                self.estaCargando = true

                do {

                    let centro = try await self.repositorio.centro()

                    // Datos oficiales del backend.
                    self.noLeidas = max(0, centro.noLeidas)
                    self.notificaciones = centro.recientes
                    self.mensajeError = nil

                } catch {

                    self.mensajeError = error.localizedDescription

                    // Se conservan los datos anteriores
                    // si falla la comunicación.
                }

            } while self.cargaPendiente

            self.estaCargando = false
            self.tareaCarga = nil
        }

        tareaCarga = tarea

        await tarea.value
    }

    // MARK: - Historial

    func historial(masLeidas: Bool) async {

        estaCargando = true

        defer {
            estaCargando = false
        }

        do {

            let resultado = try await repositorio.historial(
                masLeidas: masLeidas
            )

            notificaciones = resultado
            mensajeError = nil

        } catch {

            mensajeError = error.localizedDescription
        }
    }

    // MARK: - Marcar una notificación como leída

    func marcarLeida(id: Int) async {

        guard id > 0 else {
            return
        }

        do {

            try await repositorio.marcarLeida(id: id)

            // Flask confirma el cambio.
            // Después se actualizan historial y contador.
            await cargar()

        } catch {

            mensajeError = error.localizedDescription
        }
    }

    // MARK: - Marcar todas como leídas

    func marcarTodasLeidas() async {

        guard noLeidas > 0 else {
            return
        }

        do {

            try await repositorio.marcarTodasLeidas()

            // No se modifica el contador localmente.
            // Se consulta nuevamente el valor oficial.
            await cargar()

        } catch {

            mensajeError = error.localizedDescription
        }
    }

    // MARK: - Limpieza

    func limpiarError() {
        mensajeError = nil
    }
}
