import SwiftUI

/**
 * Identidad visual institucional de GestióN.
 *
 * Paleta oficial de Android:
 * - Verde institucional: #285C4D (RGB: 40, 92, 77)
 * - Verde oscuro: #0B3A33 (RGB: 11, 58, 51)
 * - Verde claro: #2E8B6D (RGB: 46, 139, 109)
 * - Dorado institucional: #B38E5D (RGB: 179, 142, 93)
 * - Dorado claro: #D4C19C (RGB: 212, 193, 156)
 * - Guinda: #611232 (RGB: 97, 18, 50)
 * - Fondo: #F4F3F6 (RGB: 244, 243, 246)
 * - Fondo degradado inicio: #EAF3EE, centro: #F5F7F4, fin: #F8F9F7
 * - Texto principal: #1B1F1D (RGB: 27, 31, 29)
 * - Gris texto: #7A8680 (RGB: 122, 134, 128)
 * - Borde suave: #E2E8F0 (RGB: 226, 232, 240)
 * - Rojo urgencia: #E53935 (RGB: 229, 57, 53)
 */
enum EstiloInstitucional {

    // MARK: Colores institucionales
    static let verdePrincipal = Color(red: 40 / 255.0, green: 92 / 255.0, blue: 77 / 255.0) // #285C4D
    static let verdeOscuro = Color(red: 11 / 255.0, green: 58 / 255.0, blue: 51 / 255.0)    // #0B3A33
    static let verdeClaro = Color(red: 46 / 255.0, green: 139 / 255.0, blue: 109 / 255.0)  // #2E8B6D
    static let verdeSuave = Color(red: 40 / 255.0, green: 92 / 255.0, blue: 77 / 255.0).opacity(0.12)
    static let verdeFondoIcono = Color(red: 232 / 255.0, green: 241 / 255.0, blue: 236 / 255.0) // #E8F1EC

    static let dorado = Color(red: 179 / 255.0, green: 142 / 255.0, blue: 93 / 255.0)       // #B38E5D
    static let doradoClaro = Color(red: 212 / 255.0, green: 193 / 255.0, blue: 156 / 255.0) // #D4C19C
    static let doradoFondoIcono = Color(red: 245 / 255.0, green: 239 / 255.0, blue: 223 / 255.0) // #F5EFDF

    static let guinda = Color(red: 97 / 255.0, green: 18 / 255.0, blue: 50 / 255.0)         // #611232

    static let fondo = Color(red: 244 / 255.0, green: 243 / 255.0, blue: 246 / 255.0)       // #F4F3F6
    static let superficie = Color.white
    static let textoPrincipal = Color(red: 27 / 255.0, green: 31 / 255.0, blue: 29 / 255.0) // #1B1F1D
    static let textoSecundario = Color(red: 122 / 255.0, green: 134 / 255.0, blue: 128 / 255.0) // #7A8680
    static let bordeSuave = Color(red: 226 / 255.0, green: 232 / 255.0, blue: 240 / 255.0) // #E2E8F0
    static let rojoUrgencia = Color(red: 229 / 255.0, green: 57 / 255.0, blue: 53 / 255.0) // #E53935

    // Colores tarjetas métricas (dashboard)
    static let cardBgDocumentos = Color(red: 254 / 255.0, green: 248 / 255.0, blue: 236 / 255.0)
    static let cardStrokeDocumentos = Color(red: 243 / 255.0, green: 229 / 255.0, blue: 200 / 255.0)

    static let cardBgProyectos = Color(red: 235 / 255.0, green: 247 / 255.0, blue: 242 / 255.0)
    static let cardStrokeProyectos = Color(red: 199 / 255.0, green: 232 / 255.0, blue: 219 / 255.0)

    static let cardBgCompartidos = Color(red: 253 / 255.0, green: 242 / 255.0, blue: 244 / 255.0)
    static let cardStrokeCompartidos = Color(red: 244 / 255.0, green: 211 / 255.0, blue: 217 / 255.0)

