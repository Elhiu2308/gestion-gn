import XCTest

final class NotificationTapUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func abrirCentroNotificaciones(_ springboard: XCUIApplication) {
        let inicio = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.01))
        let fin = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75))
        inicio.press(forDuration: 0.1, thenDragTo: fin)
    }

    func testNotificationTapOpensRelatedDocument() throws {
        let app = XCUIApplication()
        app.launch()
        app.terminate()
        Thread.sleep(forTimeInterval: 2)

        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        abrirCentroNotificaciones(springboard)
        Thread.sleep(forTimeInterval: 2)

        let notificacion = springboard.buttons
            .matching(NSPredicate(format: "label CONTAINS[c] %@", "Documento asignado"))
            .firstMatch
        if !notificacion.waitForExistence(timeout: 10) {
            print("UITEST_NO_NOTIFICATION_HIERARCHY\n\(springboard.debugDescription)")
        }
        XCTAssertTrue(notificacion.exists, "No hay notificación de documento")
        print("UITEST_TAP_NOTIFICATION label=\(notificacion.label)")
        notificacion.tap()

        let detalle = app.staticTexts["Información del documento"]
            .waitForExistence(timeout: 40)
        XCTAssertTrue(detalle, "La notificación no abrió el detalle del documento")

        print("UITEST_DETAIL_OPENED hierarchy=\n\(app.debugDescription)")

        let captura = XCTAttachment(screenshot: app.screenshot())
        captura.name = "notificacion-abre-documento"
        captura.lifetime = .keepAlways
        add(captura)
    }
}
