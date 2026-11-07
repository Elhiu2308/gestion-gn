import SwiftUI
import PhotosUI
import CryptoKit
import UIKit

struct PerfilView: View {

    var alRegresar: (() -> Void)? = nil

    @StateObject private var vistaModelo = PerfilViewModel()
    @State private var elementoFoto: PhotosPickerItem?
    @State private var fotoPersonalizada: UIImage?

    var body: some View {
        NavigationStack {
            ZStack {
                EstiloInstitucional.fondo.ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 20) {
                        PerfilEncabezado()

                        if let perfil = vistaModelo.perfil {
                            PerfilIdentidadCard(
                                nombre: nombreMostrado(perfil),
                                usuario: perfil.usuario,
                                rol: perfil.rol,
                                cargo: perfil.cargo,
                                grado: perfil.grado,
                                area: perfil.area,
                                correo: perfil.correo,
                                fotoPersonalizada: fotoPersonalizada,
                                elementoFoto: $elementoFoto
                            )

                            PerfilSeguridadCard(
                                autenticacionHabilitada: ServicioKeychain.tieneAccesoBiometrico(),
                                nombreBiometria: ServicioKeychain.tipoBiometria().nombre
                            )

                            PerfilFirmaCard(
                                codigoQR: vistaModelo.qrTexto,
                                hash: hashFirma(perfil),
                                alSincronizar: {
                                    Task { await vistaModelo.cargar() }
                                }
                            )
                        } else {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding(40)
                        }

                        Text("GestióN · Gestión Documental")
                            .font(.footnote)
                            .foregroundStyle(EstiloInstitucional.textoSecundario)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 4)
                            .padding(.bottom, 24)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
                }
            }
            .navigationTitle("GestióN Documental")
            .navigationBarTitleDisplayMode(.inline)
            .barraInstitucional()
            .toolbar {
                if let alRegresar {
                    ToolbarItem(placement: .topBarLeading) {
                        BotonRegresarInicio(accion: alRegresar)
                    }
                }
            }
            .task {
                fotoPersonalizada = PerfilFotoStore.cargar()
                await vistaModelo.cargar()
            }
            .onChange(of: elementoFoto) { _, nuevoElemento in
                cargarFoto(nuevoElemento)
            }
        }
    }

    private func cargarFoto(_ elemento: PhotosPickerItem?) {
        guard let elemento else { return }

        Task {
            guard let datos = try? await elemento.loadTransferable(type: Data.self),
                  let imagen = UIImage(data: datos) else {
                return
            }

            fotoPersonalizada = imagen
            PerfilFotoStore.guardar(imagen)
        }
    }

    private func nombreMostrado(_ perfil: PerfilServicio) -> String {
        perfil.nombreCompleto.isEmpty
            ? (perfil.nombre.isEmpty ? "Usuario" : perfil.nombre)
            : perfil.nombreCompleto
    }

    private func hashFirma(_ perfil: PerfilServicio) -> String {
        let semilla = vistaModelo.qrTexto ?? "\(perfil.usuario)|\(perfil.correo)|gestion"
        let digesto = SHA256.hash(data: Data(semilla.utf8))
        let hexadecimal = digesto.map { String(format: "%02X", $0) }.joined()
        return String(hexadecimal.prefix(8))
    }
}

private struct PerfilEncabezado: View {

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("GESTIÓN DOCUMENTAL")
                .font(.caption.weight(.bold))
                .foregroundStyle(EstiloInstitucional.verdePrincipal)
                .tracking(1)

            Text("Mi perfil")
                .font(.largeTitle.weight(.bold))
                .foregroundStyle(EstiloInstitucional.textoPrincipal)

            Text("Consulta tu identidad institucional y las opciones de seguridad.")
                .font(.body)
                .foregroundStyle(EstiloInstitucional.textoSecundario)
        }
    }
}

private struct PerfilIdentidadCard: View {

    let nombre: String
    let usuario: String
    let rol: String
    let cargo: String
    let grado: String
    let area: String
    let correo: String
    let fotoPersonalizada: UIImage?
    @Binding var elementoFoto: PhotosPickerItem?

    var body: some View {
        PerfilTarjeta {
            VStack(spacing: 18) {
                PhotosPicker(selection: $elementoFoto, matching: .images) {
                    ZStack(alignment: .bottomTrailing) {
                        PerfilAvatar(fotoPersonalizada: fotoPersonalizada)

                        Image(systemName: "pencil.circle.fill")
                            .font(.title2)
                            .symbolRenderingMode(.palette)
                            .foregroundStyle(.white, EstiloInstitucional.verdePrincipal)
                            .background(.white, in: Circle())
                            .accessibilityHidden(true)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Cambiar foto de perfil")

                VStack(spacing: 6) {
                    Text(nombre)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(EstiloInstitucional.textoPrincipal)
                        .multilineTextAlignment(.center)

                    Text("@\(usuario)")
                        .font(.body)
                        .foregroundStyle(EstiloInstitucional.textoSecundario)

                    if !rol.isEmpty {
                        Text(rol.uppercased())
                            .font(.caption.weight(.bold))
                            .foregroundStyle(EstiloInstitucional.verdePrincipal)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 6)
                            .background(EstiloInstitucional.verdeFondoIcono, in: Capsule())
                    }
                }

                Divider()

                VStack(alignment: .leading, spacing: 18) {
                    Text("Información institucional")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(EstiloInstitucional.verdePrincipal)

                    PerfilDato(etiqueta: "Puesto / Cargo", valor: cargo)
                    PerfilDato(etiqueta: "Grado / Empleo", valor: grado)
                    PerfilDato(etiqueta: "Área / Unidad", valor: area)
                    PerfilDato(etiqueta: "Correo institucional", valor: correo)
                }
            }
        }
    }
}

private struct PerfilAvatar: View {

