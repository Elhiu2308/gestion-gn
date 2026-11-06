import Foundation

/**
 * Servicio de autenticación contra `/api/auth/ *`.
 *
 - login(usuario:contrasena:)
 - renovarAcceso()
 */
struct ServicioAutenticacion {

    enum Error: LocalizedError {
        case credencialesInvalidas
        case sinSesion
        case servidorNoDisponible(String)
    }

    private let repositorio = RepositorioAutenticacion.shared

    func login(usuario: String, contrasena: String) async throws -> TokenSesion {
        let respuesta = try await repositorio.login(usuario: usuario, contrasena: contrasena)
        return TokenSesion(
            accessToken: respuesta.accessToken,
            refreshToken: respuesta.refreshToken,
            usuario: respuesta.usuario ?? usuario,
            nombre: respuesta.nombre ?? "",
            nombreCompleto: respuesta.nombreCompleto ?? "",
            rol: respuesta.rol ?? "",
            cargo: respuesta.cargo ?? "",
            grado: respuesta.grado ?? "",
            area: respuesta.area ?? "",
            subdireccion: respuesta.subdireccion ?? "",
            correo: respuesta.correo ?? "",
            permisos: Set(respuesta.permisos)
        )
    }

    /// Renueva el access token. No requiere volver a leer el perfil: los
    /// campos del perfil se sincronizan cuando la pantalla lo solicite.
    func renovarAcceso() async throws -> TokenSesion {
        let respuesta = try await repositorio.renovarAcceso()
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

    /// Valida la sesión contra el backend (`GET /api/auth/me`).
    func validarSesion() async throws {
        _ = try await repositorio.me()
    }
}
