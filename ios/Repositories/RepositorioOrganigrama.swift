import Foundation

final class RepositorioOrganigrama {

    static let shared = RepositorioOrganigrama()

    private init() {}

    func direccionesGeneralesPadre() async throws -> [NivelOrganizacional] {
        let filas = try await RedCentral.enviarArreglo(
            "GET",
            "/organigrama/direcciones-generales",
            consulta: [URLQueryItem(name: "solo_padre", value: "1")]
        )
        return filas.map(NivelOrganizacional.init(json:))
    }

    func hijos(_ nivel: NivelOrganigrama, padreId: Int, incluirInactivos: Bool = false) async throws -> [NivelOrganizacional] {
        let nivelPadre = nivel.parametroPadre
        var consulta: [URLQueryItem] = []
        if let nivelPadre { consulta.append(URLQueryItem(name: nivelPadre, value: "\(padreId)")) }
        if incluirInactivos { consulta.append(URLQueryItem(name: "incluir_inactivos", value: "1")) }
        let filas = try await RedCentral.enviarArreglo("GET", "/organigrama/\(nivel.rawValue)", consulta: consulta)
        return filas.map(NivelOrganizacional.init(json:))
    }

    func arbol() async throws -> [[String: Any]] {
        try await RedCentral.enviarArreglo("GET", "/organigrama/arbol")
    }

    @discardableResult
    func crear(nivel: NivelOrganigrama, nombre: String, idPadre: Int?) async throws -> [String: Any] {
        var cuerpo: [String: Any] = ["nombre": nombre]
        if let idPadre { cuerpo["id_padre"] = idPadre } else { cuerpo["id_padre"] = NSNull() }
        return try await RedCentral.enviar("POST", "/organigrama/\(nivel.rawValue)", cuerpo: cuerpo)
    }

    @discardableResult
    func editar(nivel: NivelOrganigrama, id: Int, nombre: String, estatus: Bool = true, idPadre: Int? = nil) async throws -> [String: Any] {
        var cuerpo: [String: Any] = ["nombre": nombre, "estatus": estatus]
        if let idPadre { cuerpo["id_padre"] = idPadre } else { cuerpo["id_padre"] = NSNull() }
        return try await RedCentral.enviar("PUT", "/organigrama/\(nivel.rawValue)/\(id)", cuerpo: cuerpo)
    }

    func eliminar(nivel: NivelOrganigrama, id: Int) async throws {
        try await RedCentral.enviar("DELETE", "/organigrama/\(nivel.rawValue)/\(id)")
    }

    func personas(filtros: [String: String] = [:]) async throws -> [PersonaCatalogo] {
        var consulta: [URLQueryItem] = []
        for (clave, valor) in filtros where !valor.isEmpty {
            consulta.append(URLQueryItem(name: clave, value: valor))
        }
        consulta.append(URLQueryItem(name: "limite", value: "500"))
        let filas = try await RedCentral.enviarArreglo("GET", "/personas", consulta: consulta)
        return filas.map(PersonaCatalogo.init(json:))
    }

    func usuarios() async throws -> [PersonaCatalogo] {
        let filas = try await RedCentral.enviarArreglo(
            "GET",
            "/usuarios",
            consulta: [URLQueryItem(name: "limite", value: "500")]
        )
        return filas.compactMap { fila in
            let activo = (fila["activo"] as? Bool) ?? true
            let eliminado = (fila["eliminado"] as? Bool) ?? false
            guard activo, !eliminado else { return nil }
            return PersonaCatalogo(json: fila)
        }
    }

    func bandejas() async throws -> [Bandeja] {
        let filas = try await RedCentral.enviarArreglo("GET", "/bandejas")
        return filas.map(Bandeja.init(json:))
    }
}
