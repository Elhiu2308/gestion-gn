import Foundation
import SwiftUI

@MainActor
final class CentroNavegacion: ObservableObject {
    static let shared = CentroNavegacion()

    @Published var documentoPendiente: Int?
    @Published var notificacionPendiente: Int?

    private init() {}

    func abrir(documentoId: Int, notificacionId: Int? = nil) {
        guard documentoId > 0 else { return }
        documentoPendiente = documentoId
        notificacionPendiente = notificacionId
    }

    func cerrar() {
        documentoPendiente = nil
        notificacionPendiente = nil
    }
}
