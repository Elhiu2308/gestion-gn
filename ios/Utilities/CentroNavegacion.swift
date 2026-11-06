import Foundation
import SwiftUI

/// Coordinador mínimo para abrir un documento a partir de una notificación
/// (push, socket.io en segundo plano o centro de notificaciones).
///
/// Se usa como singleton observable para que `RootView` pueda presentar el
/// detalle sin necesidad de una navegación global compleja.
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