    let fotoPersonalizada: UIImage?

    var body: some View {
        Group {
            if let fotoPersonalizada {
                Image(uiImage: fotoPersonalizada)
                    .resizable()
                    .scaledToFill()
            } else {
                Image("logo_doc")
                    .resizable()
                    .scaledToFit()
                    .padding(12)
                    .background(EstiloInstitucional.verdeFondoIcono)
            }
        }
        .frame(width: 132, height: 132)
        .clipShape(Circle())
        .overlay(Circle().stroke(EstiloInstitucional.bordeSuave, lineWidth: 1))
    }
}

private struct PerfilDato: View {

    let etiqueta: String
    let valor: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(etiqueta)
                .font(.subheadline)
                .foregroundStyle(EstiloInstitucional.textoSecundario)

            Text(valor.isEmpty ? "—" : valor)
                .font(.body.weight(.semibold))
                .foregroundStyle(EstiloInstitucional.textoPrincipal)
        }
    }
}

private struct PerfilSeguridadCard: View {

    let autenticacionHabilitada: Bool
    let nombreBiometria: String

    var body: some View {
        PerfilTarjeta {
            VStack(alignment: .leading, spacing: 10) {
                Text("Seguridad del dispositivo")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(EstiloInstitucional.textoPrincipal)

                Text(
                    autenticacionHabilitada
                        ? "Puedes acceder con \(nombreBiometria) cuando la autenticación biométrica está habilitada. La contraseña no se muestra en este perfil."
                        : "Puedes acceder con huella o reconocimiento facial si la autenticación biométrica está habilitada. La contraseña no se muestra en este perfil."
                )
                .font(.body)
                .foregroundStyle(EstiloInstitucional.textoSecundario)
            }
        }
    }
}

private struct PerfilFirmaCard: View {

    let codigoQR: String?
    let hash: String
    let alSincronizar: () -> Void

    var body: some View {
        PerfilTarjeta {
            VStack(alignment: .leading, spacing: 16) {
                Text("Firma digital QR")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(EstiloInstitucional.textoPrincipal)

                Text("Utiliza tu código para el proceso de firma de recibido de documentos asignados en el sistema web.")
                    .font(.body)
                    .foregroundStyle(EstiloInstitucional.textoSecundario)

                if let codigoQR,
                   !codigoQR.isEmpty,
                   let imagenQR = QRCode.imagenDe(texto: codigoQR, tamaño: 200) {
                    Image(uiImage: imagenQR)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 200, height: 200)
                        .padding(16)
                        .background(.white, in: RoundedRectangle(cornerRadius: 20))
                        .overlay {
                            RoundedRectangle(cornerRadius: 20)
                                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
                        }
                        .frame(maxWidth: .infinity)

                    Text("Código emitido por el servidor institucional.")
                        .font(.caption)
                        .foregroundStyle(EstiloInstitucional.verdePrincipal)
                        .frame(maxWidth: .infinity)
                } else {
                    Label(
                        "No se pudo obtener el código QR institucional. Sincroniza tu perfil para intentarlo de nuevo.",
                        systemImage: "qrcode"
                    )
                    .font(.footnote)
                    .foregroundStyle(EstiloInstitucional.textoSecundario)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                Text("Hash Firma: \(hash)")
                    .font(.subheadline)
                    .foregroundStyle(EstiloInstitucional.textoSecundario)
                    .frame(maxWidth: .infinity)

                Button(action: alSincronizar) {
                    Label("Sincronizar perfil web", systemImage: "arrow.triangle.2.circlepath")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .overlay {
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(EstiloInstitucional.verdePrincipal, lineWidth: 1.5)
                        }
                }
                .buttonStyle(.plain)
                .foregroundStyle(EstiloInstitucional.verdePrincipal)
            }
        }
    }
}

private struct PerfilTarjeta<Contenido: View>: View {

    @ViewBuilder let contenido: Contenido

    var body: some View {
        contenido
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.white, in: RoundedRectangle(cornerRadius: 24))
            .overlay {
                RoundedRectangle(cornerRadius: 24)
                    .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
            }
            .shadow(color: Color.black.opacity(0.04), radius: 4, y: 2)
    }
}

private enum PerfilFotoStore {

    private static let nombreArchivo = "foto-perfil.jpg"

    static func cargar() -> UIImage? {
        guard let datos = try? Data(contentsOf: urlArchivo()) else {
            return nil
        }

        return UIImage(data: datos)
    }

    static func guardar(_ imagen: UIImage) {
        guard let datos = imagen.jpegData(compressionQuality: 0.85) else {
            return
        }

        try? datos.write(to: urlArchivo(), options: .atomic)
    }

    private static func urlArchivo() -> URL {
        let directorio = FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        )[0]

        return directorio.appendingPathComponent(nombreArchivo)
    }
}
