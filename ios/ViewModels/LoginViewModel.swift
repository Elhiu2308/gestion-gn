
import Foundation
import Combine
import LocalAuthentication

@MainActor
final class LoginViewModel: ObservableObject {

    enum Error: LocalizedError {
        case credencialesInvalidas
        case sinSesion
        case servidorNoDisponible(String)

        var mensaje: String {
            switch self {
            case .credencialesInvalidas:
                return "Usuario o contraseña incorrectos."

            case .sinSesion:
                return "No hay sesión activa."

            case .servidorNoDisponible(let detalle):
                return detalle.isEmpty
                    ? "El servidor no está disponible."
                    : detalle
            }
        }

        var errorDescription: String? {
            mensaje
        }
    }

    @Published var usuario = ""
    @Published var contrasena = ""
    @Published var activarBiometria = false

    @Published private(set) var estaCargando = false
    @Published private(set) var errorTexto: String?
    @Published private(set) var sesionActiva = false

    private let servicioAutenticacion = ServicioAutenticacion()

    var tieneAccesoBiometrico: Bool {
        ServicioKeychain.tieneAccesoBiometrico()
    }

    var nombreUsuarioBiometrico: String {
        ServicioKeychain.nombreUsuarioBiometrico()
    }

    var tipoBiometria: (
        disponible: Bool,
        nombre: String,
        icono: String
    ) {
        ServicioKeychain.tipoBiometria()
    }

    func iniciarSesion() async {
        guard !estaCargando else {
            return
        }

        estaCargando = true
        defer { estaCargando = false }

        errorTexto = nil

        let usuarioLimpio = usuario.trimmingCharacters(
            in: .whitespacesAndNewlines
        )

        guard !usuarioLimpio.isEmpty, !contrasena.isEmpty else {
            errorTexto = "Ingresa tu usuario y contraseña."
            return
        }

        do {
            let sesion = try await servicioAutenticacion.login(
                usuario: usuarioLimpio,
                contrasena: contrasena
            )

            guard !sesion.accessToken.isEmpty else {
                errorTexto = "El servidor no proporcionó un token de acceso válido."
                return
            }

            guard aplicar(sesion) else {
                errorTexto = "No se pudo guardar la sesión de forma segura en el dispositivo."
                return
            }

            if activarBiometria {
                let biometriaGuardada = ServicioKeychain.activarBiometria(
                    usuario: usuarioLimpio,
                    contrasena: contrasena,
                    sesion: sesion
                )

                if !biometriaGuardada {
                    errorTexto = "La sesión inició correctamente, pero no se pudo activar el acceso biométrico."
                }
            }

        } catch {
            errorTexto = mensajeError(error)
        }
    }

    func accederConBiometria() async {
        guard !estaCargando else {
            return
        }

        estaCargando = true
        defer { estaCargando = false }

        errorTexto = nil

        let contexto = LAContext()
        contexto.localizedCancelTitle = "Usar contraseña"

        var errorLA: NSError?

        guard contexto.canEvaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            error: &errorLA
        ) else {
            errorTexto = "El sensor biométrico no está disponible en este momento."
            return
        }

        let motivo = "Identifícate con \(tipoBiometria.nombre) para acceder a GestióN"

        do {
            try await contexto.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: motivo
            )
        } catch {
            let codigo = (error as? LAError)?.code

            if codigo == .userCancel ||
                codigo == .appCancel ||
                codigo == .systemCancel {
                return
            }

            errorTexto = "No se pudo verificar la identidad biométrica."
            return
        }

        if let tokenRefresco = ServicioKeychain.tokenRefreshGuardado(),
           !tokenRefresco.isEmpty {

            do {
                let sesion = try await servicioAutenticacion.renovarConRefreshToken(
                    tokenRefresco
                )

                guard !sesion.accessToken.isEmpty else {
                    throw RepositorioAutenticacion.Error.tokenNoRecibido
                }

                guard ServicioKeychain.guardarTokens(
                    accessToken: sesion.accessToken,
                    refreshToken: sesion.refreshToken
                ) else {
                    throw LoginViewModel.Error.servidorNoDisponible(
                        "No se pudo actualizar la sesión de forma segura."
                    )
                }

                sesionActiva = true
                return

            } catch {

            }
        }

        guard let credenciales = ServicioKeychain.credencialesBiometricas() else {
            errorTexto = "No se encontraron credenciales biométricas guardadas. Inicia sesión con tu usuario y contraseña."
            return
        }

        do {
            let sesion = try await servicioAutenticacion.login(
                usuario: credenciales.usuario,
                contrasena: credenciales.contrasena
            )

            guard !sesion.accessToken.isEmpty else {
                errorTexto = "El servidor no proporcionó un token de acceso válido."
                return
            }

            guard ServicioKeychain.guardarSesion(sesion) else {
                errorTexto = "No se pudo guardar la sesión de forma segura en el dispositivo."
                return
            }

            sesionActiva = true

        } catch {
            errorTexto = mensajeError(error)
        }
    }

    func desactivarBiometria() {
        guard ServicioKeychain.desactivarBiometria() else {
            errorTexto = "No se pudo desactivar el acceso biométrico."
            return
        }

        errorTexto = nil
    }

    @discardableResult
    private func aplicar(_ sesion: TokenSesion) -> Bool {
        guard ServicioKeychain.guardarSesion(sesion) else {
            sesionActiva = false
            return false
        }

        sesionActiva = true
        return true
    }

    private func mensajeError(_ error: Swift.Error) -> String {
        if let errorAutenticacion = error as? LoginViewModel.Error {
            return errorAutenticacion.mensaje
        }

        return error.localizedDescription
    }
}

extension ServicioAutenticacion {

    func renovarConRefreshToken(
        _ refresco: String
    ) async throws -> TokenSesion {

        let respuesta = try await RepositorioAutenticacion.shared
            .renovarAcceso(conRefreshToken: refresco)

        guard !respuesta.accessToken.isEmpty else {
            throw RepositorioAutenticacion.Error.tokenNoRecibido
        }

        return TokenSesion(
            accessToken: respuesta.accessToken,
            refreshToken: respuesta.refreshToken,
            usuario: "",
            nombre: "",
            nombreCompleto: "",
            rol: "",
            cargo: "",
            grado: "",
            area: "",
            subdireccion: "",
            correo: "",
            permisos: []
        )
    }
}
