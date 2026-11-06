
import Foundation
import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

/// Gestiona APNs, Firebase Cloud Messaging y las notificaciones del sistema.
///
/// APNs:
/// - Recibe el token del dispositivo.
/// - Lo entrega a Firebase Messaging.
///
/// FCM:
/// - Obtiene el token de registro FCM.
/// - Registra el token en Flask cuando existe una sesión JWT.
/// - Elimina la asociación del token al cerrar sesión.
///
/// Backend:
/// POST   /api/fcm/token
/// DELETE /api/fcm/token
final class GestorMensajesFirebase: NSObject {

    static let shared = GestorMensajesFirebase()

    private let defaults = UserDefaults.standard

    private let claveTokenFCM = "gn_fcm_token"
    private let claveTokenRegistrado = "gn_fcm_token_registrado"

    private var configurado = false

    // Controla los registros automáticos del token.
    private let estadoLock = NSLock()
    private var registroAutomaticoHabilitado = false
    private var registroEnCurso = false

    private override init() {
        super.init()
    }

    // MARK: - Configuración

    func configurar() {

        guard !configurado else {
            return
        }

        configurado = true

        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }

        Messaging.messaging().delegate = self

        UNUserNotificationCenter.current().delegate = self

        UNUserNotificationCenter.current().getNotificationSettings {
            ajustes in

            print(
                "[FCM] authorizationStatus=\(ajustes.authorizationStatus.rawValue)"
            )
        }

        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .sound, .badge]
        ) { concedido, error in

            print(
                "[FCM] Permiso concedido=\(concedido) error=\(error?.localizedDescription ?? "ninguno")"
            )

            if concedido {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }

    // MARK: - Control del registro automático

    /// Habilita el registro automático después de iniciar sesión.
    ///
    /// Debe llamarse cuando el JWT ya está guardado en Keychain.
    func habilitarRegistroAutomatico() {

        estadoLock.lock()
        registroAutomaticoHabilitado = true
        estadoLock.unlock()

        print("[FCM] Registro automático habilitado.")
    }

    /// Bloquea los registros automáticos al comenzar el cierre de sesión.
    func bloquearRegistroAutomatico() {

        estadoLock.lock()
        registroAutomaticoHabilitado = false
        estadoLock.unlock()

        print("[FCM] Registro automático bloqueado.")
    }

    private func puedeRegistrarAutomaticamente() -> Bool {

        estadoLock.lock()
        let habilitado = registroAutomaticoHabilitado
        estadoLock.unlock()

        return habilitado
    }

    /// Evita que dos solicitudes de registro se ejecuten simultáneamente.
    private func iniciarRegistro() -> Bool {

        estadoLock.lock()
        defer { estadoLock.unlock() }

        guard registroAutomaticoHabilitado,
              !registroEnCurso else {
            return false
        }

        registroEnCurso = true
        return true
    }

    private func finalizarRegistro() {

        estadoLock.lock()
        registroEnCurso = false
        estadoLock.unlock()
    }

    // MARK: - Recepción del token APNs

    /// Recibe el token APNs desde AppDelegate.
    ///
    /// APNs no se registra directamente en Flask.
    /// Firebase utiliza este token para comunicarse con APNs.
    func registrarDeviceToken(_ token: Data) {

        Messaging.messaging().apnsToken = token

        print("[APNs] Token del dispositivo entregado a Firebase")

        obtenerTokenFCM()
    }

    // MARK: - Obtener token FCM

    private func obtenerTokenFCM() {

        Messaging.messaging().token { [weak self] token, error in

            guard let self else {
                return
            }

            if let error {
                print(
                    "[FCM] Error obteniendo token: \(error.localizedDescription)"
                )
                return
            }

            guard let token, !token.isEmpty else {
                print("[FCM] Firebase devolvió un token vacío")
                return
            }

            self.guardarTokenFCM(token)

            print("[FCM] Token FCM obtenido correctamente")

            // El registro en Flask se realiza únicamente cuando
            // existe una sesión y el registro automático está habilitado.
            self.registrarAutomaticamenteSiCorresponde()
        }
    }

    // MARK: - Guardar token FCM

    private func guardarTokenFCM(_ token: String) {

        guard !token.isEmpty else {
            return
        }

        let anterior = defaults.string(forKey: claveTokenFCM)

        if anterior != token {

            defaults.set(token, forKey: claveTokenFCM)

            // Un token diferente necesita registrarse nuevamente.
            defaults.removeObject(forKey: claveTokenRegistrado)

            print("[FCM] Se detectó un token nuevo.")
        }
    }

    // MARK: - Registro automático

    private func registrarAutomaticamenteSiCorresponde() {

        guard puedeRegistrarAutomaticamente(),
              ServicioKeychain.tokenAcceso() != nil else {
            return
        }

        Task { [weak self] in

            guard let self else {
                return
            }

            do {
                try await registrarTokenFCMEnBackend()

            } catch {
                print(
                    "[FCM] Error en registro automático: " +
                    error.localizedDescription
                )
            }
        }
    }

    // MARK: - Registrar token en Flask

    /// Registra el token FCM del dispositivo en el backend.
    ///
    /// Debe ejecutarse después de iniciar sesión para disponer del JWT.
    func registrarTokenFCMEnBackend() async throws {

        guard puedeRegistrarAutomaticamente() else {
            throw ErrorFCM.registroBloqueado
        }

        guard let jwt = ServicioKeychain.tokenAcceso(),
              !jwt.isEmpty else {
            throw ErrorFCM.sesionNoDisponible
        }

        // Evita registros simultáneos.
        guard iniciarRegistro() else {
            return
        }

        defer {
            finalizarRegistro()
        }

        let token: String

        if let guardado = defaults.string(forKey: claveTokenFCM),
           !guardado.isEmpty {

            token = guardado

        } else {

            token = try await obtenerTokenFCMAsync()
            guardarTokenFCM(token)
        }

        // Se vuelve a comprobar el estado después de obtener el token.
        guard puedeRegistrarAutomaticamente(),
              let jwtActual = ServicioKeychain.tokenAcceso(),
              !jwtActual.isEmpty else {
            throw ErrorFCM.registroBloqueado
        }

        let registrado = defaults.string(
            forKey: claveTokenRegistrado
        )

        if registrado == token {
            return
        }

        let url = ConfiguracionServidor.urlCon(
            ruta: "/fcm/token"
        )

        var solicitud = URLRequest(url: url)

        solicitud.httpMethod = "POST"
        solicitud.timeoutInterval = 20

        solicitud.setValue(
            "application/json",
            forHTTPHeaderField: "Content-Type"
        )

        solicitud.setValue(
            "application/json",
            forHTTPHeaderField: "Accept"
        )

        solicitud.setValue(
            "Bearer \(jwtActual)",
            forHTTPHeaderField: "Authorization"
        )

        let cuerpo: [String: Any] = [
            "token": token,
            "plataforma": "ios",
            "dispositivo": "iPhone"
        ]

        solicitud.httpBody = try JSONSerialization.data(
            withJSONObject: cuerpo,
            options: .withoutEscapingSlashes
        )

        let (datos, respuesta) = try await URLSession.shared.data(
            for: solicitud
        )

        guard let http = respuesta as? HTTPURLResponse else {
            throw ErrorFCM.respuestaInvalida
        }

        guard (200...299).contains(http.statusCode) else {
            throw ErrorFCM.errorServidor(
                http.statusCode,
                Self.mensajeServidor(datos)
            )
        }

        // Si el cierre comenzó mientras se ejecutaba la solicitud,
        // no se marca el token como registrado.
        guard puedeRegistrarAutomaticamente() else {
            defaults.removeObject(forKey: claveTokenRegistrado)
            throw ErrorFCM.registroBloqueado
        }

        // Solo se considera registrado después de una respuesta 2xx
        // y si el registro continúa habilitado.
        defaults.set(token, forKey: claveTokenRegistrado)

        print("[FCM] Token registrado correctamente en Flask")
    }

    // MARK: - Obtener token FCM de forma asíncrona

    private func obtenerTokenFCMAsync() async throws -> String {

        try await withCheckedThrowingContinuation {
            (continuacion: CheckedContinuation<String, Swift.Error>) in

            Messaging.messaging().token { token, error in

                if let error {
                    continuacion.resume(throwing: error)
                    return
                }

                guard let token, !token.isEmpty else {
                    continuacion.resume(
                        throwing: ErrorFCM.tokenNoDisponible
                    )
                    return
                }

                continuacion.resume(returning: token)
            }
        }
    }

    // MARK: - Eliminar token al cerrar sesión

    /// Elimina la asociación del token FCM al usuario autenticado.
    ///
    /// Bloquea primero los registros automáticos.
    /// Debe ejecutarse antes de borrar el JWT del Keychain.
    func eliminarDeviceToken() async throws {

        bloquearRegistroAutomatico()

        guard let jwt = ServicioKeychain.tokenAcceso(),
              !jwt.isEmpty else {
            throw ErrorFCM.sesionNoDisponible
        }

        guard let token = defaults.string(forKey: claveTokenFCM),
              !token.isEmpty else {

            defaults.removeObject(forKey: claveTokenRegistrado)
            return
        }

        let url = ConfiguracionServidor.urlCon(
            ruta: "/fcm/token"
        )

        var solicitud = URLRequest(url: url)

        solicitud.httpMethod = "DELETE"
        solicitud.timeoutInterval = 20

        solicitud.setValue(
            "application/json",
            forHTTPHeaderField: "Content-Type"
        )

        solicitud.setValue(
            "application/json",
            forHTTPHeaderField: "Accept"
        )

        solicitud.setValue(
            "Bearer \(jwt)",
            forHTTPHeaderField: "Authorization"
        )

        let cuerpo: [String: Any] = [
            "token": token,
            "plataforma": "ios",
            "dispositivo": "iPhone"
        ]

        solicitud.httpBody = try JSONSerialization.data(
            withJSONObject: cuerpo,
            options: .withoutEscapingSlashes
        )

        do {

            let (datos, respuesta) = try await URLSession.shared.data(
                for: solicitud
            )

            guard let http = respuesta as? HTTPURLResponse else {
                throw ErrorFCM.respuestaInvalida
            }

            guard (200...299).contains(http.statusCode) else {
                throw ErrorFCM.errorServidor(
                    http.statusCode,
                    Self.mensajeServidor(datos)
                )
            }

            // El backend confirmó la eliminación.
            defaults.removeObject(forKey: claveTokenFCM)
            defaults.removeObject(forKey: claveTokenRegistrado)

            print("[FCM] Token eliminado del usuario autenticado")

        } catch {

            // Se conserva el token local para poder reintentar
            // su asociación en el siguiente inicio de sesión.
            //
            // Se elimina únicamente la marca de registro.
            defaults.removeObject(forKey: claveTokenRegistrado)

            throw error
        }
    }

    // MARK: - Utilidades

    private static func mensajeServidor(_ datos: Data) -> String {

        guard let objeto = try? JSONSerialization.jsonObject(
            with: datos
        ) as? [String: Any] else {
            return ""
        }

        return objeto["mensaje"] as? String
            ?? objeto["error"] as? String
            ?? ""
    }

    // MARK: - Errores

    enum ErrorFCM: LocalizedError {

        case sesionNoDisponible
        case tokenNoDisponible
        case respuestaInvalida
        case registroBloqueado
        case errorServidor(Int, String)

        var errorDescription: String? {

            switch self {

            case .sesionNoDisponible:
                return "No existe una sesión autenticada para registrar el dispositivo."

            case .tokenNoDisponible:
                return "Firebase todavía no ha generado el token de registro."

            case .respuestaInvalida:
                return "El servidor devolvió una respuesta no válida."

            case .registroBloqueado:
                return "El registro FCM está bloqueado durante el cierre de sesión."

            case .errorServidor(let codigo, let mensaje):
                return mensaje.isEmpty
                    ? "Error del servidor. Código HTTP: \(codigo)."
                    : mensaje
            }
        }
    }
}

