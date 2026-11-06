import Foundation
import LocalAuthentication
import CryptoKit

/// Servicio de acceso biométrico (Face ID / Touch ID) y almacenamiento seguro
/// de la sesión en el Keychain.
struct ServicioAutenticacionBiometrica {

    enum Error: LocalizedError {
        case biometricoNoDisponible
        case accesoDenegado
    }

    private let repositorioAutenticacion = RepositorioAutenticacion.shared

    /// Solicita Face ID / Touch ID y renueva la sesión con el refresh token cifrado.
    func solicitarAutenticacionBiometrica() async throws -> TokenSesion {
        let refreshToken = try await ServicioKeychain.refreshTokenBiometrico()
        return try await renovarAccesoConRefreshToken(refreshToken)
    }

    /// Renueva el access token a partir del refresh token biométrico.
    func renovarAccesoConRefreshToken(_ refreshToken: String) async throws -> TokenSesion {
        let respuesta = try await repositorioAutenticacion.renovarAcceso(conRefreshToken: refreshToken)
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

    /// Indica si hay un refresh token biométrico disponible para este usuario.
    func estaHabilitado() -> Bool {
        ServicioKeychain.tieneAccesoBiometrico()
    }
}
