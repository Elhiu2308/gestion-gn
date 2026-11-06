import SwiftUI

/// Componentes visuales reutilizables más allá del tema.
///
/// - Tarjetas con títulos y contenidos.
/// - Cabeceras de sección.
/// - Botones reutilizables con el estilo institucional.
/// - Avisos de error y de éxito.
/// - Las confirmaciones destructivas se construyen en las vistas con `.alert`.
enum ComponentesUI {

    // MARK: Tarjetas

    /// Tarjeta blanca con esquinas redondeadas, como se usa en todas las pantallas.
    static func tarjeta<Contenido: View>(cabecera: String? = nil, @ViewBuilder contenido: () -> Contenido) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            if let cabecera {
                Text(cabecera)
                    .font(.callout.weight(.semibold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
            }
            contenido()
        }
        .padding(16)
        .frame(maxWidth: 760, alignment: .leading)
        .background(EstiloInstitucional.tarjeta)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .shadow(radius: 6, y: 3)
    }

    // MARK: Cabeceras de sección

    static func cabeceraSeccion(_ titulo: String) -> some View {
        Text(titulo)
            .font(.title3.weight(.semibold))
            .foregroundColor(EstiloInstitucional.verdePrincipal)
            .frame(maxWidth: 760, alignment: .leading)
    }

    // MARK: Botones

    static func botonPrincipal(etiqueta: String, accion: @escaping () -> Void) -> some View {
        Button(action: accion) {
            Label(etiqueta, systemImage: "arrow.right")
        }
        .font(.callout.weight(.semibold))
        .frame(maxWidth: 220)
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .background(EstiloInstitucional.verdePrincipal)
        .foregroundColor(.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    static func botonSecundario(etiqueta: String, accion: @escaping () -> Void) -> some View {
        Button(action: accion) {
            Label(etiqueta, systemImage: "circlepath.circle.fill")
        }
        .font(.callout.weight(.semibold))
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .background(EstiloInstitucional.verdeSuave)
        .foregroundColor(EstiloInstitucional.verdePrincipal)
        .overlay(Capsule().stroke(EstiloInstitucional.verdePrincipal, lineWidth: 1.5))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: Avisos

    /// Mensaje de error con icono y color institucional.
    static func avisoError(mensaje: String) -> some View {
        Label(mensaje, systemImage: "exclamationmark.triangle.fill")
            .font(.footnote)
            .foregroundColor(.white)
            .padding(12)
            .background(Color.orange)
            .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    static func avisoExito(mensaje: String) -> some View {
        Label(mensaje, systemImage: "checkmark.circle.fill")
            .font(.footnote)
            .foregroundColor(EstiloInstitucional.verdePrincipal)
            .padding(12)
            .background(EstiloInstitucional.verdeSuave)
            .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
