import Foundation
import LocalAuthentication
import CryptoKit

struct ServicioAutenticacionBiometrica {

    enum Error: LocalizedError {
        case biometricoNoDisponible
        case accesoDenegado
    }

    private let repositorioAutenticacion = RepositorioAutenticacion.shared

    func solicitarAutenticacionBiometrica() async throws -> TokenSesion {
        let refreshToken = try await ServicioKeychain.refreshTokenBiometrico()
        return try await renovarAccesoConRefreshToken(refreshToken)
    }

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

    func estaHabilitado() -> Bool {
        ServicioKeychain.tieneAccesoBiometrico()
    }
}
