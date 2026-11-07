
import Foundation
import SwiftUI
import UIKit

struct ServicioNotificaciones {

    private let repositorio = RepositorioNotificaciones.shared
    private let socket = SocketManager.shared

    func centro() async throws -> CentroNotificaciones {
        try await repositorio.centro()
    }

    func historial(
        masLeidas: Bool
    ) async throws -> [Notificacion] {
        try await repositorio.historial(
            masLeidas: masLeidas
        )
    }

    func marcarLeida(id: Int) async throws {
        try await repositorio.marcarLeida(id: id)
    }

    func marcarTodasLeidas() async throws {
        try await repositorio.marcarTodasLeidas()
    }

    func conectar(token: String) {
        guard !token.isEmpty else {
            print("[Notificaciones] No se puede conectar sin JWT.")
            return
        }

        socket.conectar(token: token)
    }

    func conectarConTokenGuardado() {
        guard
            let token = ServicioKeychain.tokenAcceso(),
            !token.isEmpty
        else {
            print("[Notificaciones] No existe un JWT guardado.")
            return
        }

        conectar(token: token)
    }

    func desconectar() {
        socket.desconectar()
    }
}

enum NotificacionPresentacion {

    static func titulo(
        tipo: String,
        documentoId: Int? = nil
    ) -> String {

        if tipo.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return (documentoId ?? 0) > 0
                ? "Documento asignado"
                : "Sistema"
        }

        switch tipo.uppercased() {
        case "DOCUMENTO_ASIGNADO": return "Documento asignado"
        case "DOCUMENTO_COMPARTIDO": return "Documento compartido"
        case "DOCUMENTO_RECIBIDO": return "Documento recibido"
        case "ANOTACION": return "Anotación"
        case "RESPUESTA": return "Respuesta"
        case "RESPUESTA_RECHAZADA": return "Respuesta rechazada"
        case "CORRECCION": return "Corrección"
        case "VALIDACION": return "Validación"
        case "ATENCION": return "Atención requerida"
        case "VENCIMIENTO": return "Vencimiento"
        default: return "Sistema"
        }
    }

    static func esDocumento(tipo: String, documentoId: Int?) -> Bool {

        let tipo = tipo.uppercased()

        if (documentoId ?? 0) > 0 {
            return true
        }

        return tipo.contains("DOCUMENTO")
            || ["ANOTACION", "RESPUESTA", "CORRECCION", "VALIDACION", "ATENCION", "VENCIMIENTO"]
                .contains(tipo)
    }
}

struct AvisoNotificacion: Identifiable, Equatable {

    let id = UUID()
    let notificacionId: Int
    let tipo: String
    let titulo: String
    let mensaje: String
    let documentoId: Int?
    let documentoUuid: String

    var tieneDocumento: Bool {
        (documentoId ?? 0) > 0 || !documentoUuid.isEmpty
    }
}

@MainActor
final class AvisosNotificaciones: ObservableObject {

    static let shared = AvisosNotificaciones()

    @Published private(set) var aviso: AvisoNotificacion?

    private var clavesMostradas = Set<String>()
    private var ordenClaves: [String] = []
    private var suscripcion: UUID?

    private init() {

        suscripcion = SocketManager.shared.suscribir(
            EventosSocketNotificaciones(
                notificacion: { [weak self] payload in

                    Task { @MainActor [weak self] in
                        self?.presentar(payload: payload)
                    }
                },
                contador: nil,
                unido: nil
            )
        )
    }

    func presentar(payload: NotificacionSocketPayload) {

        guard UIApplication.shared.applicationState == .active else {
            return
        }

        presentar(
            notificacionId: payload.notificacionId,
            tipo: payload.tipo,
            mensaje: payload.mensaje,
            documentoId: payload.documentoId,
            documentoUuid: payload.documentoUuid
        )
    }

    func presentar(
        desde info: [AnyHashable: Any],
        titulo tituloPush: String,
        cuerpo: String
    ) {

        let tipo = (info["tipo"] as? String) ?? "SISTEMA"

        let mensaje = (info["mensaje"] as? String)
            .flatMap { $0.isEmpty ? nil : $0 }
            ?? (cuerpo.isEmpty ? tituloPush : cuerpo)

        presentar(
            notificacionId: Self.entero(info["notificacion_id"]) ?? 0,
            tipo: tipo,
            mensaje: mensaje,
            documentoId: Self.entero(info["documento_id"]) ?? 0,
            documentoUuid: (info["documento_uuid"] as? String) ?? ""
        )
    }

    func descartar(_ aviso: AvisoNotificacion) {

        if self.aviso?.id == aviso.id {
            self.aviso = nil
        }
    }

    private func presentar(
        notificacionId: Int,
        tipo: String,
        mensaje: String,
        documentoId: Int,
        documentoUuid: String
    ) {

        let mensaje = mensaje.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !mensaje.isEmpty else {
            return
        }

        var claves = ["doc:\(documentoId):\(mensaje)"]

        if notificacionId > 0 {
            claves.append("id:\(notificacionId)")
        }

        if claves.contains(where: { clavesMostradas.contains($0) }) {
            return
        }

        for clave in claves {
            clavesMostradas.insert(clave)
            ordenClaves.append(clave)
        }

        if ordenClaves.count > 400 {
            let sobrantes = ordenClaves.count - 300
            ordenClaves.prefix(sobrantes).forEach {
                clavesMostradas.remove($0)
            }
            ordenClaves.removeFirst(sobrantes)
        }

        aviso = AvisoNotificacion(
            notificacionId: notificacionId,
            tipo: tipo,
            titulo: NotificacionPresentacion.titulo(
                tipo: tipo,
                documentoId: documentoId > 0 ? documentoId : nil
            ),
            mensaje: mensaje,
            documentoId: documentoId > 0 ? documentoId : nil,
            documentoUuid: documentoUuid
        )
    }

    private static func entero(_ valor: Any?) -> Int? {

        if let valor = valor as? Int { return valor }
        if let valor = valor as? NSNumber { return valor.intValue }
        if let valor = valor as? String { return Int(valor) }

        return nil
    }
}
