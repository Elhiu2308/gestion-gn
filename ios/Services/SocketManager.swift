
import Foundation
import UIKit
import SocketIO

struct NotificacionSocketPayload {
    let mensaje: String
    let tipo: String
    let documentoUuid: String
    let documentoId: Int
    let notificacionId: Int
    let noLeidas: Int
    let ts: Double
}

struct EventosSocketNotificaciones {
    var notificacion: ((NotificacionSocketPayload) -> Void)?
    var contador: ((Int) -> Void)?
    var unido: ((String, Int) -> Void)?
}

final class SocketManager {

    static let shared = SocketManager()

    private var manager: SocketIO.SocketManager?
    private var socket: SocketIOClient?
    private var tokenActual: String?

    private var suscripciones: [UUID: EventosSocketNotificaciones] = [:]

    private let bloqueoSuscripciones = NSLock()

    @discardableResult
    func suscribir(
        _ eventos: EventosSocketNotificaciones
    ) -> UUID {

        let id = UUID()

        bloqueoSuscripciones.lock()
        suscripciones[id] = eventos
        bloqueoSuscripciones.unlock()

        return id
    }

    func cancelarSuscripcion(_ id: UUID) {

        bloqueoSuscripciones.lock()
        suscripciones.removeValue(forKey: id)
        bloqueoSuscripciones.unlock()
    }

    private func obtenerSuscripciones() -> [EventosSocketNotificaciones] {

        bloqueoSuscripciones.lock()
        defer { bloqueoSuscripciones.unlock() }

        return Array(suscripciones.values)
    }

    private let bloqueoDeduplicacion = NSLock()
    private var identificadoresRecibidos = Set<Int>()
    private var ordenIdentificadores: [Int] = []
    private let maximoIdentificadoresRecordados = 300

    private init() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(aplicacionActiva),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    func conectar(token: String) {

        guard !token.isEmpty else {
            print("[Realtime] No se puede conectar: JWT vacío")
            return
        }

        if tokenActual == token,
           let estado = socket?.status,
           estado == .connected || estado == .connecting {
            return
        }

        let cambioUsuario = tokenActual != nil && tokenActual != token

        finalizarConexion(limpiarDeduplicacion: cambioUsuario)

        tokenActual = token

        guard let urlBase = URL(string: ConfiguracionServidor.url) else {
            print("[Realtime] URL del servidor inválida")
            return
        }

        let configuracion: SocketIOClientConfiguration = [
            .log(false),
            .compress,
            .forceWebsockets(true),
            .reconnects(true),
            .reconnectAttempts(-1),
            .reconnectWait(1),
            .reconnectWaitMax(5),
            .connectParams(["token": token])
        ]

        let nuevoManager = SocketIO.SocketManager(
            socketURL: urlBase,
            config: configuracion
        )

        let nuevoSocket = nuevoManager.socket(forNamespace: "/rt")

        manager = nuevoManager
        socket = nuevoSocket

        registrarEventos(en: nuevoSocket)

        nuevoSocket.connect()

        print("[Realtime] Iniciando conexión a /rt")
    }

