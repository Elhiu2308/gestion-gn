import Foundation
import SwiftUI
import UIKit
import Vision
import VisionKit

struct PaginaEscaneada: Identifiable {
    let id = UUID()
    let imagen: UIImage
}

enum OrigenDocumento {

    case ninguno

    case paginas([UIImage])

    case imagen(UIImage)

    case archivo(URL)
}

struct EscanerDocumentosVista: UIViewControllerRepresentable {

    static var esCompatible: Bool {
        VNDocumentCameraViewController.isSupported
    }

    var alFinalizar: ([UIImage]) -> Void

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

enum ServicioEscaner {

    static func carpetaEscaneos() -> URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let carpeta = base.appendingPathComponent("Escaneos", isDirectory: true)
        if !FileManager.default.fileExists(atPath: carpeta.path) {
            try? FileManager.default.createDirectory(at: carpeta, withIntermediateDirectories: true)
        }
        return carpeta
    }

    static func nombreUnico(prefijo: String = "documento") -> String {
        "\(prefijo)_\(Int(Date().timeIntervalSince1970 * 1000))"
    }

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

    static func imagen(desde url: URL) -> UIImage? {
        guard let datos = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: datos)
    }
}
