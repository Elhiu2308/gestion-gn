import Foundation

final class RepositorioExpedientes {

    static let shared = RepositorioExpedientes()

    private init() {}

    func listar() async throws -> [Expediente] {
        do {
            let filas = try await RedCentral.enviarArreglo("GET", "/expedientes")
            return filas.map { RepositorioExpedientes.expediente(json: $0) }
        } catch ErrorRed.respuestaInvalida {
            let (objeto, _) = try await ejecutar(
                peticion: "GET",
                ruta: "/expedientes"
            )
            let filas = objeto["expedientes"] as? [[String: Any]] ?? []
            return filas.map { RepositorioExpedientes.expediente(json: $0) }
        }
    }

    func obtener(id: Int) async throws -> Expediente? {
        let (expediente, _) = try await detalle(id: id)
        return expediente
    }

    func detalle(id: Int) async throws -> (Expediente?, [Documento]) {
        let (objeto, _) = try await ejecutar(
            peticion: "GET",
            ruta: "/expedientes/\(id)"
        )
        let expedienteJson = objeto["expediente"] as? [String: Any]
        let expediente = expedienteJson.map {
            RepositorioExpedientes.expediente(json: $0, idPorDefecto: id)
        }
        let filas = objeto["documentos"] as? [[String: Any]] ?? []
        let documentos = filas.map {
            Documento(id: ($0["id"] as? Int) ?? 0, json: $0)
        }
        return (expediente, documentos)
    }

    func documentosDelExpediente(expedienteId: Int) async throws -> [Documento] {
        let (_, documentos) = try await detalle(id: expedienteId)
        return documentos
    }

    func crearExpediente(datos: DatosExpediente) async throws -> Expediente {

        let cuerpo: [String: Any] = [
            "numero": datos.numero,
            "descripcion": datos.descripcion,
            "area_responsable": datos.areaResponsable,
            "clasificacion": datos.clasificacion
        ]
        let (respuesta, _) = try await ejecutar(
            peticion: "POST",
            ruta: "/expedientes",
            cuerpo: cuerpo
        )
        let expedienteJson = respuesta["expediente"] as? [String: Any] ?? respuesta
        return RepositorioExpedientes.expediente(json: expedienteJson)
    }

    func actualizar(id: Int, cambios: [String: String]) async throws -> Expediente {
        let (respuesta, _) = try await ejecutar(
            peticion: "PUT",
            ruta: "/expedientes/\(id)",
            cuerpo: cambios
        )
        return RepositorioExpedientes.expediente(json: respuesta, idPorDefecto: id)
    }

    static func expediente(json: [String: Any], idPorDefecto: Int = 0) -> Expediente {
        let numero = (json["numero"] as? String)
            ?? (json["codigo"] as? String)
            ?? ""
        let descripcion = (json["descripcion"] as? String)
            ?? (json["titulo"] as? String)
            ?? ""
        return Expediente(
            id: (json["id"] as? Int) ?? idPorDefecto,
            uuid: (json["uuid"] as? String) ?? "",
            codigo: numero,
            titulo: "",
            descripcion: descripcion,
            estado: (json["estado"] as? String) ?? "",
            areaResponsable: (json["area_responsable"] as? String) ?? "",
            clasificacion: (json["clasificacion"] as? String) ?? "",
            creadoEn: Documento.fecha(json["creado_en"])
        )
    }

    func vincular(expedienteId: Int, documentoId: Int) async throws {
        let cuerpo: [String: Any] = ["documento_id": documentoId]
        _ = try await ejecutar(
            peticion: "POST",
            ruta: "/expedientes/\(expedienteId)/documentos",
            cuerpo: cuerpo
        )
    }

    func desvincular(expedienteId: Int, documentoId: Int) async throws {
        _ = try await ejecutar(
            peticion: "DELETE",
            ruta: "/expedientes/\(expedienteId)/documentos/\(documentoId)"
        )
    }

    func eliminar(expedienteId: Int) async throws {
        _ = try await ejecutar(
            peticion: "DELETE",
            ruta: "/expedientes/\(expedienteId)"
        )
    }

    func ejecutar(
        peticion: String,
        ruta: String,
        cuerpo: [String: Any]? = nil
    ) async throws -> ([String: Any], Int) {
        var solicitud = URLRequest(url: ConfiguracionServidor.urlCon(ruta: ruta))
        solicitud.timeoutInterval = 20
        solicitud.httpMethod = peticion
        solicitud.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let cuerpo, !cuerpo.isEmpty {
            solicitud.httpBody = try JSONSerialization.data(
                withJSONObject: cuerpo,
                options: .withoutEscapingSlashes
            )
        }
        if let token = ServicioKeychain.tokenAcceso() {
            solicitud.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (datos, respuesta) = try await URLSession.shared.data(for: solicitud)
        let codigo = (respuesta as? HTTPURLResponse)?.statusCode ?? 0
        let objeto = try JSONSerialization.jsonObject(with: datos) as? [String: Any] ?? [:]
        return (objeto, codigo)
    }
}