    private func registrarEventos(en socket: SocketIOClient) {

        socket.on(clientEvent: .connect) { [weak self] _, _ in

            guard let self,
                  self.socket === socket else {
                return
            }

            print("[Realtime] Conectado a /rt")
            socket.emit("join")
        }

        socket.on("joined") { [weak self] datos, _ in

            guard let self,
                  let dict = datos.first as? [String: Any] else {
                return
            }

            let sala = Self.texto(dict["room"])
            let contador = Self.entero(dict["no_leidas"]) ?? 0

            print(
                "[Realtime] Unido a sala: \(sala) | No leídas: \(contador)"
            )

            DispatchQueue.main.async { [weak self] in
                guard let self else { return }

                for eventos in self.obtenerSuscripciones() {
                    eventos.unido?(sala, contador)
                    eventos.contador?(contador)
                }
            }
        }

        socket.on("notification") { [weak self] datos, _ in

            guard let self,
                  let dict = datos.first as? [String: Any] else {
                return
            }

            let contenido = dict["data"] as? [String: Any] ?? dict

            let identificador = Self.entero(
                contenido["notificacion_id"] ?? dict["notificacion_id"]
            ) ?? 0

            if identificador > 0,
               !self.registrarIdentificador(identificador) {

                print(
                    "[Realtime] Evento duplicado ignorado: \(identificador)"
                )
                return
            }

            let payload = NotificacionSocketPayload(
                mensaje: Self.texto(
                    contenido["mensaje"] ?? dict["mensaje"]
                ),
                tipo: Self.texto(
                    contenido["tipo"] ?? dict["tipo"],
                    predeterminado: "SISTEMA"
                ),
                documentoUuid: Self.texto(
                    contenido["documento_uuid"] ?? dict["documento_uuid"]
                ),
                documentoId: Self.entero(
                    contenido["documento_id"] ?? dict["documento_id"]
                ) ?? 0,
                notificacionId: identificador,
                noLeidas: Self.entero(
                    contenido["no_leidas"] ?? dict["no_leidas"]
                ) ?? -1,
                ts: Self.decimal(
                    contenido["ts"] ?? dict["ts"]
                ) ?? 0
            )

            print(
                "[Realtime] Nueva notificación: \(payload.notificacionId)"
            )

            DispatchQueue.main.async { [weak self] in
                guard let self else { return }

                for eventos in self.obtenerSuscripciones() {
                    eventos.notificacion?(payload)

                    if payload.noLeidas >= 0 {
                        eventos.contador?(payload.noLeidas)
                    }
                }
            }
        }

        socket.on("error_sala") { datos, _ in
            print("[Realtime] Error de sala: \(datos)")
        }

        socket.on("error") { datos, _ in
            print("[Realtime] Error del servidor: \(datos)")
        }

        socket.on("pong") { _, _ in
            print("[Realtime] pong")
        }

        socket.on(clientEvent: .disconnect) { datos, _ in
            print("[Realtime] Desconectado: \(datos)")
        }

        socket.on(clientEvent: .error) { datos, _ in
            print("[Realtime] Error de conexión: \(datos)")
        }
    }

    private func registrarIdentificador(_ id: Int) -> Bool {

        bloqueoDeduplicacion.lock()
        defer { bloqueoDeduplicacion.unlock() }

        guard !identificadoresRecibidos.contains(id) else {
            return false
        }

        identificadoresRecibidos.insert(id)
        ordenIdentificadores.append(id)

        if ordenIdentificadores.count > maximoIdentificadoresRecordados {
            let eliminado = ordenIdentificadores.removeFirst()
            identificadoresRecibidos.remove(eliminado)
        }

        return true
    }

    private func limpiarDeduplicacion() {

        bloqueoDeduplicacion.lock()
        defer { bloqueoDeduplicacion.unlock() }

        identificadoresRecibidos.removeAll()
        ordenIdentificadores.removeAll()
    }

    private static func entero(_ valor: Any?) -> Int? {

        guard let valor else { return nil }

        if let numero = valor as? NSNumber {
            return numero.intValue
        }

        if let cadena = valor as? String {
            return Int(cadena)
        }

        return nil
    }

    private static func decimal(_ valor: Any?) -> Double? {

        guard let valor else { return nil }

        if let numero = valor as? NSNumber {
            return numero.doubleValue
        }

        if let cadena = valor as? String {
            return Double(cadena)
        }

        return nil
    }

    private static func texto(
        _ valor: Any?,
        predeterminado: String = ""
    ) -> String {

        guard let cadena = valor as? String else {
            return predeterminado
        }

        return cadena
    }

    func ping() {
        guard socket?.status == .connected else { return }
        socket?.emit("ping")
    }

    func leave() {
        guard socket?.status == .connected else { return }
        socket?.emit("leave")
    }

    func desconectar() {
        finalizarConexion(limpiarDeduplicacion: true)
        print("[Realtime] Conexión finalizada")
    }

    private func finalizarConexion(limpiarDeduplicacion: Bool) {

        if socket?.status == .connected {
            socket?.emit("leave")
        }

        socket?.disconnect()

        socket = nil
        manager = nil
        tokenActual = nil

        if limpiarDeduplicacion {
            self.limpiarDeduplicacion()
        }
    }

    var isConnected: Bool {
        socket?.status == .connected
    }

    @objc
    private func aplicacionActiva() {

        guard let token = tokenActual ?? ServicioKeychain.tokenAcceso(),
              !token.isEmpty else {
            return
        }

        if socket?.status != .connected &&
            socket?.status != .connecting {
            conectar(token: token)
        }
    }
}
