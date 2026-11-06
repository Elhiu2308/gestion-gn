import Foundation

/**
 * Repositorio del perfil del usuario autenticado (`/api/auth/me`,
 * `/api/dashboard`, `/api/panel/resumen`) y de las listas de documentos
 * asignados (lo que me asignan / lo que yo asigno).
 */
final class RepositorioPerfil {

    static let shared = RepositorioPerfil()

    private init() {}

    /// Perfil autenticado (`GET /api/auth/me`).
    func me() async throws -> PerfilServicio {
        let respuesta = try await RedCentral.enviar("GET", "/auth/me")
        let usuario = respuesta["usuario"] as? [String: Any] ?? respuesta
        return PerfilServicio(json: usuario)
    }

    /// Resumen del panel (`GET /api/panel/resumen`).
    func panelResumen() async throws -> ResumenPanel {
        let respuesta = try await RedCentral.enviar("GET", "/panel/resumen")
        return ResumenPanel(json: respuesta)
    }

    /// Estadísticas del dashboard (`GET /api/dashboard`).
    func dashboard() async throws -> [String: Any] {
        try await RedCentral.enviar("GET", "/dashboard")
    }

    /// Documentos asignados a mí (`GET /api/documentos?bandeja=...`).
    func asignadosAMi(bandeja: String = "asignados_a_mi") async throws -> [Documento] {
        let filtros = RepositorioDocumentos.Filtros(bandeja: bandeja)
        return try await RepositorioDocumentos.shared.listar(filtros: filtros)
    }

    /// Documentos que yo creé / asigné (se filtran por `creador_usuarios_id`).
    func asignadosPorMi() async throws -> [Documento] {
        let perfil = try await me()
        let todos = try await RepositorioDocumentos.shared.listar(
            filtros: RepositorioDocumentos.Filtros()
        )
        return todos.filter { $0.creadorId == perfil.id }
    }

    /// Cambia la contraseña (`POST /api/auth/contrasena`).
    func cambiarContrasena(actual: String, nueva: String) async throws {
        try await RedCentral.enviar("POST", "/auth/contrasena", cuerpo: ["actual": actual, "nueva": nueva])
    }

    /// Código QR del usuario (`GET /api/usuarios/<id>/qr`).
    func qrUsuario(id: Int) async throws -> (texto: String, matriz: [[Bool]]) {
        let respuesta = try await RedCentral.enviar("GET", "/usuarios/\(id)/qr")
        let texto = respuesta["texto"] as? String ?? ""
        let filas = respuesta["matriz"] as? [[Any]] ?? []
        let matriz = filas.map { fila in fila.map { celda in
            if let b = celda as? Bool { return b }
            if let i = celda as? Int { return i == 1 }
            return false
        } }
        return (texto, matriz)
    }

    /// Actividad del usuario (`GET /api/usuarios/<id>/actividad`).
    func actividad(id: Int) async throws -> [[String: Any]] {
        try await RedCentral.enviarArreglo("GET", "/usuarios/\(id)/actividad")
    }
}
