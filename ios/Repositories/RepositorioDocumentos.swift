import Foundation

final class RepositorioDocumentos {

    static let shared = RepositorioDocumentos()

    private init() {}

    struct Filtros {
        var busqueda: String = ""
        var tipo: String = ""
        var estado: String = ""
        var urgencia: String = ""
        var bandeja: String = ""

        var consulta: [URLQueryItem] {
            var items: [URLQueryItem] = []
            if !busqueda.trimmingCharacters(in: .whitespaces).isEmpty {
                items.append(URLQueryItem(name: "q", value: busqueda.trimmingCharacters(in: .whitespaces)))
            }
            if !tipo.isEmpty { items.append(URLQueryItem(name: "tipo", value: tipo)) }
            if !estado.isEmpty { items.append(URLQueryItem(name: "estado", value: estado)) }
            if !urgencia.isEmpty { items.append(URLQueryItem(name: "urgencia", value: urgencia)) }
            if !bandeja.isEmpty { items.append(URLQueryItem(name: "bandeja", value: bandeja)) }
            return items
        }

        var hayFiltros: Bool { !consulta.isEmpty }
    }

    struct FiltrosBusqueda {
        var q: String = ""
        var folio: String = ""
        var asunto: String = ""
        var descripcion: String = ""
        var destinatario: String = ""
        var remitente: String = ""
        var areaOrigen: String = ""
        var areaDestino: String = ""
        var tipo: String = ""
        var urgencia: String = ""
        var desde: String = ""
        var hasta: String = ""

        var consulta: [URLQueryItem] {
            var items: [URLQueryItem] = []

            func agregar(_ nombre: String, _ valor: String) {
                let limpio = valor.trimmingCharacters(in: .whitespacesAndNewlines)
                if !limpio.isEmpty {
                    items.append(URLQueryItem(name: nombre, value: limpio))
                }
            }

            agregar("q", q)
            agregar("folio", folio)
            agregar("asunto", asunto)
            agregar("descripcion", descripcion)
            agregar("destinatario", destinatario)
            agregar("remitente", remitente)
            agregar("area_origen", areaOrigen)
            agregar("area_destino", areaDestino)
            agregar("tipo", tipo)
            agregar("urgencia", urgencia)
            agregar("desde", desde)
            agregar("hasta", hasta)
            items.append(URLQueryItem(name: "limite", value: "50"))
            return items
        }
    }

    func buscar(filtros: FiltrosBusqueda) async throws -> [Documento] {
        let respuesta = try await RedCentral.enviar(
            "GET",
            "/buscar",
            consulta: filtros.consulta
        )

        let filas = respuesta["documentos"] as? [[String: Any]] ?? []
        return filas.compactMap { fila in
            let id = (fila["id"] as? Int) ?? 0
            guard id > 0 else { return nil }
            return Documento(id: id, json: fila)
        }
    }

