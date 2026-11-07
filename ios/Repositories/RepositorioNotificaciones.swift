
import Foundation

final class RepositorioNotificaciones {

    static let shared = RepositorioNotificaciones()

    private init() {}

    func centro() async throws -> CentroNotificaciones {

        let (respuesta, _) = try await ejecutar(
            peticion: "GET",
            ruta: "/notificaciones/contador"
        )

        let noLeidas = Self.entero(respuesta["no_leidas"]) ?? 0

        let recientes = try await historialCompleto(limite: 50)

        return CentroNotificaciones(
            noLeidas: noLeidas,
            recientes: Array(recientes.prefix(50))
        )
    }

    func historial(masLeidas: Bool) async throws -> [Notificacion] {

        let notificaciones = try await historialCompleto(limite: 200)

        return notificaciones.filter {
            $0.leida == masLeidas
        }
    }

    private func historialCompleto(
        limite: Int
    ) async throws -> [Notificacion] {

        let limiteSeguro = min(max(limite, 1), 200)

        let (respuesta, _) = try await ejecutarArreglo(
            peticion: "GET",
            ruta: "/notificaciones?limite=\(limiteSeguro)"
        )

        return respuesta.compactMap {
            Self.convertirNotificacion($0)
        }
    }

    func marcarLeida(id: Int) async throws {

        guard id > 0 else {
            throw ErrorRepositorio.idInvalido
        }

        let cuerpo: [String: Any] = [
            "ids": [id]
        ]

        _ = try await ejecutar(
            peticion: "POST",
            ruta: "/notificaciones/leer",
            cuerpo: cuerpo
        )
    }

    func marcarTodasLeidas() async throws {

        _ = try await ejecutar(
            peticion: "POST",
            ruta: "/notificaciones/leer",
            cuerpo: [:]
        )
    }

    private static func convertirNotificacion(
        _ objeto: [String: Any]
    ) -> Notificacion? {

        guard
            let id = entero(objeto["id"]),
            id > 0
        else {
            return nil
        }

        let documentoId = entero(objeto["documento_id"])

        let fecha = convertirFecha(objeto["creado_en"])

        let fechaLectura: Date?

        if let valor = objeto["leida_en"],
           !(valor is NSNull) {
            fechaLectura = convertirFecha(valor)
        } else {
            fechaLectura = nil
        }

        return Notificacion(
            id: id,
            uuid: objeto["uuid"] as? String ?? "",
            documentoId: documentoId,
            documentoUuid: objeto["documento_uuid"] as? String ?? "",
            tipo: objeto["tipo"] as? String ?? "SISTEMA",
            mensaje: objeto["mensaje"] as? String ?? "",
            leida: objeto["leida"] as? Bool ?? false,
            emitidaEn: fecha,
            leidaEn: fechaLectura
        )
    }

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

    private static func convertirFecha(_ valor: Any?) -> Date {

        guard let valor, !(valor is NSNull) else {
            return Date()
        }

        if let numero = valor as? NSNumber {

            var timestamp = numero.doubleValue

            if abs(timestamp) > 100_000_000_000 {
                timestamp /= 1000
            }

            return Date(timeIntervalSince1970: timestamp)
        }

        guard let cadena = valor as? String else {
            return Date()
        }

        let iso = ISO8601DateFormatter()

        iso.formatOptions = [
            .withInternetDateTime,
            .withFractionalSeconds
        ]

        if let fecha = iso.date(from: cadena) {
            return fecha
        }

        let isoNormal = ISO8601DateFormatter()

        if let fecha = isoNormal.date(from: cadena) {
            return fecha
        }

        let formatos = [
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSS"
        ]

        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current

        for formato in formatos {

            formatter.dateFormat = formato

            if let fecha = formatter.date(from: cadena) {
                return fecha
            }
        }

        return Date()
    }

    private func ejecutar(
        peticion: String,
        ruta: String,
        cuerpo: [String: Any]? = nil
    ) async throws -> ([String: Any], Int) {

        let (datos, codigo) = try await solicitar(
            peticion: peticion,
            ruta: ruta,
            cuerpo: cuerpo
        )

        guard
            let objeto = try JSONSerialization.jsonObject(
                with: datos
            ) as? [String: Any]
        else {
            throw ErrorRepositorio.respuestaInvalida
        }

        return (objeto, codigo)
    }

    private func ejecutarArreglo(
        peticion: String,
        ruta: String
    ) async throws -> ([[String: Any]], Int) {

        let (datos, codigo) = try await solicitar(
            peticion: peticion,
            ruta: ruta
        )

        guard
            let arreglo = try JSONSerialization.jsonObject(
                with: datos
            ) as? [[String: Any]]
        else {
            throw ErrorRepositorio.respuestaInvalida
        }

        return (arreglo, codigo)
    }

    private func solicitar(
        peticion: String,
        ruta: String,
        cuerpo: [String: Any]? = nil
    ) async throws -> (Data, Int) {

        guard
            let token = ServicioKeychain.tokenAcceso(),
            !token.isEmpty
        else {
            throw ErrorRepositorio.sesionNoDisponible
        }

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

        solicitud.setValue(
            "Bearer \(token)",
            forHTTPHeaderField: "Authorization"
        )

        if let cuerpo {

            solicitud.httpBody = try JSONSerialization.data(
                withJSONObject: cuerpo,
                options: .withoutEscapingSlashes
            )
        }

        let (datos, respuesta) = try await URLSession.shared.data(
            for: solicitud
        )

        guard let http = respuesta as? HTTPURLResponse else {
            throw ErrorRepositorio.respuestaInvalida
        }

        guard (200...299).contains(http.statusCode) else {

            if http.statusCode == 401 {
                throw ErrorRepositorio.sesionNoAutorizada
            }

            if http.statusCode == 403 {
                throw ErrorRepositorio.sinPermisos
            }

            throw ErrorRepositorio.errorServidor(http.statusCode)
        }

        return (datos, http.statusCode)
    }

    enum ErrorRepositorio: LocalizedError {

        case idInvalido
        case sesionNoDisponible
        case sesionNoAutorizada
        case sinPermisos
        case respuestaInvalida
        case errorServidor(Int)

        var errorDescription: String? {

            switch self {

            case .idInvalido:
                return "El identificador de la notificación no es válido."

            case .sesionNoDisponible:
                return "No existe una sesión activa."

            case .sesionNoAutorizada:
                return "La sesión expiró. Inicia sesión nuevamente."

            case .sinPermisos:
                return "No tienes permisos para consultar las notificaciones."

            case .respuestaInvalida:
                return "El servidor devolvió una respuesta no válida."

            case .errorServidor(let codigo):
                return "Error del servidor. Código HTTP: \(codigo)."
            }
        }
    }
}
