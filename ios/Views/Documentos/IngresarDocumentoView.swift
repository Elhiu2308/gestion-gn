import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

/// Pantalla "Ingresar documento": punto de entrada para dar de alta un documento.
///
/// Réplica de la pantalla Android con las tres formas de ingreso:
/// 1. **Escanear documento** — abre el escáner nativo (`VNDocumentCameraViewController`).
/// 2. **Seleccionar de galería** — elige una fotografía o imagen guardada.
/// 3. **Importar archivo** — selecciona un PDF u otro archivo compatible.
struct IngresarDocumentoView: View {

    @Environment(\.dismiss) private var dismiss

    /// Se invoca tras registrar un documento correctamente.
    var onCreada: (Documento) -> Void = { _ in }

    @State private var origen: OrigenDocumento = .ninguno
    @State private var irANuevoDocumento = false

    @State private var mostrandoEscaner = false
    @State private var seleccionGaleria: PhotosPickerItem?
    @State private var mostrandoImportador = false
    @State private var mensajeError: String?

    var body: some View {
        NavigationStack {
            ZStack {
                EstiloInstitucional.fondo.ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        encabezado
                        tarjetaEscanear
                        tarjetaGaleria
                        tarjetaArchivo

                        Text("GestióN · Gestión Documental")
                            .font(.system(size: 11))
                            .foregroundColor(EstiloInstitucional.textoSecundario)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 8)
                            .padding(.bottom, 24)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                }
            }
            .navigationTitle("Ingresar documento")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { dismiss() }
                }
            }
            // Escáner de documentos nativo (con el marco de ajuste de esquinas).
            .fullScreenCover(isPresented: $mostrandoEscaner) {
                if EscanerDocumentosVista.esCompatible {
                    EscanerDocumentosVista(
                        alFinalizar: { paginas in
                            mostrandoEscaner = false
                            guard !paginas.isEmpty else { return }
                            origen = .paginas(paginas)
                            irANuevoDocumento = true
                        },
                        alCancelar: { mostrandoEscaner = false }
                    )
                    .ignoresSafeArea()
                } else {
                    VistaNoCompatible()
                }
            }
            // Importador de archivos (PDF, imágenes y documentos).
            .fileImporter(
                isPresented: $mostrandoImportador,
                allowedContentTypes: [.pdf, .image, .plainText, .data],
                allowsMultipleSelection: false
            ) { resultado in
                manejarImportacion(resultado)
            }
            // Al seleccionar una imagen de la galería.
            .onChange(of: seleccionGaleria) { _, nuevaSeleccion in
                guard let nuevo = nuevaSeleccion else { return }
                Task { await cargarDesdeGaleria(nuevo) }
            }
            // Empuje del formulario "Nuevo Documento" con el origen elegido.
            .navigationDestination(isPresented: $irANuevoDocumento) {
                CrearDocumentoView(origen: origen, onCreada: onCreada)
            }
            .alert(mensajeError ?? "", isPresented: Binding(
                get: { mensajeError != nil },
                set: { if !$0 { mensajeError = nil } }
            )) {
                Button("Entendido", role: .cancel) {}
            }
        }
    }

    // MARK: - Encabezado

    private var encabezado: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "doc.text.viewfinder")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                Text("GESTIÓN DOCUMENTAL")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .tracking(0.6)
            }

            Text("Nuevo documento")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(EstiloInstitucional.textoPrincipal)

            Text("Incorpora un documento al sistema y comienza su trazabilidad.")
                .font(.system(size: 14))
                .foregroundColor(EstiloInstitucional.textoSecundario)
                .lineSpacing(2)
        }
    }

    // MARK: - Tarjetas de opción

    private var tarjetaEscanear: some View {
        TarjetaOpcionDocumento(
            titulo: "Digitaliza tus documentos",
            descripcion: "Escanea hojas físicas o selecciona un archivo digital.",
            icono: "doc.text.viewfinder",
            colorIcono: EstiloInstitucional.verdePrincipal,
            fondoIcono: EstiloInstitucional.verdeFondoIcono,
            tituloOpcion: "Escanear documento",
            descripcionOpcion: "Captura las hojas con la cámara del dispositivo.",
            textoBoton: "Abrir cámara",
            iconoBoton: "camera.fill"
        ) {
            if EscanerDocumentosVista.esCompatible {
                mostrandoEscaner = true
            } else {
                mensajeError = "Este dispositivo no soporta el escáner de documentos."
            }
        }
    }

    private var tarjetaGaleria: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 15)
                        .fill(EstiloInstitucional.verdeFondoIcono)
                        .frame(width: 54, height: 54)
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.system(size: 24))
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text("Seleccionar de galería")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(EstiloInstitucional.textoPrincipal)
                    Text("Elige una fotografía o imagen guardada.")
                        .font(.system(size: 13))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                        .lineLimit(2)
                }
                Spacer()
            }

            PhotosPicker(selection: $seleccionGaleria, matching: .images) {
                Label("Elegir imagen", systemImage: "photo.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(EstiloInstitucional.verdeFondoIcono)
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, y: 2)
    }

    private var tarjetaArchivo: some View {
        TarjetaOpcionDocumento(
            titulo: nil,
            descripcion: nil,
            icono: "doc.badge.arrow.up",
            colorIcono: EstiloInstitucional.verdePrincipal,
            fondoIcono: EstiloInstitucional.verdeFondoIcono,
            tituloOpcion: "Importar archivo",
            descripcionOpcion: "Selecciona un PDF u otro archivo compatible.",
            textoBoton: "Explorar archivos",
            iconoBoton: "folder.fill"
        ) {
            mostrandoImportador = true
        }
    }

    // MARK: - Acciones

    private func manejarImportacion(_ resultado: Result<[URL], Error>) {
        switch resultado {
        case .success(let urls):
            guard let url = urls.first else { return }
            origen = .archivo(url)
            irANuevoDocumento = true
        case .failure:
            mensajeError = "No se pudo abrir el archivo seleccionado."
        }
    }

    private func cargarDesdeGaleria(_ elemento: PhotosPickerItem) async {
        guard let datos = try? await elemento.loadTransferable(type: Data.self),
              let imagen = UIImage(data: datos) else {
            mensajeError = "No se pudo cargar la imagen seleccionada."
            return
        }
        origen = .imagen(imagen)
        irANuevoDocumento = true
    }
}

