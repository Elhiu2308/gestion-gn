import Foundation
import SwiftUI
import UIKit
import Vision
import VisionKit

// ---------------------------------------------------------------------------
// Escaneo de documentos (VisionKit) y OCR (Vision)
// ---------------------------------------------------------------------------
//
// Equivalencias con la app Android:
//   - `ML Kit Document Scanner`  ->  `VNDocumentCameraViewController` (VisionKit)
//   - `ML Kit Text Recognition`  ->  `VNRecognizeTextRequest` (Vision)
//
// El controlador nativo de Apple ya incluye el "marco": detecta los bordes de
// la hoja, permite **ajustar las esquinas**, **rotar**, **volver a tomar** y
// **aplicar**, y admite varias páginas por sesión.

/// Página capturada por el escáner de documentos.
struct PaginaEscaneada: Identifiable {
    let id = UUID()
    let imagen: UIImage
}

/// Origen del documento que se está registrando en la pantalla "Nuevo Documento".
enum OrigenDocumento {
    /// Se abre el formulario sin archivo adjunto.
    case ninguno
    /// Varias páginas capturadas con el escáner de documentos.
    case paginas([UIImage])
    /// Una imagen elegida desde la galería.
    case imagen(UIImage)
    /// Un archivo (PDF/imagen) importado del almacenamiento.
    case archivo(URL)
}

/// Envuelve `VNDocumentCameraViewController` (VisionKit) para usarlo desde SwiftUI.
struct EscanerDocumentosVista: UIViewControllerRepresentable {

    /// Indica si el dispositivo soporta el escáner de documentos nativo.
    static var esCompatible: Bool {
        VNDocumentCameraViewController.isSupported
    }

    /// Se invoca con las páginas digitalizadas (una o más).
    var alFinalizar: ([UIImage]) -> Void
    /// Se invoca al cancelar o si el escáner falla.
    var alCancelar: () -> Void

    func makeCoordinator() -> Coordinador {
        Coordinador(self)
    }

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let controlador = VNDocumentCameraViewController()
        controlador.delegate = context.coordinator
        return controlador
    }

    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}

    final class Coordinador: NSObject, VNDocumentCameraViewControllerDelegate {

        private let padre: EscanerDocumentosVista

        init(_ padre: EscanerDocumentosVista) {
            self.padre = padre
        }

        func documentCameraViewController(
            _ controller: VNDocumentCameraViewController,
            didFinishWith scan: VNDocumentCameraScan
        ) {
            var paginas: [UIImage] = []
            for indice in 0..<scan.pageCount {
                paginas.append(scan.imageOfPage(at: indice))
            }
            padre.alFinalizar(paginas)
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            padre.alCancelar()
        }

        func documentCameraViewController(
            _ controller: VNDocumentCameraViewController,
            didFailWithError error: Error
        ) {
            padre.alCancelar()
        }
    }
}

/// Utilidades del escaneo: guardado local (imágenes y PDF) y OCR con Vision.
enum ServicioEscaner {
    /// Carpeta privada donde se guardan los escaneos (`<Documents>/Escaneos`).
    static func carpetaEscaneos() -> URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let carpeta = base.appendingPathComponent("Escaneos", isDirectory: true)
        if !FileManager.default.fileExists(atPath: carpeta.path) {
            try? FileManager.default.createDirectory(at: carpeta, withIntermediateDirectories: true)
        }
        return carpeta
    }

    /// Nombre único para un escaneo con la marca de tiempo actual.
    static func nombreUnico(prefijo: String = "documento") -> String {
        "\(prefijo)_\(Int(Date().timeIntervalSince1970 * 1000))"
    }

    /// Guarda una imagen escaneada como JPEG y devuelve su ruta local.
    @discardableResult
    static func guardar(imagen: UIImage, nombre: String) -> URL? {
        guard let datos = imagen.jpegData(compressionQuality: 0.85) else { return nil }
        let destino = carpetaEscaneos().appendingPathComponent("\(nombre).jpg")
        do {
            try datos.write(to: destino, options: .atomic)
            return destino
        } catch {
            return nil
        }
    }

    /// Genera un PDF con todas las páginas escaneadas y devuelve su ruta local.
    @discardableResult
    static func guardarPDF(paginas: [UIImage], nombre: String) -> URL? {
        guard let primera = paginas.first else { return nil }
        let destino = carpetaEscaneos().appendingPathComponent("\(nombre).pdf")
        let renderizador = UIGraphicsPDFRenderer(
            bounds: CGRect(x: 0, y: 0, width: primera.size.width, height: primera.size.height)
        )
        do {
            try renderizador.writePDF(to: destino) { contexto in
                for pagina in paginas {
                    let rect = CGRect(x: 0, y: 0, width: pagina.size.width, height: pagina.size.height)
                    contexto.beginPage(withBounds: rect, pageInfo: [:])
                    pagina.draw(in: rect)
                }
            }
            return destino
        } catch {
            return nil
        }
    }

    /// Reconoce el texto de una imagen con el framework Vision (equivalente a ML Kit).
    static func reconocerTexto(en imagen: UIImage) async -> String {
        guard let cgImage = imagen.cgImage else { return "" }
        return await Task.detached(priority: .userInitiated) {
            let peticion = VNRecognizeTextRequest()
            peticion.recognitionLevel = .accurate
            peticion.recognitionLanguages = ["es-ES", "en-US"]
            peticion.usesLanguageCorrection = true
            let manejador = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try manejador.perform([peticion])
                let observaciones = peticion.results ?? []
                return observaciones
                    .compactMap { $0.topCandidates(1).first?.string }
                    .joined(separator: "\n")
            } catch {
                return ""
            }
        }.value
    }

    /// Carga un `UIImage` desde una URL local (archivo importado o foto de galería).
    static func imagen(desde url: URL) -> UIImage? {
        guard let datos = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: datos)
    }
}
