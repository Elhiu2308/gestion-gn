
import Foundation

final class RepositorioAutenticacion {

    static let shared = RepositorioAutenticacion()

    private init() {}

    enum Error: LocalizedError {
        case sinRefreshToken
        case tokenNoRecibido
        case respuestaNoValida
        case servidor(Int, String)

        var errorDescription: String? {
            switch self {
            case .sinRefreshToken:
                return "No existe un refresh token guardado. Inicia sesión nuevamente."

            case .tokenNoRecibido:
                return "El servidor no devolvió un access token válido."

            case .respuestaNoValida:
                return "El servidor devolvió una respuesta no válida."

            case .servidor(let codigo, let mensaje):
                return mensaje.isEmpty
                    ? "Error de autenticación. Código HTTP: \(codigo)"
                    : mensaje
            }
        }
    }

    func login(
        usuario: String,
        contrasena: String
    ) async throws -> RespuestaLogin {

        let datos: [String: Any] = [
            "usuario": usuario,
            "contrasena": contrasena
        ]

        let (datosRespuesta, codigo) = try await ejecutar(
            peticion: "POST",
            ruta: "/auth/login",
            cuerpo: datos
        )

        try validarRespuesta(datosRespuesta, codigo: codigo)

        let accessToken =
            datosRespuesta["access_token"] as? String
            ?? datosRespuesta["token"] as? String
            ?? ""

        guard !accessToken.isEmpty else {
            throw Error.tokenNoRecibido
        }

        let datosUsuario =
            datosRespuesta["usuario"] as? [String: Any] ?? [:]

        return RespuestaLogin(
            accessToken: accessToken,
            refreshToken: datosRespuesta["refresh_token"] as? String ?? "",
            usuario: datosUsuario["usuario"] as? String ?? usuario,
            nombre: datosUsuario["nombre"] as? String ?? "",
            nombreCompleto: datosUsuario["nombre_completo"] as? String ?? "",
            rol: datosUsuario["rol"] as? String ?? "",
            cargo: datosUsuario["cargo"] as? String ?? "",
            grado: datosUsuario["grado"] as? String ?? "",
            area: datosUsuario["area"] as? String ?? "",
            subdireccion: datosUsuario["subdireccion"] as? String ?? "",
            correo: datosUsuario["correo"] as? String ?? "",
            permisos: datosUsuario["permisos"] as? [String] ?? []
        )
    }

    func renovarAcceso() async throws -> RespuestaRenovacion {

        guard let refreshToken = ServicioKeychain.tokenRefreshGuardado(),
              !refreshToken.isEmpty else {
            throw Error.sinRefreshToken
        }

        return try await renovarAcceso(
            conRefreshToken: refreshToken
        )
    }

    func renovarAcceso(
        conRefreshToken refreshToken: String
    ) async throws -> RespuestaRenovacion {

        guard !refreshToken.isEmpty else {
            throw Error.sinRefreshToken
        }

        let (datos, codigo) = try await ejecutar(
            peticion: "POST",
            ruta: "/auth/refresh",
            cabeceras: [
                "Authorization": "Bearer \(refreshToken)"
            ]
        )

        try validarRespuesta(datos, codigo: codigo)

        let accessToken = datos["access_token"] as? String ?? ""

        guard !accessToken.isEmpty else {
            throw Error.tokenNoRecibido
        }

        return RespuestaRenovacion(
            accessToken: accessToken,
            refreshToken: datos["refresh_token"] as? String ?? ""
        )
    }

    func me() async throws -> [String: Any] {

        let (objeto, codigo) = try await ejecutar(
            peticion: "GET",
            ruta: "/auth/me"
        )

        try validarRespuesta(objeto, codigo: codigo)

        return objeto
    }

    private func ejecutar(
        peticion: String,
        ruta: String,
        cuerpo: [String: Any]? = nil,
        cabeceras: [String: String]? = nil
    ) async throws -> (datos: [String: Any], codigo: Int) {

        var solicitud = URLRequest(
            url: ConfiguracionServidor.urlCon(ruta: ruta)
        )

        solicitud.timeoutInterval = 20
        solicitud.httpMethod = peticion

        solicitud.setValue(
            "application/json",
            forHTTPHeaderField: "Content-Type"
        )

        solicitud.setValue(
            "application/json",
            forHTTPHeaderField: "Accept"
        )

        for (clave, valor) in cabeceras ?? [:] {
            solicitud.setValue(
                valor,
                forHTTPHeaderField: clave
            )
        }

        if let cuerpo {
            solicitud.httpBody = try JSONSerialization.data(
                withJSONObject: cuerpo,
                options: .withoutEscapingSlashes
            )
        }

        let (datos, respuesta) = try await URLSession.shared.data(
            for: solicitud
        )

        guard let respuestaHTTP = respuesta as? HTTPURLResponse else {
            throw Error.respuestaNoValida
        }

        let codigo = respuestaHTTP.statusCode

        let objeto = (
            try? JSONSerialization.jsonObject(with: datos)
        ) as? [String: Any] ?? [:]

        return (objeto, codigo)
    }

    private func validarRespuesta(
        _ datos: [String: Any],
        codigo: Int
    ) throws {

        guard (200..<300).contains(codigo) else {

            let mensaje =
                datos["error"] as? String
                ?? datos["mensaje"] as? String
                ?? ""

            throw Error.servidor(codigo, mensaje)
        }
    }

    struct RespuestaLogin {
        let accessToken: String
        let refreshToken: String
        let usuario: String?
        let nombre: String?
        let nombreCompleto: String?
        let rol: String?
        let cargo: String?
        let grado: String?
        let area: String?
        let subdireccion: String?
        let correo: String?
        let permisos: [String]
    }

    struct RespuestaRenovacion {
        let accessToken: String
        let refreshToken: String
    }
}