    static let cardBgSubir = Color(red: 238 / 255.0, green: 245 / 255.0, blue: 252 / 255.0)
    static let cardStrokeSubir = Color(red: 203 / 255.0, green: 223 / 255.0, blue: 245 / 255.0)

    // MARK: Tipografía
    static func fuente(_ estilo: Font.TextStyle) -> Font {
        .system(estilo)
    }

    // MARK: Componentes
    static var tarjeta: some View {
        RoundedRectangle(cornerRadius: 18)
            .fill(EstiloInstitucional.superficie)
            .shadow(color: Color.black.opacity(0.04), radius: 6, y: 3)
    }

    static func tarjeta<Contenido: View>(@ViewBuilder contenido: () -> Contenido) -> some View {
        EstiloInstitucional.tarjeta
            .padding(16)
            .overlay {
                contenido()
            }
    }

    static func botonPrincipal(etiqueta: String, accion: @escaping () -> Void) -> some View {
        Button(etiqueta, action: accion)
            .font(.callout.weight(.semibold))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(EstiloInstitucional.verdePrincipal)
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    static func botonSecundario(etiqueta: String, accion: @escaping () -> Void) -> some View {
        Button(etiqueta, action: accion)
            .font(.callout.weight(.semibold))
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .background(EstiloInstitucional.verdeSuave)
            .foregroundColor(EstiloInstitucional.verdePrincipal)
            .overlay(Capsule().stroke(EstiloInstitucional.verdePrincipal, lineWidth: 1.5))
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    /// Banner de error o aviso, con icono y acciones opcionales.
    static func aviso(mensaje: String, sistema: Bool = true) -> some View {
        if sistema {
            Label(mensaje, systemImage: "exclamationmark.triangle.fill")
                .font(.footnote)
                .foregroundColor(.white)
                .padding(12)
                .background(Color.orange)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        } else {
            Label(mensaje, systemImage: "info.circle.fill")
                .font(.footnote)
                .foregroundColor(EstiloInstitucional.verdePrincipal)
                .padding(12)
                .background(EstiloInstitucional.verdeSuave)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }

    // MARK: Fondo institucional degradado (réplica exacta de bg_login_fondo.xml)
    static var fondoLogin: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 234 / 255.0, green: 243 / 255.0, blue: 238 / 255.0),
                    Color(red: 245 / 255.0, green: 247 / 255.0, blue: 244 / 255.0),
                    Color(red: 248 / 255.0, green: 249 / 255.0, blue: 247 / 255.0)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            GeometryReader { proxy in
                // Mancha verde suave superior derecha
                Circle()
                    .fill(Color(red: 11 / 255.0, green: 93 / 255.0, blue: 59 / 255.0).opacity(0.10))
                    .frame(width: 300, height: 300)
                    .offset(x: proxy.size.width - 150, y: -112)

                // Mancha verde sutil superior izquierda
                Circle()
                    .fill(Color(red: 11 / 255.0, green: 93 / 255.0, blue: 59 / 255.0).opacity(0.05))
                    .frame(width: 150, height: 150)
                    .offset(x: -60, y: 100)

                // Acento dorado inferior izquierdo
                Circle()
                    .fill(Color(red: 198 / 255.0, green: 162 / 255.0, blue: 74 / 255.0).opacity(0.10))
                    .frame(width: 220, height: 220)
                    .offset(x: -70, y: proxy.size.height - 120)
            }
            .ignoresSafeArea()
        }
    }
}

// MARK: - Barra de navegación institucional

extension View {
    /// Aplica el color verde institucional a la barra de navegación.
    /// Se centraliza aquí para que todas las pantallas mantengan la misma
    /// identidad visual sin repetir colores ni configuraciones.
    func barraInstitucional() -> some View {
        self
            .toolbarBackground(EstiloInstitucional.verdePrincipal, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
    }
}

/// Botón de regreso compartido por las pestañas que no tienen un botón
/// automático de navegación. Vuelve a la pestaña Inicio.
struct BotonRegresarInicio: View {

    let accion: () -> Void

    var body: some View {
        Button(action: accion) {
            Image(systemName: "chevron.left")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
        }
        .accessibilityLabel("Regresar")
    }
}
