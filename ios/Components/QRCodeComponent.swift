import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

/// Visualización de un código QR interoperable con los lectores del sistema.
struct QRCodeComponent: View {

    let url: String
    var tamaño: CGFloat = 180

    var body: some View {
        VStack(spacing: 12) {
            if let imagen = QRCode.imagenDe(texto: url, tamaño: tamaño) {
                Image(uiImage: imagen)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: tamaño, height: tamaño)
                    .background(.white, in: RoundedRectangle(cornerRadius: 16))
                    .padding(12)
                    .background(EstiloInstitucional.tarjeta)
            } else {
                Text("El código QR no está disponible")
                    .foregroundStyle(EstiloInstitucional.textoSecundario)
            }

            Text(url)
                .font(.caption)
                .foregroundStyle(EstiloInstitucional.textoSecundario)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
        }
        .padding(16)
    }
}

/// Generador basado en Core Image, compatible con lectores QR estándar.
enum QRCode {

    static func imagenDe(texto: String, tamaño: CGFloat) -> UIImage? {
        guard let data = texto.data(using: .utf8) else { return nil }

        let filter = CIFilter.qrCodeGenerator()
        filter.correctionLevel = "M"
        filter.message = data

        guard let outputImage = filter.outputImage else { return nil }

        let scale = tamaño / outputImage.extent.width
        let transformedImage = outputImage.transformed(
            by: CGAffineTransform(scaleX: scale, y: scale)
        )
        return UIImage(ciImage: transformedImage)
    }
}