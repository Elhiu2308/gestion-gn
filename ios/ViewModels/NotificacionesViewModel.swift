
import Foundation
import SwiftUI

@MainActor
final class NotificacionesViewModel: ObservableObject {

    @Published private(set) var notificaciones: [Notificacion] = []
    @Published private(set) var noLeidas: Int = 0
    @Published private(set) var estaCargando = false
    @Published private(set) var mensajeError: String?

    private let repositorio = RepositorioNotificaciones.shared
    private let socket = SocketManager.shared

    private var suscripcionSocket: UUID?

    private var tareaCarga: Task<Void, Never>?
    private var cargaPendiente = false

    init() {
        registrarEventosSocket()
    }

    deinit {
        if let suscripcionSocket {
            SocketManager.shared.cancelarSuscripcion(suscripcionSocket)
        }
    }

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

                        await self.cargar()
                    }
                }
            )
        )
    }

    private func recibirSocket(
        _ payload: NotificacionSocketPayload
    ) async {

        if payload.noLeidas >= 0 {
            noLeidas = payload.noLeidas
        }

        await cargar()
    }

    func cargar() async {

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

                    self.noLeidas = max(0, centro.noLeidas)
                    self.notificaciones = centro.recientes
                    self.mensajeError = nil

                } catch {

                    self.mensajeError = error.localizedDescription

                }

            } while self.cargaPendiente

            self.estaCargando = false
            self.tareaCarga = nil
        }

        tareaCarga = tarea

        await tarea.value
    }

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

    func marcarLeida(id: Int) async {

        guard id > 0 else {
            return
        }

        do {

            try await repositorio.marcarLeida(id: id)

            await cargar()

        } catch {

            mensajeError = error.localizedDescription
        }
    }

    func marcarTodasLeidas() async {

        guard noLeidas > 0 else {
            return
        }

        do {

            try await repositorio.marcarTodasLeidas()

            await cargar()

        } catch {

            mensajeError = error.localizedDescription
        }
    }

    func limpiarError() {
        mensajeError = nil
    }
}