// MARK: - Subvistas

/// Tarjeta de opción reutilizable (icono + título + descripción + botón).
private struct TarjetaOpcionDocumento: View {

    let titulo: String?
    let descripcion: String?
    let icono: String
    let colorIcono: Color
    let fondoIcono: Color
    let tituloOpcion: String
    let descripcionOpcion: String
    let textoBoton: String
    let iconoBoton: String
    let accion: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let titulo {
                VStack(alignment: .leading, spacing: 3) {
                    Text(titulo)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(EstiloInstitucional.textoPrincipal)
                    if let descripcion {
                        Text(descripcion)
                            .font(.system(size: 13))
                            .foregroundColor(EstiloInstitucional.textoSecundario)
                            .lineLimit(2)
                    }
                }
            }

            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 15)
                        .fill(fondoIcono)
                        .frame(width: 54, height: 54)
                    Image(systemName: icono)
                        .font(.system(size: 24))
                        .foregroundColor(colorIcono)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(tituloOpcion)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(EstiloInstitucional.textoPrincipal)
                    Text(descripcionOpcion)
                        .font(.system(size: 13))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                        .lineLimit(2)
                }
                Spacer()
            }

            Button(action: accion) {
                Label(textoBoton, systemImage: iconoBoton)
                    .font(.system(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(EstiloInstitucional.verdeFondoIcono)
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, y: 2)
    }
}

/// Vista informativa para dispositivos sin soporte de escáner (p. ej. simulador).
private struct VistaNoCompatible: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            EstiloInstitucional.fondo.ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "camera.metering.unknown")
                    .font(.system(size: 52))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
                Text("Escáner no disponible")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(EstiloInstitucional.textoPrincipal)
                Text("Este dispositivo no soporta el escáner de documentos. Usa la galería o importa un archivo.")
                    .font(.system(size: 14))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                Button("Cerrar") { dismiss() }
                    .buttonStyle(.borderedProminent)
                    .tint(EstiloInstitucional.verdePrincipal)
            }
        }
    }
}