    func listar(filtros: Filtros = Filtros()) async throws -> [Documento] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos", consulta: filtros.consulta)
        return filas.map { Documento(id: (($0["id"] as? Int) ?? 0), json: $0) }
    }

    func obtener(id: Int) async throws -> Documento? {
        let respuesta = try await RedCentral.enviar("GET", "/documentos/\(id)")
        guard let objeto = respuesta["documento"] as? [String: Any] else { return nil }
        return Documento(id: id, json: objeto)
    }

    @discardableResult
    func crear(datos: DatosDocumento) async throws -> Documento {
        var cuerpo: [String: Any] = [
            "tipo": datos.tipo.rawValue,
            "folio": datos.folio,
            "asunto": datos.asunto,
            "referencia": datos.referencia,
            "num_anexos": datos.numAnexos,
            "observaciones": datos.observaciones,
            "urgencia": datos.urgencia.rawValue,
            "clasificacion": datos.clasificacion.rawValue,
            "notas": datos.notas
        ]
        if !datos.destinatario.isEmpty { cuerpo["destinatario"] = datos.destinatario }
        if let idDestinatario = datos.idDestinatario { cuerpo["id_destinatario"] = idDestinatario }
        if !datos.fechaDocumento.isEmpty { cuerpo["fecha_documento"] = datos.fechaDocumento }
        if let fechaRecepcion = datos.fechaRecepcion { cuerpo["fecha_recepcion"] = fechaRecepcion }
        if !datos.fechaLimite.isEmpty { cuerpo["fecha_limite"] = datos.fechaLimite }
        if !datos.fechaLimiteHora.isEmpty { cuerpo["fecha_limite_hora"] = datos.fechaLimiteHora }
        if !datos.textoOCR.isEmpty { cuerpo["texto_ocr"] = datos.textoOCR }
        if !datos.areaOrigen.isEmpty { cuerpo["area_origen"] = datos.areaOrigen }
        if !datos.areaDestino.isEmpty { cuerpo["area_destino"] = datos.areaDestino }
        if !datos.areaOrigen.isEmpty { cuerpo["dependencia"] = datos.areaOrigen }
        cuerpo["org_dg_origen_id"] = datos.orgDgOrigenId ?? NSNull()
        cuerpo["org_dir_origen_id"] = datos.orgDirOrigenId ?? NSNull()
        cuerpo["org_sub_origen_id"] = datos.orgSubOrigenId ?? NSNull()
        cuerpo["org_dept_origen_id"] = datos.orgDeptOrigenId ?? NSNull()
        cuerpo["org_dg_destino_id"] = datos.orgDgDestinoId ?? NSNull()
        cuerpo["org_dir_destino_id"] = datos.orgDirDestinoId ?? NSNull()
        cuerpo["org_sub_destino_id"] = datos.orgSubDestinoId ?? NSNull()
        cuerpo["org_dept_destino_id"] = datos.orgDeptDestinoId ?? NSNull()

        let respuesta = try await RedCentral.enviar("POST", "/documentos", cuerpo: cuerpo)
        let id = (respuesta["id"] as? Int) ?? 0
        return Documento(id: id, json: respuesta)
    }

    @discardableResult
    func actualizar(id: Int, cuerpo: [String: Any]) async throws -> Documento {
        let respuesta = try await RedCentral.enviar("PUT", "/documentos/\(id)", cuerpo: cuerpo)
        return Documento(id: id, json: respuesta["documento"] as? [String: Any] ?? respuesta)
    }

    func eliminar(id: Int) async throws {
        try await RedCentral.enviar("DELETE", "/documentos/\(id)")
    }

    func historial(id: Int) async throws -> [PasoHistorial] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos/\(id)/historial")
        return filas.map(PasoHistorial.init(json:))
    }

    func anotaciones(id: Int) async throws -> [Anotacion] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos/\(id)/anotaciones")
        return filas.map { Anotacion(json: $0, documentoId: id) }
    }

    @discardableResult
    func crearAnotacion(documentoId: Int, tipo: String, texto: String) async throws -> [Anotacion] {
        let respuesta = try await RedCentral.enviar(
            "POST",
            "/documentos/\(documentoId)/anotaciones",
            cuerpo: ["comentario": texto, "tipo": tipo]
        )
        let filas = respuesta["anotaciones"] as? [[String: Any]] ?? []
        return filas.map { Anotacion(json: $0, documentoId: documentoId) }
    }

    func eliminarAnotacion(documentoId: Int, anotacionId: Int) async throws {
        try await RedCentral.enviar("DELETE", "/documentos/\(documentoId)/anotaciones/\(anotacionId)")
    }

    func asignaciones(id: Int) async throws -> [AsignacionDocumento] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos/\(id)/asignaciones")
        return filas.map(AsignacionDocumento.init(json:))
    }

    func respuestas(id: Int) async throws -> [RespuestaOficial] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos/\(id)/respuestas")
        return filas.map(RespuestaOficial.init(json:))
    }

    func relaciones(id: Int) async throws -> [RelacionDocumento] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos/\(id)/relaciones")
        return filas.map(RelacionDocumento.init(json:))
    }

    func crearRelacion(
        documentoId: Int,
        documentoRelacionadoId: Int,
        tipoRelacion: TipoRelacion
    ) async throws {
        try await RedCentral.enviar(
            "POST",
            "/documentos/\(documentoId)/relaciones",
            cuerpo: [
                "documento_relacionado_id": documentoRelacionadoId,
                "tipo_relacion": tipoRelacion.rawValue
            ]
        )
    }

    func eliminarRelacion(documentoId: Int, relacionId: Int) async throws {
        try await RedCentral.enviar("DELETE", "/documentos/\(documentoId)/relaciones/\(relacionId)")
    }

    @discardableResult
    func accion(_ accion: String, documentoId: Int, cuerpo: [String: Any] = [:]) async throws -> AccionFlujo {
        let respuesta = try await RedCentral.enviar(
            "POST",
            "/documentos/\(documentoId)/\(accion)",
            cuerpo: cuerpo
        )
        return AccionFlujo(
            ok: (respuesta["ok"] as? Bool) ?? true,
            estado: respuesta["estado"] as? String ?? "",
            error: respuesta["error"] as? String ?? "",
            usuarioAsignado: respuesta["usuario_asignado"] as? String,
            documentoUuid: respuesta["documento_uuid"] as? String
        )
    }

    func recibir(documentoId: Int) async throws -> AccionFlujo {
        try await accion("recibir", documentoId: documentoId)
    }

    func turnar(
        documentoId: Int,
        opcionTipo: String,
        opcionId: Int,
        usuarioDestinoId: Int? = nil,
        comentario: String = ""
    ) async throws -> AccionFlujo {
        var cuerpo: [String: Any] = [
            "opcion_tipo": opcionTipo,
            "opcion_id": opcionId,
            "comentario": comentario
        ]
        if let usuarioDestinoId, usuarioDestinoId > 0 {
            cuerpo["usuario_destino_id"] = usuarioDestinoId
        }
        return try await accion(
            "asignar",
            documentoId: documentoId,
            cuerpo: cuerpo
        )
    }

    func compartir(
        documentoId: Int,
        areaA: String,
        areaB: String,
        comentario: String = ""
    ) async throws -> AccionFlujo {
        try await accion(
            "compartir",
            documentoId: documentoId,
            cuerpo: ["area_a": areaA, "area_b": areaB, "comentario": comentario]
        )
    }

    func atender(documentoId: Int, comentario: String = "") async throws -> AccionFlujo {
        try await accion("atender", documentoId: documentoId, cuerpo: ["comentario": comentario])
    }

    func leer(documentoId: Int, acuse: Bool, comentario: String = "") async throws -> AccionFlujo {
        try await accion(
            "leer",
            documentoId: documentoId,
            cuerpo: ["acuse": acuse, "comentario": comentario]
        )
    }

    func responder(
        documentoId: Int,
        texto: String,
        observaciones: String = "",
        rama: Int = 0
    ) async throws -> AccionFlujo {
        try await accion(
            "responder",
            documentoId: documentoId,
            cuerpo: ["texto": texto, "observaciones": observaciones, "rama": rama]
        )
    }

    func consolidar(
        documentoId: Int,
        texto: String,
        comentario: String = ""
    ) async throws -> AccionFlujo {
        try await accion(
            "consolidar",
            documentoId: documentoId,
            cuerpo: ["texto": texto, "comentario": comentario]
        )
    }

    func revisar(documentoId: Int, aceptar: Bool, comentario: String = "") async throws -> AccionFlujo {
        try await accion(
            "revisar",
            documentoId: documentoId,
            cuerpo: ["aceptar": aceptar, "comentario": comentario]
        )
    }

    func validar(documentoId: Int, aceptar: Bool, comentario: String = "") async throws -> AccionFlujo {
        try await accion(
            "validar",
            documentoId: documentoId,
            cuerpo: ["aceptar": aceptar, "comentario": comentario]
        )
    }

    func archivar(
        documentoId: Int,
        folioFinal: String = "",
        comentario: String = ""
    ) async throws -> AccionFlujo {
        try await accion(
            "archivar",
            documentoId: documentoId,
            cuerpo: ["folio_final": folioFinal, "comentario": comentario]
        )
    }

    func historico(documentoId: Int) async throws -> AccionFlujo {
        try await accion("historico", documentoId: documentoId)
    }

    func opcionesTurnado() async throws -> OpcionesTurnado {
        let respuesta = try await RedCentral.enviar("GET", "/turnado/opciones")
        return OpcionesTurnado(json: respuesta)
    }

    func archivo(filtros: Filtros = Filtros()) async throws -> [Documento] {
        var consulta = filtros.consulta
        consulta.append(URLQueryItem(name: "limite", value: "500"))
        let filas = try await RedCentral.enviarArreglo("GET", "/archivo", consulta: consulta)
        return filas.map { Documento(id: (($0["id"] as? Int) ?? 0), json: $0) }
    }

    func bandejas() async throws -> [Bandeja] {
        let filas = try await RedCentral.enviarArreglo("GET", "/bandejas")
        return filas.map(Bandeja.init(json:))
    }

    @discardableResult
    func subirArchivo(
        documentoId: Int,
        archivo url: URL,
        nombre: String,
        mimeType: String
    ) async throws -> [String: Any] {
        try await RedCentral.subirArchivo(
            "/documentos/\(documentoId)/pdf",
            archivo: url,
            nombre: nombre,
            campo: "archivo",
            mimeType: mimeType
        )
    }

    func descargarArchivo(documentoId: Int) async throws -> (datos: Data, nombre: String) {
        let (datos, _) = try await RedCentral.descargar("/documentos/\(documentoId)/pdf")
        return (datos, "documento-\(documentoId).pdf")
    }

    func guardarOCR(documentoId: Int, texto: String) async throws {
        try await RedCentral.enviar(
            "PUT",
            "/documentos/\(documentoId)/ocr",
            cuerpo: ["texto_ocr": texto]
        )
    }

    func textoOCR(documentoId: Int) async throws -> String {
        let respuesta = try await RedCentral.enviar("GET", "/documentos/\(documentoId)/ocr")
        return respuesta["texto_ocr"] as? String ?? (respuesta["texto"] as? String ?? "")
    }

    func qr(documentoId: Int) async throws -> (texto: String, matriz: [[Bool]]) {
        let respuesta = try await RedCentral.enviar("GET", "/documentos/\(documentoId)/qr")
        let texto = respuesta["texto"] as? String ?? ""
        let filas = respuesta["matriz"] as? [[Any]] ?? []
        let matriz = filas.map { fila in fila.map { celda in
            if let b = celda as? Bool { return b }
            if let i = celda as? Int { return i == 1 }
            return false
        } }
        return (texto, matriz)
    }
}
