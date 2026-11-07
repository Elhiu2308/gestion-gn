import Foundation
import UniformTypeIdentifiers

struct ServicioDocumentos {

    enum Error: LocalizedError {
        case sinDocumento
        case servidorNoDisponible(String)
    }

    private let repositorio = RepositorioDocumentos.shared

    func listar(filtros: RepositorioDocumentos.Filtros = RepositorioDocumentos.Filtros()) async throws -> [Documento] {
        try await repositorio.listar(filtros: filtros)
    }

    func crear(datos: DatosDocumento) async throws -> Documento {
        try await repositorio.crear(datos: datos)
    }

    func obtener(id: Int) async throws -> Documento? {
        try await repositorio.obtener(id: id)
    }

    func actualizar(id: Int, cuerpo: [String: Any]) async throws -> Documento {
        try await repositorio.actualizar(id: id, cuerpo: cuerpo)
    }

    func eliminar(id: Int) async throws {
        try await repositorio.eliminar(id: id)
    }

    func historial(id: Int) async throws -> [PasoHistorial] {
        try await repositorio.historial(id: id)
    }

    func anotaciones(id: Int) async throws -> [Anotacion] {
        try await repositorio.anotaciones(id: id)
    }

    func anotar(documentoId: Int, tipo: TipoAnotacion, texto: String) async throws -> [Anotacion] {
        try await repositorio.crearAnotacion(documentoId: documentoId, tipo: tipo.rawValue, texto: texto)
    }

    func asignaciones(id: Int) async throws -> [AsignacionDocumento] {
        try await repositorio.asignaciones(id: id)
    }

    func respuestas(id: Int) async throws -> [RespuestaOficial] {
        try await repositorio.respuestas(id: id)
    }

    func relaciones(id: Int) async throws -> [RelacionDocumento] {
        try await repositorio.relaciones(id: id)
    }

    func crearRelacion(documentoId: Int, otroId: Int, tipo: TipoRelacion) async throws {
        try await repositorio.crearRelacion(documentoId: documentoId, documentoRelacionadoId: otroId, tipoRelacion: tipo)
    }

    func eliminarRelacion(documentoId: Int, relacionId: Int) async throws {
        try await repositorio.eliminarRelacion(documentoId: documentoId, relacionId: relacionId)
    }

    func recibir(documentoId: Int) async throws -> AccionFlujo {
        try await repositorio.recibir(documentoId: documentoId)
    }

    func turnar(documentoId: Int, opcionTipo: String, opcionId: Int, usuarioDestinoId: Int? = nil, comentario: String = "") async throws -> AccionFlujo {
        try await repositorio.turnar(documentoId: documentoId, opcionTipo: opcionTipo, opcionId: opcionId, usuarioDestinoId: usuarioDestinoId, comentario: comentario)
    }

    func turnadoOpciones() async throws -> OpcionesTurnado {
        try await repositorio.opcionesTurnado()
    }

    func compartir(documentoId: Int, areaA: String, areaB: String, comentario: String = "") async throws -> AccionFlujo {
        try await repositorio.compartir(documentoId: documentoId, areaA: areaA, areaB: areaB, comentario: comentario)
    }

    func atender(documentoId: Int, comentario: String = "") async throws -> AccionFlujo {
        try await repositorio.atender(documentoId: documentoId, comentario: comentario)
    }

    func leer(documentoId: Int, acuse: Bool, comentario: String = "") async throws -> AccionFlujo {
        try await repositorio.leer(documentoId: documentoId, acuse: acuse, comentario: comentario)
    }

    func responder(documentoId: Int, texto: String, observaciones: String = "", rama: Int = 0) async throws -> AccionFlujo {
        try await repositorio.responder(documentoId: documentoId, texto: texto, observaciones: observaciones, rama: rama)
    }

    func consolidar(documentoId: Int, texto: String, comentario: String = "") async throws -> AccionFlujo {
        try await repositorio.consolidar(documentoId: documentoId, texto: texto, comentario: comentario)
    }

    func revisar(documentoId: Int, aceptar: Bool, comentario: String = "") async throws -> AccionFlujo {
        try await repositorio.revisar(documentoId: documentoId, aceptar: aceptar, comentario: comentario)
    }

    func validar(documentoId: Int, aceptar: Bool, comentario: String = "") async throws -> AccionFlujo {
        try await repositorio.validar(documentoId: documentoId, aceptar: aceptar, comentario: comentario)
    }

    func archivar(documentoId: Int, folioFinal: String = "", comentario: String = "") async throws -> AccionFlujo {
        try await repositorio.archivar(documentoId: documentoId, folioFinal: folioFinal, comentario: comentario)
    }

    func historico(documentoId: Int) async throws -> AccionFlujo {
        try await repositorio.historico(documentoId: documentoId)
    }

    func eliminarAnotacion(documentoId: Int, anotacionId: Int) async throws {
        try await repositorio.eliminarAnotacion(documentoId: documentoId, anotacionId: anotacionId)
    }

    func subirArchivo(documentoId: Int, url: URL, nombre: String, mimeType: String) async throws {
        _ = try await repositorio.subirArchivo(documentoId: documentoId, archivo: url, nombre: nombre, mimeType: mimeType)
    }

    func descargarArchivo(documentoId: Int) async throws -> (datos: Data, nombre: String) {
        try await repositorio.descargarArchivo(documentoId: documentoId)
    }

    func guardarOCR(documentoId: Int, texto: String) async throws {
        try await repositorio.guardarOCR(documentoId: documentoId, texto: texto)
    }

    func textoOCR(documentoId: Int) async throws -> String {
        try await repositorio.textoOCR(documentoId: documentoId)
    }

    func qr(documentoId: Int) async throws -> (texto: String, matriz: [[Bool]]) {
        try await repositorio.qr(documentoId: documentoId)
    }

    func archivo(filtros: RepositorioDocumentos.Filtros = RepositorioDocumentos.Filtros()) async throws -> [Documento] {
        try await repositorio.archivo(filtros: filtros)
    }

    func bandejas() async throws -> [Bandeja] {
        try await repositorio.bandejas()
    }

    func buscar(filtros: RepositorioDocumentos.FiltrosBusqueda) async throws -> [Documento] {
        try await repositorio.buscar(filtros: filtros)
    }

}
