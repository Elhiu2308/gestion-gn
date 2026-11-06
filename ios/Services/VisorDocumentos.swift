import SwiftUI
import QuickLook
import PDFKit

/// Visor de documentos (PDF, imágenes y otros archivos) usando QuickLook.
struct VistaPreviaArchivo: UIViewControllerRepresentable {

    let url: URL

    func makeCoordinator() -> Coordinador {
        Coordinador(url: url)
    }

    func makeUIViewController(context: Context) -> QLPreviewController {
        let controlador = QLPreviewController()
        controlador.dataSource = context.coordinator
        return controlador
    }

    func updateUIViewController(_ uiViewController: QLPreviewController, context: Context) {}

    final class Coordinador: NSObject, QLPreviewControllerDataSource {

        let url: URL

        init(url: URL) {
            self.url = url
        }

        func numberOfPreviewItems(in controller: QLPreviewController) -> Int {
            1
        }

        func previewController(
            _ controller: QLPreviewController,
            previewItemAt index: Int
        ) -> QLPreviewItem {
            url as NSURL
        }
    }
}

/// Visor en línea de un PDF a partir de sus datos en memoria.
struct VisorPDF: UIViewRepresentable {
    let datos: Data

    func makeUIView(context: Context) -> PDFView {
        let vista = PDFView()
        vista.autoScales = true
        vista.displayMode = .singlePageContinuous
        if let documento = PDFDocument(data: datos) {
            vista.document = documento
        }
        return vista
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        if uiView.document == nil, let documento = PDFDocument(data: datos) {
            uiView.document = documento
        }
    }
}

/// Pantalla de vista previa de un archivo con botón para cerrar.
struct PantallaVistaPrevia: View {

    @Environment(\.dismiss) private var dismiss
    let url: URL

    var body: some View {
        NavigationStack {
            VistaPreviaArchivo(url: url)
                .ignoresSafeArea(edges: .bottom)
                .navigationTitle(url.lastPathComponent)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Cerrar") { dismiss() }
                    }
                }
        }
    }
}
