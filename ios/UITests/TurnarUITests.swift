import XCTest

final class TurnarUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testDumpTurnarSheet() throws {
        let app = XCUIApplication()
        app.launch()
        abrirDocumentos(app)

        let buscador = app.searchFields.firstMatch
        XCTAssertTrue(buscador.waitForExistence(timeout: 10))
        buscador.tap()
        buscador.typeText("871")

        let folio = app.staticTexts["871"].firstMatch
        XCTAssertTrue(folio.waitForExistence(timeout: 20), "No apareció el documento 871")
        folio.tap()

        XCTAssertTrue(app.staticTexts["Información del documento"].waitForExistence(timeout: 25), "No abrió el detalle")
        let turnar = app.buttons["Turnar"].firstMatch
        if !turnar.waitForExistence(timeout: 5) {
            print("UITEST_NO_TURNAR_BUTTON\n\(app.debugDescription)")
        }
        XCTAssertTrue(turnar.exists, "No hay botón Turnar")
        turnar.tap()

        XCTAssertTrue(app.navigationBars["Turnar"].waitForExistence(timeout: 20), "No abrió la hoja Turnar")
        Thread.sleep(forTimeInterval: 2)
        print("UITEST_TURNAR_HIERARCHY\n\(app.debugDescription)")
    }

    func testDumpAreaPicker() throws {
        let app = XCUIApplication()
        app.launch()
        abrirDocumentos(app)
        let buscador = app.searchFields.firstMatch
        XCTAssertTrue(buscador.waitForExistence(timeout: 10))
        buscador.tap(); buscador.typeText("871")
        let folio = app.staticTexts["871"].firstMatch
        XCTAssertTrue(folio.waitForExistence(timeout: 20))
        folio.tap()
        XCTAssertTrue(app.staticTexts["Información del documento"].waitForExistence(timeout: 25))
        app.buttons["Turnar"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Turnar"].waitForExistence(timeout: 20))
        let area = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "1. Área")).firstMatch
        XCTAssertTrue(area.waitForExistence(timeout: 10))
        area.tap()
        Thread.sleep(forTimeInterval: 2)
        print("UITEST_AREA_PICKER\n\(app.debugDescription)")
    }
}

extension TurnarUITests {

    /// Abre la lista de documentos desde el dashboard. La pestaña
    /// "Expedientes" ahora muestra expedientes (no documentos), por lo que el
    /// acceso a documentos se hace desde la tarjeta "Mis documentos".
    private func abrirDocumentos(_ app: XCUIApplication) {
        XCTAssertTrue(app.staticTexts["Tus documentos"].waitForExistence(timeout: 30))

        let tarjeta = app.buttons
            .matching(NSPredicate(format: "label CONTAINS[c] %@", "Mis documentos"))
            .firstMatch

        if !tarjeta.waitForExistence(timeout: 5) {
            app.swipeUp()
        }

        XCTAssertTrue(tarjeta.waitForExistence(timeout: 10), "No apareció la tarjeta Mis documentos")
        tarjeta.tap()

        if !app.navigationBars["Documentos"].waitForExistence(timeout: 20) {
            print("UITEST_NO_DOCUMENTOS\n\(app.debugDescription)")
        }
        XCTAssertTrue(app.navigationBars["Documentos"].exists)
    }

    private func seleccionar(_ app: XCUIApplication, prefijo: String, opcion: String) {
        let boton = app.buttons
            .matching(NSPredicate(format: "label BEGINSWITH %@", prefijo))
            .firstMatch
        XCTAssertTrue(boton.waitForExistence(timeout: 15), "No existe selector \(prefijo)")
        boton.tap()

        let elegido = app.buttons[opcion]
        if !elegido.waitForExistence(timeout: 10) {
            print("UITEST_NO_OPCION \(opcion)\n\(app.debugDescription)")
        }
        XCTAssertTrue(elegido.exists, "No existe la opción \(opcion)")

        var intentos = 0
        while !elegido.isHittable && intentos < 8 {
            app.collectionViews.firstMatch.swipeUp()
            intentos += 1
        }
        XCTAssertTrue(elegido.isHittable, "La opción \(opcion) no quedó visible")
        elegido.tap()
    }

    /// Flujo real 48360 -> 72438: desde la app iOS, abre el documento 871
    /// (turnado antes desde Android) y lo turna a la Subdirección de Christian.
    func testTurnarDocumentoAChristian() throws {
        let app = XCUIApplication()
        app.launch()
        abrirDocumentos(app)

        let buscador = app.searchFields.firstMatch
        XCTAssertTrue(buscador.waitForExistence(timeout: 10))
        buscador.tap()
        buscador.typeText("871")

        let folio = app.staticTexts["871"].firstMatch
        XCTAssertTrue(folio.waitForExistence(timeout: 20), "No apareció el documento 871")
        folio.tap()
        XCTAssertTrue(app.staticTexts["Información del documento"].waitForExistence(timeout: 25))
        app.buttons["Turnar"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Turnar"].waitForExistence(timeout: 20))

        seleccionar(app, prefijo: "1. Área", opcion: "Innovación Tecnológica")
        Thread.sleep(forTimeInterval: 3)
        seleccionar(app, prefijo: "2. Subdirección", opcion: "Subdirección de Desarrollo de Aplicaciones Web Operativas y de Proximidad")
        Thread.sleep(forTimeInterval: 3)
        seleccionar(app, prefijo: "Persona", opcion: "Christian Elhiu Espindola Muñoz")

        let turnar = app.navigationBars["Turnar"].buttons["Turnar"]
        XCTAssertTrue(turnar.waitForExistence(timeout: 10))
        XCTAssertTrue(turnar.isEnabled, "El botón Turnar sigue deshabilitado")
        turnar.tap()

        let cerro = !app.navigationBars["Turnar"].waitForExistence(timeout: 25)
        if !cerro {
            print("UITEST_TURNAR_ERROR\n\(app.debugDescription)")
        }
        XCTAssertTrue(cerro, "La hoja Turnar no se cerró (posible error del servidor)")

        XCTAssertTrue(app.navigationBars["871"].waitForExistence(timeout: 15))
        let captura = XCTAttachment(screenshot: app.screenshot())
        captura.name = "turnar-48360-a-72438"
        captura.lifetime = .keepAlways
        add(captura)
        print("UITEST_TURNAR_OK documento=871")
    }
}
