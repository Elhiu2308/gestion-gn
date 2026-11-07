import AVFoundation
import SwiftUI
import VisionKit

struct EscannerQRVista: UIViewControllerRepresentable {

    static var esCompatible: Bool {
        if #available(iOS 16.0, *) {
            return DataScannerViewController.isSupported
        }
        return false
    }

    var alEscanear: (String) -> Void

    var alFallar: (String) -> Void

    func makeCoordinator() -> Coordinador {
        Coordinador(alEscanear: alEscanear, alFallar: alFallar)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        guard #available(iOS 16.0, *) else {
            return UIViewController()
        }
        let controlador = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: true,
            isHighlightingEnabled: true
        )
        controlador.delegate = context.coordinator
        do {
            try controlador.startScanning()
        } catch {
            alFallar("No se pudo iniciar la cámara para escanear el código QR.")
        }
        return controlador
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    final class Coordinador: NSObject, DataScannerViewControllerDelegate {

        private let alEscanear: (String) -> Void
        private let alFallar: (String) -> Void
        private var yaEscaneado = false

        init(alEscanear: @escaping (String) -> Void, alFallar: @escaping (String) -> Void) {
            self.alEscanear = alEscanear
            self.alFallar = alFallar
        }

        @available(iOS 16.0, *)
        func dataScanner(
            _ dataScanner: DataScannerViewController,
            didAdd addedItems: [RecognizedItem],
            allItems: [RecognizedItem]
        ) {
            guard !yaEscaneado else { return }
            for item in addedItems {
                if case let .barcode(codigo) = item,
                   let valor = codigo.payloadStringValue,
                   !valor.isEmpty {
                    yaEscaneado = true
                    dataScanner.stopScanning()
                    alEscanear(valor)
                    return
                }
            }
        }
    }
}

struct EscanerQRContinuo: UIViewControllerRepresentable {

    @Binding var activo: Bool

    var alEscanear: (String) -> Void

    var alFallar: (String) -> Void

    func makeCoordinator() -> Coordinador {
        Coordinador(alEscanear: alEscanear, alFallar: alFallar)
    }

    @available(iOS 16.0, *)
    func makeUIViewController(context: Context) -> DataScannerViewController {
        let controlador = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: true,
            isHighlightingEnabled: true
        )
        controlador.delegate = context.coordinator

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            guard activo, !controlador.isScanning else { return }
            do {
                try controlador.startScanning()
            } catch {
                alFallar("No se pudo iniciar la cámara para escanear el código QR.")
            }
        }
        return controlador
    }

    @available(iOS 16.0, *)
    func updateUIViewController(_ controlador: DataScannerViewController, context: Context) {
        if activo {
            if !context.coordinator.estabaActivo {
                context.coordinator.reiniciar()
            }
            if !controlador.isScanning {
                do {
                    try controlador.startScanning()
                } catch {
                    alFallar("No se pudo reanudar la cámara.")
                }
            }
        } else if controlador.isScanning {
            controlador.stopScanning()
        }
        context.coordinator.estabaActivo = activo
    }

    final class Coordinador: NSObject, DataScannerViewControllerDelegate {

        private let alEscanear: (String) -> Void
        private let alFallar: (String) -> Void
        private var yaEscaneado = false
        var estabaActivo = true

        init(alEscanear: @escaping (String) -> Void, alFallar: @escaping (String) -> Void) {
            self.alEscanear = alEscanear
            self.alFallar = alFallar
        }

        func reiniciar() {
            yaEscaneado = false
        }

        @available(iOS 16.0, *)
        func dataScanner(
            _ dataScanner: DataScannerViewController,
            didAdd addedItems: [RecognizedItem],
            allItems: [RecognizedItem]
        ) {
            guard !yaEscaneado else { return }
            for item in addedItems {
                if case let .barcode(codigo) = item,
                   let valor = codigo.payloadStringValue,
                   !valor.isEmpty {
                    yaEscaneado = true
                    dataScanner.stopScanning()
                    alEscanear(valor)
                    return
                }
            }
        }
    }
}

enum ServicioLinterna {

    static var disponible: Bool {
        AVCaptureDevice.default(for: .video)?.hasTorch ?? false
    }

    @discardableResult
    static func establecer(_ encendida: Bool) -> Bool {
        guard let dispositivo = AVCaptureDevice.default(for: .video),
              dispositivo.hasTorch else { return false }
        do {
            try dispositivo.lockForConfiguration()
            dispositivo.torchMode = encendida ? .on : .off
            dispositivo.unlockForConfiguration()
            return true
        } catch {
            return false
        }
    }
}

struct PantallaEscanerQR: View {

    @Environment(\.dismiss) private var dismiss
    var alEscanear: (String) -> Void

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if EscannerQRVista.esCompatible {
                EscannerQRVista(
                    alEscanear: { valor in
                        alEscanear(valor)
                        dismiss()
                    },
                    alFallar: { _ in dismiss() }
                )
                .ignoresSafeArea()

                MarcoEscaneoQR()
            } else {
                VStack(spacing: 14) {
                    Image(systemName: "qrcode.viewfinder")
                        .font(.system(size: 52))
                        .foregroundColor(.white.opacity(0.8))
                    Text("El escáner de códigos QR no está disponible en este dispositivo.")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
            }

            VStack {
                HStack {
                    Spacer()
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 30))
                            .foregroundColor(.white)
                            .padding()
                    }
                }
                Spacer()
                Text("Apunta al código QR del documento")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.black.opacity(0.5))
                    .clipShape(Capsule())
                    .padding(.bottom, 40)
            }
        }
    }
}

private struct MarcoEscaneoQR: View {

    var body: some View {
        GeometryReader { proxy in
            let lado = min(proxy.size.width, proxy.size.height) * 0.65
            ZStack {
                RoundedRectangle(cornerRadius: 24)
                    .stroke(Color.white.opacity(0.35), lineWidth: 1)
                    .frame(width: lado, height: lado)

                EsquinasMarco()
                    .stroke(EstiloInstitucional.dorado, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                    .frame(width: lado, height: lado)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .ignoresSafeArea()
    }
}

private struct EsquinasMarco: Shape {

    func path(in rect: CGRect) -> Path {
        let largo = rect.width * 0.18
        var camino = Path()

        camino.move(to: CGPoint(x: rect.minX, y: rect.minY + largo))
        camino.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        camino.addLine(to: CGPoint(x: rect.minX + largo, y: rect.minY))

        camino.move(to: CGPoint(x: rect.maxX - largo, y: rect.minY))
        camino.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        camino.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + largo))

        camino.move(to: CGPoint(x: rect.maxX, y: rect.maxY - largo))
        camino.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        camino.addLine(to: CGPoint(x: rect.maxX - largo, y: rect.maxY))

        camino.move(to: CGPoint(x: rect.minX + largo, y: rect.maxY))
        camino.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        camino.addLine(to: CGPoint(x: rect.minX, y: rect.maxY - largo))

        return camino
    }
}
