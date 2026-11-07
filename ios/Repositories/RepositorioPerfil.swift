import Foundation

final class RepositorioPerfil {

    static let shared = RepositorioPerfil()

    private init() {}

    func me() async throws -> PerfilServicio {
        let respuesta = try await RedCentral.enviar("GET", "/auth/me")
        let usuario = respuesta["usuario"] as? [String: Any] ?? respuesta
        return PerfilServicio(json: usuario)
    }

    func panelResumen() async throws -> ResumenPanel {
        let respuesta = try await RedCentral.enviar("GET", "/panel/resumen")
        return ResumenPanel(json: respuesta)
    }

    func dashboard() async throws -> [String: Any] {
        try await RedCentral.enviar("GET", "/dashboard")
    }

    func asignadosAMi(bandeja: String = "asignados_a_mi") async throws -> [Documento] {
        let filtros = RepositorioDocumentos.Filtros(bandeja: bandeja)
        return try await RepositorioDocumentos.shared.listar(filtros: filtros)
    }

    func asignadosPorMi() async throws -> [Documento] {
        let perfil = try await me()
        let todos = try await RepositorioDocumentos.shared.listar(
            filtros: RepositorioDocumentos.Filtros()
        )
        return todos.filter { $0.creadorId == perfil.id }
    }

    func cambiarContrasena(actual: String, nueva: String) async throws {
        try await RedCentral.enviar("POST", "/auth/contrasena", cuerpo: ["actual": actual, "nueva": nueva])
    }

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

    func actividad(id: Int) async throws -> [[String: Any]] {
        try await RedCentral.enviarArreglo("GET", "/usuarios/\(id)/actividad")
    }
}