// MARK: - Firebase Messaging Delegate

extension GestorMensajesFirebase: MessagingDelegate {

    /// Firebase entrega o renueva el token FCM.
    func messaging(
        _ messaging: Messaging,
        didReceiveRegistrationToken fcmToken: String?
    ) {

        guard let fcmToken, !fcmToken.isEmpty else {
            return
        }

        print("[FCM] Firebase notificó un token de registro")

        guardarTokenFCM(fcmToken)

        // Si existe sesión y el registro está habilitado,
        // actualiza la asociación del token en Flask.
        registrarAutomaticamenteSiCorresponde()
    }
}

// MARK: - Delegado de notificaciones del sistema

extension GestorMensajesFirebase: UNUserNotificationCenterDelegate {

    /// Presentación de notificaciones push con la app abierta.
    ///
    /// En primer plano no se muestra el banner del sistema: se publica el aviso
    /// emergente propio, que incluye las acciones Abrir y Ver datos. Socket.IO
    /// y APNs pueden entregar el mismo evento; AvisosNotificaciones los deduplica.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (
            UNNotificationPresentationOptions
        ) -> Void
    ) {

        let contenido = notification.request.content

        Task { @MainActor in
            AvisosNotificaciones.shared.presentar(
                desde: contenido.userInfo,
                titulo: contenido.title,
                cuerpo: contenido.body
            )
        }

        completionHandler([
            .sound,
            .badge
        ])
    }

    /// El usuario toca una notificación del sistema.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {

        let info = response.notification.request.content.userInfo

        let documentoId = Self.entero(
            info["documento_id"]
        ) ?? 0

        let notificacionId = Self.entero(
            info["notificacion_id"]
        )

        let documentoUuid = info["documento_uuid"] as? String ?? ""

        // Se confirma inmediatamente la recepción de la interacción.
        completionHandler()

        // La navegación se realiza en el hilo principal.
        Task { @MainActor in

            if documentoId > 0 {

                CentroNavegacion.shared.abrir(
                    documentoId: documentoId,
                    notificacionId: notificacionId
                )

            } else if let notificacionId, notificacionId > 0 {

                // Todavía no se navega al centro porque se requiere
                // revisar la estructura de CentroNavegacion.
                print(
                    "[FCM] Notificación seleccionada: \(notificacionId)"
                )
            }

            if !documentoUuid.isEmpty {
                print("[FCM] Documento UUID: \(documentoUuid)")
            }
        }
    }

    // MARK: - Conversión de identificadores

    private static func entero(_ valor: Any?) -> Int? {

        guard let valor else {
            return nil
        }

        if let numero = valor as? NSNumber {
            return numero.intValue
        }

        if let cadena = valor as? String {
            return Int(cadena)
        }

        return nil
    }
}
