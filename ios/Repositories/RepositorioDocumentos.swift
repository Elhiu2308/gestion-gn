import Foundation

/**
 * Repositorio de documentos (`/api/documentos/ *`).
 *
 * Reproduce exactamente los endpoints que consume la app Android (y el panel
 * web del mismo backend):
 *
 * | Operación              | Endpoint                                  |
 * |------------------------|-------------------------------------------|
 * | Listar                 | `GET  /api/documentos?q&tipo&estado&…`   |
 * | Detalle                | `GET  /api/documentos/<id>`               |
 * | Crear                  | `POST /api/documentos`                     |
 * | Editar                 | `PUT  /api/documentos/<id>`               |
 * | Eliminar               | `DELETE /api/documentos/<id>`             |
 * | Historial              | `GET  /api/documentos/<id>/historial`     |
 * | Anotaciones            | `GET/POST /api/documentos/<id>/anotaciones`|
 * | Asignaciones           | `GET  /api/documentos/<id>/asignaciones`  |
 * | Respuestas             | `GET  /api/documentos/<id>/respuestas`    |
 * | Relaciones             | `GET/POST /api/documentos/<id>/relaciones`|
 * | Acciones de trámite    | `POST /api/documentos/<id>/<accion>`      |
 * | Opciones de turno      | `GET  /api/turnado/opciones`              |
 * | Subir PDF              | `POST /api/documentos/<id>/pdf` (multipart)|
 * | Descargar PDF          | `GET  /api/documentos/<id>/pdf` (binario) |
 * | Guardar OCR            | `PUT  /api/documentos/<id>/ocr`           |
 * | Código QR              | `GET  /api/documentos/<id>/qr`            |
 */
final class RepositorioDocumentos {

    static let shared = RepositorioDocumentos()

    private init() {}

    // MARK: - Listado

    /// Filtros de la lista de documentos (los mismos que usa el panel web).
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

    /// Filtros combinables del buscador (`GET /api/buscar`).
    ///
    /// Los nombres de los parámetros son los mismos que usa la app Android y
    /// el panel web. El backend ignora los valores vacíos.
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

    /// Busca documentos con filtros combinables (`GET /api/buscar`).
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

