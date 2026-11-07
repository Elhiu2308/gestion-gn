import Foundation

enum ErrorRed: LocalizedError {

    case sinConexion(String)

    case sesionExpirada

    case permisoDenegado(String)

    case servidor(String, Int)

    case respuestaInvalida

    var errorDescription: String? {
        switch self {
        case .sinConexion(let detalle):
            return "Sin conexión con el servidor. \(detalle)"
        case .sesionExpirada:
            return "Tu sesión expiró. Vuelve a iniciar sesión."
        case .permisoDenegado(let detalle):
            return detalle.isEmpty ? "Permiso denegado." : detalle
        case .servidor(let mensaje, let codigo):
            return mensaje.isEmpty ? "Error del servidor (\(codigo))." : mensaje
        case .respuestaInvalida:
            return "La respuesta del servidor no se pudo interpretar."
        }
    }
}

enum RedCentral {

    static let agente = "GestioN-iOS/1.0"

    static func url(_ ruta: String, consulta: [URLQueryItem] = []) -> URL {
        let base = ConfiguracionServidor.urlCon(ruta: ruta)
        guard !consulta.isEmpty, var componentes = URLComponents(url: base, resolvingAgainstBaseURL: false) else {
            return base
        }
        componentes.queryItems = consulta
        return componentes.url ?? base
    }

    @discardableResult
    static func enviar(
        _ metodo: String,
        _ ruta: String,
        consulta: [URLQueryItem] = [],
        cuerpo: [String: Any]? = nil,
        requiereToken: Bool = true
    ) async throws -> [String: Any] {
        let (datos, _) = try await ejecutar(
            metodo,
            ruta,
            consulta: consulta,
            cuerpo: cuerpo,
            requiereToken: requiereToken
        )
        guard let objeto = try? JSONSerialization.jsonObject(with: datos) as? [String: Any] else {
            throw ErrorRed.respuestaInvalida
        }
        return objeto
    }

    static func enviarArreglo(
        _ metodo: String,
        _ ruta: String,
        consulta: [URLQueryItem] = [],
        cuerpo: [String: Any]? = nil
    ) async throws -> [[String: Any]] {
        let (datos, _) = try await ejecutar(metodo, ruta, consulta: consulta, cuerpo: cuerpo)
        if let arreglo = try? JSONSerialization.jsonObject(with: datos) as? [[String: Any]] {
            return arreglo
        }

        if let objeto = try? JSONSerialization.jsonObject(with: datos) as? [String: Any] {
            for clave in ["documentos", "data", "items", "resultados", "filas"] {
                if let arreglo = objeto[clave] as? [[String: Any]] { return arreglo }
            }
        }
        return []
    }

    static func descargar(
        _ ruta: String,
        consulta: [URLQueryItem] = []
    ) async throws -> (datos: Data, respuesta: HTTPURLResponse) {
        var solicitud = construir(ruta, consulta: consulta, requiereToken: true)
        solicitud.httpMethod = "GET"
        return try await enviar(solicitud, decodificaJSON: false)
    }

    @discardableResult
    static func subirArchivo(
        _ ruta: String,
        archivo url: URL,
        nombre: String,
        campo: String = "archivo",
        mimeType: String = "application/octet-stream"
    ) async throws -> [String: Any] {
        let datosArchivo = try Data(contentsOf: url)
        let limite = "----GestioNFormBoundary\(UUID().uuidString)"
        var cuerpo = Data()

        cuerpo.append("--\(limite)\r\n".data(using: .utf8)!)
        cuerpo.append(
            "Content-Disposition: form-data; name=\"\(campo)\"; filename=\"\(nombre)\"\r\n"
                .data(using: .utf8)!
        )
        cuerpo.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        cuerpo.append(datosArchivo)
        cuerpo.append("\r\n".data(using: .utf8)!)
        cuerpo.append("--\(limite)--\r\n".data(using: .utf8)!)

        var solicitud = construir(ruta, requiereToken: true)
        solicitud.httpMethod = "POST"
        solicitud.setValue(
            "multipart/form-data; boundary=\(limite)",
            forHTTPHeaderField: "Content-Type"
        )
        solicitud.httpBody = cuerpo

        let (datos, _) = try await enviar(solicitud, decodificaJSON: true)
        return (try? JSONSerialization.jsonObject(with: datos) as? [String: Any]) ?? [:]
    }

    private static func construir(
        _ ruta: String,
        consulta: [URLQueryItem] = [],
        requiereToken: Bool = true
    ) -> URLRequest {
        var solicitud = URLRequest(url: url(ruta, consulta: consulta))
        solicitud.timeoutInterval = 20
        solicitud.setValue("application/json", forHTTPHeaderField: "Accept")
        solicitud.setValue(agente, forHTTPHeaderField: "User-Agent")
        if requiereToken, let token = ServicioKeychain.tokenAcceso(), !token.isEmpty {
            solicitud.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return solicitud
    }

    private static func enviar(
        _ solicitud: URLRequest,
        decodificaJSON: Bool
    ) async throws -> (Data, HTTPURLResponse) {
        let datos: Data
        let respuesta: URLResponse
        do {
            (datos, respuesta) = try await URLSession.shared.data(for: solicitud)
        } catch {
            throw ErrorRed.sinConexion(error.localizedDescription)
        }

        guard let http = respuesta as? HTTPURLResponse else {
            throw ErrorRed.respuestaInvalida
        }

        if http.statusCode == 401 {
            throw ErrorRed.sesionExpirada
        }
        if http.statusCode == 403 {
            throw ErrorRed.permisoDenegado(mensajeDeError(datos))
        }
        guard (200..<300).contains(http.statusCode) else {
            throw ErrorRed.servidor(mensajeDeError(datos), http.statusCode)
        }
        return (datos, http)
    }

    private static func ejecutar(
        _ metodo: String,
        _ ruta: String,
        consulta: [URLQueryItem] = [],
        cuerpo: [String: Any]? = nil,
        requiereToken: Bool = true
    ) async throws -> (Data, HTTPURLResponse) {
        var solicitud = construir(ruta, consulta: consulta, requiereToken: requiereToken)
        solicitud.httpMethod = metodo
        if let cuerpo, !cuerpo.isEmpty {
            solicitud.setValue("application/json", forHTTPHeaderField: "Content-Type")
            solicitud.httpBody = try JSONSerialization.data(
                withJSONObject: cuerpo,
                options: .withoutEscapingSlashes
            )
        }
        return try await enviar(solicitud, decodificaJSON: true)
    }

    private static func mensajeDeError(_ datos: Data) -> String {
        guard let objeto = try? JSONSerialization.jsonObject(with: datos) as? [String: Any] else {
            return ""
        }
        for clave in ["error", "mensaje", "message", "detalle"] {
            if let texto = objeto[clave] as? String, !texto.isEmpty { return texto }
        }
        return ""
    }
}
