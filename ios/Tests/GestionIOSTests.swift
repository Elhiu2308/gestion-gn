import XCTest
@testable import GestionIOS

/// Pruebas básicas del proyecto iOS (capa de utilidades y permisos).
final class GestionIOSTests: XCTestCase {

    // MARK: URL del backend

    func testURLBackendIncluyePrefijoAPI() {
        let url = ConfiguracionServidor.urlCon(ruta: "/documentos")
        XCTAssertTrue(url.absoluteString.contains("/api/documentos"))
    }

    func testURLBackendPorDefecto() {
        let url = ConfiguracionServidor.urlCon(ruta: "")
        XCTAssertEqual(url.absoluteString, "https://thermos-provider-populace.ngrok-free.dev/api")
    }

    // MARK: Permisos efectivos

    func testPuedeSubirDocumentoConPermisoSubirPDF() {
        XCTAssertTrue(Permisos.puedeSubirDocumento(["DOCUMENTO_SUBIR_PDF"]))
    }

    func testPuedeSubirDocumentoConPermisoCrear() {
        XCTAssertTrue(Permisos.puedeSubirDocumento(["DOCUMENTO_CREAR"]))
    }

    func testNoPuedeSubirDocumentoSinPermiso() {
        XCTAssertFalse(Permisos.puedeSubirDocumento(["DOCUMENTO_CONSULTAR"]))
    }

    // MARK: Token de sesión

    func testTokenAccesoSinSesionEsNulo() {
        // Sin sesión iniciada en el entorno de pruebas, el token debe ser nil.
        XCTAssertNil(ServicioKeychain.tokenAcceso())
    }

    // MARK: Agrupaciones del dashboard y archivo

    private func documento(
        estado: EstadoDocumento,
        diasRestantes: Int? = nil
    ) -> Documento {
        var json: [String: Any] = ["estado": estado.rawValue]
        if let diasRestantes {
            json["dias_restantes"] = diasRestantes
        }
        return Documento(id: 1, json: json)
    }

    func testGrupoPendientesIncluyeRecibido() {
        let doc = documento(estado: .recibido)
        XCTAssertTrue(GrupoDocumentos.pendientes.incluye(doc))
        XCTAssertFalse(GrupoDocumentos.enProceso.incluye(doc))
        XCTAssertFalse(GrupoDocumentos.finalizados.incluye(doc))
    }

    func testGrupoEnProcesoIncluyeTurnado() {
        XCTAssertTrue(GrupoDocumentos.enProceso.incluye(documento(estado: .turnado)))
        XCTAssertTrue(GrupoDocumentos.enProceso.incluye(documento(estado: .enAtencion)))
        XCTAssertFalse(GrupoDocumentos.pendientes.incluye(documento(estado: .turnado)))
    }

    func testGrupoFinalizadosIncluyeConcluido() {
        XCTAssertTrue(GrupoDocumentos.finalizados.incluye(documento(estado: .concluido)))
        XCTAssertFalse(GrupoDocumentos.finalizados.incluye(documento(estado: .enAtencion)))
    }

    func testGrupoVencidosDetectaDiasNegativos() {
        XCTAssertTrue(GrupoDocumentos.vencidos.incluye(documento(estado: .turnado, diasRestantes: -2)))
        XCTAssertFalse(GrupoDocumentos.vencidos.incluye(documento(estado: .turnado, diasRestantes: 2)))
    }

    func testGrupoPorVencerTresDias() {
        XCTAssertTrue(GrupoDocumentos.porVencer.incluye(documento(estado: .turnado, diasRestantes: 0)))
        XCTAssertTrue(GrupoDocumentos.porVencer.incluye(documento(estado: .turnado, diasRestantes: 3)))
        XCTAssertFalse(GrupoDocumentos.porVencer.incluye(documento(estado: .turnado, diasRestantes: 4)))
    }

    func testArchivoClasificaDocumentoVencido() {
        let archivo = DocumentoArchivo(
            documento: documento(estado: .turnado, diasRestantes: -1)
        )
        XCTAssertTrue(archivo.estaVencido)
        XCTAssertTrue(CategoriaArchivo.vencidos.incluye(archivo))
        XCTAssertFalse(CategoriaArchivo.porVencer.incluye(archivo))
    }

    func testFechaDelBackendAceptaMilisegundos() {
        let fecha = Documento.fecha(1_700_000_000_000)
        XCTAssertNotNil(fecha)
        XCTAssertEqual(
            Int(fecha!.timeIntervalSince1970),
            1_700_000_000
        )
    }
}