    /// Lista los documentos accesibles (`GET /api/documentos`).
    func listar(filtros: Filtros = Filtros()) async throws -> [Documento] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos", consulta: filtros.consulta)
        return filas.map { Documento(id: (($0["id"] as? Int) ?? 0), json: $0) }
    }

    /// Detalle del documento (`GET /api/documentos/<id>`).
    func obtener(id: Int) async throws -> Documento? {
        let respuesta = try await RedCentral.enviar("GET", "/documentos/\(id)")
        guard let objeto = respuesta["documento"] as? [String: Any] else { return nil }
        return Documento(id: id, json: objeto)
    }

    // MARK: - Alta y edición

    /// Crea un documento (`POST /api/documentos`).
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

    /// Actualiza un documento (`PUT /api/documentos/<id>`).
    @discardableResult
    func actualizar(id: Int, cuerpo: [String: Any]) async throws -> Documento {
        let respuesta = try await RedCentral.enviar("PUT", "/documentos/\(id)", cuerpo: cuerpo)
        return Documento(id: id, json: respuesta["documento"] as? [String: Any] ?? respuesta)
    }

    /// Elimina un documento (`DELETE /api/documentos/<id>`).
    func eliminar(id: Int) async throws {
        try await RedCentral.enviar("DELETE", "/documentos/\(id)")
    }

    // MARK: - Piezas de la ficha

    /// Línea de tiempo del documento (`GET /api/documentos/<id>/historial`).
    func historial(id: Int) async throws -> [PasoHistorial] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos/\(id)/historial")
        return filas.map(PasoHistorial.init(json:))
    }

    /// Anotaciones internas (`GET /api/documentos/<id>/anotaciones`).
    func anotaciones(id: Int) async throws -> [Anotacion] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos/\(id)/anotaciones")
        return filas.map { Anotacion(json: $0, documentoId: id) }
    }

    /// Crea una anotación (`POST /api/documentos/<id>/anotaciones`).
    ///
    /// El backend espera las claves `comentario` y `tipo`.
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

    /// Elimina una anotación (`DELETE /api/documentos/<id>/anotaciones/<id>`).
    func eliminarAnotacion(documentoId: Int, anotacionId: Int) async throws {
        try await RedCentral.enviar("DELETE", "/documentos/\(documentoId)/anotaciones/\(anotacionId)")
    }

    /// Árbol de asignación (`GET /api/documentos/<id>/asignaciones`).
    func asignaciones(id: Int) async throws -> [AsignacionDocumento] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos/\(id)/asignaciones")
        return filas.map(AsignacionDocumento.init(json:))
    }

    /// Respuestas oficiales (`GET /api/documentos/<id>/respuestas`).
    func respuestas(id: Int) async throws -> [RespuestaOficial] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos/\(id)/respuestas")
        return filas.map(RespuestaOficial.init(json:))
    }

    /// Documentos relacionados (`GET /api/documentos/<id>/relaciones`).
    func relaciones(id: Int) async throws -> [RelacionDocumento] {
        let filas = try await RedCentral.enviarArreglo("GET", "/documentos/\(id)/relaciones")
        return filas.map(RelacionDocumento.init(json:))
    }

    /// Vincula otro documento (`POST /api/documentos/<id>/relaciones`).
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

    /// Elimina un vínculo (`DELETE /api/documentos/<id>/relaciones/<id>`).
    func eliminarRelacion(documentoId: Int, relacionId: Int) async throws {
        try await RedCentral.enviar("DELETE", "/documentos/\(documentoId)/relaciones/\(relacionId)")
    }

    // MARK: - Acciones del flujo

    /// Ejecuta una acción genérica (`POST /api/documentos/<id>/<accion>`).
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

    /// Registra la recepción (`POST /api/documentos/<id>/recibir`).
    func recibir(documentoId: Int) async throws -> AccionFlujo {
        try await accion("recibir", documentoId: documentoId)
    }

    /// Turna el documento al nivel inferior (`POST /api/documentos/<id>/asignar`).
    ///
    /// El backend exige `opcion_tipo` y `opcion_id` (los que entrega
    /// `GET /api/turnado/opciones`), no un nivel libre. Cuando se selecciona
    /// una persona concreta, `usuarioDestinoId` permite asignarle el documento
    /// dentro del nivel organizacional indicado.
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

    /// Comparte con dos direcciones (`POST /api/documentos/<id>/compartir`).
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

    /// Marca el documento en atención (`POST /api/documentos/<id>/atender`).
    func atender(documentoId: Int, comentario: String = "") async throws -> AccionFlujo {
        try await accion("atender", documentoId: documentoId, cuerpo: ["comentario": comentario])
    }

    /// Marca leído / acuse de recibo (`POST /api/documentos/<id>/leer`).
    func leer(documentoId: Int, acuse: Bool, comentario: String = "") async throws -> AccionFlujo {
        try await accion(
            "leer",
            documentoId: documentoId,
            cuerpo: ["acuse": acuse, "comentario": comentario]
        )
    }

    /// Registra la respuesta oficial (`POST /api/documentos/<id>/responder`).
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

    /// Consolida las respuestas (`POST /api/documentos/<id>/consolidar`).
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

    /// Revisa la respuesta (`POST /api/documentos/<id>/revisar`).
    func revisar(documentoId: Int, aceptar: Bool, comentario: String = "") async throws -> AccionFlujo {
        try await accion(
            "revisar",
            documentoId: documentoId,
            cuerpo: ["aceptar": aceptar, "comentario": comentario]
        )
    }

    /// Valida definitivamente (`POST /api/documentos/<id>/validar`).
    func validar(documentoId: Int, aceptar: Bool, comentario: String = "") async throws -> AccionFlujo {
        try await accion(
            "validar",
            documentoId: documentoId,
            cuerpo: ["aceptar": aceptar, "comentario": comentario]
        )
    }

    /// Archiva el documento (`POST /api/documentos/<id>/archivar`).
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

    /// Pasa el documento a histórico (`POST /api/documentos/<id>/historico`).
    func historico(documentoId: Int) async throws -> AccionFlujo {
        try await accion("historico", documentoId: documentoId)
    }

    // MARK: - Turnado

    /// Opciones de destino del turno (`GET /api/turnado/opciones`).
    func opcionesTurnado() async throws -> OpcionesTurnado {
        let respuesta = try await RedCentral.enviar("GET", "/turnado/opciones")
        return OpcionesTurnado(json: respuesta)
    }

    // MARK: - Archivo

    /// Archivo virtual (`GET /api/archivo`).
    func archivo(filtros: Filtros = Filtros()) async throws -> [Documento] {
        var consulta = filtros.consulta
        consulta.append(URLQueryItem(name: "limite", value: "500"))
        let filas = try await RedCentral.enviarArreglo("GET", "/archivo", consulta: consulta)
        return filas.map { Documento(id: (($0["id"] as? Int) ?? 0), json: $0) }
    }

    /// Bandejas de trabajo del usuario (`GET /api/bandejas`).
    func bandejas() async throws -> [Bandeja] {
        let filas = try await RedCentral.enviarArreglo("GET", "/bandejas")
        return filas.map(Bandeja.init(json:))
    }

    // MARK: - Archivos adjuntos

    /// Sube el archivo escaneado (`POST /api/documentos/<id>/pdf`, multipart).
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

    /// Descarga el PDF/imagen adjunto (`GET /api/documentos/<id>/pdf`).
    func descargarArchivo(documentoId: Int) async throws -> (datos: Data, nombre: String) {
        let (datos, _) = try await RedCentral.descargar("/documentos/\(documentoId)/pdf")
        return (datos, "documento-\(documentoId).pdf")
    }

    /// Guarda la corrección manual del OCR (`PUT /api/documentos/<id>/ocr`).
    func guardarOCR(documentoId: Int, texto: String) async throws {
        try await RedCentral.enviar(
            "PUT",
            "/documentos/\(documentoId)/ocr",
            cuerpo: ["texto_ocr": texto]
        )
    }

    /// Texto OCR almacenado en el backend.
    func textoOCR(documentoId: Int) async throws -> String {
        let respuesta = try await RedCentral.enviar("GET", "/documentos/\(documentoId)/ocr")
        return respuesta["texto_ocr"] as? String ?? (respuesta["texto"] as? String ?? "")
    }

    // MARK: - Código QR

    /// Código QR del documento (`GET /api/documentos/<id>/qr`).
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
